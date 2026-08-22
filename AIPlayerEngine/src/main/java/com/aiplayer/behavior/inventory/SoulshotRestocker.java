package com.aiplayer.behavior.inventory;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.core.GameStateMirror;
import com.aiplayer.core.GameStateMirror.BotStateSnapshot;
import com.aiplayer.protocol.L2JProtocol;

import java.io.IOException;
import com.aiplayer.core.BotSnapshot;
import com.aiplayer.behavior.inventory.ItemDatabase;
import com.aiplayer.behavior.inventory.ItemDatabase.ItemInfo;

/**
 * Monitors soulshot/spiritshot counts and triggers restocking actions.
 * Phase 0: Alert-only when shots run low; Phase 1: automated NPC purchase.
 *
 * Thresholds:
 * - Critical: < 50 shots — disable auto-shot to prevent spamming empty shots
 * - Low: < 200 shots — flag for restock, prefer lower-grade fallback
 * - Restock trigger: < 500 shots — attempt to buy from nearest grocery
 */
public final class SoulshotRestocker {

    private static final int CRITICAL_THRESHOLD = 50;
    private static final int LOW_THRESHOLD = 200;
    private static final int RESTOCK_THRESHOLD = 500;
    private static final int RESTOCK_TARGET = 2000; // aim for 2k shots

    private final String accountName;
    private final L2JProtocol protocol;
    private final InventoryTracker inventoryTracker;

    private volatile boolean criticalAlertSent = false;
    private volatile boolean lowAlertSent = false;
    private volatile long lastRestockAttempt = 0;
    private static final long RESTOCK_COOLDOWN_MS = 60000; // 1 min between attempts

    public SoulshotRestocker(String accountName, L2JProtocol protocol, InventoryTracker inventoryTracker) {
        this.accountName = accountName;
        this.protocol = protocol;
        this.inventoryTracker = inventoryTracker;
    }

    /**
     * Call every tick. Returns true if a restock action was triggered.
     */
    public boolean tick() {
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null || (self.hpMax > 0 ? self.hpCurrent * 100 / self.hpMax : 100) <= 0) return false;

        int grade = inventoryTracker.getWeaponGrade();
        boolean isMage = self.isMageClass;

        int count = isMage ? inventoryTracker.getSpiritshotCount() : inventoryTracker.getSoulshotCount();
        int blessedCount = isMage ? inventoryTracker.getBlessedSpiritshotCount() : 0;

        // Use blessed if available and mage
        if (isMage && blessedCount > CRITICAL_THRESHOLD) {
            // Prefer blessed spiritshots for mages when available
            ensureBlessedSpiritshotEnabled(grade);
        }

        // Critical: disable auto-shot to prevent empty-shot spam (detectable)
        if (count < CRITICAL_THRESHOLD) {
            if (!criticalAlertSent) {
                disableAutoShots(isMage);
                criticalAlertSent = true;
            }
            return false;
        } else {
            criticalAlertSent = false;
        }

        // Low: try fallback to lower grade
        if (count < LOW_THRESHOLD) {
            if (!lowAlertSent) {
                tryFallbackGrade(grade, isMage);
                lowAlertSent = true;
            }
        } else {
            lowAlertSent = false;
        }

        // Restock trigger
        if (count < RESTOCK_THRESHOLD) {
            long now = System.currentTimeMillis();
            if (now - lastRestockAttempt > RESTOCK_COOLDOWN_MS) {
                lastRestockAttempt = now;
                return attemptRestock(grade, isMage);
            }
        }

        return false;
    }

    /**
     * Called after respawn (Task 4) to re-enable shots if available.
     */
    public void restockAfterDeath() {
        criticalAlertSent = false;
        lowAlertSent = false;
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return;

        int grade = inventoryTracker.getWeaponGrade();
        boolean isMage = self.isMageClass;

        int count = isMage ? inventoryTracker.getSpiritshotCount() : inventoryTracker.getSoulshotCount();
        if (count > CRITICAL_THRESHOLD) {
            enableAutoShots(grade, isMage);
        }
    }

    public boolean isCritical() {
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return false;
        int count = self.isMageClass ? inventoryTracker.getSpiritshotCount() : inventoryTracker.getSoulshotCount();
        return count < CRITICAL_THRESHOLD;
    }

    public boolean isLow() {
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return false;
        int count = self.isMageClass ? inventoryTracker.getSpiritshotCount() : inventoryTracker.getSoulshotCount();
        return count < LOW_THRESHOLD;
    }

    // ------------------------------------------------------------------

    private void disableAutoShots(boolean isMage) {
        int grade = inventoryTracker.getWeaponGrade();
        if (isMage) {
            ItemDatabase.ItemInfo ss = ItemDatabase.findSpiritshotForGrade(grade, false);
            if (ss != null) sendAutoshot(ss.itemId, false);
            ItemDatabase.ItemInfo bss = ItemDatabase.findSpiritshotForGrade(grade, true);
            if (bss != null) sendAutoshot(bss.itemId, false);
        } else {
            ItemDatabase.ItemInfo ss = ItemDatabase.findSoulshotForGrade(grade);
            if (ss != null) sendAutoshot(ss.itemId, false);
        }
    }

    private void enableAutoShots(int grade, boolean isMage) {
        if (isMage) {
            // Prefer blessed, fallback normal
            ItemDatabase.ItemInfo bss = ItemDatabase.findSpiritshotForGrade(grade, true);
            if (bss != null && inventoryTracker.getSnapshot().hasItem(bss.itemId, CRITICAL_THRESHOLD)) {
                sendAutoshot(bss.itemId, true);
            } else {
                ItemDatabase.ItemInfo ss = ItemDatabase.findSpiritshotForGrade(grade, false);
                if (ss != null) sendAutoshot(ss.itemId, true);
            }
        } else {
            ItemDatabase.ItemInfo ss = ItemDatabase.findSoulshotForGrade(grade);
            if (ss != null) sendAutoshot(ss.itemId, true);
        }
    }

    private void ensureBlessedSpiritshotEnabled(int grade) {
        ItemDatabase.ItemInfo bss = ItemDatabase.findSpiritshotForGrade(grade, true);
        if (bss != null && inventoryTracker.getSnapshot().hasItem(bss.itemId, CRITICAL_THRESHOLD)) {
            if (!inventoryTracker.isAutoSpiritshotEnabled()) {
                sendAutoshot(bss.itemId, true);
            }
        }
    }

    private void tryFallbackGrade(int currentGrade, boolean isMage) {
        // Try to enable lower-grade shots if current grade is empty
        InventorySnapshot inv = inventoryTracker.getSnapshot();
        for (int g = currentGrade - 1; g >= 0; g--) {
            if (isMage) {
                ItemDatabase.ItemInfo ss = ItemDatabase.findSpiritshotForGrade(g, false);
                if (ss != null && inv.hasItem(ss.itemId, CRITICAL_THRESHOLD)) {
                    sendAutoshot(ss.itemId, true);
                    return;
                }
            } else {
                ItemDatabase.ItemInfo ss = ItemDatabase.findSoulshotForGrade(g);
                if (ss != null && inv.hasItem(ss.itemId, CRITICAL_THRESHOLD)) {
                    sendAutoshot(ss.itemId, true);
                    return;
                }
            }
        }
    }

    private boolean attemptRestock(int grade, boolean isMage) {
        // Phase 0: Log alert only. Phase 1: Navigate to grocery NPC and buy.
        // For now, return false to indicate automated restock not yet implemented.
        // The AI Player will continue farming until shots deplete, then fallback/disable.
        return false;
    }

    /**
     * Send an auto-soulshot toggle, swallowing the checked IOException from the
     * protocol layer (the caller is a best-effort tick, not a place that can
     * meaningfully recover from a dead socket).
     */
    private void sendAutoshot(int itemId, boolean on) {
        try {
            protocol.sendAutoSoulShot(itemId, on);
        } catch (IOException e) {
            // best-effort; a dead socket is surfaced upstream by the connection owner
        }
    }
}

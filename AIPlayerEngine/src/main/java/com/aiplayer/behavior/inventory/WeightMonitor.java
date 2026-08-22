package com.aiplayer.behavior.inventory;
import com.aiplayer.core.BotSnapshot;
import com.aiplayer.core.GameStateMirror;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */


/**
 * Monitors inventory weight and triggers responses:
 * - 80%+ overweight: slow movement, consider selling/dropping junk
 * - 90%+ critical: stop looting, use Scroll of Escape to town
 * - 95%+ emergency: force SoE, alert operator
 *
 * Weight values come from StatusUpdate packets (current / max).
 */
public final class WeightMonitor {

    private static final double OVERWEIGHT_THRESHOLD = 80.0;
    private static final double CRITICAL_WEIGHT_THRESHOLD = 90.0;
    private static final double EMERGENCY_WEIGHT_THRESHOLD = 95.0;
    private static final long CHECK_INTERVAL_MS = 5000; // check every 5s
    private static final long ALERT_COOLDOWN_MS = 30000; // 30s between alerts

    private final String accountName;
    private final InventorySnapshot snapshot;

    private volatile boolean isOverweight = false;
    private volatile boolean isCritical = false;
    private volatile long lastAlertTime = 0;
    private volatile long lastCheckTime = 0;
    private volatile boolean autoLootDisabled = false;

    public WeightMonitor(String accountName, InventorySnapshot snapshot) {
        this.accountName = accountName;
        this.snapshot = snapshot;
    }

    /**
     * Call every tick (lightweight — actual check throttled).
     */
    public void tick() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime < CHECK_INTERVAL_MS) return;
        lastCheckTime = now;
        check();
    }

    /**
     * Force immediate weight check.
     */
    public void check() {
        double pct = snapshot.getWeightPercent();
        isOverweight = pct >= OVERWEIGHT_THRESHOLD;
        isCritical = pct >= CRITICAL_WEIGHT_THRESHOLD;

        if (pct >= EMERGENCY_WEIGHT_THRESHOLD) {
            handleEmergencyWeight();
            return;
        }

        if (pct >= CRITICAL_WEIGHT_THRESHOLD) {
            handleCriticalWeight();
            return;
        }

        if (pct >= OVERWEIGHT_THRESHOLD) {
            handleOverweight();
            return;
        }

        // Normal weight — re-enable auto-loot if disabled
        if (autoLootDisabled && pct < OVERWEIGHT_THRESHOLD - 5.0) {
            autoLootDisabled = false;
        }
    }

    public boolean isOverweight() {
        return isOverweight;
    }

    public boolean isCritical() {
        return isCritical;
    }

    public boolean shouldDisableLooting() {
        return isCritical || autoLootDisabled;
    }

    public double getWeightPercent() {
        return snapshot.getWeightPercent();
    }

    // ------------------------------------------------------------------

    private void handleOverweight() {
        // Phase 0: Disable auto-loot to prevent further weight gain
        autoLootDisabled = true;
        maybeAlert("OVERWEIGHT: " + String.format("%.1f%%", snapshot.getWeightPercent()) +
                   " — auto-loot disabled");
    }

    private void handleCriticalWeight() {
        autoLootDisabled = true;
        maybeAlert("CRITICAL WEIGHT: " + String.format("%.1f%%", snapshot.getWeightPercent()) +
                   " — considering Scroll of Escape");
        // Phase 1: Trigger town return to sell
        // For now, just alert and disable looting
    }

    private void handleEmergencyWeight() {
        autoLootDisabled = true;
        maybeAlert("EMERGENCY WEIGHT: " + String.format("%.1f%%", snapshot.getWeightPercent()) +
                   " — forcing escape!");
        // Phase 1: Use SoE immediately, then navigate to grocery/sell
        // Phase 0: Alert only
    }

    private void maybeAlert(String message) {
        long now = System.currentTimeMillis();
        if (now - lastAlertTime > ALERT_COOLDOWN_MS) {
            lastAlertTime = now;
            // Phase 0: System.err or logging framework
            System.err.println("[WeightMonitor] " + accountName + ": " + message);
        }
    }
}

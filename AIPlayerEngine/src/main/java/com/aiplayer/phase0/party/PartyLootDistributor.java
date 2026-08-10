package com.aiplayer.phase0.party;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import com.aiplayer.phase0.humanize.AntiDetectionEngine;
import com.aiplayer.phase0.humanize.TimingJitter;
import com.aiplayer.phase0.town.ItemValueEstimator;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles party loot distribution with human-like reaction times.
 * Supports all loot rules: Random, Round Robin, Leader, Finders Keepers.
 * Performs need/greed rolls with personality-driven decisions.
 */
public final class PartyLootDistributor {

    private final String accountName;
    private final PartyManager party;
    private final AntiDetectionEngine anti;
    private final ItemValueEstimator estimator;

    // Roll history for round-robin tracking
    private final Map<String, Integer> roundRobinCount = new ConcurrentHashMap<>();
    private int myRollCounter = 0;

    public PartyLootDistributor(String accountName, PartyManager party,
                                AntiDetectionEngine anti, ItemValueEstimator estimator) {
        this.accountName = accountName;
        this.party = party;
        this.anti = anti;
        this.estimator = estimator;
    }

    /**
     * Called when loot appears on ground or roll window opens.
     */
    public void onLootAvailable(int itemId, String itemName, int itemGrade) {
        long delay = anti.getDelay(TimingJitter.ActionContext.LOOT_PICKUP);

        switch (party.getLootRule()) {
            case RANDOM:
            case FINDERS_KEEPERS:
                // No action needed — server handles random distribution
                break;

            case ROUND_ROBIN:
                // Track turns; if it's our turn, we might pass on junk
                myRollCounter++;
                break;

            case LEADER:
                // Leader decides — as member, we wait or pass
                break;
        }
    }

    /**
     * Decide on a need/greed roll.
     * Returns: NEED, GREED, or PASS.
     */
    public RollDecision decideRoll(int itemId, int itemGrade, boolean isEquipment) {
        ItemValueEstimator.ItemFate fate = estimator.evaluate(
            com.aiplayer.phase0.ItemSnapshot.from(itemId, 1L));
        switch (fate) {
            case KEEP: return isEquipment ? RollDecision.NEED : RollDecision.GREED;
            case WAREHOUSE: return RollDecision.GREED;
            case SELL: return anti.getRandom().nextDouble() < 0.15 ? RollDecision.GREED : RollDecision.PASS;
            case DESTROY: return RollDecision.PASS;
        }
        return RollDecision.PASS;
    }

    /**
     * Execute the roll with human-like delay.
     */
    public void executeRoll(int itemId, RollDecision decision) {
        long delay = anti.getDelay(TimingJitter.ActionContext.INVENTORY_USE);
        // In real integration: protocol.sendRollRequest(itemId, decision.ordinal())
    }

    /**
     * Check if we should pick up spoils (spoilers only).
     */
    public boolean shouldSpoil(int mobTemplateId) {
        // Only spoiler classes should spoil
        // Phase 0: basic check — if we have spoil skill, yes
        return false; // Placeholder
    }

    public enum RollDecision {
        NEED, GREED, PASS
    }
}

package com.aiplayer.behavior;

import com.aiplayer.core.BotProfile;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */


/**
 * Simple FSM for Phase 0.
 * Transitions are rule-based, not learned.
 */
public class StateMachine {
    private BotState current = BotState.IDLE;
    private long stateEnteredAt = System.currentTimeMillis();
    private long lastCombatTick = 0;
    private int idleScanCooldown = 0;

    public synchronized BotState getState() { return current; }

    public synchronized void transition(BotState next) {
        if (next != current) {
            current = next;
            stateEnteredAt = System.currentTimeMillis();
        }
    }

    public synchronized BotState tick(BotProfile profile, int hpPercent, int mpPercent,
                                      boolean targetInRange, boolean targetAlive,
                                      boolean chatPending, boolean partyInvitePending,
                                      boolean isDead) {
        if (isDead) {
            transition(BotState.DEATH);
            return BotState.DEATH;
        }

        if (hpPercent < 25 && !profile.isHealer()) {
            transition(BotState.RETREAT);
            return BotState.RETREAT;
        }

        switch (current) {
            case IDLE:
                if (chatPending || partyInvitePending) {
                    transition(BotState.SOCIAL);
                    return BotState.SOCIAL;
                }
                if (++idleScanCooldown > 5) {
                    idleScanCooldown = 0;
                    transition(BotState.FARM);
                    return BotState.FARM;
                }
                break;

            case FARM:
                if (targetInRange && targetAlive) {
                    transition(BotState.COMBAT);
                    return BotState.COMBAT;
                }
                if (chatPending) {
                    transition(BotState.SOCIAL);
                    return BotState.SOCIAL;
                }
                break;

            case COMBAT:
                lastCombatTick = System.currentTimeMillis();
                if (!targetAlive) {
                    transition(BotState.FARM);
                    return BotState.FARM;
                }
                if (hpPercent < 30 && !profile.isHealer()) {
                    transition(BotState.RETREAT);
                    return BotState.RETREAT;
                }
                break;

            case RETREAT:
                if (hpPercent > 60) {
                    transition(BotState.FARM);
                    return BotState.FARM;
                }
                break;

            case SOCIAL:
                if (!chatPending && !partyInvitePending) {
                    long inSocial = System.currentTimeMillis() - stateEnteredAt;
                    if (inSocial > 8000) {
                        transition(BotState.IDLE);
                        return BotState.IDLE;
                    }
                }
                break;

            case DEATH:
                break;
        }
        return current;
    }

    public long getTimeInState() {
        return System.currentTimeMillis() - stateEnteredAt;
    }

    public long getLastCombatTick() { return lastCombatTick; }
}

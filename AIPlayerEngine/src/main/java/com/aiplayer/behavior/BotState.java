package com.aiplayer.behavior;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

/**
 * Phase 0 Finite State Machine states.
 */
public enum BotState {
    IDLE,
    FARM,
    COMBAT,
    DEATH,
    SOCIAL,
    RETREAT
}

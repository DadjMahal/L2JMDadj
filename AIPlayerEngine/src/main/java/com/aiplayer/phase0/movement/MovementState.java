package com.aiplayer.phase0.movement;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

/**
 * Movement states for AI Player.
 */
public enum MovementState {
    IDLE,
    MOVING,
    ARRIVING,
    STUCK,
    RECOVERING,
    KITING,
    FOLLOWING,
    FLEEING
}

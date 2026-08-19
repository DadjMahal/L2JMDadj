package com.aiplayer.core;

/**
 * AI Player States
 */
public enum AIPlayerState {
    OFFLINE("Offline"),
    LOGGING_IN("Logging In"),
    IN_GAME("In Game"),
    IDLE("Idle"),
    MOVING("Moving"),
    COMBAT("In Combat"),
    TRADING("Trading"),
    TRADING_WAITING("Waiting Trade"),
    TRADING_CONFIRM("Confirm Trade"),
    QUESTING("Questing"),
    CRAFTING("Crafting"),
    SOCIAL("Socializing"),
    DEAD("Dead"),
    DISCONNECTED("Disconnected");

    private final String description;

    AIPlayerState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

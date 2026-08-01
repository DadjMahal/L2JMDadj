package com.aiplayer.engine;

/**
 * AI Actions
 */
public class AIAction {
    public enum ActionType {
        MOVE,
        ATTACK,
        USE_ITEM,
        BUY,
        SELL,
        INTERACT_NPC,
        ACCEPT_QUEST,
        COMPLETE_QUEST,
        TURN_IN_QUEST,
        JOIN_PARTY,
        LEAVE_PARTY,
        JOIN_CLAN,
        CHAT,
        REST,
        INSPECT,
        PICKUP,
        CRAFT,
        TRADE,
        STAND,
        SIT,
        HUNT,           // Hunt monsters/items
        PARTY_INVITE,   // Invite to party
        COMBAT_MODE,    // Toggle auto combat
        STOP_ATTACK     // Stop current attack
    }
    
    private final ActionType type;
    private final Object[] parameters;
    private final long timestamp;
    
    public AIAction(ActionType type, Object... parameters) {
        this.type = type;
        this.parameters = parameters;
        this.timestamp = System.currentTimeMillis();
    }
    
    public ActionType getType() {
        return type;
    }
    
    public Object[] getParameters() {
        return parameters;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.name());
        if (parameters != null && parameters.length > 0) {
            sb.append("(");
            for (int i = 0; i < parameters.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(parameters[i]);
            }
            sb.append(")");
        }
        return sb.toString();
    }
}
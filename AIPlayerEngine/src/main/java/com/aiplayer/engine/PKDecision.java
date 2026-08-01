package com.aiplayer.engine;

import java.util.logging.Logger;

/**
 * Task 97: Player-kill (PK) decision engine - attack on sight vs. flag only
 *
 * Determines combat engagement policy based on:
 * - Player karma level (evil/neutral/good)
 * - PK flag status (has the player attacked recently?)
 * - Safe zone location (towns, GvS, castle gates)
 * - Target's protection status
 * - Own protection status
 */
public class PKDecision {
    private static final Logger LOGGER = Logger.getLogger(PKDecision.class.getName());
    
    public enum DecisionType { ATTACK, FLEE, FLAG_ONLY, OBSERVE }
    
    // Simulated player state for demo
    public static class PKBot {
        private int karma;
        private boolean hasPkFlag;
        private boolean isProtected;
        private int x, y;
        
        public PKBot(int karma, boolean hasPkFlag, boolean isProtected, int x, int y) {
            this.karma = karma;
            this.hasPkFlag = hasPkFlag;
            this.isProtected = isProtected;
            this.x = x;
            this.y = y;
        }
        
        public int getKarma() { return karma; }
        public boolean hasPkFlag() { return hasPkFlag; }
        public boolean isProtected() { return isProtected; }
        public int getX() { return x; }
        public int getY() { return y; }
        public void setKarma(int karma) { this.karma = karma; }
        public void setHasPkFlag(boolean flag) { this.hasPkFlag = flag; }
    }
    
    // Decision result
    public static class Decision {
        public final DecisionType type;
        public final String reason;
        
        public Decision(DecisionType type, String reason) {
            this.type = type;
            this.reason = reason;
        }
        
        @Override
        public String toString() {
            return "Decision{type=" + type + ", reason='" + reason + "'}";
        }
    }
    
    /**
     * Make PK decision. Town coordinates 1-6 are safe zones.
     */
    public static Decision makeDecision(PKBot attacker, PKBot target) {
        // Safe zone check
        if (isSafeZone(attacker.getX(), attacker.getY())) {
            return new Decision(DecisionType.FLEE, "In safe zone - cannot PK");
        }
        if (isSafeZone(target.getX(), target.getY())) {
            LOGGER.info("PKDecision: Target in safe zone at (" + target.getX() + "," + target.getY() + ")");
            return new Decision(DecisionType.FLEE, "Target in safe zone");
        }
        
        // Protection check
        if (target.isProtected) {
            return new Decision(DecisionType.FLEE, "Target is protected");
        }
        if (attacker.isProtected) {
            return new Decision(DecisionType.OBSERVE, "Self is protected - observe only");
        }
        
        // Karma-based PK policy
        if (attacker.getKarma() <= 0 && target.getKarma() < 0) {
            // Good or neutral player only PKs evil targets
            return new Decision(DecisionType.ATTACK, "Target is evil - legal PK");
        }
        
        if (attacker.getKarma() > 0 && target.getKarma() >= 0) {
            // Evil player PKs only evil targets
            return new Decision(DecisionType.FLAG_ONLY, "Cross-karma - flag required");
        }
        
        // Same alignment - can attack
        return new Decision(DecisionType.ATTACK, "Engage target");
    }
    
    private static boolean isSafeZone(int x, int y) {
        // Simplified: towns at coordinates 1-6
        return x >= 1 && x <= 6 && y >= 1 && y <= 6;
    }
    
    // Demo test
    public static void main(String[] args) {
        System.out.println("=== PK Decision Demo ===");
        PKBot bot1 = new PKBot(-5000, false, false, 100, 100); // Good karma
        PKBot bot2 = new PKBot(-10000, false, false, 150, 150); // Evil karma
        PKBot bot3 = new PKBot(0, false, false, 1, 1); // In safe zone
        
        System.out.println("Good vs Evil: " + makeDecision(bot1, bot2));
        System.out.println("Safe zone: " + makeDecision(bot1, bot3));
        System.out.println("Evil vs Protected: " + makeDecision(bot2, new PKBot(-5000, false, true, 200, 200)));
    }
}

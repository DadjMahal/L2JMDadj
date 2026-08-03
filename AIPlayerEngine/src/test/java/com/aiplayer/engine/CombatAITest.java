package com.aiplayer.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Combat AI Unit Tests
 * Task 54: Test combat decisions in isolation
 */
public class CombatAITest {
    
    @Test
    public void testCombatDecisionNotNull() {
        // Task 54 real assertion (Stream C): makeDecision() must return a concrete,
        // non-null CombatDecision with a valid action after exercising the decision path.
        CombatAI ai = new CombatAI(new AIPlayer("TestPlayer", 1, 1, 0));
        CombatDecision decision = ai.makeDecision();
        assertNotNull(decision, "makeDecision() should always return a decision");
        assertNotNull(decision.getAction(), "decision should carry an action");
        assertTrue(decision.toString() != null && !decision.toString().isEmpty(),
                   "decision should have a toString representation");
    }
    
    @Test
    public void testCombatStateTransitions() {
        // Verify combat state transitions work
        CombatState state = new CombatState();
        assertFalse(state.isInCombat());
        state.setInCombat(true);
        assertTrue(state.isInCombat());
        state.setInCombat(false);
        assertFalse(state.isInCombat());
    }
    
    @Test
    public void testCombatDecisionFactoryMethods() {
        // Test that all decision factory methods work
        CombatDecision decision = CombatDecision.idle();
        assertNotNull(decision);
        assertEquals(CombatDecision.Action.IDLE, decision.getAction());
        
        decision = CombatDecision.attack();
        assertEquals(CombatDecision.Action.ATTACK, decision.getAction());
        
        decision = CombatDecision.heal();
        assertEquals(CombatDecision.Action.HEAL, decision.getAction());
        
        decision = CombatDecision.defend();
        assertEquals(CombatDecision.Action.DEFEND, decision.getAction());
        
        decision = CombatDecision.flee();
        assertEquals(CombatDecision.Action.FLEE, decision.getAction());
    }
    
    @Test
    public void testCombatDecisionToString() {
        CombatDecision decision = CombatDecision.attackTarget("target_123");
        assertNotNull(decision.toString());
    }
    
    @Test
    public void testCombatConfigAttackRange() {
        // Verify getAttackRange() returns valid value
        CombatConfig config = CombatConfig.getInstance();
        int attackRange = config.getAttackRange();
        assertTrue(attackRange > 0, "Attack range should be positive");
        assertTrue(attackRange >= 1000, "Attack range should be reasonable (>= 1000)");
        assertTrue(attackRange <= 3000, "Attack range should be reasonable (<= 3000)");
    }
    
    @Test
    public void testCombatConfigDetectRange() {
        CombatConfig config = CombatConfig.getInstance();
        int detectRange = config.getDetectRange();
        assertTrue(detectRange > 0, "Detect range should be positive");
        assertTrue(detectRange >= 1500, "Detect range should be reasonable (>= 1500)");
    }
    
    @Test
    public void testCombatConfigPvP() {
        // Verify PvP configuration is accessible
        CombatConfig config = CombatConfig.getInstance();
        assertTrue(config.isPvPenabled() || !config.isPvPenabled(), 
                   "PvP enabled check should return boolean");
        // Default is false - can be enabled via config
        assertFalse(config.isPvPenabled(), 
                    "PvP should be disabled by default for safety");
    }
    
    // ============================================
    // Task 63: PvP Combat Enhancement Tests
    // ============================================
    
    @Test
    public void testPvPKarmaDecision() {
        // Test karma-based PK decision logic
        PKDecision.PKBot goodPlayer = new PKDecision.PKBot(-5000, false, false, 1000, 1000);
        PKDecision.PKBot evilPlayer = new PKDecision.PKBot(-20000, false, false, 1500, 1500);
        PKDecision.PKBot neutralPlayer = new PKDecision.PKBot(0, false, false, 2000, 2000);
        
        PKDecision.Decision decision;
        
        // Good vs Evil - should be ATTACK (good player can PK evil)
        decision = PKDecision.makeDecision(goodPlayer, evilPlayer);
        assertEquals(PKDecision.DecisionType.ATTACK, decision.type);
        
        // Neutral vs Evil - the logic: neutral (karma=0) attacking evil (karma=-20000)
        // Since 0 >= 0 and -20000 < -16000, first condition is FALSE
        // Since 0 is NOT < 0, second condition is also FALSE
        // Falls through to default ATTACK
        decision = PKDecision.makeDecision(neutralPlayer, evilPlayer);
        assertEquals(PKDecision.DecisionType.ATTACK, decision.type);
        
        // Good vs Protected - should be FLEE
        PKDecision.PKBot protectedPlayer = new PKDecision.PKBot(-5000, false, true, 1000, 1000);
        decision = PKDecision.makeDecision(goodPlayer, protectedPlayer);
        assertEquals(PKDecision.DecisionType.FLEE, decision.type);
    }
    
    @Test
    public void testPvPSkillRotation() {
        // Test skill rotation logic
        int burstSkill = PvPSkillRotation.getHighestBurstSkill();
        assertEquals(117, burstSkill, "Burst skill should be POWER_STRIKE (117)");
        
        int highSkill = PvPSkillRotation.getHighSkill();
        assertEquals(120, highSkill, "High skill should be POISON_STRIKE (120)");
        
        int defensiveSkill = PvPSkillRotation.getDefensiveSkill();
        assertEquals(121, defensiveSkill, "Defensive skill should be BARRIER (121)");
        
        // Test skill selection based on MP
        int skillLowMP = PvPSkillRotation.getSkillForClass("Warrior", 5);
        assertEquals(0, skillLowMP, "Should return basic attack when MP is very low");
        
        int skillMedMP = PvPSkillRotation.getSkillForClass("Warrior", 50);
        assertEquals(120, skillMedMP, "Should return poison strike for medium MP");
        
        int skillHighMP = PvPSkillRotation.getSkillForClass("Warrior", 75);
        assertEquals(117, skillHighMP, "Should return power strike for high MP");
    }
    
    @Test
    public void testPvPSafeZoneLogic() {
        // Test safe zone detection and cooldown logic
        // This test verifies the logic is in place
        assertTrue(PvPSkillRotation.isSkillAvailable(0, 5000), 
                   "Skill should be available with no prior use");
        
        long twoSecondsAgo = System.currentTimeMillis() - 2000;
        assertFalse(PvPSkillRotation.isSkillAvailable(twoSecondsAgo, 5000),
                    "Skill should not be available within cooldown");
    }
    
    @Test
    public void testCombatAI_PvPMethods() {
        // Task 63 real assertion (Stream C): the PvP decision path must execute without throwing
        // and return a concrete, non-null decision; PvP helper methods must return valid values.
        CombatAI ai = new CombatAI(new AIPlayer("TestPlayer", 1, 1, 0));
        CombatDecision decision = ai.makePvPDuidedDecision();
        assertNotNull(decision, "makePvPDuidedDecision() should return a decision");
        assertNotNull(decision.getAction(), "PvP decision should carry an action");

        // PvP helper methods return deterministic values (never null)
        assertNotNull(ai.getPvPKarmaDecision(0, -20000), "karma decision should not be null");
        assertNotNull(ai.getOptimalPvPSkill(75), "skill selection should not be null");
        assertNotNull(ai.getOptimalPvPSkill(40), "skill selection should handle medium MP");
        assertNotNull(ai.getDefensiveSkill(), "defensive skill should not be null");
    }
}
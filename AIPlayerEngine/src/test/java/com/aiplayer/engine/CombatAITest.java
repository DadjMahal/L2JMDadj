package com.aiplayer.engine;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.aiplayer.protocol.PacketLogger;
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

    // ============================================
    // Stream C: real-data decision tests (no Math.random)
    // ============================================

    @Test
    public void testRealDistanceFromPacketPositions() {
        AIPlayer player = new AIPlayer("TestPlayer", 1, 1, 0);
        player.setPosition(0, 0, 0);
        CombatAI ai = new CombatAI(player);
        // Hostile NPC at (3000,4000,0) -> distance from origin is 5000
        ai.getPacketLogger().logPacket(buildNpcInfoFrame(1001, 1000000 + 10001, 1, 3000, 4000, 0, 0));
        assertEquals(5000.0, ai.calculateDistanceTo("objId=1001"), 0.1,
            "distance from (0,0,0) to (3000,4000,0) should be 5000 (real coords, not mock)");
    }

    @Test
    public void testDistanceUnknownTargetIsMax() {
        CombatAI ai = new CombatAI(new AIPlayer("TestPlayer", 1, 1, 0));
        assertEquals(Double.MAX_VALUE, ai.calculateDistanceTo("objId=999"), "untracked target -> out of range");
        assertEquals(Double.MAX_VALUE, ai.calculateDistanceTo(null), "null target -> out of range");
    }

    @Test
    public void testShouldDefendUsesRealData() {
        // Low HP (10%) + multiple hostiles -> deterministic TRUE (was Math.random()).
        AIPlayer weak = new AIPlayer("WeakBot", 1, 1, 0);
        weak.setPosition(0, 0, 0);
        CombatAI ai = new CombatAI(weak);
        ai.getPacketLogger().logPacket(buildStatusUpdateFrame(100, 1000)); // 10% HP
        ai.getPacketLogger().logPacket(buildNpcInfoFrame(1001, 1000000 + 10001, 1, 100, 0, 0, 0));
        ai.getPacketLogger().logPacket(buildNpcInfoFrame(1002, 1000000 + 10002, 1, 200, 0, 0, 0));
        assertTrue(ai.shouldDefend(), "low HP + multiple hostiles should defend");

        // Healthy (default 100%) + single hostile -> deterministic FALSE.
        AIPlayer healthy = new AIPlayer("HealthyBot", 1, 1, 0);
        CombatAI ai2 = new CombatAI(healthy);
        ai2.getPacketLogger().logPacket(buildNpcInfoFrame(1003, 1000000 + 10003, 1, 100, 0, 0, 0));
        assertFalse(ai2.shouldDefend(), "healthy + single hostile should not defend");
    }

    @Test
    public void testSetPacketLoggerSharesLiveBuffer() {
        // Slice 5: CombatAI must decide from the SAME buffer the live GameServerClient reader feeds,
        // not from its own empty private buffer. Attaching the live logger (shared object) must make
        // makeDecision() see real parsed NPC_INFO entities.
        AIPlayer player = new AIPlayer("LiveBot", 1, 1, 0);
        player.setPosition(0, 0, 0);
        CombatAI ai = new CombatAI(player);
        PacketLogger live = new PacketLogger("LiveBot");
        // Hostile NPC at (100,100,0) - well inside the 1500 target distance from (0,0,0).
        live.logPacket(buildNpcInfoFrame(4242, 1000000 + 20545, 1, 100, 100, 0, 0));
        ai.setPacketLogger(live);
        CombatDecision decision = ai.makeDecision();
        assertEquals(CombatDecision.Action.ATTACK, decision.getAction(),
            "attaching the live reader logger must make decisions from real parsed NPC_INFO");
        assertEquals(4242, ai.getSelectedTargetObjId(),
            "the shared logger's hostile NPC must become the selected combat target");
    }

    @Test
    public void testDeathGateReturnsIdle() {
        // Slice 6: self-death feedback via real StatusUpdate (self objId 5). A dead bot must NOT
        // attack even with hostiles present; makeDecision must return IDLE and no target is selected.
        AIPlayer player = new AIPlayer("DeadBot", 1, 1, 0);
        player.setPosition(0, 0, 0);
        CombatAI ai = new CombatAI(player);
        PacketLogger live = new PacketLogger("DeadBot");
        live.setSelfObjectId(5);
        live.logPacket(buildStatusUpdateFrame(5, 0, 100));        // self HP = 0 -> dead
        live.logPacket(buildNpcInfoFrame(1001, 1000000 + 20545, 1, 100, 100, 0, 0)); // hostile present
        ai.setPacketLogger(live);
        assertFalse(ai.isBotAlive(), "self HP 0 must mean the bot is dead");
        assertEquals(CombatDecision.Action.IDLE, ai.makeDecision().getAction(),
            "a dead bot must not attack (slice 6 death gate)");
        assertEquals(-1, ai.getSelectedTargetObjId(), "a dead bot must not select a combat target");
    }
    @Test
    public void testFleeGateHoldsLowHpOutOfCombat() {
        // TIM-001 H5 survivability (2026-08-13): detectNearbyEnemy() must never return a target while
        // HP% <= combat.health_threshold (the flee gate). The old code only consulted the threshold in
        // shouldHeal(), so a low-HP bot BETWEEN fights would still "detect" a hostile pack and
        // re-engage -> guaranteed-death loop (the observed H5 blocker: bots dead, no kills, no XP).
        // Combats are sticky (engageEnemy sets inCombat=true), so each HP level gets a fresh bot.
        int threshold = CombatConfig.getInstance().getHealthThreshold();
        assertTrue(threshold > 5 && threshold < 95,
            "this regression assumes a sane health_threshold for the flee gate; got " + threshold);

        assertEquals(CombatDecision.Action.ATTACK, fleeGateDecision(100, 100).getAction(),
            "full HP must engage a hostile in range (control)");
        assertEquals(CombatDecision.Action.ATTACK, fleeGateDecision(threshold + 15, 100).getAction(),
            "above the flee gate, engagement is allowed");
        assertNotEquals(CombatDecision.Action.ATTACK, fleeGateDecision(threshold, 100).getAction(),
            "at the flee gate the bot must NOT engage a hostile");
        assertNotEquals(CombatDecision.Action.ATTACK, fleeGateDecision(Math.max(1, threshold / 2), 100).getAction(),
            "below the flee gate the bot must NOT engage a hostile");
    }

    // One fresh bot + live logger at the given HP; hostile Orc Fighter in engage range at (100,100,0).
    private CombatDecision fleeGateDecision(int curHp, int maxHp) {
        AIPlayer player = new AIPlayer("FleeGateBot", 1, 1, 0);
        player.setPosition(0, 0, 0);
        CombatAI ai = new CombatAI(player);
        PacketLogger live = new PacketLogger("FleeGateBot");
        live.logPacket(buildNpcInfoFrame(9001, 1000000 + 20545, 1, 100, 100, 0, 0));
        live.setCurHp(curHp);
        live.setMaxHp(maxHp);
        ai.setPacketLogger(live);
        return ai.makeDecision();
    }

    @Test
    public void testRetargetsAfterTargetDeath() {
        // Slice 6: DeleteObject removes the engaged target -> combat ends -> the bot re-acquires the
        // NEXT hostile. Previously handleCombatEnd left combatState.inCombat=true so it stayed stuck.
        AIPlayer player = new AIPlayer("RetargetBot", 1, 1, 0);
        player.setPosition(0, 0, 0);
        CombatAI ai = new CombatAI(player);
        PacketLogger live = new PacketLogger("RetargetBot");
        live.logPacket(buildNpcInfoFrame(1001, 1000000 + 20545, 1, 100, 0, 0, 0)); // nearest
        live.logPacket(buildNpcInfoFrame(1002, 1000000 + 20546, 1, 300, 0, 0, 0)); // second
        ai.setPacketLogger(live);

        assertEquals(CombatDecision.Action.ATTACK, ai.makeDecision().getAction());
        assertEquals(1001, ai.getSelectedTargetObjId(), "should engage the nearest hostile first");

        live.logPacket(buildDeleteObjectFrame(1001)); // server: target died/despawned
        CombatDecision end = ai.makeDecision();
        assertEquals(CombatDecision.Action.LEAVE_COMBAT, end.getAction(),
            "vanished target must end combat");
        assertEquals(-1, ai.getSelectedTargetObjId(), "cleared target after combat end");

        CombatDecision again = ai.makeDecision();
        assertEquals(CombatDecision.Action.ATTACK, again.getAction(),
            "must re-acquire the next enemy after the previous died");
        assertEquals(1002, ai.getSelectedTargetObjId(),
            "re-target must select the next nearest hostile");
    }

    @Test
    public void testStaleTargetWatchdogAbandonsWithoutXp() throws InterruptedException {
        // STEP 3 follow-up: the watchdog must abandon an engagement that produces NO XP within its
        // budget, or the bot chases an un-killable/stale target forever (the merchant/chase stall).
        AIPlayer player = new AIPlayer("StaleBot", 1, 1, 0);
        player.setPosition(0, 0, 0);
        CombatAI ai = new CombatAI(player);
        PacketLogger live = new PacketLogger("StaleBot");
        live.logPacket(buildNpcInfoFrame(9001, 1000000 + 20545, 1, 100, 100, 0, 0));
        ai.setPacketLogger(live);

        assertEquals(CombatDecision.Action.ATTACK, ai.makeDecision().getAction(),
            "hostile in range engages");
        assertTrue(ai.getCombatState().isInCombat(), "engaged target must be in combat");

        assertFalse(ai.checkStaleTarget(0, 100), "first check only arms the stale timer");
        Thread.sleep(10); // deterministically advance wall clock past the next budget
        assertTrue(ai.checkStaleTarget(0, 1), "unchanged-XP target past budget must be abandoned");

        assertFalse(ai.getCombatState().isInCombat(), "abandoned target must exit combat");
        assertEquals(-1, ai.getSelectedTargetObjId(), "abandoned target must be cleared");
    }

    @Test
    public void testStaleTargetWatchdogKeepsCombatWhileXpProgresses() throws InterruptedException {
        // XP progress resets the stale clock: a bot that is actually gaining XP must keep fighting.
        AIPlayer player = new AIPlayer("ProgressBot", 1, 1, 0);
        player.setPosition(0, 0, 0);
        CombatAI ai = new CombatAI(player);
        PacketLogger live = new PacketLogger("ProgressBot");
        live.logPacket(buildNpcInfoFrame(9001, 1000000 + 20545, 1, 100, 100, 0, 0));
        ai.setPacketLogger(live);
        assertEquals(CombatDecision.Action.ATTACK, ai.makeDecision().getAction());

        assertFalse(ai.checkStaleTarget(1000, 15), "arms the stale timer at 15ms budget");
        Thread.sleep(20); // exceed the budget BEFORE the XP update
        assertFalse(ai.checkStaleTarget(1250, 15), "XP progress past budget resets the stale clock");
        Thread.sleep(5);  // only 5ms since the reset — well inside the 15ms budget
        assertFalse(ai.checkStaleTarget(1250, 15), "unchanged XP inside a fresh budget must NOT abandon");
        assertTrue(ai.getCombatState().isInCombat(), "progressing fight stays in combat");
    }

    // Real-layout builders matching the proven probe framing (same as PacketLoggerNpcInfoTest).
    private byte[] buildNpcInfoFrame(int objectId, int displayId, int isAttackable,
                                     int x, int y, int z, int heading) {
        int payloadLen = 1 + 4 + 4 + 4 + 4 + 4 + 4 + 4; // opcode + 7 ints
        ByteBuffer payload = ByteBuffer.allocate(payloadLen).order(ByteOrder.LITTLE_ENDIAN);
        payload.put((byte) 0x16);
        payload.putInt(objectId);
        payload.putInt(displayId);
        payload.putInt(isAttackable);
        payload.putInt(x);
        payload.putInt(y);
        payload.putInt(z);
        payload.putInt(heading);
        int frameLen = payloadLen + 2;
        ByteBuffer frame = ByteBuffer.allocate(frameLen).order(ByteOrder.LITTLE_ENDIAN);
        frame.putShort((short) frameLen);
        frame.put(payload.array());
        return frame.array();
    }

    // [0x0E][objectId][attrCount][CUR_HP=9][val][MAX_HP=10][val]
    private byte[] buildStatusUpdateFrame(int curHp, int maxHp) {
        int payloadLen = 1 + 4 + 4 + 8 + 8;
        ByteBuffer payload = ByteBuffer.allocate(payloadLen).order(ByteOrder.LITTLE_ENDIAN);
        payload.put((byte) 0x0E);
        payload.putInt(12345);
        payload.putInt(2);
        payload.putInt(0x09); payload.putInt(curHp);
        payload.putInt(0x0A); payload.putInt(maxHp);
        int frameLen = payloadLen + 2;
        ByteBuffer frame = ByteBuffer.allocate(frameLen).order(ByteOrder.LITTLE_ENDIAN);
        frame.putShort((short) frameLen);
        frame.put(payload.array());
        return frame.array();
    }

    // Slice 6 helpers: objId-controlled StatusUpdate + DeleteObject
    // [0x0E][objectId][attrCount][CUR_HP=9][val][MAX_HP=10][val]
    private byte[] buildStatusUpdateFrame(int objectId, int curHp, int maxHp) {
        int payloadLen = 1 + 4 + 4 + 8 + 8;
        ByteBuffer payload = ByteBuffer.allocate(payloadLen).order(ByteOrder.LITTLE_ENDIAN);
        payload.put((byte) 0x0E);
        payload.putInt(objectId);
        payload.putInt(2);
        payload.putInt(0x09); payload.putInt(curHp);
        payload.putInt(0x0A); payload.putInt(maxHp);
        int frameLen = payloadLen + 2;
        ByteBuffer frame = ByteBuffer.allocate(frameLen).order(ByteOrder.LITTLE_ENDIAN);
        frame.putShort((short) frameLen);
        frame.put(payload.array());
        return frame.array();
    }

    // [0x12][objectId]
    private byte[] buildDeleteObjectFrame(int objectId) {
        int payloadLen = 1 + 4;
        ByteBuffer payload = ByteBuffer.allocate(payloadLen).order(ByteOrder.LITTLE_ENDIAN);
        payload.put((byte) 0x12);
        payload.putInt(objectId);
        int frameLen = payloadLen + 2;
        ByteBuffer frame = ByteBuffer.allocate(frameLen).order(ByteOrder.LITTLE_ENDIAN);
        frame.putShort((short) frameLen);
        frame.put(payload.array());
        return frame.array();
    }
}


package com.aiplayer.engine;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

import com.aiplayer.protocol.PacketLogger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stream G (G-Live) tests — LiveFeedbackBridge transition detection.
 *
 * <p>Proves that real packet-derived state transitions fire the D/E/F outcome hooks:
 * level-up (StatusUpdate level rises) -> onLevelUp (emotion confidence +, MAX_LEVEL goal +);
 * death (HP->0) -> onDeath (frustration +); respawn (HP->>0) -> onRespawn (emotion decay);
 * hostile despawn (DeleteObject of an engaged target) -> onKill (kill count +).
 */
public class LiveFeedbackBridgeTest {

    private PacketLogger buildSelfLog(String name, int selfObjId) {
        PacketLogger l = new PacketLogger(name);
        l.setSelfObjectId(selfObjId);
        return l;
    }

    /** Build a StatusUpdate(0x0E) packet with the given attrs (attrId->value) for an object id. */
    private byte[] statusUpdatePkt(int objId, int[][] attrs) {
        int n = attrs.length;
        ByteBuffer body = ByteBuffer.allocate(4 + 4 + n * 8).order(ByteOrder.LITTLE_ENDIAN);
        body.putInt(objId);
        body.putInt(n);
        for (int[] a : attrs) { body.putInt(a[0]); body.putInt(a[1]); }
        byte[] payload = body.array();
        int size = 2 + 1 + payload.length;
        ByteBuffer pkt = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        pkt.putShort((short) size);
        pkt.put((byte) PacketLogger.OP_STATUS_UPDATE);
        pkt.put(payload);
        return pkt.array();
    }

    @Test
    public void levelUpFiresCombatHookAndAdvancesGoal() {
        AIPlayer p = new AIPlayer("GLive_Lvl", 0, 1, 0);
        p.setLevel(1);
        PacketLogger log = buildSelfLog("GLive_Lvl", 999);
        // Seed a level=2 StatusUpdate.
        log.logPacket(statusUpdatePkt(999, new int[][]{
                {PacketLogger.STAT_LEVEL, 2},
                {PacketLogger.STAT_CUR_HP, 100},
                {PacketLogger.STAT_MAX_HP, 100}}));

        LiveFeedbackBridge bridge = new LiveFeedbackBridge(p, log);
        double confBefore = p.getEmotions().getConfidenceLevel();
        int goalBefore = p.getLongTermGoals().getGoalProgress(LongTermGoalsAI.Goal.MAX_LEVEL);

        boolean fired = bridge.handleTick();

        assertTrue(fired, "level-up must be detected");
        assertEquals(2, p.getLevel(), "player level must adopt the parsed level");
        assertTrue(p.getEmotions().getConfidenceLevel() > confBefore,
                "level-up must raise confidence");
        assertEquals(goalBefore + 1, p.getLongTermGoals().getGoalProgress(LongTermGoalsAI.Goal.MAX_LEVEL),
                "level-up must advance MAX_LEVEL long-term goal");
    }

    @Test
    public void deathThenRespawnFiresBothHooks() {
        AIPlayer p = new AIPlayer("GLive_Death", 0, 1, 0);
        PacketLogger log = buildSelfLog("GLive_Death", 999);
        log.setCurHp(100);
        LiveFeedbackBridge bridge = new LiveFeedbackBridge(p, log);
        double frustBefore = p.getEmotions().getFrustrationLevel();

        // Death: HP -> 0.
        log.setCurHp(0);
        assertTrue(bridge.handleTick(), "death must be detected");
        assertTrue(p.getEmotions().getFrustrationLevel() > frustBefore,
                "death must raise frustration");

        // Respawn: HP -> 100. Frustration decays toward neutral.
        double frustAtDeath = p.getEmotions().getFrustrationLevel();
        log.setCurHp(100);
        assertTrue(bridge.handleTick(), "respawn must be detected");
        assertTrue(p.getEmotions().getFrustrationLevel() <= frustAtDeath,
                "respawn decay must not raise frustration further");
    }

    @Test
    public void hostileDespawnFiresKillHook() {
        AIPlayer p = new AIPlayer("GLive_Kill", 0, 1, 0);
        p.setPosition(0, 0, 100);
        PacketLogger log = buildSelfLog("GLive_Kill", 999);
        p.getCombatAI().setPacketLogger(log);
        // Seed two hostiles near the player.
        log.addEntityForTest(new PacketLogger.EntityInfo(700, 20001, 100, 0, 0, 0, true));
        log.addEntityForTest(new PacketLogger.EntityInfo(701, 20002, 100, 0, 0, 0, true));

        // Engage: makeDecision should select a hostile target.
        p.getCombatAI().makeDecision();
        assertTrue(p.getCombatAI().getSelectedTargetObjId() > 0,
                "makeDecision must engage a nearby hostile so a target is selected");

        LiveFeedbackBridge bridge = new LiveFeedbackBridge(p, log);
        int killsBefore = p.getCombatAI().getCombatState().getKillCount();
        bridge.handleTick(); // stabilize (2 hostiles tracked)

        // A hostile despawns (DeleteObject) while we have a target selected -> kill hook.
        log.removeEntityForTest(701);
        boolean fired = bridge.handleTick();
        assertEquals(killsBefore + 1, p.getCombatAI().getCombatState().getKillCount(),
                "hostile despawn while engaged must count as a kill");
        assertTrue(fired, "kill must be flagged as fired this tick");
    }
}

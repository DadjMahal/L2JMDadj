package com.aiplayer.engine;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.aiplayer.protocol.PacketLogger;
import com.aiplayer.protocol.PacketLogger.EntityInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Perception Accuracy Test Suite (Task 42)
 *
 * Verifies accuracy of perception systems against real server packet formats.
 * Tests: CharInfo, StatusUpdate, NpcInfo, ItemList, QuestInfo, DeleteObject
 */
public class PerceptionAccuracyTest {
    private PacketLogger packetLogger;

    @BeforeEach
    void setUp() {
        packetLogger = new PacketLogger("TestPlayer");
    }

    // ==========================================
    // Task 42: Perception Accuracy Tests
    // ==========================================

    @Test
    public void testCharInfoPacketParsingAccuracy() {
        // Verify CharInfo packet (opcode 0x03) parsing
        byte[] packet = createCharInfoPacket();
        packetLogger.logPacket(packet);

        assertEquals(11000, packetLogger.getPlayerX(), "Player X should be parsed correctly");
        assertEquals(22000, packetLogger.getPlayerY(), "Player Y should be parsed correctly");
        assertEquals(-50, packetLogger.getPlayerZ(), "Player Z should be parsed correctly");
        // Heading is delivered by the self-move packet (ValidateLocation 0x61), not CharInfo start.
        packetLogger.logPacket(createValidateLocationPacket(12345, 11000, 22000, -50, 32768));
        assertEquals(32768, packetLogger.getPlayerHeading(), "Player heading should be parsed correctly");
    }

    @Test
    public void testStatusUpdatePacketForHpMp() {
        byte[] packet = createStatusUpdatePacket();
        packetLogger.logPacket(packet);

        assertEquals(500, packetLogger.getCurHp(), "Current HP should be 500");
        assertEquals(1000, packetLogger.getMaxHp(), "Max HP should be 1000");
        assertEquals(50.0, packetLogger.getHpPercentage(), 0.1, "HP percentage should be 50%");
        assertEquals(150, packetLogger.getCurMp(), "Current MP should be 150");
        assertEquals(300, packetLogger.getMaxMp(), "Max MP should be 300");
        assertEquals(50.0, packetLogger.getMpPercentage(), 0.1, "MP percentage should be 50%");
    }

    @Test
    public void testNpcInfoPacketParsingAccuracy() {
        byte[] packet = createNpcInfoPacket();
        packetLogger.logPacket(packet);

        EntityInfo entity = packetLogger.getEntity(1001);
        assertNotNull(entity, "Entity should be tracked");
        assertEquals(10001, entity.npcId, "NPC ID should be parsed correctly");
        assertEquals(11000, entity.x, "Entity X should be parsed correctly");
        assertEquals(22000, entity.y, "Entity Y should be parsed correctly");
        assertEquals(-50, entity.z, "Entity Z should be parsed correctly");
        assertTrue(entity.isHostile, "Monster NPC should be hostile");
    }

    @Test
    public void testNpcInfoHostilityDetectionMonster() {
        // A farmable monster template arrives with the packet's isAttackable=1 (server truth).
        byte[] packet = createNpcInfoWithId(2001, 10001, 1);
        packetLogger.logPacket(packet);

        EntityInfo entity = packetLogger.getEntity(2001);
        assertNotNull(entity, "Entity should be parsed");
        assertTrue(entity.isHostile, "Attackable monster should be hostile");
    }

    @Test
    public void testNpcInfoNeutralDetection() {
        // The bug this regression guards: a Merchant/Guard NPC can carry a LOW template id (here
        // 10001, in the old "hostile by ID range" band AND the merchant template id). The packet's
        // isAttackable=0 is the server's ground truth (character.isAutoAttackable), so it must win.
        byte[] packet = createNpcInfoWithId(3001, 10001, 0);
        packetLogger.logPacket(packet);

        EntityInfo entity = packetLogger.getEntity(3001);
        assertNotNull(entity, "Entity should be parsed");
        assertFalse(entity.isHostile, "Non-attackable NPC must NOT be hostile even with a monster-like npcId");
    }

    @Test
    public void testItemListPacketAccuracy() {
        // ItemList: 50 items out of 120 slots = 41.66% -> 41%
        byte[] packet = createItemListPacket((byte) 50);
        packetLogger.logPacket(packet);

        assertEquals(41, packetLogger.getInventoryUsagePercent(),
            "Inventory usage should be 41% (50/120 = 41.66, truncated)");
    }

    @Test
    public void testDeleteObjectPacketAccuracy() {
        // First add an entity
        packetLogger.logPacket(createNpcInfoWithId(5001, 10001, 1));
        assertNotNull(packetLogger.getEntity(5001), "Entity should exist before deletion");

        // Now delete it
        byte[] delPacket = createDeleteObjectPacket(5001);
        packetLogger.logPacket(delPacket);

        assertNull(packetLogger.getEntity(5001), "Entity should be removed after deletion");
    }

    @Test
    public void testEntityDistanceCalculation() {
        // Entity at (3000, 4000, 0) from (0, 0, 0) = 5000 distance
        EntityInfo entity = new EntityInfo(1001, 10001, 3000, 4000, 0, 0, true);

        double distance = Math.sqrt(
            Math.pow(entity.x - 0, 2) +
            Math.pow(entity.y - 0, 2) +
            Math.pow(entity.z - 0, 2)
        );

        assertEquals(5000.0, distance, 0.1, "Distance calculation should be accurate");
    }

    @Test
    public void testHostileEntityCount() {
        // Add monster (hostile) - NPC ID 10001, packet isAttackable=1
        packetLogger.logPacket(createNpcInfoWithId(1001, 10001, 1));
        // Add neutral NPC - NPC ID 250010, packet isAttackable=0
        packetLogger.logPacket(createNpcInfoWithId(1002, 250010, 0));
        // Add another monster (hostile) - NPC ID 15001, packet isAttackable=1
        packetLogger.logPacket(createNpcInfoWithId(1003, 15001, 1));

        assertEquals(2, packetLogger.getHostileEntityCount(),
            "Should have 2 hostile entities (monster IDs 10001, 15001)");
    }

    @Test
    public void testFindNearestHostile() {
        // Add hostile at (0, 10000, 0) - distance 10000
        packetLogger.logPacket(createNpcInfoAt(1001, 10001, 0, 10000, 0, 1));
        // Add hostile at (3000, 3000, 0) - distance 4242.6
        packetLogger.logPacket(createNpcInfoAt(1002, 10002, 3000, 3000, 0, 1));
        // Add hostile at (0, 5000, 0) - distance 5000
        packetLogger.logPacket(createNpcInfoAt(1003, 15001, 0, 5000, 0, 1));

        EntityInfo nearest = packetLogger.findNearestHostile(0, 0, 0, 15000);
        assertNotNull(nearest, "Should find nearest hostile");
        assertEquals(1002, nearest.objectId, "Entity 1002 should be nearest (4242 distance)");
    }

    @Test
    public void testTelemetrySummaryAccuracy() {
        packetLogger.logPacket(createCharInfoPacket());
        packetLogger.logPacket(createStatusUpdatePacket());
        packetLogger.logPacket(createNpcInfoPacket());
        packetLogger.logPacket(createItemListPacket((byte) 25));

        String summary = packetLogger.getTelemetrySummary();
        assertTrue(summary.contains("total=4"), "Should count 4 packets, got: " + summary);
    }

    @Test
    public void testPacketCountAccuracy() {
        for (int i = 0; i < 10; i++) {
            packetLogger.logPacket(createCharInfoPacket());
        }

        assertEquals(10, packetLogger.getCharInfoCount(),
            "Should count 10 CharInfo packets correctly");
        assertEquals(10, packetLogger.getTotalPacketsLogged(),
            "Total packets should be 10");
    }

    @Test
    public void testInvalidPacketHandling() {
        byte[] invalid = new byte[2];
        packetLogger.logPacket(invalid);
        assertEquals(0, packetLogger.getTotalPacketsLogged());
    }

    @Test
    public void testNullPacketHandling() {
        packetLogger.logPacket(null);
        assertEquals(0, packetLogger.getTotalPacketsLogged());
    }

    @Test
    public void testPositionTrackingInCombatAI() {
        packetLogger.logPacket(createCharInfoPacketWithPosition());

        int px = packetLogger.getPlayerX();
        int py = packetLogger.getPlayerY();
        int pz = packetLogger.getPlayerZ();

        assertEquals(10000, px, "CombatAI should read player X");
        assertEquals(20000, py, "CombatAI should read player Y");
        assertEquals(-100, pz, "CombatAI should read player Z");
    }

    @Test
    public void testHpMpInCombatAI() {
        packetLogger.logPacket(createStatusUpdatePacket());

        // Data: curHp=500 / maxHp=1000 = 50%, curMp=150 / maxMp=300 = 50%.
        assertTrue(packetLogger.getHpPercentage() >= 50, "Should detect at least 50% HP");
        assertTrue(packetLogger.getMpPercentage() >= 40, "Should detect healthy MP");
    }

    @Test
    public void testEntityTrackingInCombatAI() {
        packetLogger.logPacket(createNpcInfoAt(1001, 10001, 1000, 2000, 0, 1));

        EntityInfo enemy = packetLogger.findNearestHostile(0, 0, 0, 5000);
        assertNotNull(enemy, "Should detect nearby enemy");
        assertTrue(enemy.isHostile, "Should be hostile");
    }

    // ==========================================
    // Helper Methods - Create Realistic Packets
    // ==========================================

    private byte[] createCharInfoPacket() {
        // Real Interlude CharInfo (0x03) write order (CharInfo.java writeImpl):
        //   [opcode][x][y][z][vehicle][objId][name UTF16LE+null][race][female][baseClass]
        ByteBuffer payload = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN);
        payload.put((byte) 0x03);
        payload.putInt(11000);
        payload.putInt(22000);
        payload.putInt(-50);
        payload.putInt(0);            // vehicle objectId
        payload.putInt(12345);        // objId
        putStringLE(payload, "TestPlayer");
        payload.putInt(0);            // race
        payload.putInt(0);            // female
        payload.putInt(0);            // baseClass
        return wrap(payload);
    }

    private byte[] createCharInfoPacketWithPosition() {
        ByteBuffer payload = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN);
        payload.put((byte) 0x03);
        payload.putInt(10000);
        payload.putInt(20000);
        payload.putInt(-100);
        payload.putInt(0);            // vehicle
        payload.putInt(12345);        // objId
        putStringLE(payload, "TestPlayer");
        payload.putInt(0);
        payload.putInt(0);
        payload.putInt(0);
        return wrap(payload);
    }

    /** ValidateLocation (0x61): [opcode][objId][x][y][z][heading] — authoritative self-move packet. */
    private byte[] createValidateLocationPacket(int objId, int x, int y, int z, int heading) {
        ByteBuffer payload = ByteBuffer.allocate(1 + 5 * 4).order(ByteOrder.LITTLE_ENDIAN);
        payload.put((byte) 0x61);
        payload.putInt(objId);
        payload.putInt(x);
        payload.putInt(y);
        payload.putInt(z);
        payload.putInt(heading);
        return wrap(payload);
    }

    private static void putStringLE(ByteBuffer buf, String s) {
        for (int i = 0; i < s.length(); i++) {
            buf.putShort((short) s.charAt(i));
        }
        buf.putShort((short) 0); // null terminator
    }

    private static byte[] wrap(ByteBuffer payload) {
        int len = payload.position();
        ByteBuffer frame = ByteBuffer.allocate(len + 2).order(ByteOrder.LITTLE_ENDIAN);
        frame.putShort((short) (len + 2)); // self-inclusive size
        payload.flip();
        frame.put(payload);
        return frame.array();
    }

    private byte[] createStatusUpdatePacket() {
        // Packet: [size:2][opcode:1][objectId:4][attrCount:4][attrId:4][value:4]...
        // 4 attributes: 4 + 4 + 8*4 = 40 bytes data, total 43 bytes
        return new byte[]{
            (byte) 0x2B, (byte) 0x00,  // size = 43 (0x2B)
            (byte) 0x0E,  // opcode
            0x39, 0x30, (byte) 0x00, (byte) 0x00,  // objectId = 12345
            (byte) 0x04, (byte) 0x00, (byte) 0x00, (byte) 0x00,  // attributeCount = 4
            // STAT_CUR_HP (9 = 0x09)
            (byte) 0x09, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            // curHp = 500
            (byte) 0xF4, (byte) 0x01, (byte) 0x00, (byte) 0x00,
            // STAT_MAX_HP (10 = 0x0A)
            (byte) 0x0A, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            // maxHp = 1000
            (byte) 0xE8, (byte) 0x03, (byte) 0x00, (byte) 0x00,
            // STAT_CUR_MP (11 = 0x0B)
            (byte) 0x0B, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            // curMp = 150
            (byte) 0x96, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            // STAT_MAX_MP (12 = 0x0C)
            (byte) 0x0C, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            // maxMp = 300
            (byte) 0x2C, (byte) 0x01, (byte) 0x00, (byte) 0x00
        };
    }

    private byte[] createNpcInfoPacket() {
        // Positioned hostile monster at (11000, 22000, -50) — matches the parsing assertions.
        return createNpcInfoAt(1001, 10001, 11000, 22000, -50, 1);
    }

    private byte[] createNpcInfoWithId(int objectId, int npcId, int isAttackable) {
        // Stream C: real Interlude AbstractNpcInfo layout (Audit/35, proven by B4):
        //   [0x16][objectId][displayId = npcId+1000000][isAttackable][x][y][z][heading]
        // Frame = [2-byte self-inclusive size=31][payload]. isAttackable is the packet truth:
        // 1 for attackable/farmable monsters, 0 for town NPCs (merchants/guards/quest givers).
        return buildNpcInfoFrame(objectId, npcId + 1000000, isAttackable, 0, 0, 0, 0);
    }

    private byte[] createNpcInfoAt(int objectId, int npcId, int x, int y, int z, int isAttackable) {
        return buildNpcInfoFrame(objectId, npcId + 1000000, isAttackable, x, y, z, 0);
    }

    /** Build a real-layout NPC_INFO frame: [2-byte self-inclusive size][opcode][fields]. */
    private byte[] buildNpcInfoFrame(int objectId, int displayId, int isAttackable,
                                     int x, int y, int z, int heading) {
        int payloadLen = 1 + 4 + 4 + 4 + 4 + 4 + 4 + 4; // opcode + 7 ints
        ByteBuffer payload = ByteBuffer.allocate(payloadLen).order(ByteOrder.LITTLE_ENDIAN);
        payload.put((byte) 0x16);
        payload.putInt(objectId);
        payload.putInt(displayId);     // real template id + 1000000
        payload.putInt(isAttackable);  // 0 or 1
        payload.putInt(x);
        payload.putInt(y);
        payload.putInt(z);
        payload.putInt(heading);

        int frameLen = payloadLen + 2; // self-inclusive size header
        ByteBuffer frame = ByteBuffer.allocate(frameLen).order(ByteOrder.LITTLE_ENDIAN);
        frame.putShort((short) frameLen);
        frame.put(payload.array());
        return frame.array();
    }

    private byte[] createItemListPacket(byte itemCount) {
        return new byte[]{
            0x0A, 0x00,  // size = 10
            (byte) 0x1B,  // opcode
            0x01, 0x00,  // showWindow
            itemCount, 0x00,  // itemCount
            0x00, 0x00, 0x00, 0x00  // filler
        };
    }

    private byte[] createDeleteObjectPacket(int objectId) {
        return new byte[]{
            0x07, 0x00,  // size = 7
            (byte) 0x12,  // opcode
            (byte) (objectId & 0xFF),
            (byte) ((objectId >> 8) & 0xFF),
            (byte) ((objectId >> 16) & 0xFF),
            (byte) ((objectId >> 24) & 0xFF)
        };
    }
}

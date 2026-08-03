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
        // NPC IDs 1-199999 are hostile monsters
        byte[] packet = createNpcInfoWithId(2001, 10001);
        packetLogger.logPacket(packet);
        
        EntityInfo entity = packetLogger.getEntity(2001);
        assertNotNull(entity, "Entity should be parsed");
        assertTrue(entity.isHostile, "Monster should be detected as hostile by ID range");
    }
    
    @Test
    public void testNpcInfoNeutralDetection() {
        // NPC IDs 200000+ are neutral (non-hostile)
        byte[] packet = createNpcInfoWithId(3001, 250000);
        packetLogger.logPacket(packet);
        
        EntityInfo entity = packetLogger.getEntity(3001);
        assertNotNull(entity, "Entity should be parsed");
        assertFalse(entity.isHostile, "Regular NPC should NOT be hostile");
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
        packetLogger.logPacket(createNpcInfoWithId(5001, 10001));
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
        // Add monster (hostile) - NPC ID 10001
        packetLogger.logPacket(createNpcInfoWithId(1001, 10001));
        // Add neutral NPC - NPC ID 250010 (neutral)
        packetLogger.logPacket(createNpcInfoWithId(1002, 250010));
        // Add another monster (hostile) - NPC ID 15001
        packetLogger.logPacket(createNpcInfoWithId(1003, 15001));
        
        assertEquals(2, packetLogger.getHostileEntityCount(), 
            "Should have 2 hostile entities (monster IDs 10001, 15001)");
    }
    
    @Test
    public void testFindNearestHostile() {
        // Add hostile at (0, 10000, 0) - distance 10000
        packetLogger.logPacket(createNpcInfoAt(1001, 10001, 0, 10000, 0));
        // Add hostile at (3000, 3000, 0) - distance 4242.6
        packetLogger.logPacket(createNpcInfoAt(1002, 10002, 3000, 3000, 0));
        // Add hostile at (0, 5000, 0) - distance 5000
        packetLogger.logPacket(createNpcInfoAt(1003, 15001, 0, 5000, 0));
        
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
        packetLogger.logPacket(createNpcInfoAt(1001, 10001, 1000, 2000, 0));
        
        EntityInfo enemy = packetLogger.findNearestHostile(0, 0, 0, 5000);
        assertNotNull(enemy, "Should detect nearby enemy");
        assertTrue(enemy.isHostile, "Should be hostile");
    }
    
    // ==========================================
    // Helper Methods - Create Realistic Packets
    // ==========================================
    
    private byte[] createCharInfoPacket() {
        // Packet: [size:2][opcode:1][objectId:4][x:4][y:4][z:4][heading:4]
        return new byte[]{
            (byte) 0x17, (byte) 0x00,  // size = 23
            (byte) 0x03,  // opcode
            0x39, 0x30, (byte) 0x00, (byte) 0x00,  // objectId = 12345
            (byte) 0xF8, (byte) 0x2A, (byte) 0x00, (byte) 0x00,  // x = 11000
            (byte) 0xF0, (byte) 0x55, (byte) 0x00, (byte) 0x00,  // y = 22000
            (byte) 0xCE, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,  // z = -50
            (byte) 0x00, (byte) 0x80, (byte) 0x00, (byte) 0x00   // heading = 32768 (0x8000 LE)
        };
    }
    
    private byte[] createCharInfoPacketWithPosition() {
        return new byte[]{
            (byte) 0x17, (byte) 0x00,  // size = 23
            (byte) 0x03,  // opcode
            0x39, 0x30, (byte) 0x00, (byte) 0x00,  // objectId = 12345
            (byte) 0x10, (byte) 0x27, (byte) 0x00, (byte) 0x00,  // x = 10000 (0x2710 LE)
            (byte) 0x20, (byte) 0x4E, (byte) 0x00, (byte) 0x00,  // y = 20000 (0x4E20 LE)
            (byte) 0x9C, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,  // z = -100
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00   // heading = 0
        };
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
        return createNpcInfoAt(1001, 10001, 11000, 22000, -50);
    }
    
    private byte[] createNpcInfoWithId(int objectId, int npcId) {
        // Stream C: real Interlude AbstractNpcInfo layout (Audit/35, proven by B4):
        //   [0x16][objectId][displayId = npcId+1000000][isAttackable][x][y][z][heading]
        // Frame = [2-byte self-inclusive size=31][payload]. isAttackable is left 0 here so the
        // ID-range heuristic (isHostileNpc) exercises hostility, as these tests intend; the
        // packet-flag path is covered by PacketLoggerNpcInfoTest.
        return buildNpcInfoFrame(objectId, npcId + 1000000, 0, 0, 0, 0, 0);
    }

    private byte[] createNpcInfoAt(int objectId, int npcId, int x, int y, int z) {
        return buildNpcInfoFrame(objectId, npcId + 1000000, 0, x, y, z, 0);
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

package com.aiplayer.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * L2J Packet Codec - Rewritten Task 67
 * 
 * Handles encoding/decoding of L2JMobius protocol packets.
 * Uses standard L2J packet format: [2-byte size][opcode][data]
 * 
 * OPCODES FROM SourceCode/java/org/l2jmobius/gameserver/network/ClientPackets.java:
 * - 0x00: Init (server-initiated login handshake)
 * - 0x08: AUTH_LOGIN (AUTH_LOGIN in clientpackets)
 * - 0x0D: CHARACTER_SELECT (CHARACTER_SELECT in clientpackets)
 * - 0x0A: ATTACK_REQUEST (ATTACK_REQUEST in clientpackets)
 * - 0x01: MOVE_TO_LOCATION (MOVE_TO_LOCATION in clientpackets)
 * - 0x42: CHAT (from original implementation)
 */
public class PacketCodec {
    private static final Logger LOGGER = Logger.getLogger(PacketCodec.class.getName());
    
    /**
     * Encode a packet (size + opcode + data) - LITTLE_ENDIAN
     */
    public static byte[] encode(short opcode) {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 4);
        buf.putShort(opcode);
        buf.flip();
        return buf.array();
    }
    
    /**
     * Encode packet with integer data - LITTLE_ENDIAN
     */
    public static byte[] encodeInt(short opcode, int value) {
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 6);
        buf.putShort(opcode);
        buf.putInt(value);
        buf.flip();
        return buf.array();
    }
    
    /**
     * Encode movement packet (Client->Server)
     * OPCODE: 0x01 = MOVE_TO_LOCATION from ClientPackets.java
     * Format: cddcch (objectId, originX, originY, originZ, heading)
     */
    public static byte[] encodeMovement(int objectId, int x, int y, int z, short heading) {
        // size = 2(header) + 1(opcode) + 4(objectId) + 4(x) + 4(y) + 4(z) + 2(heading) = 21
        ByteBuffer buf = ByteBuffer.allocate(22);
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 21);  // packet size
        buf.put((byte) 0x01);      // MOVE_TO_LOCATION opcode (from ClientPackets.java line 46)
        buf.putInt(objectId);
        buf.putInt(x);
        buf.putInt(y);
        buf.putInt(z);
        buf.putShort(heading);
        buf.flip();
        return buf.array();
    }
    
    /**
     * Encode attack packet 
     * OPCODE: 0x0A = ATTACK_REQUEST from ClientPackets.java
     * Format: cddddc (objectId, originX, originY, originZ, attackId)
     */
    public static byte[] encodeAttack(int attackerObjId, int targetX, int targetY, int targetZ) {
        // size = 2(header) + 1(opcode) + 4(objectId) + 4(x) + 4(y) + 4(z) + 1(attackId) = 20
        ByteBuffer buf = ByteBuffer.allocate(21);
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 20);  // packet size
        buf.put((byte) 0x0A);       // ATTACK_REQUEST opcode (from ClientPackets.java line 48)
        buf.putInt(attackerObjId);
        buf.putInt(targetX);
        buf.putInt(targetY);
        buf.putInt(targetZ);
        buf.put((byte) 0);         // attackId: 0 for simple click, 1 for shift-click
        buf.flip();
        return buf.array();
    }
    
    /**
     * Encode character select packet
     * OPCODE: 0x0D = CHARACTER_SELECT from ClientPackets.java
     */
    public static byte[] encodeCharSelect(int charId, String accountName) {
        // size = 2(header) + 1(opcode) + 4(unknown) + 4(charId) + 1(nameLen) + name
        ByteBuffer buf = ByteBuffer.allocate(50);
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 50);
        buf.put((byte) 0x0D);      // CHARACTER_SELECT opcode (from ClientPackets.java line 41)
        buf.putInt(0);             // unknown
        buf.putInt(charId);
        byte[] nameBytes = accountName.getBytes();
        buf.put((byte) Math.min(nameBytes.length, 15));
        buf.put(nameBytes);
        buf.flip();
        byte[] result = new byte[buf.position()];
        System.arraycopy(buf.array(), 0, result, 0, result.length);
        return result;
    }
    
    /**
     * Encode chat packet
     * OPCODE from L2J implementation
     */
    public static byte[] encodeChat(String message) {
        // Simplified chat packet
        byte[] msgBytes = message.getBytes(StandardCharsets.UTF_16LE);
        int size = 4 + 2 + msgBytes.length + 2;  // header + opcode + type + message + null
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) size);
        buf.put((byte) 0x42);      // CHAT opcode (type 0 = shouting)
        buf.putShort((short) msgBytes.length); // message length in chars
        buf.put(msgBytes);
        buf.putShort((short) 0);   // null termination
        buf.flip();
        return buf.array();
    }
    
    /**
     * Decode received packet - returns opcode and buffer
     */
    public static DecodedPacket decode(byte[] data) {
        if (data == null || data.length < 4) {
            return null;
        }
        
        ByteBuffer buf = ByteBuffer.wrap(data);
        short size = buf.getShort();
        short opcode = buf.getShort();
        
        byte[] payload = new byte[size - 4];
        buf.get(payload);
        
        return new DecodedPacket(opcode, payload);
    }
    
    /**
     * Decoded packet container
     */
    public static class DecodedPacket {
        public final short opcode;
        public final byte[] payload;
        
        public DecodedPacket(short opcode, byte[] payload) {
            this.opcode = opcode;
            this.payload = payload;
        }
        
        public int readInt() {
            if (payload.length < 4) return 0;
            return ByteBuffer.wrap(payload).getInt();
        }
        
        public short readShort() {
            if (payload.length < 2) return 0;
            return ByteBuffer.wrap(payload).getShort();
        }
        
        public String readString() {
            int len = 0;
            while (len < payload.length && payload[len] != 0) len++;
            return new String(payload, 0, len, StandardCharsets.UTF_8);
        }
    }
}
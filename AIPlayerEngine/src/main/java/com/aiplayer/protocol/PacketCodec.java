package com.aiplayer.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * L2J Packet Codec - Task 67
 * 
 * Handles encoding and decoding of L2JMobius protocol packets.
 * Uses standard L2J packet format: [2-byte size][opcode][data]
 */
public class PacketCodec {
    private static final Logger LOGGER = Logger.getLogger(PacketCodec.class.getName());
    
    // L2J Protocol opcodes
    public static final short OPCODE_AUTH_REQUEST = 0x01;
    public static final short OPCODE_PLAY_OK = 0x03;
    public static final short OPCODE_AUTH_FAIL = 0x04;
    public static final short OPCODE_SERVERLIST = 0x06;
    public static final short OPCODE_CHAR_SELECT = 0x0D;
    public static final short OPCODE_CHAR_RELEASE = 0x0E;
    public static final short OPCODE_USE_SKILL = 0x2F;
    public static final short OPCODE_ACTION = 0x04;
    public static final short OPCODE_MOVE_TO_LOCATION = 0x46;
    public static final short OPCODE_MOVE_TO_BOARD = 0x47;
    public static final short OPCODE_CHARMOVE = 0x4E;
    public static final short OPCODE_CHAT = 0x42;
    public static final short OPCODE_TEXT = 0x11;
    public static final short OPCODE_TRADE_REQUEST = 0x37;
    public static final short OPCODE_ADD_TRADE_ITEM = 0x39;
    public static final short OPCODE_TRADE_COMPLETE = 0x3A;
    
    /**
     * Encode a packet (size + opcode + data)
     */
    public static byte[] encode(short opcode) {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.putShort((short) 4);
        buf.putShort(opcode);
        buf.flip();
        return buf.array();
    }
    
    /**
     * Encode packet with integer data
     */
    public static byte[] encodeInt(short opcode, int value) {
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.putShort((short) 6);
        buf.putShort(opcode);
        buf.putInt(value);
        buf.flip();
        return buf.array();
    }
    
    /**
     * Encode character select packet
     */
    public static byte[] encodeCharSelect(int charId) {
        ByteBuffer buf = ByteBuffer.allocate(20);
        buf.putShort((short) 20);
        buf.putShort(OPCODE_CHAR_SELECT);
        buf.putInt(0);
        buf.putInt(charId);
        buf.flip();
        return buf.array();
    }
    
    /**
     * Encode movement packet (Client->Server)
     */
    public static byte[] encodeMovement(int objectId, int x, int y, int z, short heading) {
        ByteBuffer buf = ByteBuffer.allocate(22);
        buf.putShort((short) 22);
        buf.putShort(OPCODE_CHARMOVE);
        buf.putInt(objectId);
        buf.putInt(x);
        buf.putInt(y);
        buf.putInt(z);
        buf.putShort(heading);
        buf.flip();
        return buf.array();
    }
    
    /**
     * Encode attack packet (using ATTACK opcode 0x05)
     */
    public static byte[] encodeAttack(int attackerObjId, int targetX, int targetY, int targetZ) {
        ByteBuffer buf = ByteBuffer.allocate(20);
        buf.putShort((short) 20);
        buf.putShort((short) 0x05); // ATTACK opcode
        buf.putInt(attackerObjId);
        buf.putInt(targetX);
        buf.putInt(targetY);
        buf.putInt(targetZ);
        buf.flip();
        return buf.array();
    }
    
    /**
     * Encode character select packet (from L2JM client packets)
     */
    public static byte[] encodeCharSelect(int charId, String accountName) {
        ByteBuffer buf = ByteBuffer.allocate(50);
        buf.putShort((short) 50);
        buf.putShort((short) 0x0D); // RequestCharacterSelect opcode
        buf.putInt(0); // unknown
        // Write account name and charId
        buf.putInt(charId);
        byte[] nameBytes = accountName.getBytes(StandardCharsets.UTF_8);
        buf.put((byte) nameBytes.length);
        buf.put(nameBytes);
        buf.flip();
        return buf.array();
    }
    
    /**
     * Encode chat packet
     */
    public static byte[] encodeChat(String message, int chatType) {
        int size = 4 + 2 + message.length() * 2 + 2; // opcode + type + message (UTF-16) + null term
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.putShort((short) size);
        buf.putShort((short) 0x42); // CHAT opcode
        buf.putInt(chatType);
        byte[] msgBytes = message.getBytes(StandardCharsets.UTF_16LE);
        buf.putShort((short) (msgBytes.length / 2));
        buf.put(msgBytes);
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
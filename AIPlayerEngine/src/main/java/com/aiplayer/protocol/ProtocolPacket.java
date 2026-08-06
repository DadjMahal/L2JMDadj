package com.aiplayer.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Protocol Packet
 * Represents a network packet to be sent to L2JMobius server
 */
public class ProtocolPacket {
    public enum PacketType {
        // Authentication
        AUTH_REQUEST,
        SERVER_LIST,
        PLAY_OK,

        // Game play
        ENTER_WORLD,
        CHAR_SELECT,
        CHAR_DESELECT,
        MOVE_TO,
        STOP_MOVE,
        ATTACK,
        STOP_ATTACK,

        // Trade
        TRADE_REQUEST,
        TRADE_OK,
        TRADE_CANCEL,
        BUY_ITEM,
        SELL_ITEM,

        // Quest
        QUEST_EVENT,

        // Social
        PARTY_INVITE,
        PARTY_JOIN,
        PARTY_LEAVE,
        CLAN_JOIN,
        CLAN_PROMOTE,

        // Chat
        CHAT,
        SHOUT,

        // Movement
        MOVE_TO_LOCATION,
        STOP_MOVEMENT,

        // Unknown/Placeholder
        UNKNOWN
    }

    private final PacketType type;
    private final byte[] data;

    public ProtocolPacket(PacketType type, byte[] data) {
        this.type = type;
        this.data = data != null ? data.clone() : new byte[0];
    }

    public ProtocolPacket(PacketType type) {
        this(type, new byte[0]);
    }

    public PacketType getType() {
        return type;
    }

    public byte[] getData() {
        return data.clone();
    }

    /**
     * Convert packet to byte array for network transmission
     * L2JMobius packet format: [size:2][type:1][data]
     */
    public byte[] toByteArray() {
        int totalSize = 2 + 1 + data.length;
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);

        // Packet size (excluding size field itself)
        buffer.putShort((short) (totalSize - 2));

        // Packet type
        buffer.put(getOpcode());

        // Data
        buffer.put(data);

        return buffer.array();
    }

    /**
     * Get opcode for packet type
     */
    private byte getOpcode() {
        switch (type) {
            case AUTH_REQUEST: return 0x01; // Placeholder
            case PLAY_OK: return (byte) 195;
            case MOVE_TO: return 0x35;
            case STOP_MOVE: return 0x34;
            case ATTACK: return 0x29;
            case STOP_ATTACK: return 0x2A;
            case TRADE_REQUEST: return 0x37;
            case BUY_ITEM: return 0x38;
            case SELL_ITEM: return 0x39;
            case CHAT: return 0x41;
            case MOVE_TO_LOCATION: return 0x33;
            case STOP_MOVEMENT: return 0x34;
            case UNKNOWN:
            default: return 0x00;
        }
    }

    /**
     * Create a MOVE_TO packet
     */
    public static ProtocolPacket createMoveTo(int x, int y, int z, int heading) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putInt(x);
        buffer.putInt(y);
        buffer.putInt(z);
        buffer.put((byte) heading);
        buffer.put((byte) 0); // Ground

        return new ProtocolPacket(PacketType.MOVE_TO, buffer.array());
    }

    /**
     * Create a CHAT packet
     */
    public static ProtocolPacket createChat(String message, int type) {
        byte[] textBytes = message.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + textBytes.length + 4);
        buffer.put((byte) type);
        buffer.put(textBytes);
        buffer.putInt(0); // Zone

        return new ProtocolPacket(PacketType.CHAT, buffer.array());
    }

    /**
     * Create an ATTACK packet
     */
    public static ProtocolPacket createAttack(int targetId) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(targetId);

        return new ProtocolPacket(PacketType.ATTACK, buffer.array());
    }

    @Override
    public String toString() {
        return "ProtocolPacket{" +
                "type=" + type +
                ", dataLength=" + (data != null ? data.length : 0) +
                '}';
    }
}
package com.aiplayer.phase0.protocol;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;

/**
 * Phase 0 protocol extensions — adds missing packet methods to L2JProtocol.
 * Interlude C4 opcodes from ClientPackets:
 * 0x39 = REQUEST_MAGIC_SKILL_USE
 * 0x14 = USE_ITEM
 * 0x6D = REQUEST_RESTART_POINT (respawn to village)
 * 0x48 = VALIDATE_POSITION
 * 0x1F = ACTION (interact/target)
 */
public class Phase0ProtocolExt {

    public static byte[] encodeSkillUse(int skillId, boolean ctrlPressed, boolean shiftPressed) {
        ByteBuffer buf = ByteBuffer.allocate(14);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 14);
        buf.put((byte) 0x39);
        buf.putInt(skillId);
        buf.put(ctrlPressed ? (byte) 1 : (byte) 0);
        buf.put(shiftPressed ? (byte) 1 : (byte) 0);
        buf.flip();
        return buf.array();
    }

    public static byte[] encodeUseItem(int objectId) {
        ByteBuffer buf = ByteBuffer.allocate(10);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 10);
        buf.put((byte) 0x14);
        buf.putInt(objectId);
        buf.flip();
        return buf.array();
    }

    public static byte[] encodeRequestRestartPoint() {
        ByteBuffer buf = ByteBuffer.allocate(5);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 5);
        buf.put((byte) 0x6D);
        buf.putInt(0);
        buf.flip();
        return buf.array();
    }

    public static byte[] encodeValidatePosition(int x, int y, int z, int heading) {
        ByteBuffer buf = ByteBuffer.allocate(18);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 18);
        buf.put((byte) 0x48);
        buf.putInt(x);
        buf.putInt(y);
        buf.putInt(z);
        buf.putInt(heading);
        buf.flip();
        return buf.array();
    }

    public static byte[] encodeTargetSelect(int targetObjId, int originX, int originY, int originZ, boolean shift) {
        ByteBuffer buf = ByteBuffer.allocate(18);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 18);
        buf.put((byte) 0x1F);
        buf.putInt(targetObjId);
        buf.putInt(originX);
        buf.putInt(originY);
        buf.putInt(originZ);
        buf.put(shift ? (byte) 1 : (byte) 0);
        buf.flip();
        return buf.array();
    }

    public static void sendSkillUse(SocketChannel channel, int skillId, boolean ctrl, boolean shift) throws IOException {
        if (channel == null || !channel.isOpen()) return;
        channel.write(ByteBuffer.wrap(encodeSkillUse(skillId, ctrl, shift)));
    }

    public static void sendUseItem(SocketChannel channel, int objectId) throws IOException {
        if (channel == null || !channel.isOpen()) return;
        channel.write(ByteBuffer.wrap(encodeUseItem(objectId)));
    }

    public static void sendRequestRestartPoint(SocketChannel channel) throws IOException {
        if (channel == null || !channel.isOpen()) return;
        channel.write(ByteBuffer.wrap(encodeRequestRestartPoint()));
    }

    public static void sendValidatePosition(SocketChannel channel, int x, int y, int z, int heading) throws IOException {
        if (channel == null || !channel.isOpen()) return;
        channel.write(ByteBuffer.wrap(encodeValidatePosition(x, y, z, heading)));
    }
}

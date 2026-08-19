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
     * Encode REQUEST_MAGIC_SKILL_USE (client->server skill cast).
     *
     * OPCODE: 0x2F per SourceCode/java/.../ClientPackets.java (Interlude). This is the
     * LIVE-verified opcode (the patch-upgrade tree's 0x39 is Interlude C4 and is WRONG;
     * a real Interlude GameServer parses 0x2F and replied ActionFailed to a correctly framed cast,
     * proving the opcode + field layout below).
     *
     * Field widths are server-authoritative per RequestMagicSkillUse.java:42-44:
     *   readInt()  _magicId   -> skillId   (4 bytes)
     *   readInt()  _ctrlPressed -> ctrl     (4 bytes)  "True if ForceAttack : Ctrl pressed"
     *   readByte() _shiftPressed -> shift   (1 byte)  "True if Shift pressed"
     *
     * Frame layout (little-endian, self-inclusive 2-byte size header):
     *   [size=12][0x2F][int skillId][int ctrl][byte shift]  => 2+1+4+4+1 = 12 bytes total.
     *
     * NOTE: the server targets the player's CURRENT target (set via a prior Action 0x04),
     * so the frame carries NO target object_id — contrary to the earlier wrong-width stub.
     */
    public static byte[] encodeUseSkill(int skillId, boolean ctrl, boolean shift)
    {
        // size = 2(header) + 1(opcode) + 4(skillId) + 4(ctrl) + 1(shift) = 12 (self-inclusive)
        ByteBuffer buf = ByteBuffer.allocate(12);
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 12); // self-inclusive packet size
        buf.put((byte) 0x2F);      // REQUEST_MAGIC_SKILL_USE (Interlude opcode)
        buf.putInt(skillId);        // skillId (readInt, 4B)
        buf.putInt(ctrl ? 1 : 0);   // ctrl flag (readInt, 4B)
        buf.put((byte) (shift ? 1 : 0)); // shift flag (readByte, 1B)
        buf.flip();
        return buf.array();
    }

    /**
     * Stream C: real Action (0x04) frame, matching the B4-proven CombatProbe wire format.
     * Payload = [0x04][targetObjId][originX][originY][originZ][actionId=0]; frame = 2-byte
     * self-inclusive size + payload. The server's Action.java readImpl is
     * targetObjectId, originX, originY, originZ, actionId:byte (Audit/35).
     */
    public static byte[] encodeAction(int targetObjId, int originX, int originY, int originZ) {
        int payloadLen = 1 + 4 + 4 + 4 + 4 + 1; // opcode + 4 ints + actionId
        int frameLen = payloadLen + 2;          // self-inclusive size header
        ByteBuffer buf = ByteBuffer.allocate(frameLen).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) frameLen);
        buf.put((byte) 0x04);   // ACTION
        buf.putInt(targetObjId);
        buf.putInt(originX);
        buf.putInt(originY);
        buf.putInt(originZ);
        buf.put((byte) 0);      // actionId = 0 (simple click -> select + auto-attack)
        buf.flip();
        return buf.array();
    }

    /**
     * Stream C: real AttackRequest (0x0A) frame, matching the B4-proven CombatProbe wire format.
     * Payload = [0x0A][targetObjId][0][0][0][attackId=0]; frame = 2-byte self-inclusive size + payload.
     */
    public static byte[] encodeAttackRequest(int targetObjId) {
        int payloadLen = 1 + 4 + 4 + 4 + 4 + 1; // opcode + 4 ints + attackId
        int frameLen = payloadLen + 2;
        ByteBuffer buf = ByteBuffer.allocate(frameLen).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) frameLen);
        buf.put((byte) 0x0A);   // ATTACK_REQUEST
        buf.putInt(targetObjId);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        buf.put((byte) 0);      // attackId = 0
        buf.flip();
        return buf.array();
    }

    /**
     * Stream C: real MoveToLocation (0x01) frame, matching the B8-proven MoveProbe wire format.
     * Payload = [0x01][targetX][targetY][targetZ][originX][originY][originZ][moveType:int]
     * (moveType 0 = cursor-key walk, 1 = mouse); frame = 2-byte self-inclusive size + payload.
     */
    public static byte[] encodeMoveToLocation(int targetX, int targetY, int targetZ,
                                              int originX, int originY, int originZ, int moveType) {
        int payloadLen = 1 + 7 * 4; // opcode + 7 ints
        int frameLen = payloadLen + 2;
        ByteBuffer buf = ByteBuffer.allocate(frameLen).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) frameLen);
        buf.put((byte) 0x01);   // MOVE_TO_LOCATION
        buf.putInt(targetX);
        buf.putInt(targetY);
        buf.putInt(targetZ);
        buf.putInt(originX);
        buf.putInt(originY);
        buf.putInt(originZ);
        buf.putInt(moveType);
        buf.flip();
        return buf.array();
    }

    // ------------------------------------------------------------------
    // GameServer handshake payload builders (plaintext = opcode + fields, NO size header).
    // These mirror the proven B3 / B4 probe builders (EnterWorldProbe / CombatProbe); a
    // GameServerClient frames + optionally game-crypt-encrypts them before writing.
    // ------------------------------------------------------------------

    /** ProtocolVersion (0x00): [0x00][version int] — always plaintext. */
    public static byte[] encodeProtocolVersion(int version) {
        ByteBuffer bb = ByteBuffer.allocate(1 + 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) 0x00);
        bb.putInt(version);
        return bb.array();
    }

    /**
     * AuthLogin (0x08): [0x08][account UTF-16LE][\0 short][playKey2][playKey1][loginKey1][loginKey2].
     * Keys come from {@code L2JProtocol} after the login-server handshake.
     */
    public static byte[] encodeAuthLogin(String account, int playOk2, int playOk1,
                                         int loginOk1, int loginOk2) {
        byte[] name = account.getBytes(StandardCharsets.UTF_16LE);
        ByteBuffer bb = ByteBuffer.allocate(1 + name.length + 2 + 16).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) 0x08);
        bb.put(name);
        bb.putShort((short) 0); // null terminator
        bb.putInt(playOk2);     // playKey2
        bb.putInt(playOk1);     // playKey1
        bb.putInt(loginOk1);    // loginKey1
        bb.putInt(loginOk2);    // loginKey2
        return bb.array();
    }

    /** CharacterSelect (0x0D): [0x0D][charSlot int][unk1 short][unk2][unk3][unk4 ints = 0]. */
    public static byte[] encodeCharacterSelect(int charSlot) {
        ByteBuffer bb = ByteBuffer.allocate(1 + 4 + 2 + 4 + 4 + 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) 0x0D);
        bb.putInt(charSlot);
        bb.putShort((short) 0);
        bb.putInt(0);
        bb.putInt(0);
        bb.putInt(0);
        return bb.array();
    }

    /**
     * EnterWorld (0x03): [0x03][readBytes(32)][4×int][readBytes(32)][int][5×4 tracert] = 105 bytes,
     * all zeros (server builds 0.0.0.0 traceroute IPs). Required after CharSelected to actually spawn
     * (B4 finding #1 — without it the GS stays in ENTERING and sends no NPC_INFO burst).
     */
    public static byte[] encodeEnterWorld() {
        ByteBuffer bb = ByteBuffer.allocate(1 + 32 + 16 + 32 + 4 + 20).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) 0x03);
        for (int i = 0; i < 32; i++) bb.put((byte) 0);
        bb.putInt(0); bb.putInt(0); bb.putInt(0); bb.putInt(0);
        for (int i = 0; i < 32; i++) bb.put((byte) 0);
        bb.putInt(0);
        for (int i = 0; i < 20; i++) bb.put((byte) 0); // 5x4 tracert bytes
        return bb.array();
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

    /** S6-T04: UseItem (server opcode 0x14): [0x14][objectId:int][ctrlPressed:int]. */
    public static byte[] encodeUseItem(int objectId)
    {
        ByteBuffer buf = ByteBuffer.allocate(11);
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 11);
        buf.put((byte) 0x14);
        buf.putInt(objectId);
        buf.putInt(0); // ctrlPressed = false
        buf.flip();
        return buf.array();
    }

    /** Encode chat packet. OPCODE from L2J implementation. */
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
     * Stream C7: RequestBypassToServer (0x21) packet encoder.
     * Used for NPC HTML bypass commands (quest starts, window opening, etc.)
     * Format: 2-byte size | 0x21 | request string (null-terminated, max 512)
     */
    public static byte[] encodeBypass(String request) {
        // L2JMobius reads client strings as UTF-16LE: BaseReadablePacket.readString() does
        // readShort() per 16-bit char until a short==0 terminator. Sending UTF-8 + single null
        // (the original bug) garbles the command at the server (e.g. "Script" -> 0x6353, 0x6972..)
        // and the bypass is silently dropped. Encode UTF-16LE + 2-byte null terminator.
        // Max request string (incl null terminator) is 512 * 2 bytes.
        byte[] reqBytes = request.getBytes(StandardCharsets.UTF_16LE);
        if (reqBytes.length > 508 * 2) {
            reqBytes = java.util.Arrays.copyOf(reqBytes, 508 * 2);
        }
        int size = 3 + reqBytes.length + 2;  // size-header(2) + opcode(1) + UTF-16LE string + 2-byte null
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) size);
        buf.put((byte) 0x21);
        buf.put(reqBytes);
        buf.putShort((short) 0);   // UTF-16LE null terminator
        buf.flip();
        return buf.array();
    }

    /**
     * Stream C7: RequestQuestList (0x63) packet encoder.
     * Opcode-only client frame; no payload.
     */
    public static byte[] encodeQuestList() {
        // size = 2(header) + 1(opcode) = 3
        ByteBuffer buf = ByteBuffer.allocate(3);
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 3);
        buf.put((byte) 0x63);
        buf.flip();
        return buf.array();
    }

    /**
     * Decode received packet - returns opcode and buffer
     */
    /**
     * Encode restart point packet (Client->Server)
     * OPCODE: 0x6D = REQUEST_RESTART_POINT from ClientPackets.java
     * Format: c (pointType)
     */
    public static byte[] encodeRestartPoint(int pointType) {
        // size = 2(header) + 1(opcode) + 4(pointType) = 7
        ByteBuffer buf = ByteBuffer.allocate(7);
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 7);
        buf.put((byte) 0x6D);
        buf.putInt(pointType);
        buf.flip();
        return buf.array();
    }


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

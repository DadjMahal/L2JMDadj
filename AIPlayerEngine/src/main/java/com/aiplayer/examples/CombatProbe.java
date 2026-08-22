package com.aiplayer.examples;

import com.aiplayer.core.FleetConfig;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.aiplayer.net.AIPlayer;
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.protocol.crypt.GameCrypt;

/**
 * B4 probe: live NPC combat proof (2026-08-03).
 *
 * <p>Extends the B3 {@link EnterWorldProbe} flow (login-server auth + GameServer enter-world) and,
 * once the player is in-world, scans {@code NPC_INFO}(0x16) packets for an attackable monster, then
 * sends {@code Action}(0x04) + {@code AttackRequest}(0x0A) to force auto-attack, and tallies the
 * server->client combat packets ({@code ATTACK} 0x05, {@code DIE} 0x06, {@code STATUS_UPDATE} 0x0E,
 * {@code DELETE_OBJECT} 0x12, {@code SYSTEM_MESSAGE} 0x64) received afterwards.
 *
 * <p>Server facts (Interlude, {@code SourceCode/...}, {@code PacketEncryption = False}):
 * <ul>
 *   <li>Game crypt is DISABLED -> all GS packets are plaintext (no Blowfish/XOR on this channel).</li>
 *   <li>AbstractNpcInfo.writeImpl (opcode 0x16) field order:
 *       {@code [0x16][objectId][displayId+1000000][isAttackable][x][y][z][heading]...}</li>
 *   <li>Action (0x04, client): {@code [0x04][targetObjId][originX][originY][originZ][actionId]}.</li>
 *   <li>AttackRequest (0x0A, client): {@code [0x0A][targetObjId][originX][originY][originZ][attackId]}.</li>
 * </ul>
 *
 * <p>Verification: this probe prints a combat opcode tally; the caller ({@code scripts/_probes/b4_combat_prove.sh})
 * also checks the player's {@code exp} in the {@code gameserver} DB before/after. B4 is PROVEN when an
 * {@code ATTACK}/{@code STATUS_UPDATE}/{@code DIE} packet is received after our attack, or {@code exp}
 * increased (a monster died).
 *
 * <p>Wire framing mirrors {@link EnterWorldProbe} verbatim (proven by B3): 2-byte self-inclusive size
 * header; plaintext payload {@code [opcode][fields]}.
 */
@Deprecated // S10-T06: superseded by examples.FleetPlay
public class CombatProbe
{
    private static final int PROTOCOL_VERSION = 746;
    private static final byte[] KEY_TAIL =
    { (byte) 0xC8, (byte) 0x27, (byte) 0x93, (byte) 0x01, (byte) 0xA1, (byte) 0x6C, (byte) 0x31, (byte) 0x97 };

    // Server->client opcodes (from SourceCode/.../network/ServerPackets.java)
    private static final int OP_CHAR_SELECT_INFO = 0x13;
    private static final int OP_CHAR_SELECTED = 0x15;
    private static final int OP_ATTACK = 0x05;
    private static final int OP_DIE = 0x06;
    private static final int OP_STATUS_UPDATE = 0x0E;
    private static final int OP_DELETE_OBJECT = 0x12;
    private static final int OP_NPC_INFO = 0x16;
    private static final int OP_STOP_MOVE = 0x47;
    private static final int OP_VALIDATE_LOCATION = 0x61;
    private static final int OP_SYSTEM_MESSAGE = 0x64;

    // Client->server opcodes (from SourceCode/.../network/ClientPackets.java)
    private static final int OP_C_ACTION = 0x04;
    private static final int OP_C_ATTACK_REQUEST = 0x0A;
    private static final int OP_C_ENTER_WORLD = 0x03;
    /** LIVE-PROBE (throwaway): REQUEST_MAGIC_SKILL_USE, client->server.
     *  Server-authoritative opcode per SourceCode/.../ClientPackets.java (0x2F on THIS Interlude server),
     *  NOT the patch-upgrade tree's 0x39 (Interlude C4) - verified live before wiring sendUseSkill.
     */
    private static final int OP_C_MAGIC_SKILL_USE = 0x2F;

    // Target candidate (first attackable NPC_INFO seen)
    private static int targetObjId = -1;
    private static int targetNpcType = -1;
    private static int targetX, targetY, targetZ;

    // Combat opcode tally (after we start attacking)
    private static int attackCount, dieCount, statusCount, deleteCount, stopMoveCount, validateCount, sysMsgCount, npcInfoCount;

    // LIVE-PROBE (throwaway): full opcode histogram so any skill-cast server response is visible.
    private static final int[] opcodeHist = new int[256];

    public static void main(String[] args) throws Exception
    {
        String account = args.length > 0 ? args[0] : "ai_combat_01";
        String password = args.length > 1 ? args[1] : FleetConfig.accountPassword();
        String host = args.length > 2 ? args[2] : "127.0.0.1";
        int gamePort = args.length > 3 ? Integer.parseInt(args[3]) : 7777;

        // Phase 1: login-server auth -> SessionKey (kept alive in this same process).
        AIPlayer player = new AIPlayer(account, 100, 1, 0);
        L2JProtocol login = new L2JProtocol(player, host, 2106, gamePort);
        boolean ok = login.connectAndLogin(account, password, 2);
        System.out.println("[CombatProbe] login=" + ok);
        if (!ok)
        {
            login.disconnect();
            System.exit(2);
        }

        try (Socket gs = new Socket(host, gamePort))
        {
            // Classic blocking Socket: setSoTimeout IS honored by InputStream.read (throws
            // SocketTimeoutException after the timeout) — unlike NIO SocketChannel, which ignores it.
            // This lets the scan/combat loops tolerate server pauses without hanging forever.
            gs.setSoTimeout(3000);
            final OutputStream out = gs.getOutputStream();
            final InputStream in = gs.getInputStream();
            System.out.println("[CombatProbe] connected to GS " + host + ":" + gamePort);

            sendFrame(out, buildProtocolVersion());

            byte[] keyFrame = readFrame(in);
            if (keyFrame == null)
            {
                System.out.println("[CombatProbe][FAIL] no KeyPacket");
                login.disconnect();
                System.exit(3);
            }
            int packetEncFlag = leInt(keyFrame, 12);
            boolean useEnc = packetEncFlag != 0;
            byte[] key = new byte[16];
            System.arraycopy(keyFrame, 4, key, 0, 8);
            System.arraycopy(KEY_TAIL, 0, key, 8, 8);
            GameCrypt crypt = new GameCrypt();
            crypt.setKey(key);
            System.out.println("[CombatProbe] KeyPacket packetEncryption=" + packetEncFlag + " useGameCrypt=" + useEnc);

            // AuthLogin + enter world.
            sendAuthLogin(out, crypt, useEnc, account, login);

            boolean entered = false;
            long enterDeadline = System.currentTimeMillis() + 12000;
            while (System.currentTimeMillis() < enterDeadline)
            {
                byte[] payload;
                try
                {
                    payload = readPayload(in);
                }
                catch (Exception e)
                {
                    continue; // timeout mid-handshake; keep trying until deadline
                }
                if (payload == null)
                {
                    break;
                }
                byte[] plain = Arrays.copyOf(payload, payload.length);
                if (useEnc)
                {
                    crypt.decrypt(plain, 0, plain.length);
                }
                int op = plain[0] & 0xff;
                System.out.println("[CombatProbe] GS opcode=0x" + Integer.toHexString(op) + " len=" + plain.length);
                if (op == OP_CHAR_SELECT_INFO)
                {
                    sendCharacterSelect(out, crypt, useEnc, 0);
                    System.out.println("[CombatProbe] sent CharacterSelect(0x0D) slot=0");
                }
                if (op == OP_CHAR_SELECTED)
                {
                    System.out.println("[CombatProbe] *** CHAR SELECTED — entering combat phase");
                    entered = true;
                    break;
                }
            }
            if (!entered)
            {
                System.out.println("[CombatProbe][FAIL] did not enter world (no CharSelected)");
                login.disconnect();
                System.exit(4);
            }

            // Send EnterWorld (0x03) to finalize entering the world. Without it the connection stays
            // in ENTERING state and the server never spawns the player / sends the world burst
            // (NpcInfo, etc.), so no nearby monster is ever visible (B3 only needed online=1, which is
            // set in CharacterSelect.runImpl before EnterWorld). Read initial world-population packets
            // for ~3s so the player finishes spawning.
            sendEnterWorld(out, crypt, useEnc);
            System.out.println("[CombatProbe] sent EnterWorld(0x03) — world should populate now");

        // Player origin (Action/AttackRequest origin coords) — defaults to CombatBot_01 spawn.
        int px = args.length > 4 ? Integer.parseInt(args[4]) : 16600;
        int py = args.length > 5 ? Integer.parseInt(args[5]) : 17000;
        int pz = args.length > 6 ? Integer.parseInt(args[6]) : 434;

            // Combat phase 1: scan NPC_INFO ~6s to acquire an attackable monster target.
            long scanDeadline = System.currentTimeMillis() + 6000;
            while (System.currentTimeMillis() < scanDeadline)
            {
                byte[] payload;
                try
                {
                    payload = readPayload(in);
                }
                catch (Exception e)
                {
                    continue;
                }
                if (payload == null)
                {
                    break;
                }
                byte[] plain = Arrays.copyOf(payload, payload.length);
                if (useEnc)
                {
                    crypt.decrypt(plain, 0, plain.length);
                }
                int op = plain[0] & 0xff;
                tally(op);
                if (op == OP_NPC_INFO && plain.length >= 25)
                {
                    // Real AbstractNpcInfo offsets: [0x16][objectId][npcType][isAttackable][x][y][z]...
                    int objId = leInt(plain, 1);
                    int npcType = leInt(plain, 5);
                    int isAttackable = leInt(plain, 9);
                    int nx = leInt(plain, 13);
                    int ny = leInt(plain, 17);
                    int nz = leInt(plain, 21);
                    System.out.println("[CombatProbe] NPC_INFO objId=" + objId + " npcType=" + npcType
                        + " attackable=" + isAttackable + " pos=(" + nx + "," + ny + "," + nz + ")");
                    if (isAttackable != 0 && targetObjId < 0)
                    {
                        targetObjId = objId;
                        targetNpcType = npcType;
                        targetX = nx;
                        targetY = ny;
                        targetZ = nz;
                    }
                }
            }

            if (targetObjId < 0)
            {
                System.out.println("[CombatProbe][WARN] no attackable NPC_INFO seen in scan window"
                    + " (npcInfoCount=" + npcInfoCount + "). Cannot attack; tallying idle traffic only.");
            }
            else
            {
                System.out.println("[CombatProbe] TARGET acquired objId=" + targetObjId + " npcType=" + targetNpcType
                    + " at (" + targetX + "," + targetY + "," + targetZ + ")");
                // Action(0x04): select + auto-attack (sets ATTACK intention; auto-runs into range).
                sendAction(out, crypt, useEnc, targetObjId, px, py, pz);
                System.out.println("[CombatProbe] sent Action(0x04) target=" + targetObjId);
                Thread.sleep(1000); // respect flood protector canPerformPlayerAction()

                // LIVE-PROBE (throwaway; only when args[7] = skillId given): send REQUEST_MAGIC_SKILL_USE
                // (0x2F) to confirm the server accepts the skill-cast opcode. Real skillId -> a cast
                // fires; unknown skillId -> the server replies ActionFailed/SystemMessage; a WRONG
                // opcode would be dropped or disconnect. That is our discriminator. c5 path unchanged.
                if (args.length > 7) {
                    int probeSkillId = Integer.parseInt(args[7]);
                    sendMagicSkillUse(out, crypt, useEnc, probeSkillId, false, false);
                    System.out.println("[CombatProbe] sent MagicSkillUse(0x2F) skillId=" + probeSkillId
                        + " target=" + targetObjId);
                }
                // AttackRequest(0x0A): force attack on the now-selected target.
                sendAttackRequest(out, crypt, useEnc, targetObjId);
                System.out.println("[CombatProbe] sent AttackRequest(0x0A) target=" + targetObjId);
            }

            // Combat phase 2: read server packets ~20s, tally combat opcodes.
            long combatDeadline = System.currentTimeMillis() + 20000;
            while (System.currentTimeMillis() < combatDeadline)
            {
                byte[] payload;
                try
                {
                    payload = readPayload(in);
                }
                catch (Exception e)
                {
                    continue; // timeout gap; keep reading until combat deadline
                }
                if (payload == null)
                {
                    break;
                }
                byte[] plain = Arrays.copyOf(payload, payload.length);
                if (useEnc)
                {
                    crypt.decrypt(plain, 0, plain.length);
                }
                int op = plain[0] & 0xff;
                tally(op);
                if (op == OP_ATTACK || op == OP_DIE || op == OP_DELETE_OBJECT || op == OP_SYSTEM_MESSAGE)
                {
                    System.out.println("[CombatProbe] COMBAT pkt 0x" + Integer.toHexString(op) + " len=" + plain.length);
                }
            }

            System.out.println("[CombatProbe] === COMBAT TALLY (after attack) ===");
            System.out.println("  ATTACK(0x05)=" + attackCount);
            System.out.println("  DIE(0x06)=" + dieCount);
            System.out.println("  STATUS_UPDATE(0x0E)=" + statusCount);
            System.out.println("  DELETE_OBJECT(0x12)=" + deleteCount);
            System.out.println("  STOP_MOVE(0x47)=" + stopMoveCount);
            System.out.println("  VALIDATE_LOCATION(0x61)=" + validateCount);
            System.out.println("  SYSTEM_MESSAGE(0x64)=" + sysMsgCount);
            System.out.println("  NPC_INFO(0x16)=" + npcInfoCount);

            // LIVE-PROBE: full opcode histogram (catches skill-cast server responses the named tallies miss).
            System.out.println("[CombatProbe] === LIVE-PROBE SKILL-CAST OPCODE HISTOGRAM ===");
            for (int h = 0; h < opcodeHist.length; h++)
            {
                if (opcodeHist[h] > 0)
                {
                    System.out.println(String.format("  [0x%02X]=%d", h, opcodeHist[h]));
                }
            }
            // Combat is proven only by a server ATTACK(0x05) packet (real hit) or a DIE(0x06).
            // StatusUpdate(0x0E) is NOT proof (idle online players receive it) — earlier that caused a
            // false "PROVEN" when the player was dead and the server sent its death StatusUpdate/DIE.
            boolean combatProven = (attackCount > 0) || (dieCount > 0);
            System.out.println("[CombatProbe] COMBAT PROVEN (attack|die > 0) = " + combatProven);
            System.out.println("[CombatProbe] done");
        }
        login.disconnect();
    }


    // ------------------------------------------------------------------
    // Combat packet builders (plaintext; game crypt disabled on this server)
    // ------------------------------------------------------------------

    /** REQUEST_MAGIC_SKILL_USE (0x2F): [0x2F][int skillId][int ctrlPressed][byte shiftPressed].
     *  Read order per SourceCode/.../RequestMagicSkillUse.java
     *  (readInt(magicId) -> readInt(ctrl) -> readByte(shift)): 12 bytes incl. the 2-byte LE size header.
     */
    private static void sendMagicSkillUse(OutputStream out, GameCrypt crypt, boolean useEnc,
            int skillId, boolean ctrl, boolean shift) throws Exception
    {
        ByteBuffer bb = ByteBuffer.allocate(1 + 4 + 4 + 1).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) OP_C_MAGIC_SKILL_USE);
        bb.putInt(skillId);
        bb.putInt(ctrl ? 1 : 0);
        bb.put((byte) (shift ? 1 : 0));
        sendPayload(out, crypt, useEnc, bb);
    }

    /** Action (0x04): [0x04][targetObjId][originX][originY][originZ][actionId]. */
    private static void sendAction(OutputStream out, GameCrypt crypt, boolean useEnc, int targetObjId, int ox, int oy, int oz) throws Exception
    {
        ByteBuffer bb = ByteBuffer.allocate(1 + 4 + 4 + 4 + 4 + 1).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) OP_C_ACTION);
        bb.putInt(targetObjId);
        bb.putInt(ox);
        bb.putInt(oy);
        bb.putInt(oz);
        bb.put((byte) 0); // actionId = 0 (simple click)
        sendPayload(out, crypt, useEnc, bb);
    }

    /** AttackRequest (0x0A): [0x0A][targetObjId][originX][originY][originZ][attackId]. */
    private static void sendAttackRequest(OutputStream out, GameCrypt crypt, boolean useEnc, int targetObjId) throws Exception
    {
        ByteBuffer bb = ByteBuffer.allocate(1 + 4 + 4 + 4 + 4 + 1).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) OP_C_ATTACK_REQUEST);
        bb.putInt(targetObjId);
        bb.putInt(0);
        bb.putInt(0);
        bb.putInt(0);
        bb.put((byte) 0); // attackId = 0
        sendPayload(out, crypt, useEnc, bb);
    }

    private static void sendPayload(OutputStream out, GameCrypt crypt, boolean useEnc, ByteBuffer bb) throws Exception
    {
        byte[] plain = new byte[bb.position()];
        bb.flip();
        bb.get(plain);
        if (useEnc)
        {
            crypt.encrypt(plain, 0, plain.length);
        }
        sendFrame(out, plain);
    }

    private static void tally(int op)
    {

        // LIVE-PROBE: record every opcode for the histogram printout.
        if (op >= 0 && op < opcodeHist.length)
        {
            opcodeHist[op]++;
        }
        switch (op)
        {
            case OP_ATTACK:
                attackCount++;
                break;
            case OP_DIE:
                dieCount++;
                break;
            case OP_STATUS_UPDATE:
                statusCount++;
                break;
            case OP_DELETE_OBJECT:
                deleteCount++;
                break;
            case OP_STOP_MOVE:
                stopMoveCount++;
                break;
            case OP_VALIDATE_LOCATION:
                validateCount++;
                break;
            case OP_SYSTEM_MESSAGE:
                sysMsgCount++;
                break;
            case OP_NPC_INFO:
                npcInfoCount++;
                break;
            default:
                break;
        }
    }


    // ---------------- enter-world helpers (mirror proven EnterWorldProbe) ----------------

    private static byte[] buildProtocolVersion() throws Exception
    {
        ByteBuffer bb = ByteBuffer.allocate(1 + 4).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) 0x00);
        bb.putInt(PROTOCOL_VERSION);
        return bb.array();
    }

    /** Build AuthLogin (0x08) plaintext then encrypt (if game crypt enabled) and send. */
    private static void sendAuthLogin(OutputStream out, GameCrypt crypt, boolean useEnc, String account, L2JProtocol login) throws Exception
    {
        byte[] name = account.getBytes(StandardCharsets.UTF_16LE);
        ByteBuffer bb = ByteBuffer.allocate(1 + name.length + 2 + 16).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) 0x08);
        bb.put(name);
        bb.putShort((short) 0); // null terminator
        bb.putInt(login.getPlayOk2()); // playKey2
        bb.putInt(login.getPlayOk1()); // playKey1
        bb.putInt(login.getLoginOk1()); // loginKey1
        bb.putInt(login.getLoginOk2()); // loginKey2
        byte[] plain = new byte[bb.position()];
        bb.flip();
        bb.get(plain);
        if (useEnc)
        {
            crypt.encrypt(plain, 0, plain.length);
        }
        sendFrame(out, plain);
    }

    /** Send CharacterSelect (0x0D): charSlot(int), unk1(short), unk2/3/4(ints) — unks are zeros. */
    private static void sendCharacterSelect(OutputStream out, GameCrypt crypt, boolean useEnc, int charSlot) throws Exception
    {
        ByteBuffer bb = ByteBuffer.allocate(1 + 4 + 2 + 4 + 4 + 4).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) 0x0D);
        bb.putInt(charSlot);
        bb.putShort((short) 0);
        bb.putInt(0);
        bb.putInt(0);
        bb.putInt(0);
        byte[] plain = new byte[bb.position()];
        bb.flip();
        bb.get(plain);
        if (useEnc)
        {
            crypt.encrypt(plain, 0, plain.length);
        }
        sendFrame(out, plain);
    }

    /** Send EnterWorld (0x03): readBytes(32) + 4xint + readBytes(32) + int + 5x4 tracert (all zeros). */
    private static void sendEnterWorld(OutputStream out, GameCrypt crypt, boolean useEnc) throws Exception
    {
        ByteBuffer bb = ByteBuffer.allocate(1 + 32 + 16 + 32 + 4 + 20).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) OP_C_ENTER_WORLD); // 0x03
        for (int i = 0; i < 32; i++)
        {
            bb.put((byte) 0);
        }
        bb.putInt(0); // Unknown
        bb.putInt(0); // Unknown
        bb.putInt(0); // Unknown
        bb.putInt(0); // Unknown
        for (int i = 0; i < 32; i++)
        {
            bb.put((byte) 0);
        }
        bb.putInt(0); // Unknown
        for (int i = 0; i < 20; i++) // 5x4 tracert bytes (server builds 0.0.0.0 IPs)
        {
            bb.put((byte) 0);
        }
        byte[] plain = new byte[bb.position()];
        bb.flip();
        bb.get(plain);
        if (useEnc)
        {
            crypt.encrypt(plain, 0, plain.length);
        }
        sendFrame(out, plain);
    }

    // ---------------- GS wire helpers (classic Socket; setSoTimeout honored) ----------------

    private static void sendFrame(OutputStream out, byte[] payload) throws Exception
    {
        ByteBuffer buf = ByteBuffer.allocate(2 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) (payload.length + 2)); // self-inclusive size
        buf.put(payload);
        buf.flip();
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        out.write(data);
        out.flush();
    }

    private static byte[] readFrame(InputStream in) throws Exception
    {
        byte[] payload = readPayload(in);
        if (payload == null)
        {
            return null;
        }
        byte[] frame = new byte[payload.length + 2];
        frame[0] = (byte) ((payload.length + 2) & 0xff);
        frame[1] = (byte) (((payload.length + 2) >> 8) & 0xff);
        System.arraycopy(payload, 0, frame, 2, payload.length);
        return frame;
    }

    /**
     * Read one GS packet payload (after the 2-byte self-inclusive size header).
     * Uses DataInputStream.readFully which honors the socket SO_TIMEOUT: if no data arrives
     * within the timeout, it throws SocketTimeoutException (caught by callers to tolerate gaps).
     */
    private static byte[] readPayload(InputStream in) throws Exception
    {
        DataInputStream dis = (in instanceof DataInputStream) ? (DataInputStream) in : new DataInputStream(in);
        byte[] sizeBytes = new byte[2];
        dis.readFully(sizeBytes); // throws SocketTimeoutException on timeout, EOFException on close
        int size = (sizeBytes[0] & 0xff) | ((sizeBytes[1] & 0xff) << 8);
        if (size < 2 || size > 65535)
        {
            return null;
        }
        byte[] payload = new byte[size - 2];
        dis.readFully(payload);
        return payload;
    }

    private static int leInt(byte[] d, int i)
    {
        return (d[i] & 0xff) | ((d[i + 1] & 0xff) << 8) | ((d[i + 2] & 0xff) << 16) | ((d[i + 3] & 0xff) << 24);
    }
}


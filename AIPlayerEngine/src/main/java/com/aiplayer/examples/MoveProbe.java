package com.aiplayer.examples;

import com.aiplayer.core.FleetConfig;

import java.io.DataInputStream;
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
 * B8 probe: live movement proof (2026-08-03).
 *
 * <p>Uses the proven B3/B4 enter-world flow (login-server auth + GameServer key exchange + EnterWorld),
 * then, once in-world, sends {@code MoveToLocation}(0x01) to a valid Talking Island village destination
 * (Trader 30040's spawn -82515,241221,-3728), and tallies the server->client movement packets
 * ({@code CHAR_MOVE_TO_LOCATION} 0x01, {@code VALIDATE_LOCATION} 0x61, {@code STOP_MOVE} 0x47) received
 * afterwards. On logout the server persists the walked position to {@code characters.x/y/z}, which the caller
 * verifies before/after — that is the B8 proof.
 *
 * <p>Server facts (Interlude SourceCode): MoveToLocation(0x01) client readImpl =
 * {@code [targetX][targetY][targetZ][originX][originY][originZ][moveType:int]} (0=cursor-key walk, 1=mouse).
 * Game crypt is DISABLED on this server (packetEncryption=0) -> plaintext GS channel, mirroring CombatProbe.
 *
 * <p>Verification: caller (scripts/_probes/b8_move_prove.sh) checks {@code characters.x/y/z} for CombatBot_01 before
 * and after; B8 is PROVEN when the DB position moved from the origin toward the destination (and/or movement
 * packets were observed). No L2JM server source changed.
 */
@Deprecated // S10-T06: superseded by examples.FleetPlay
public class MoveProbe
{
    private static final int PROTOCOL_VERSION = 746;
    private static final byte[] KEY_TAIL =
    { (byte) 0xC8, (byte) 0x27, (byte) 0x93, (byte) 0x01, (byte) 0xA1, (byte) 0x6C, (byte) 0x31, (byte) 0x97 };

    // Server->client opcodes
    private static final int OP_CHAR_SELECT_INFO = 0x13;
    private static final int OP_CHAR_SELECTED = 0x15;
    private static final int OP_CHAR_MOVE_TO_LOCATION = 0x01;
    private static final int OP_NPC_INFO = 0x16;
    private static final int OP_STOP_MOVE = 0x47;
    private static final int OP_VALIDATE_LOCATION = 0x61;
    private static final int OP_SYSTEM_MESSAGE = 0x64;

    // Client->server opcodes
    private static final int OP_C_ENTER_WORLD = 0x03;
    private static final int OP_C_MOVE_TO_LOCATION = 0x01;

    // Movement opcode tally
    private static int moveCount, validateCount, stopCount, npcInfoCount, sysMsgCount;

    public static void main(String[] args) throws Exception
    {
        String account = args.length > 0 ? args[0] : "ai_combat_01";
        String password = args.length > 1 ? args[1] : FleetConfig.accountPassword();
        String host = args.length > 2 ? args[2] : "127.0.0.1";
        int gamePort = args.length > 3 ? Integer.parseInt(args[3]) : 7777;

        // Destination (walkable ground). Default = a SHORT walk from the origin so the character
        // physically completes the move within the ~15s tally window (B8 regression fix, 2026-08-08:
        // the old hardcoded destination was ~8.6k units from the current bot position and never
        // persisted). scripts/_probes/b8_move_prove.sh now passes the live DB position as origin and a nearby
        // destination, so the walk actually lands and the DB persists it on logout.
        int ox = args.length > 4 ? Integer.parseInt(args[4]) : -83789;
        int oy = args.length > 5 ? Integer.parseInt(args[5]) : 240799;
        int oz = args.length > 6 ? Integer.parseInt(args[6]) : -3717;
        int targetX = args.length > 7 ? Integer.parseInt(args[7]) : ox + 120;
        int targetY = args.length > 8 ? Integer.parseInt(args[8]) : oy;
        int targetZ = args.length > 9 ? Integer.parseInt(args[9]) : oz;

        // Phase 1: same proven login as CombatProbe (LS auth -> SessionKey kept alive in-process).
        AIPlayer player = new AIPlayer(account, 100, 1, 0);
        L2JProtocol login = new L2JProtocol(player, host, 2106, gamePort);
        boolean ok = login.connectAndLogin(account, password, 2);
        System.out.println("[MoveProbe] login=" + ok);
        if (!ok)
        {
            login.disconnect();
            System.exit(2);
        }

        try (Socket gs = new Socket(host, gamePort))
        {
            gs.setSoTimeout(3000);
            final OutputStream out = gs.getOutputStream();
            final InputStream in = gs.getInputStream();
            System.out.println("[MoveProbe] connected to GS " + host + ":" + gamePort);

            sendFrame(out, buildProtocolVersion());

            byte[] keyFrame = readFrame(in);
            if (keyFrame == null)
            {
                System.out.println("[MoveProbe][FAIL] no KeyPacket");
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
            System.out.println("[MoveProbe] KeyPacket packetEncryption=" + packetEncFlag + " useGameCrypt=" + useEnc);

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
                if (op == OP_CHAR_SELECT_INFO)
                {
                    sendCharacterSelect(out, crypt, useEnc, 0);
                    System.out.println("[MoveProbe] sent CharacterSelect(0x0D) slot=0");
                }
                if (op == OP_CHAR_SELECTED)
                {
                    System.out.println("[MoveProbe] *** CHAR SELECTED — entering movement phase");
                    entered = true;
                    break;
                }
            }
            if (!entered)
            {
                System.out.println("[MoveProbe][FAIL] did not enter world (no CharSelected)");
                login.disconnect();
                System.exit(4);
            }

            // EnterWorld (0x03) finalizes spawning; let the world burst (NPC_INFO etc.) populate for ~3s.
            sendEnterWorld(out, crypt, useEnc);
            System.out.println("[MoveProbe] sent EnterWorld(0x03) — world populating");
            long settle = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < settle)
            {
                try
                {
                    byte[] payload = readPayload(in);
                    if (payload == null)
                    {
                        break;
                    }
                    byte[] plain = Arrays.copyOf(payload, payload.length);
                    if (useEnc)
                    {
                        crypt.decrypt(plain, 0, plain.length);
                    }
                    tally(plain[0] & 0xff);
                }
                catch (Exception e)
                {
                    continue;
                }
            }

            // Phase 2: send MoveToLocation to the destination.
            sendMoveToLocation(out, crypt, useEnc, targetX, targetY, targetZ, ox, oy, oz);
            System.out.println("[MoveProbe] sent MoveToLocation(0x01) -> (" + targetX + "," + targetY + "," + targetZ + ")"
                + " from (" + ox + "," + oy + "," + oz + ")");
            System.out.println("[MoveProbe] player should now auto-walk; tallying movement packets ~15s");

            // Phase 3: read server packets ~15s, tallying movement confirmations.
            long moveDeadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < moveDeadline)
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
                if (op == OP_CHAR_MOVE_TO_LOCATION || op == OP_VALIDATE_LOCATION || op == OP_STOP_MOVE)
                {
                    System.out.println("[MoveProbe] MOVEMENT pkt 0x" + Integer.toHexString(op) + " len=" + plain.length);
                }
            }

            System.out.println("[MoveProbe] === MOVEMENT TALLY (client said walk; server replied) ===");
            System.out.println("  CHAR_MOVE_TO_LOCATION(0x01)=" + moveCount);
            System.out.println("  VALIDATE_LOCATION(0x61)=" + validateCount);
            System.out.println("  STOP_MOVE(0x47)=" + stopCount);
            System.out.println("  NPC_INFO(0x16)=" + npcInfoCount);
            System.out.println("  SYSTEM_MESSAGE(0x64)=" + sysMsgCount);
            // Movement is proven by the DB position change (caller verifies characters.x/y/z).
            // A CHAR_MOVE_TO_LOCATION / VALIDATE_LOCATION packet is strong corroboration.
            boolean moveReplied = (moveCount > 0) || (validateCount > 0) || (stopCount > 0);
            System.out.println("[MoveProbe] server replied to our walk (move|validate|stop > 0) = " + moveReplied);
            // Clean grep-able boolean for b8_move_prove.sh (avoid fragile regex on the tallies).
            System.out.println("[MoveProbe] MOVE_PROVEN=" + moveReplied);
            System.out.println("[MoveProbe] done");
        }
        login.disconnect();
    }

    // --------------------------------------------------------------
    // Packet builders (plaintext; game crypt disabled on this server)
    // --------------------------------------------------------------

    /** Build protocol-version packet (0x00 + PROTOCOL_VERSION). */
    private static byte[] buildProtocolVersion() throws Exception
    {
        ByteBuffer bb = ByteBuffer.allocate(1 + 4).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) 0x00);
        bb.putInt(PROTOCOL_VERSION);
        return bb.array();
    }

    /** MoveToLocation (0x01, client): [0x01][targetX][targetY][targetZ][originX][originY][originZ][moveType:int]. */
    private static void sendMoveToLocation(OutputStream out, GameCrypt crypt, boolean useEnc,
        int tX, int tY, int tZ, int oX, int oY, int oZ) throws Exception
    {
        ByteBuffer bb = ByteBuffer.allocate(1 + 28).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) OP_C_MOVE_TO_LOCATION);
        bb.putInt(tX);
        bb.putInt(tY);
        bb.putInt(tZ);
        bb.putInt(oX);
        bb.putInt(oY);
        bb.putInt(oZ);
        bb.putInt(0); // moveType = 0 (walk via movement intent)
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
        switch (op)
        {
            case OP_CHAR_MOVE_TO_LOCATION:
                moveCount++;
                break;
            case OP_VALIDATE_LOCATION:
                validateCount++;
                break;
            case OP_STOP_MOVE:
                stopCount++;
                break;
            case OP_NPC_INFO:
                npcInfoCount++;
                break;
            case OP_SYSTEM_MESSAGE:
                sysMsgCount++;
                break;
            default:
                break;
        }
    }

    // ---------------- enter-world helpers (mirror proven EnterWorldProbe) ----------------

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
        for (int i = 0; i < 20; i++) // 5x4 tracert bytes
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

    /** Read one GS packet payload after the 2-byte self-inclusive size header (honors SO_TIMEOUT). */
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

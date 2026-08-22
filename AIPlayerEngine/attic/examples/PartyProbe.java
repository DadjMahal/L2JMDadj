// package com.aiplayer.examples;

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
import java.util.concurrent.atomic.AtomicInteger;

import com.aiplayer.net.AIPlayer;
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.protocol.crypt.GameCrypt;

/**
 * B10 probe: live party proof (2026-08-03).
 *
 * <p>Logs in two accounts (`ai_combat_01` = CombatBot_01, `ai_combat_02` = CombatBot_02, both already near
 * each other at -82515,241221,-3728) and enters both into the world (proven B3/B5 flow). Bot A then sends
 * {@code RequestJoinParty}(0x29) targeting bot B by name; the server sends {@code AskJoinParty}(0x39) to B
 * and bot B answers {@code RequestAnswerJoinParty}(0x2A) with 1 (accept). The server creates a real party.
 * Reader threads tally the server party packets on each connection; a party is PROVEN when the leader (A)
 * receives {@code PARTY_SMALL_WINDOW_ALL}(0x4E) and/or the joiner (B) receives {@code PARTY_SMALL_WINDOW_ADD}
 * (0x4F), which the server only sends after a party object is created.
 *
 * <p>Server facts (SourceCode):
 * <ul>
 *   <li>RequestJoinParty(0x29) readImpl = {@code [name:readString][partyDistributionTypeId:int]} (target by NAME).</li>
 *   <li>RequestAnswerJoinParty(0x2A) readImpl = {@code [response:int]} (1 = accept).</li>
 *   <li>Server packets: ASK_JOIN_PARTY(0x39) {@code [requestorName][distId]} to invitee; JOIN_PARTY(0x3A);
 *       PARTY_SMALL_WINDOW_ALL(0x4E) to leader; PARTY_SMALL_WINDOW_ADD(0x4F) to joiner.</li>
 *   <li>Invite requires {@code World.getPlayer(name)} + {@code target.isVisibleFor(requestor)} — bots co-located.</li>
 * </ul>
 *
 * <p>Verification: A's conn seeing PARTY_SMALL_WINDOW_ALL(0x4E) and/or B's conn seeing PARTY_SMALL_WINDOW_ADD(0x4F)
 * = the server created a party and pushed real party windows to both. No L2JM server source changed.
 */
@Deprecated // S10-T06: superseded by examples.FleetPlay
public class PartyProbe
{
    private static final int PROTOCOL_VERSION = 746;
    private static final byte[] KEY_TAIL =
    { (byte) 0xC8, (byte) 0x27, (byte) 0x93, (byte) 0x01, (byte) 0xA1, (byte) 0x6C, (byte) 0x31, (byte) 0x97 };

    // Server->client opcodes (ServerPackets.java)
    private static final int OP_CHAR_SELECT_INFO = 0x13;
    private static final int OP_CHAR_SELECTED = 0x15;
    private static final int OP_ASK_JOIN_PARTY = 0x39;
    private static final int OP_JOIN_PARTY = 0x3A;
    private static final int OP_PARTY_SMALL_WINDOW_ALL = 0x4E;
    private static final int OP_PARTY_SMALL_WINDOW_ADD = 0x4F;

    // Client->server opcodes (ClientPackets.java)
    private static final int OP_C_CHARACTER_SELECT = 0x0D;
    private static final int OP_C_ENTER_WORLD = 0x03;
    private static final int OP_C_REQUEST_JOIN_PARTY = 0x29;
    private static final int OP_C_REQUEST_ANSWER_JOIN_PARTY = 0x2A;

    // Per-connection party tally
    private static final AtomicInteger joinOnA = new AtomicInteger(0);          // 0x3A JoinParty on A's conn
    private static final AtomicInteger allWindowOnA = new AtomicInteger(0);     // 0x4E PartySmallWindowAll on A's conn
    private static final AtomicInteger allWindowOnB = new AtomicInteger(0);     // 0x4E PartySmallWindowAll on B's conn (joiner window)
    private static final AtomicInteger addWindowOnA = new AtomicInteger(0);     // 0x4F PartySmallWindowAdd on A's conn (leader gets Add)
    private static final AtomicInteger addWindowOnB = new AtomicInteger(0);     // 0x4F PartySmallWindowAdd on B's conn
    private static final AtomicInteger askOnB = new AtomicInteger(0);           // 0x39 AskJoinParty on B's conn

    /** A live GameServer connection for one bot. */
    private static class GsConn
    {
        final String account;
        final Socket socket;
        final OutputStream out;
        final InputStream in;
        final boolean useEnc;
        final L2JProtocol login;

        GsConn(String account, Socket socket, OutputStream out, InputStream in, boolean useEnc, L2JProtocol login)
        {
            this.account = account;
            this.socket = socket;
            this.out = out;
            this.in = in;
            this.useEnc = useEnc;
            this.login = login;
        }

        void close()
        {
            try
            {
                socket.close();
            }
            catch (IOException ignored)
            {
            }
            try
            {
                login.disconnect();
            }
            catch (Exception ignored)
            {
            }
        }
    }

    public static void main(String[] args) throws Exception
    {
        String accA = args.length > 0 ? args[0] : "ai_combat_01";
        String passA = args.length > 1 ? args[1] : FleetConfig.accountPassword();
        String accB = args.length > 2 ? args[2] : "ai_combat_02";
        String passB = args.length > 3 ? args[3] : FleetConfig.accountPassword();
        String host = args.length > 4 ? args[4] : "127.0.0.1";
        int port = args.length > 5 ? Integer.parseInt(args[5]) : 7777;
        String inviteeName = args.length > 6 ? args[6] : "CombatBot_02";
        final int DIST_RANDOM = 1; // PartyDistributionType.RANDOM

        GsConn cnA = enterWorld(accA, passA, host, port);
        System.out.println("[PartyProbe] A IN WORLD (" + accA + ")");
        GsConn cnB = enterWorld(accB, passB, host, port);
        System.out.println("[PartyProbe] B IN WORLD (" + accB + ")");

        Thread ra = new Thread(() -> readLoop(cnA.in, true), "readerA");
        Thread rb = new Thread(() -> readLoop(cnB.in, false), "readerB");
        ra.start();
        rb.start();

        Thread.sleep(3000); // both online + world settled

        // A invites B by name (distribution = RANDOM).
        sendRequestJoinParty(cnA.out, cnA.useEnc, inviteeName, DIST_RANDOM);
        System.out.println("[PartyProbe] A sent RequestJoinParty(0x29) -> " + inviteeName);

        Thread.sleep(2000); // server routes AskJoinParty(0x39) to B

        // B accepts.
        sendAnswerJoinParty(cnB.out, cnB.useEnc, 1);
        System.out.println("[PartyProbe] B sent RequestAnswerJoinParty(0x2A) response=1 (accept)");

        Thread.sleep(6000); // server creates party + pushes window packets

        cnA.close();
        cnB.close();
        ra.join(3000);
        rb.join(3000);

        System.out.println("[PartyProbe] === PARTY TALLY ===");
        System.out.println("  A conn: JOIN_PARTY(0x3A)=" + joinOnA + "  SMALL_ALL(0x4E)=" + allWindowOnA + "  SMALL_ADD(0x4F)=" + addWindowOnA);
        System.out.println("  B conn: ASK_JOIN_PARTY(0x39)=" + askOnB + "  SMALL_ALL(0x4E)=" + allWindowOnB + "  SMALL_ADD(0x4F)=" + addWindowOnB);

        // Party formed (Party.addPartyMember): joiner B gets PARTY_SMALL_WINDOW_ALL(0x4E);
        // existing members (leader A) get PARTY_SMALL_WINDOW_ADD(0x4F).
        boolean created = (allWindowOnB.get() > 0) || (addWindowOnA.get() > 0);
        System.out.println("[PartyProbe] B got PartySmallWindowAll (0x4E)=" + (allWindowOnB.get() > 0));
        System.out.println("[PartyProbe] A got PartySmallWindowAdd (0x4F)=" + (addWindowOnA.get() > 0));
        System.out.println("[PartyProbe] B was asked to join (0x39)=" + (askOnB.get() > 0));
        System.out.println("[PartyProbe] A told party joined (0x3A)=" + (joinOnA.get() > 0));
        System.out.println("[PartyProbe] PARTY PROVEN (server created a real party, windows pushed) = " + created);
        System.out.println("[PartyProbe] done");
    }

    private static void readLoop(InputStream in, boolean isA)
    {
        while (true)
        {
            byte[] pl;
            try
            {
                pl = readPayload(in);
            }
            catch (java.net.SocketTimeoutException e)
            {
                continue;
            }
            catch (Exception e)
            {
                break;
            }
            if (pl == null)
            {
                break;
            }
            int op = pl[0] & 0xff;
            if (op == OP_PARTY_SMALL_WINDOW_ALL)
            {
                if (isA)
                {
                    allWindowOnA.incrementAndGet();
                }
                else
                {
                    allWindowOnB.incrementAndGet();
                    System.out.println("[PartyProbe] B: PARTY_SMALL_WINDOW_ALL(0x4E) len=" + pl.length);
                }
            }
            else if (op == OP_PARTY_SMALL_WINDOW_ADD)
            {
                if (isA)
                {
                    addWindowOnA.incrementAndGet();
                    System.out.println("[PartyProbe] A: PARTY_SMALL_WINDOW_ADD(0x4F) len=" + pl.length);
                }
                else
                {
                    addWindowOnB.incrementAndGet();
                }
            }
            else if ((op == OP_JOIN_PARTY) && isA)
            {
                joinOnA.incrementAndGet();
            }
            else if ((op == OP_ASK_JOIN_PARTY) && !isA)
            {
                askOnB.incrementAndGet();
                System.out.println("[PartyProbe] B: ASK_JOIN_PARTY(0x39) len=" + pl.length);
            }
        }
    }

    private static void sendRequestJoinParty(OutputStream out, boolean useEnc, String targetName, int distId) throws Exception
    {
        byte[] nameBytes = (targetName + "\0").getBytes(StandardCharsets.UTF_16LE);
        ByteBuffer bb = ByteBuffer.allocate(1 + nameBytes.length + 4).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) OP_C_REQUEST_JOIN_PARTY);
        bb.put(nameBytes);
        bb.putInt(distId);
        sendPayloadFrame(out, bb);
    }

    private static void sendAnswerJoinParty(OutputStream out, boolean useEnc, int response) throws Exception
    {
        ByteBuffer bb = ByteBuffer.allocate(1 + 4).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) OP_C_REQUEST_ANSWER_JOIN_PARTY);
        bb.putInt(response);
        sendPayloadFrame(out, bb);
    }

    private static void sendPayloadFrame(OutputStream out, ByteBuffer bb) throws Exception
    {
        byte[] plain = new byte[bb.position()];
        bb.flip();
        bb.get(plain);
        sendFrame(out, plain);
    }

    /** Login + GameServer enter-world for one account; returns a live GsConn after EnterWorld(0x03). */
    private static GsConn enterWorld(String account, String pass, String host, int port) throws Exception
    {
        AIPlayer player = new AIPlayer(account, 100, 1, 0);
        L2JProtocol login = new L2JProtocol(player, host, 2106, port);
        if (!login.connectAndLogin(account, pass, 0))
        {
            login.disconnect();
            throw new RuntimeException("[PartyProbe] login failed for " + account);
        }
        System.out.println("[PartyProbe] " + account + " login OK");

        Socket s = new Socket(host, port);
        s.setSoTimeout(2000);
        OutputStream out = s.getOutputStream();
        InputStream in = s.getInputStream();

        sendFrame(out, buildProtocolVersion());
        byte[] keyFrame = readFrame(in);
        if (keyFrame == null)
        {
            s.close();
            throw new RuntimeException("[PartyProbe] no KeyPacket for " + account);
        }
        int encFlag = leInt(keyFrame, 12);
        boolean useEnc = encFlag != 0;
        byte[] key = new byte[16];
        System.arraycopy(keyFrame, 4, key, 0, 8);
        System.arraycopy(KEY_TAIL, 0, key, 8, 8);
        GameCrypt crypt = new GameCrypt();
        crypt.setKey(key);
        System.out.println("[PartyProbe] " + account + " KeyPacket packetEncryption=" + encFlag);

        sendAuthLogin(out, crypt, useEnc, account, login);
        boolean spawned = false;
        long deadline = System.currentTimeMillis() + 12000;
        while (System.currentTimeMillis() < deadline)
        {
            byte[] pl;
            try
            {
                pl = readPayload(in);
            }
            catch (Exception e)
            {
                continue;
            }
            if (pl == null)
            {
                break;
            }
            byte[] plain = Arrays.copyOf(pl, pl.length);
            if (useEnc)
            {
                crypt.decrypt(plain, 0, plain.length);
            }
            int op = plain[0] & 0xff;
            if (op == OP_CHAR_SELECT_INFO)
            {
                sendCharacterSelect(out, crypt, useEnc, 0);
                System.out.println("[PartyProbe] " + account + " sent CharacterSelect(0x0D) slot=0");
            }
            if (op == OP_CHAR_SELECTED)
            {
                sendEnterWorld(out, crypt, useEnc);
                System.out.println("[PartyProbe] " + account + " sent EnterWorld(0x03)");
                spawned = true;
                break;
            }
        }
        if (!spawned)
        {
            s.close();
            throw new RuntimeException("[PartyProbe] no CharSelected for " + account);
        }
        return new GsConn(account, s, out, in, useEnc, login);
    }

    // ---------------- enter-world + wire helpers (classic Socket) ----------------

    private static byte[] buildProtocolVersion() throws Exception
    {
        ByteBuffer bb = ByteBuffer.allocate(1 + 4).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) 0x00);
        bb.putInt(PROTOCOL_VERSION);
        return bb.array();
    }

    private static void sendAuthLogin(OutputStream out, GameCrypt crypt, boolean useEnc, String account, L2JProtocol login) throws Exception
    {
        byte[] name = account.getBytes(StandardCharsets.UTF_16LE);
        ByteBuffer bb = ByteBuffer.allocate(1 + name.length + 2 + 16).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) 0x08);
        bb.put(name);
        bb.putShort((short) 0);
        bb.putInt(login.getPlayOk2());
        bb.putInt(login.getPlayOk1());
        bb.putInt(login.getLoginOk1());
        bb.putInt(login.getLoginOk2());
        byte[] plain = new byte[bb.position()];
        bb.flip();
        bb.get(plain);
        if (useEnc)
        {
            crypt.encrypt(plain, 0, plain.length);
        }
        sendFrame(out, plain);
    }

    private static void sendCharacterSelect(OutputStream out, GameCrypt crypt, boolean useEnc, int slot) throws Exception
    {
        ByteBuffer bb = ByteBuffer.allocate(1 + 4 + 2 + 4 + 4 + 4).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) OP_C_CHARACTER_SELECT);
        bb.putInt(slot);
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

    private static void sendEnterWorld(OutputStream out, GameCrypt crypt, boolean useEnc) throws Exception
    {
        ByteBuffer bb = ByteBuffer.allocate(1 + 32 + 16 + 32 + 4 + 20).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) OP_C_ENTER_WORLD);
        for (int i = 0; i < 32; i++)
        {
            bb.put((byte) 0);
        }
        bb.putInt(0);
        bb.putInt(0);
        bb.putInt(0);
        bb.putInt(0);
        for (int i = 0; i < 32; i++)
        {
            bb.put((byte) 0);
        }
        bb.putInt(0);
        for (int i = 0; i < 20; i++)
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

    private static void sendFrame(OutputStream out, byte[] payload) throws Exception
    {
        ByteBuffer buf = ByteBuffer.allocate(2 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) (payload.length + 2));
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

    private static byte[] readPayload(InputStream in) throws Exception
    {
        DataInputStream dis = (in instanceof DataInputStream) ? (DataInputStream) in : new DataInputStream(in);
        byte[] sizeBytes = new byte[2];
        dis.readFully(sizeBytes);
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

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
 * B9 probe: live chat proof (2026-08-03).
 *
 * <p>Logs in two accounts (`ai_combat_01` = CombatBot_01, `ai_combat_02` = CombatBot_02) and enters both
 * into the world (proven B3/B5 flow). Bot A then sends a WHISPER via the client chat packet {@code Say2}(0x38)
 * addressed to Bot B, containing a run-unique token. Two reader threads on each connection scan incoming
 * server packets for {@code CREATURE_SAY}(0x4A) payloads containing that token. CHAT is PROVEN when the
 * receiver (Bot B) sees the server-delivered whisper containing the token (and/or the sender sees its echo
 * {@code "->CombatBot_02"}).
 *
 * <p>Server facts (SourceCode + handler scripts):
 * <ul>
 *   <li>Client {@code Say2}(0x38) readImpl = {@code [_text:readString][_type:int][_target:readString (whisper)]};
 *       {@code readString()} = null-terminated UTF-16LE 2-byte chars. WHISPER chat client id = 2 (ChatType).</li>
 *   <li>Server {@code CREATURE_SAY}(0x4A) writeImpl = {@code [0x4A][senderObjId:int][chatType:int][senderName][text]}.</li>
 *   <li>{@code ChatWhisper} (script handler) delivers to {@code World.getPlayer(target)} with NO level gate and
 *       NO range limit (GENERAL chat is gated to level {@code MinimumChatLevel}=20, so whisper is used here).</li>
 * </ul>
 *
 * <p>Verification: B's connection observing a 0x4A packet containing the token = the server processed A's chat
 * and delivered it to the other bot. No L2JM server source changed.
 */
@Deprecated // S10-T06: superseded by examples.FleetPlay
public class ChatProbe
{
    private static final int PROTOCOL_VERSION = 746;
    private static final byte[] KEY_TAIL =
    { (byte) 0xC8, (byte) 0x27, (byte) 0x93, (byte) 0x01, (byte) 0xA1, (byte) 0x6C, (byte) 0x31, (byte) 0x97 };

    // Server->client opcodes (ServerPackets.java)
    private static final int OP_CHAR_SELECT_INFO = 0x13;
    private static final int OP_CHAR_SELECTED = 0x15;
    private static final int OP_CREATURE_SAY = 0x4A; // chat broadcast / whisper delivery

    // Client->server opcodes (ClientPackets.java)
    private static final int OP_C_CHARACTER_SELECT = 0x0D;
    private static final int OP_C_ENTER_WORLD = 0x03;
    private static final int OP_C_SAY2 = 0x38; // client chat

    private static final int CHAT_WHISPER = 2; // ChatType.WHISPER.getClientId()

    // Per-connection chat tally: how many CREATURE_SAY packets contain our token.
    private static final AtomicInteger tokenInA = new AtomicInteger(0);
    private static final AtomicInteger tokenInB = new AtomicInteger(0);
    private static final AtomicInteger sayTotalA = new AtomicInteger(0);
    private static final AtomicInteger sayTotalB = new AtomicInteger(0);
    private static volatile String token; // set before threads start

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
            login.disconnect();
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
        String targetCharName = args.length > 6 ? args[6] : "CombatBot_02";

        token = "B9WHISPER_" + (System.nanoTime() & 0xFFFFF);
        System.out.println("[ChatProbe] token=" + token);

        GsConn cnA = enterWorld(accA, passA, host, port);
        System.out.println("[ChatProbe] A IN WORLD (" + accA + ")");
        GsConn cnB = enterWorld(accB, passB, host, port);
        System.out.println("[ChatProbe] B IN WORLD (" + accB + ")");

        Thread ra = new Thread(() -> readLoop(cnA.in, tokenInA, sayTotalA), "readerA");
        Thread rb = new Thread(() -> readLoop(cnB.in, tokenInB, sayTotalB), "readerB");
        ra.start();
        rb.start();

        Thread.sleep(3000); // let both be online & the world settle

        // A whispers the token to B.
        sendSay2(cnA.out, cnA.useEnc, token, targetCharName);
        System.out.println("[ChatProbe] A sent Say2(0x38) whisper to " + targetCharName + " text=" + token);

        Thread.sleep(8000); // let the server route/deliver the whisper

        cnA.close();
        cnB.close();
        ra.join(3000);
        rb.join(3000);

        System.out.println("[ChatProbe] === CHAT TALLY ===");
        System.out.println("  CREATURE_SAY(0x4A) on A's conn: " + sayTotalA + " (with token: " + tokenInA + ")");
        System.out.println("  CREATURE_SAY(0x4A) on B's conn: " + sayTotalB + " (with token: " + tokenInB + ")");

        // PROVEN when the receiver B saw the token (or the sender saw its own echo).
        boolean deliveredToB = tokenInB.get() > 0;
        boolean echoedToA = tokenInA.get() > 0;
        boolean chatProven = deliveredToB || echoedToA;
        System.out.println("[ChatProbe] B received the whisper token = " + deliveredToB);
        System.out.println("[ChatProbe] A saw its own echo = " + echoedToA);
        System.out.println("[ChatProbe] CHAT PROVEN (server processed A's chat + delivered it) = " + chatProven);
        System.out.println("[ChatProbe] done");
    }

    /**
     * Read loop for one connection: count CREATURE_SAY(0x4A) payloads and, among them, those whose bytes
     * contain our token (UTF-16LE), until the socket closes.
     */
    private static void readLoop(InputStream in, AtomicInteger tokenHits, AtomicInteger sayTotal)
    {
        final byte[] needle = token.getBytes(StandardCharsets.UTF_16LE);
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
                break; // socket closed / EOF
            }
            if (pl == null)
            {
                break;
            }
            int op = pl[0] & 0xff;
            if (op == OP_CREATURE_SAY)
            {
                sayTotal.incrementAndGet();
                if (contains(pl, needle))
                {
                    tokenHits.incrementAndGet();
                    System.out.println("[ChatProbe] *** 0x4A with TOKEN len=" + pl.length);
                }
            }
        }
    }

    private static boolean contains(byte[] haystack, byte[] needle)
    {
        if ((needle.length == 0) || (haystack.length < needle.length))
        {
            return false;
        }
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++)
        {
            for (int j = 0; j < needle.length; j++)
            {
                if (haystack[i + j] != needle[j])
                {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    /** Login + GameServer enter-world for one account; returns a live GsConn after EnterWorld(0x03). */
    private static GsConn enterWorld(String account, String pass, String host, int port) throws Exception
    {
        AIPlayer player = new AIPlayer(account, 100, 1, 0);
        L2JProtocol login = new L2JProtocol(player, host, 2106, port);
        if (!login.connectAndLogin(account, pass, 0))
        {
            login.disconnect();
            throw new RuntimeException("[ChatProbe] login failed for " + account);
        }
        System.out.println("[ChatProbe] " + account + " login OK");

        Socket s = new Socket(host, port);
        s.setSoTimeout(2000);
        OutputStream out = s.getOutputStream();
        InputStream in = s.getInputStream();

        sendFrame(out, buildProtocolVersion());
        byte[] keyFrame = readFrame(in);
        if (keyFrame == null)
        {
            s.close();
            throw new RuntimeException("[ChatProbe] no KeyPacket for " + account);
        }
        int encFlag = leInt(keyFrame, 12);
        boolean useEnc = encFlag != 0;
        byte[] key = new byte[16];
        System.arraycopy(keyFrame, 4, key, 0, 8);
        System.arraycopy(KEY_TAIL, 0, key, 8, 8);
        GameCrypt crypt = new GameCrypt();
        crypt.setKey(key);
        System.out.println("[ChatProbe] " + account + " KeyPacket packetEncryption=" + encFlag);

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
                System.out.println("[ChatProbe] " + account + " sent CharacterSelect(0x0D) slot=0");
            }
            if (op == OP_CHAR_SELECTED)
            {
                sendEnterWorld(out, crypt, useEnc);
                System.out.println("[ChatProbe] " + account + " sent EnterWorld(0x03)");
                spawned = true;
                break;
            }
        }
        if (!spawned)
        {
            s.close();
            throw new RuntimeException("[ChatProbe] no CharSelected for " + account);
        }
        return new GsConn(account, s, out, in, useEnc, login);
    }

    // ------------------------------------------------------------------
    // Client packet builders (plaintext; game crypt disabled on this server)
    // ------------------------------------------------------------------

    /**
     * Say2 (0x38, client): [0x38][text:UTF-16LE null-term][type:int][target:UTF-16LE null-term (whisper)].
     */
    private static void sendSay2(OutputStream out, boolean useEnc, String text, String target) throws Exception
    {
        byte[] textBytes = (text + "\0").getBytes(StandardCharsets.UTF_16LE);
        byte[] targetBytes = (target + "\0").getBytes(StandardCharsets.UTF_16LE);
        ByteBuffer bb = ByteBuffer.allocate(1 + textBytes.length + 4 + targetBytes.length).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) OP_C_SAY2);
        bb.put(textBytes);
        bb.putInt(CHAT_WHISPER);
        bb.put(targetBytes);
        sendPayloadFrame(out, bb);
    }

    private static void sendPayloadFrame(OutputStream out, ByteBuffer bb) throws Exception
    {
        byte[] plain = new byte[bb.position()];
        bb.flip();
        bb.get(plain);
        sendFrame(out, plain);
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

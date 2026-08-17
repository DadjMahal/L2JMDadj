package com.aiplayer.examples;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.aiplayer.engine.AIPlayer;
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.protocol.crypt.GameCrypt;

/**
 * B6 quest probe: prove an AI player interacts with the live quest system over the external socket.
 *
 * <p>Enters CombatBot_01 (ai_combat_01, charId 2) into the world — the server auto-starts the Tutorial
 * quest `Q00255_Tutorial` (id 255) via EnterWorld.loadTutorial — then sends `RequestQuestList`(0x63) and
 * parses the server `QuestList`(0x80) reply: `[0x80][short count]` then per quest `[int id][int status]`.
 * The quest state is also verified in `gameserver.character_quests` for charId=2.
 *
 * <p>Server facts (SourceCode): packetEncryption=0 -> plaintext GS channel; classic Socket setSoTimeout
 * is honored; QuestList(0x80) layout confirmed in QuestList.java writeImpl. No L2JM server source changed.
 */
@Deprecated // S10-T06: superseded by examples.FleetPlay
public class QuestProbe
{
    private static final int PROTOCOL_VERSION = 746;
    private static final byte[] KEY_TAIL =
    { (byte) 0xC8, (byte) 0x27, (byte) 0x93, (byte) 0x01, (byte) 0xA1, (byte) 0x6C, (byte) 0x31, (byte) 0x97 };

    // Server->client opcodes (SourceCode/.../ServerPackets.java)
    private static final int OP_CHAR_SELECT_INFO = 0x13;
    private static final int OP_CHAR_SELECTED = 0x15;
    private static final int OP_QUEST_LIST = 0x80;

    // Client->server opcodes (SourceCode/.../ClientPackets.java)
    private static final int OP_C_CHARACTER_SELECT = 0x0D;
    private static final int OP_C_ENTER_WORLD = 0x03;
    private static final int OP_C_REQUEST_QUEST_LIST = 0x63;

    private static final int EXPECTED_QUEST_ID = 255; // Q00255_Tutorial

    // Quest ids found in any QuestList(0x80) packet.
    private static final List<Integer> foundQuestIds = new ArrayList<>();

    /** A live GameServer connection. */
    private static class GsConn
    {
        final Socket socket;
        final OutputStream out;
        final InputStream in;
        final boolean useEnc;
        final L2JProtocol login;

        GsConn(Socket socket, OutputStream out, InputStream in, boolean useEnc, L2JProtocol login)
        {
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
        String account = args.length > 0 ? args[0] : "ai_combat_01";
        String pass = args.length > 1 ? args[1] : "ai123pass";
        String host = args.length > 2 ? args[2] : "127.0.0.1";
        int port = args.length > 3 ? Integer.parseInt(args[3]) : 7777;

        System.out.println("[QuestProbe] entering world: " + account);
        GsConn cn = enterWorld(account, pass, host, port);
        System.out.println("[QuestProbe] IN WORLD (" + account + ")");

        // Reader thread: collect quest ids from QuestList(0x80) packets.
        Thread reader = new Thread(() -> readLoop(cn.in), "questReader");
        reader.start();

        Thread.sleep(2500); // let the spawn-time packets / auto Tutorial arrive

        System.out.println("[QuestProbe] sending RequestQuestList(0x63)");
        sendRequestQuestList(cn.out, cn.useEnc);

        Thread.sleep(4000); // give the server time to reply QuestList

        cn.close();
        reader.join(3000);

        System.out.println("[QuestProbe] === quest ids seen in QuestList(0x80) packets ===");
        if (foundQuestIds.isEmpty())
        {
            System.out.println("  (none)");
        }
        else
        {
            for (int id : foundQuestIds)
            {
                System.out.println("  quest id " + id + (id == EXPECTED_QUEST_ID ? "  <-- Q00255_Tutorial" : ""));
            }
        }
        boolean hasTutorial = foundQuestIds.contains(EXPECTED_QUEST_ID);
        System.out.println("[QuestProbe] QUEST_LIST showed id " + EXPECTED_QUEST_ID + " = " + hasTutorial
            + " (Tutorial is excluded from the visible list by its Ex flag — the live-quest proof is the"
            + " server adding Q00255 state to character_quests on enter-world; see scripts/b6_quest_prove.sh).");
        System.out.println("[QuestProbe] done");
    }

    /** Read loop: parse every QuestList(0x80) packet into quest ids. */
    private static void readLoop(InputStream in)
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
                continue; // no data yet; keep polling
            }
            catch (IOException e)
            {
                break; // socket closed
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
            if (op == OP_QUEST_LIST)
            {
                parseQuestList(pl);
            }
        }
    }

    /** QuestList(0x80): [short count] then per quest [int id][int status]. */
    private static synchronized void parseQuestList(byte[] pl)
    {
        if (pl.length < 3)
        {
            return;
        }
        int count = (pl[1] & 0xff) | ((pl[2] & 0xff) << 8);
        System.out.println("[QuestProbe] QuestList(0x80): questCount=" + count);
        int off = 3;
        for (int i = 0; i < count && (off + 8) <= pl.length; i++)
        {
            int id = leInt(pl, off);
            int status = leInt(pl, off + 4);
            off += 8;
            if (!foundQuestIds.contains(id))
            {
                foundQuestIds.add(id);
            }
            System.out.println("[QuestProbe]   quest id=" + id + " status=" + status);
        }
    }


    /** Login + GameServer enter-world; returns a live GsConn after EnterWorld(0x03). */
    private static GsConn enterWorld(String account, String pass, String host, int port) throws Exception
    {
        AIPlayer player = new AIPlayer(account, 100, 1, 0);
        L2JProtocol login = new L2JProtocol(player, host, 2106, port);
        if (!login.connectAndLogin(account, pass, 0))
        {
            login.disconnect();
            throw new RuntimeException("[QuestProbe] login failed for " + account);
        }
        System.out.println("[QuestProbe] " + account + " login OK");

        Socket s = new Socket(host, port);
        s.setSoTimeout(2000);
        OutputStream out = s.getOutputStream();
        InputStream in = s.getInputStream();

        sendFrame(out, buildProtocolVersion());
        byte[] keyFrame = readFrame(in);
        if (keyFrame == null)
        {
            s.close();
            throw new RuntimeException("[QuestProbe] no KeyPacket for " + account);
        }
        int encFlag = leInt(keyFrame, 12);
        boolean useEnc = encFlag != 0;
        byte[] key = new byte[16];
        System.arraycopy(keyFrame, 4, key, 0, 8);
        System.arraycopy(KEY_TAIL, 0, key, 8, 8);
        GameCrypt crypt = new GameCrypt();
        crypt.setKey(key);

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
            }
            if (op == OP_CHAR_SELECTED)
            {
                sendEnterWorld(out, crypt, useEnc);
                System.out.println("[QuestProbe] " + account + " sent EnterWorld(0x03)");
                spawned = true;
                break;
            }
        }
        if (!spawned)
        {
            s.close();
            throw new RuntimeException("[QuestProbe] no CharSelected for " + account);
        }
        return new GsConn(s, out, in, useEnc, login);
    }

    /** RequestQuestList (0x63): opcode-only (RequestQuestList.readImpl is empty). */
    private static void sendRequestQuestList(OutputStream out, boolean useEnc) throws Exception
    {
        byte[] payload = new byte[] { (byte) OP_C_REQUEST_QUEST_LIST };
        sendFrame(out, payload);
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


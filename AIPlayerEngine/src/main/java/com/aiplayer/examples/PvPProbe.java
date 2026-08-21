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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.aiplayer.net.AIPlayer;
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.protocol.crypt.GameCrypt;

/**
 * B5 PvP probe: two AI players fight each other over the external socket.
 *
 * <p>Logs in accounts A (`ai_combat_01`, objId/charId 2) and B (`ai_combat_02`, objId/charId 3),
 * enters both into the world at the same open-field position, then each sends `Action`(0x04) +
 * `AttackRequest`(0x0A) on the OTHER player (forced attack = PvP flag, see Creature.onForcedAttack).
 * Two reader threads tally server `Attack`(0x05) packets **by attacker objectId** on each connection;
 * PvP is PROVEN when a connection observes hits by BOTH player 2 and player 3 (mutual player-vs-player).
 *
 * <p>Server facts (Interlude, SourceCode): packetEncryption=0 -> all GS packets plaintext; classic
 * `Socket.setSoTimeout` is honored (NIO SocketChannel ignores it); Attack(0x05) layout =
 * `[0x05][attackerObjId][targetId][damage][flags][attackerX..Z][(hits-1)][...][targetX..Z]`.
 * DB has `karma/pvpkills/pkkills` for a kill-based bonus proof. No L2JM server source changed.
 */
@Deprecated // S10-T06: superseded by examples.FleetPlay
public class PvPProbe
{
    private static final int PROTOCOL_VERSION = 746;
    private static final byte[] KEY_TAIL =
    { (byte) 0xC8, (byte) 0x27, (byte) 0x93, (byte) 0x01, (byte) 0xA1, (byte) 0x6C, (byte) 0x31, (byte) 0x97 };

    // Server->client opcodes (SourceCode/.../ServerPackets.java)
    private static final int OP_CHAR_SELECT_INFO = 0x13;
    private static final int OP_CHAR_SELECTED = 0x15;
    private static final int OP_ATTACK = 0x05;
    private static final int OP_NPC_INFO = 0x16;

    // Client->server opcodes (SourceCode/.../ClientPackets.java)
    private static final int OP_C_CHARACTER_SELECT = 0x0D;
    private static final int OP_C_ENTER_WORLD = 0x03;
    private static final int OP_C_ACTION = 0x04;
    private static final int OP_C_ATTACK_REQUEST = 0x0A;

    // Per-connection Attack(0x05) tally: attacker objectId -> hit count.
    private static final Map<Integer, Integer> tallyA = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> tallyB = new ConcurrentHashMap<>();

    /** A live GameServer connection for one bot. */
    private static class GsConn
    {
        final String account;
        final Socket socket;
        final OutputStream out;
        final InputStream in;
        final boolean useEnc;
        final L2JProtocol login; // kept referenced so the login session stays valid while in-game

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
        int objIdA = args.length > 6 ? Integer.parseInt(args[6]) : 2; // CombatBot_01 charId
        int objIdB = args.length > 7 ? Integer.parseInt(args[7]) : 3; // CombatBot_02 charId
        int ox = args.length > 8 ? Integer.parseInt(args[8]) : -83477;
        int oy = args.length > 9 ? Integer.parseInt(args[9]) : 250274;
        int oz = args.length > 10 ? Integer.parseInt(args[10]) : -3596;

        System.out.println("[PvPProbe] entering world: A=" + accA + "(objId " + objIdA + ") B=" + accB + "(objId " + objIdB + ")");
        GsConn cnA = enterWorld(accA, passA, host, port);
        System.out.println("[PvPProbe] A IN WORLD (" + accA + ")");
        GsConn cnB = enterWorld(accB, passB, host, port);
        System.out.println("[PvPProbe] B IN WORLD (" + accB + ")");

        // Two reader threads: tally Attack(0x05) attacker objectIds on each connection.
        Thread ra = new Thread(() -> readLoop(cnA.in, tallyA), "readerA");
        Thread rb = new Thread(() -> readLoop(cnB.in, tallyB), "readerB");
        ra.start();
        rb.start();

        Thread.sleep(3000); // let the world populate / both become visible

        // A attacks B (select then force-attack = PvP flag).
        sendAction(cnA.out, cnA.useEnc, objIdB, ox, oy, oz);
        System.out.println("[PvPProbe] A sent Action(0x04) on objId " + objIdB);
        Thread.sleep(600); // flood protector canPerformPlayerAction()
        sendAttackRequest(cnA.out, cnA.useEnc, objIdB);
        System.out.println("[PvPProbe] A sent AttackRequest(0x0A) on objId " + objIdB);

        // B attacks A.
        sendAction(cnB.out, cnB.useEnc, objIdA, ox, oy, oz);
        System.out.println("[PvPProbe] B sent Action(0x04) on objId " + objIdA);
        Thread.sleep(600);
        sendAttackRequest(cnB.out, cnB.useEnc, objIdA);
        System.out.println("[PvPProbe] B sent AttackRequest(0x0A) on objId " + objIdA);

        Thread.sleep(18000); // let PvP run

        cnA.close();
        cnB.close();
        ra.join(3000);
        rb.join(3000);

        System.out.println("[PvPProbe] === A's connection Attack attacker-objId -> hits ===");
        tallyA.forEach((k, v) -> System.out.println("  attacker objId " + k + " : " + v + " hits"));
        System.out.println("[PvPProbe] === B's connection Attack attacker-objId -> hits ===");
        tallyB.forEach((k, v) -> System.out.println("  attacker objId " + k + " : " + v + " hits"));

        // Mutual PvP = some connection observed hits by BOTH player 2 and player 3.
        boolean seenBothA = tallyA.containsKey(objIdA) && tallyA.containsKey(objIdB);
        boolean seenBothB = tallyB.containsKey(objIdA) && tallyB.containsKey(objIdB);
        boolean pvp = seenBothA || seenBothB;
        System.out.println("[PvPProbe] A's conn saw attacks by {2,3}=" + seenBothA + " ; B's conn saw {2,3}=" + seenBothB);
        System.out.println("[PvPProbe] PVP PROVEN (mutual player-vs-player attacks) = " + pvp);
        System.out.println("[PvPProbe] done");
    }

    /** Read loop for one connection: tally ATTACK(0x05) attacker objectIds until the socket closes. */
    private static void readLoop(InputStream in, Map<Integer, Integer> tally)
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
                break; // socket closed (EOF)
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
            if ((op == OP_ATTACK) && (pl.length >= 6))
            {
                int attacker = leInt(pl, 1);
                tally.merge(attacker, 1, Integer::sum);
            }
        }
    }


    /** Login + GameServer enter-world for one account; returns a live GsConn after EnterWorld(0x03). */
    private static GsConn enterWorld(String account, String pass, String host, int port) throws Exception
    {
        AIPlayer player = new AIPlayer(account, 100, 1, 0);
        L2JProtocol login = new L2JProtocol(player, host, 2106, port);
        if (!login.connectAndLogin(account, pass, 0))
        {
            login.disconnect();
            throw new RuntimeException("[PvPProbe] login failed for " + account);
        }
        System.out.println("[PvPProbe] " + account + " login OK");

        Socket s = new Socket(host, port);
        s.setSoTimeout(2000); // classic Socket: honored by InputStream.read
        OutputStream out = s.getOutputStream();
        InputStream in = s.getInputStream();

        sendFrame(out, buildProtocolVersion());
        byte[] keyFrame = readFrame(in);
        if (keyFrame == null)
        {
            s.close();
            throw new RuntimeException("[PvPProbe] no KeyPacket for " + account);
        }
        int encFlag = leInt(keyFrame, 12);
        boolean useEnc = encFlag != 0;
        byte[] key = new byte[16];
        System.arraycopy(keyFrame, 4, key, 0, 8);
        System.arraycopy(KEY_TAIL, 0, key, 8, 8);
        GameCrypt crypt = new GameCrypt();
        crypt.setKey(key);
        System.out.println("[PvPProbe] " + account + " KeyPacket packetEncryption=" + encFlag);

        // AuthLogin -> CharSelectInfo -> CharacterSelect -> CharSelected -> EnterWorld.
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
                System.out.println("[PvPProbe] " + account + " sent CharacterSelect(0x0D) slot=0");
            }
            if (op == OP_CHAR_SELECTED)
            {
                sendEnterWorld(out, crypt, useEnc);
                System.out.println("[PvPProbe] " + account + " sent EnterWorld(0x03)");
                spawned = true;
                break;
            }
        }
        if (!spawned)
        {
            s.close();
            throw new RuntimeException("[PvPProbe] no CharSelected for " + account);
        }
        return new GsConn(account, s, out, in, useEnc, login);
    }

    // ------------------------------------------------------------------
    // Client packet builders (plaintext; game crypt disabled on this server)
    // ------------------------------------------------------------------

    /** Action (0x04): [0x04][targetObjId][originX][originY][originZ][actionId]. */
    private static void sendAction(OutputStream out, boolean useEnc, int targetObjId, int ox, int oy, int oz) throws Exception
    {
        ByteBuffer bb = ByteBuffer.allocate(1 + 4 + 4 + 4 + 4 + 1).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) OP_C_ACTION);
        bb.putInt(targetObjId);
        bb.putInt(ox);
        bb.putInt(oy);
        bb.putInt(oz);
        bb.put((byte) 0); // actionId = 0 (click)
        sendPayloadFrame(out, bb);
    }

    /** AttackRequest (0x0A): [0x0A][targetObjId][originX][originY][originZ][attackId] -> forced attack / PvP flag. */
    private static void sendAttackRequest(OutputStream out, boolean useEnc, int targetObjId) throws Exception
    {
        ByteBuffer bb = ByteBuffer.allocate(1 + 4 + 4 + 4 + 4 + 1).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) OP_C_ATTACK_REQUEST);
        bb.putInt(targetObjId);
        bb.putInt(0);
        bb.putInt(0);
        bb.putInt(0);
        bb.put((byte) 0);
        sendPayloadFrame(out, bb);
    }

    private static void sendPayloadFrame(OutputStream out, ByteBuffer bb) throws Exception
    {
        byte[] plain = new byte[bb.position()];
        bb.flip();
        bb.get(plain);
        sendFrame(out, plain); // game crypt disabled -> plaintext
    }


    // ---------------- enter-world + wire helpers (classic Socket) ----------------

    private static byte[] buildProtocolVersion() throws Exception
    {
        ByteBuffer bb = ByteBuffer.allocate(1 + 4).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) 0x00);
        bb.putInt(PROTOCOL_VERSION);
        return bb.array();
    }

    /** AuthLogin (0x08): account UTF-16LE + session key ints. */
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

    /** CharacterSelect (0x0D): charSlot(int) + unks. */
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

    /** EnterWorld (0x03): readBytes(32)+4xint+readBytes(32)+int+5x4 tracert (all zeros). */
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
        dis.readFully(sizeBytes); // SocketTimeoutException on timeout, EOFException on close
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


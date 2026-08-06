package com.aiplayer.examples;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.aiplayer.engine.AIPlayer;
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.protocol.crypt.GameCrypt;

/**
 * Phase 2 probe: login-server auth (Phase 1) then GameServer handshake + AuthLogin.
 * Reveals each decrypted GameServer packet so the enter-world flow can be completed/iterated.
 *
 * Flow implemented:
 *   ProtocolVersion (0x00, ver 746, plaintext) -> KeyPacket (0x00, plaintext, 8-byte key)
 *   -> 16-byte game key = key8 + fixed suffix {C8 27 93 01 A1 6C 31 97}
 *   -> AuthLogin (0x08, game-crypt encrypted): [08][login UTF-16LE+\0][playKey2][playKey1][loginKey1][loginKey2]
 *   -> reads + decrypts subsequent server packets, prints opcode/hex.
 */
public class EnterWorldProbe
{
    private static final int PROTOCOL_VERSION = 746;
    private static final byte[] KEY_TAIL =
    { (byte) 0xC8, (byte) 0x27, (byte) 0x93, (byte) 0x01, (byte) 0xA1, (byte) 0x6C, (byte) 0x31, (byte) 0x97 };

    public static void main(String[] args) throws Exception
    {
        String account = args.length > 0 ? args[0] : "ai_combat_01";
        String password = args.length > 1 ? args[1] : "ai123pass";
        String host = args.length > 2 ? args[2] : "127.0.0.1";
        int gamePort = args.length > 3 ? Integer.parseInt(args[3]) : 7777;

        // Phase 1: login-server auth -> capture SessionKey (kept alive in this same process).
        AIPlayer player = new AIPlayer(account, 100, 1, 0);
        L2JProtocol login = new L2JProtocol(player, host, 2106, gamePort);
        boolean ok = login.connectAndLogin(account, password, 2);
        System.out.println("[EnterWorldProbe] login=" + ok + " sessionKey(ok1=" + login.getLoginOk1()
                + ", ok2=" + login.getLoginOk2() + ", pk1=" + login.getPlayOk1() + ", pk2=" + login.getPlayOk2() + ")");
        if (!ok)
        {
            System.exit(2);
        }

        try (SocketChannel gs = SocketChannel.open())
        {
            gs.configureBlocking(true);
            gs.connect(new InetSocketAddress(host, gamePort));
            gs.socket().setSoTimeout(8000);
            System.out.println("[EnterWorldProbe] connected to GS " + host + ":" + gamePort);

            sendFrame(gs, buildProtocolVersion());

            byte[] keyFrame = readFrame(gs);
            System.out.println("[EnterWorldProbe] GS first packet (KeyPacket, plaintext), size=" + (keyFrame == null ? 0 : keyFrame.length));
            if (keyFrame == null)
            {
                login.disconnect();
                System.exit(3);
            }
            dump(keyFrame, "KeyPacket");

            int result = keyFrame[3] & 0xff;          // payload[1]
            int packetEncFlag = leInt(keyFrame, 12);  // payload[10..13]
            boolean useEnc = packetEncFlag != 0;
            byte[] key = new byte[16];
            System.arraycopy(keyFrame, 4, key, 0, 8);   // payload[2..9]
            System.arraycopy(KEY_TAIL, 0, key, 8, 8);
            System.out.println("[EnterWorldProbe] KeyPacket result=" + result + " key8=" + hex(keyFrame, 4, 8)
                    + " packetEncryption=" + packetEncFlag + " useGameCrypt=" + useEnc);

            GameCrypt crypt = new GameCrypt();
            crypt.setKey(key);

            sendAuthLogin(gs, crypt, useEnc, account, login);
            System.out.println("[EnterWorldProbe] sent AuthLogin. Reading GS replies...");

            long deadline = System.currentTimeMillis() + 9000;
            while (System.currentTimeMillis() < deadline)
            {
                byte[] payload = readPayload(gs);
                if (payload == null)
                {
                    System.out.println("[EnterWorldProbe] connection closed / no more data");
                    break;
                }
                byte[] plain = Arrays.copyOf(payload, payload.length);
                if (useEnc)
                {
                    crypt.decrypt(plain, 0, plain.length);
                }
                int op = plain[0] & 0xff;
                System.out.println("[EnterWorldProbe] GS reply opcode=0x" + Integer.toHexString(op)
                        + " plaintextLen=" + plain.length);
                if (op == 0x13)
                {
                    // CharSelectInfo: pick slot 0 and enter the world.
                    sendCharacterSelect(gs, crypt, useEnc, 0);
                    System.out.println("[EnterWorldProbe] >>> sent CharacterSelect (0x0D) slot=0");
                }
                if (op == 0x15)
                {
                    // CharSelected: server loaded the player (setOnlineStatus(true,true) already called).
                    // Hold the connection open so an external DB check can see online=1.
                    System.out.println("[EnterWorldProbe] *** CHAR SELECTED — player should be ONLINE. Holding 45s for DB check...");
                    deadline = System.currentTimeMillis() + 45000;
                }
            }
            System.out.println("[EnterWorldProbe] done (probe window closed)");
        }
        login.disconnect();
    }

    private static byte[] buildProtocolVersion() throws Exception
    {
        ByteBuffer bb = ByteBuffer.allocate(1 + 4).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) 0x00);
        bb.putInt(PROTOCOL_VERSION);
        return bb.array();
    }

    /** Build AuthLogin (0x08) plaintext then encrypt (if game crypt enabled) and send. */
    private static void sendAuthLogin(SocketChannel ch, GameCrypt crypt, boolean useEnc, String account, L2JProtocol login) throws Exception
    {
        byte[] name = account.getBytes(StandardCharsets.UTF_16LE);
        ByteBuffer bb = ByteBuffer.allocate(1 + name.length + 2 + 16).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) 0x08);
        bb.put(name);
        bb.putShort((short) 0); // null terminator
        bb.putInt(login.getPlayOk2());  // playKey2
        bb.putInt(login.getPlayOk1());  // playKey1
        bb.putInt(login.getLoginOk1()); // loginKey1
        bb.putInt(login.getLoginOk2()); // loginKey2
        byte[] plain = new byte[bb.position()];
        bb.flip();
        bb.get(plain);
        if (useEnc)
        {
            crypt.encrypt(plain, 0, plain.length);
        }
        sendFrame(ch, plain);
    }
    /** Send CharacterSelect (0x0D): charSlot(int), unk1(short), unk2/3/4(ints) — unks are zeros. */
    private static void sendCharacterSelect(SocketChannel ch, GameCrypt crypt, boolean useEnc, int charSlot) throws Exception
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
        sendFrame(ch, plain);
    }

    // ---------------- GS wire helpers ----------------

    private static void sendFrame(SocketChannel ch, byte[] payload) throws Exception
    {
        ByteBuffer buf = ByteBuffer.allocate(2 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) (payload.length + 2)); // self-inclusive size
        buf.put(payload);
        buf.flip();
        ch.write(buf);
    }

    private static byte[] readFrame(SocketChannel ch) throws Exception
    {
        byte[] payload = readPayload(ch);
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

    private static byte[] readPayload(SocketChannel ch) throws Exception
    {
        ByteBuffer sizeBuf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        long deadline = System.currentTimeMillis() + 9000;
        while (sizeBuf.hasRemaining())
        {
            int n = ch.read(sizeBuf);
            if (n < 0)
            {
                return null;
            }
            if (n == 0)
            {
                if (System.currentTimeMillis() > deadline)
                {
                    return null;
                }
                Thread.sleep(10);
            }
        }
        sizeBuf.flip();
        int size = sizeBuf.getShort() & 0xffff;
        if (size < 2 || size > 65535)
        {
            return null;
        }
        byte[] payload = new byte[size - 2];
        ByteBuffer dataBuf = ByteBuffer.wrap(payload);
        deadline = System.currentTimeMillis() + 9000;
        while (dataBuf.hasRemaining())
        {
            int n = ch.read(dataBuf);
            if (n < 0)
            {
                return null;
            }
            if (n == 0)
            {
                if (System.currentTimeMillis() > deadline)
                {
                    return null;
                }
                Thread.sleep(10);
            }
        }
        return payload;
    }

    private static int leInt(byte[] d, int i)
    {
        return (d[i] & 0xff) | ((d[i + 1] & 0xff) << 8) | ((d[i + 2] & 0xff) << 16) | ((d[i + 3] & 0xff) << 24);
    }

    private static String hex(byte[] d, int off, int len)
    {
        StringBuilder s = new StringBuilder();
        for (int i = off; i < off + len; i++)
        {
            s.append(String.format("%02x", d[i] & 0xff));
        }
        return s.toString();
    }

    private static void dump(byte[] d, String label)
    {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < d.length; i++)
        {
            s.append(String.format("%02x ", d[i] & 0xff));
            if ((i + 1) % 16 == 0)
            {
                s.append('\n');
            }
        }
        System.out.println("  [" + label + "] " + d.length + " bytes:\n  " + s.toString().replace("\n", "\n  "));
    }
}


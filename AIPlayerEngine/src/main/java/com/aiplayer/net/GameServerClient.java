package com.aiplayer.net;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Logger;

import com.aiplayer.protocol.GameServerFrameWriter;
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.protocol.PacketCodec;
import com.aiplayer.protocol.PacketLogger;
import com.aiplayer.protocol.crypt.GameCrypt;
import com.aiplayer.behavior.combat.CombatFramePlanner;

/**
 * Stream C: reusable, in-engine GameServer client (external socket, no server source changes).
 *
 * <p>Retains the proven B3 Phase-2 / B4 flow (EnterWorldProbe / CombatProbe) as a reusable class:
 * ProtocolVersion(746) plaintext -> KeyPacket (8-byte key + fixed suffix = 16-byte session key;
 * packetEncryption flag) -> AuthLogin(0x08) with the SessionKey captured by {@link L2JProtocol}
 * -> CharSelectInfo(0x13) -> CharacterSelect(0x0D, slot) -> CharSelected(0x15) -> EnterWorld(0x03)
 * (EnterWorld is required to actually spawn — B4 finding #1).
 *
 * <p>Uses a classic {@link Socket} whose InputStream honors {@code setSoTimeout} (the B4 finding:
 * NIO SocketChannel in blocking mode ignores SO_TIMEOUT and hangs).
 *
 * <p>After {@link #connectAndEnterWorld}, {@link #startReader()} feeds every inbound packet
 * (decrypted when game crypt is enabled) into a {@link PacketLogger}; the outbound stream is attached to
 * an {@link AIPlayerConnection} via {@link #attachToConnection} so {@link CombatFramePlanner} frames are
 * sent over this same socket.
 */
public class GameServerClient
{
    private static final Logger LOGGER = Logger.getLogger(GameServerClient.class.getName());

    /** Interlude protocol version (EnterWorldProbe / CombatProbe). */
    public static final int PROTOCOL_VERSION = 746;
    /** Fixed suffix appended to the 8-byte KeyPacket key to reconstruct the 16-byte session key. */
    public static final byte[] KEY_TAIL = { (byte) 0xC8, (byte) 0x27, (byte) 0x93, (byte) 0x01, (byte) 0xA1, (byte) 0x6C, (byte) 0x31, (byte) 0x97 };

    /** Current Interlude protocol version reported to the GameServer. */
    public static int getProtocolVersion()
    {
        return PROTOCOL_VERSION;
    }

    // Server->client opcodes used during the handshake
    private static final int OP_CHAR_SELECT_INFO = 0x13;
    private static final int OP_CHAR_SELECTED = 0x15;

    /** Handshake / read timeouts (ms). */
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 8000;

    private final AIPlayer player;
    private final String host;
    private final int gamePort;
    private final PacketLogger packetLogger;
    /** S2-T04: per-bot packet health — total frames read + consecutive read timeouts (feed the dashboard). */
    public volatile long packetsRead;
    public volatile int idleTimeouts;

    private Socket socket;
    private DataInputStream in;
    private OutputStream out;
    private GameCrypt crypt;
    private boolean useEnc;
    private boolean inWorld;
    private volatile boolean open;
    private Thread readerThread;

    private final Object writeLock = new Object();

    public GameServerClient(AIPlayer player, String host, int gamePort)
    {
        this.player = player;
        this.host = host;
        this.gamePort = gamePort;
        this.packetLogger = new PacketLogger(player.getName());
    }

    public PacketLogger getPacketLogger()
    {
        return packetLogger;
    }

    public boolean isInWorld()
    {
        return inWorld;
    }

    public boolean isOpen()
    {
        return open;
    }

    /**
     * Perform the full B3/B4 GameServer handshake and send EnterWorld.
     *
     * @param login    a connected {@link L2JProtocol} holding the SessionKey (PlayOk/LoginOk)
     * @param account  the account name (must match the SessionKey)
     * @param charSlot the character slot to select (0-based, as the probes use)
     * @return true when the player has been selected and EnterWorld was sent
     */
    public boolean connectAndEnterWorld(L2JProtocol login, String account, int charSlot)
    {
        try
        {
            if (login == null || !login.isLoggedIn())
            {
                LOGGER.warning("[" + player.getName() + "] GS: login not established yet");
                return false;
            }

            socket = new Socket();
            socket.connect(new InetSocketAddress(host, gamePort), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            // S2-T08: keep dead-peers detectable + no Nagle lag for 50 concurrent sessions.
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
            out = socket.getOutputStream();
            in = new DataInputStream(socket.getInputStream());
            open = true;
            LOGGER.info("[" + player.getName() + "] GS connected " + host + ":" + gamePort);

            // 1. ProtocolVersion (plaintext)
            sendPayload(PacketCodec.encodeProtocolVersion(PROTOCOL_VERSION), false);

            // 2. KeyPacket (plaintext): [0x00][result][key8][packetEncryption:int]...
            byte[] keyFrame = readFrame();
            if (keyFrame == null || keyFrame.length < 14)
            {
                LOGGER.severe("[" + player.getName() + "] GS: bad/no KeyPacket");
                closeInternal();
                return false;
            }
            int result = keyFrame[3] & 0xff;
            useEnc = leInt(keyFrame, 12) != 0;
            byte[] key = new byte[16];
            System.arraycopy(keyFrame, 4, key, 0, 8);
            System.arraycopy(KEY_TAIL, 0, key, 8, 8);
            crypt = new GameCrypt();
            crypt.setKey(key);
            LOGGER.info("[" + player.getName() + "] GS KeyPacket result=" + result + " packetEncryption=" + useEnc);
            if (result != 0)
            {
                // S2-T10: several L2JM builds tolerate a protocol-version mismatch (result=1) and keep
                // the session fully usable — verified live: a 50-bot fleet farmed for a 2h run with
                // result=1 logged every connect. A hard fail-fast blocked ALL logins, so this stays a
                // loud WARNING; actual connection death is caught downstream by isOpen()/reader EOF.
                LOGGER.warning("[" + player.getName() + "] GS: KeyPacket result=" + result
                    + " (protocol v" + getProtocolVersion() + " may mismatch the server; tolerated, continuing)");
            }

            // 3. AuthLogin with the SessionKey from the login server
            sendPayload(PacketCodec.encodeAuthLogin(account, login.getPlayOk2(), login.getPlayOk1(),
                login.getLoginOk1(), login.getLoginOk2()), useEnc);
            LOGGER.info("[" + player.getName() + "] GS AuthLogin sent");

            // 4. React to CharSelectInfo -> CharacterSelect; CharSelected -> EnterWorld
            long deadline = System.currentTimeMillis() + 12000;
            boolean sentSelect = false;
            while (System.currentTimeMillis() < deadline && !inWorld)
            {
                byte[] payload;
                try
                {
                    payload = readPayload();
                }
                catch (SocketTimeoutException te)
                {
                    continue;
                }
                if (payload == null)
                {
                    break;
                }
                if (useEnc)
                {
                    crypt.decrypt(payload, 0, payload.length);
                }
                int op = payload[0] & 0xff;
                if (op == OP_CHAR_SELECT_INFO)
                {
                    // S2-T05: record who this character really is (name/class/race) from the
                    // CharSelectInfo snapshot; the existing CharacterSelect send stays unchanged.
                    packetLogger.recordCharSelectInfo(payload);
                    if (!sentSelect)
                    {
                        sendPayload(PacketCodec.encodeCharacterSelect(charSlot), useEnc);
                        sentSelect = true;
                        LOGGER.info("[" + player.getName() + "] GS -> CharacterSelect slot=" + charSlot);
                    }
                }
                else if (op == OP_CHAR_SELECTED)
                {
                    sendPayload(PacketCodec.encodeEnterWorld(), useEnc);
                    inWorld = true;
                    open = true;
                    LOGGER.info("[" + player.getName() + "] GS EnterWorld sent - player in world");
                }
            }

            if (!inWorld)
            {
                LOGGER.severe("[" + player.getName() + "] GS enter-world failed (no CharSelected)");
                closeInternal();
                return false;
            }
            return true;
        }
        catch (IOException e)
        {
            LOGGER.severe("[" + player.getName() + "] GS error: " + e.getMessage());
            closeInternal();
            return false;
        }
    }

    /** Feed every subsequent server packet (decrypted) into the {@link PacketLogger} on a background thread. */
    public void startReader()
    {
        if (readerThread != null)
        {
            return;
        }
        readerThread = new Thread(this::readLoop, "gs-reader-" + player.getName());
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readLoop()
    {
        while (open)
        {
            try
            {
                byte[] payload = readPayload();
                if (payload == null)
                {
                    break;
                }
                if (useEnc)
                {
                    crypt.decrypt(payload, 0, payload.length);
                }
                // logPacket expects the full frame ([2-byte size][payload]).
                byte[] frame = new byte[payload.length + 2];
                frame[0] = (byte) ((payload.length + 2) & 0xff);
                frame[1] = (byte) (((payload.length + 2) >> 8) & 0xff);
                System.arraycopy(payload, 0, frame, 2, payload.length);
                packetLogger.logPacket(frame);
                packetsRead++;
                idleTimeouts = 0; // any successful frame clears the idle streak
            }
            catch (SocketTimeoutException te)
            {
                // No data within timeout — keep waiting while open.
                idleTimeouts++;
            }
            catch (EOFException e)
            {
                break;
            }
            catch (IOException e)
            {
                LOGGER.fine("[" + player.getName() + "] GS reader: " + e.getMessage());
                break;
            }
        }
        open = false;
        LOGGER.info("[" + player.getName() + "] GS reader stopped");
    }

    /**
     * Attach the live outbound stream to a connection so {@link CombatFramePlanner} frames
     * ({@code encodeAction}/{@code encodeAttackRequest}/{@code encodeMoveToLocation}) are sent here.
     */
    public void attachToConnection(AIPlayerConnection connection)
    {
        connection.setGameServerWriter(new GameServerFrameWriter(out));
        LOGGER.info("[" + player.getName() + "] GS outbound writer attached to connection");
    }

    /** Write an already-framed client packet (2-byte size + payload) — plaintext (crypt disabled). */
    public void sendGameFrame(byte[] frame) throws IOException
    {
        synchronized (writeLock)
        {
            if (out == null)
            {
                throw new IOException("GS not connected");
            }
            out.write(frame);
            out.flush();
        }
    }

    /** Frame a plaintext payload ([2-byte self-inclusive size]) and, if encrypt, game-crypt it first. */
    private void sendPayload(byte[] payload, boolean encrypt) throws IOException
    {
        byte[] wire = payload;
        if (encrypt && crypt != null)
        {
            wire = payload.clone();
            crypt.encrypt(wire, 0, wire.length);
        }
        ByteBuffer buf = ByteBuffer.allocate(2 + wire.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) (wire.length + 2));
        buf.put(wire);
        buf.flip();
        byte[] frame = new byte[buf.remaining()];
        buf.get(frame);
        synchronized (writeLock)
        {
            out.write(frame);
            out.flush();
        }
    }

    private byte[] readFrame() throws IOException
    {
        byte[] payload = readPayload();
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

    /** Read one payload after the 2-byte self-inclusive size header (honors SO_TIMEOUT). */
    private byte[] readPayload() throws IOException
    {
        byte[] sizeBytes = new byte[2];
        in.readFully(sizeBytes);
        int size = (sizeBytes[0] & 0xff) | ((sizeBytes[1] & 0xff) << 8);
        if (size < 2 || size > 65535)
        {
            return null;
        }
        byte[] payload = new byte[size - 2];
        in.readFully(payload);
        return payload;
    }

    private static int leInt(byte[] d, int i)
    {
        return (d[i] & 0xff) | ((d[i + 1] & 0xff) << 8) | ((d[i + 2] & 0xff) << 16) | ((d[i + 3] & 0xff) << 24);
    }

    public void disconnect()
    {
        open = false;
        closeInternal();
        LOGGER.info("[" + player.getName() + "] GS disconnected");
    }

    private void closeInternal()
    {
        try
        {
            if (socket != null)
            {
                socket.close();
            }
        }
        catch (IOException ignored)
        {
        }
        socket = null;
        in = null;
    }
}

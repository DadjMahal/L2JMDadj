package com.aiplayer.protocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.security.PublicKey;
import java.util.logging.Logger;

import com.aiplayer.engine.AIPlayer;

/**
 * L2JMobius protocol — real L2J Interlude login handshake (Task B2).
 * Implements the login-server auth per Documentation/Audit/31-login-protocol-handshake.md:
 *   Init (parse + unscramble RSA) -> AuthGameGuard (static blowfish key + XOR)
 *   -> RequestAuthLogin (RSA-encrypted creds, session blowfish key + checksum) -> LoginOk.
 * Game-server enter-world (char select) is Task B3.
 */
public class L2JProtocol {
    private static final Logger LOGGER = Logger.getLogger(L2JProtocol.class.getName());

    private final AIPlayer aiPlayer;
    private SocketChannel channel;
    private final String host;
    private final int loginPort;
    private final int gamePort;

    private int sessionId;
    private PublicKey rsaPublicKey;      // from unscrambled Init modulus
    private byte[] sessionBlowfishKey;   // from Init (session key for later packets)
    private int loginOk1;
    private int loginOk2;

    private volatile boolean connected = false;
    private volatile boolean loggedIn = false;
    private volatile boolean inGame = false;

    public L2JProtocol(AIPlayer aiPlayer, String host, int loginPort, int gamePort) {
        this.aiPlayer = aiPlayer;
        this.host = host;
        this.loginPort = loginPort;
        this.gamePort = gamePort;
    }

    /** Login-server authentication. Returns true when LoginOk is received (session key captured). */
    public boolean connectAndLogin(String accountName, String password, int charId) {
        try {
            inGame = false;
            loggedIn = false;
            connected = false;
            sessionId = 0;
            rsaPublicKey = null;
            sessionBlowfishKey = null;

            if (!connectLoginServer()) {
                return false;
            }

            byte[] init = readPacket();
            if (init == null || ((init[2] & 0xff) != 0x00)) {
                LOGGER.severe("[" + aiPlayer.getName() + "] Bad/no Init packet");
                return false;
            }
            parseInit(init);
            LOGGER.info("[" + aiPlayer.getName() + "] Init ok, sessionId=" + sessionId);

            // 2. AuthGameGuard (0x07) with the STATIC blowfish key + XOR pass.
            sendAuthGameGuard();
            byte[] ggReply = readPacket();
            if (ggReply == null || ((ggReply[2] & 0xff) != 0x0b)) {
                LOGGER.warning("[" + aiPlayer.getName() + "] No GGAuth reply");
                return false;
            }

            // 3. RequestAuthLogin (0x00): RSA-encrypted 128-byte creds block, session key.
            byte[] auth = LoginCrypt.buildAuthBlock(accountName, password);
            byte[] rsa = LoginCrypt.rsaEncrypt(rsaPublicKey, auth);
            byte[] payload = new byte[1 + rsa.length];
            payload[0] = 0x00;
            System.arraycopy(rsa, 0, payload, 1, rsa.length);
            sendLoginPacket(payload, false);

            // 4. Expect LoginOk (0x03) or ServerList (0x04).
            byte[] resp = readPacket();
            if (resp == null) {
                LOGGER.warning("[" + aiPlayer.getName() + "] No login response");
                return false;
            }
            int op = resp[2] & 0xff;
            if (op == 0x03) { // LoginOk
                loginOk1 = LoginCrypt.readIntLE(resp, 3);
                loginOk2 = LoginCrypt.readIntLE(resp, 7);
                loggedIn = true;
                LOGGER.info("[" + aiPlayer.getName() + "] LoginOk received (loginOk=" + loginOk1 + "," + loginOk2 + ")");
                return true;
            } else if (op == 0x04) { // ServerList (SHOW_LICENCE=false path) — proceed to server select (B3)
                LOGGER.info("[" + aiPlayer.getName() + "] Auth accepted; ServerList received (server select -> B3)");
                loggedIn = true;
                return true;
            }
            LOGGER.warning("[" + aiPlayer.getName() + "] Unexpected login response opcode 0x" + Integer.toHexString(op));
            return false;

        } catch (Exception e) {
            LOGGER.severe("[" + aiPlayer.getName() + "] Login failed: " + e.getMessage());
            disconnect();
            return false;
        }
    }

    private boolean connectLoginServer() throws IOException {
        channel = SocketChannel.open();
        channel.configureBlocking(true);
        channel.connect(new InetSocketAddress(host, loginPort));
        connected = true;
        return true;
    }

    /** Parse the Init packet (size header at [0..1]; payload at [2..]) and build our crypto context. */
    private void parseInit(byte[] pkt) {
        sessionId = LoginCrypt.readIntLE(pkt, 3);              // [3..6]
        byte[] scrambled = new byte[0x80];                     // modulus [11..138]
        System.arraycopy(pkt, 11, scrambled, 0, 0x80);
        byte[] modulus = LoginCrypt.unscrambleModulus(scrambled);
        rsaPublicKey = LoginCrypt.buildPublicKey(modulus);
        byte[] bf = LoginCrypt.readNullTerminated(pkt, 155);   // blowfish key [155..]
        sessionBlowfishKey = bf;
    }

    /** Send AuthGameGuard (opcode 0x07): sessionId + 4 reserved ints, static blowfish key + XOR. */
    private void sendAuthGameGuard() throws Exception {
        ByteBuffer bb = ByteBuffer.allocate(5 + 4 * 4);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) 0x07);
        bb.putInt(sessionId);
        for (int i = 0; i < 4; i++) bb.putInt(0);
        sendLoginPacket(bb.array(), true);
    }

    /** Frame + encrypt + write a client login packet. First packet uses the static key (+XOR); later use the session key (+checksum). */
    private void sendLoginPacket(byte[] payload, boolean staticKey) throws Exception {
        int header = staticKey ? 8 : 4;
        int sz = payload.length + header;
        int rem = sz % 8;
        if (rem != 0) sz += (8 - rem);
        int blockSize = sz + 8; // checksum/XOR reserve
        byte[] block = new byte[blockSize];
        System.arraycopy(payload, 0, block, 0, payload.length);

        if (staticKey) {
            LoginCrypt.encXORPass(block, 0, blockSize, new java.util.Random().nextInt());
            block = LoginCrypt.blowfishEncrypt(LoginCrypt.STATIC_BLOWFISH_KEY, block);
        } else {
            LoginCrypt.appendChecksum(block, 0, blockSize);
            block = LoginCrypt.blowfishEncrypt(sessionBlowfishKey, block);
        }

        ByteBuffer buf = ByteBuffer.allocate(2 + block.length);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) block.length);
        buf.put(block);
        buf.flip();
        channel.write(buf);
        LOGGER.info("[" + aiPlayer.getName() + "] sent login packet op=0x" + Integer.toHexString(payload[0] & 0xff) + " size=" + block.length);
    }

    /**
     * Read a packet with proper header (2-byte size LE).
     * Wire: [2-byte size][payload(2..)], payload[0]=opcode, payload[1..]=data.
     */
    private byte[] readPacket() throws IOException {
        ByteBuffer sizeBuf = ByteBuffer.allocate(2);
        sizeBuf.order(ByteOrder.LITTLE_ENDIAN);
        int total = 0;
        while (total < 2) {
            int read = channel.read(sizeBuf);
            if (read <= 0) return null;
            total += read;
        }
        sizeBuf.flip();
        short size = sizeBuf.getShort();
        if (size < 2 || size > 8192) return null;

        byte[] packet = new byte[size];
        ByteBuffer dataBuf = ByteBuffer.allocate(size - 2);
        dataBuf.order(ByteOrder.LITTLE_ENDIAN);
        total = 0;
        while (total < size - 2) {
            int read = channel.read(dataBuf);
            if (read <= 0) return null;
            total += read;
        }
        dataBuf.flip();
        dataBuf.get(packet, 2, size - 2);
        return packet;
    }

    /** In-game action: move (B3+). Writes the encoded movement packet. */
    public void sendMove(int x, int y, int z) throws IOException {
        int objectId = aiPlayer.getCharacterId();
        if (objectId <= 0) return;
        channel.write(ByteBuffer.wrap(PacketCodec.encodeMovement(objectId, x, y, z, (short) 0)));
    }

    /** In-game action: attack (B3+). */
    public void sendAttack(int targetId) throws IOException {
        int objectId = aiPlayer.getCharacterId();
        if (objectId <= 0) return;
        channel.write(ByteBuffer.wrap(PacketCodec.encodeAttack(objectId, 0, 0, 0)));
    }

    /** In-game action: chat (B3+). */
    public void sendChat(String message) throws IOException {
        channel.write(ByteBuffer.wrap(PacketCodec.encodeChat(message)));
    }

    public void disconnect() {
        try {
            if (channel != null && channel.isOpen()) channel.close();
        } catch (IOException ignored) {}
        connected = false;
        loggedIn = false;
        inGame = false;
    }

    public boolean isConnected() { return connected; }
    public boolean isLoggedIn() { return loggedIn; }
    public boolean isInGame() { return inGame; }
}

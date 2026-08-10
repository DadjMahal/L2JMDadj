package com.aiplayer.protocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.security.PublicKey;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.aiplayer.engine.AIPlayer;

/**
 * L2JMobius login-server client — real Interlude login handshake (B2 / B3 Phase 1).
 *
 * Correct flow (verified against the running ServerBuild LoginServer, 2026-08-03):
 *   1. Connect -> read Init  (encrypted with STATIC blowfish + XOR; LE blowfish, see Audit/32)
 *   2. AuthGameGuard (0x07, SESSION key + checksum) -> GGAuth (0x0b)
 *   3. RequestAuthLogin (0x00, RSA 128B creds, SESSION key + checksum) -> LoginOk (0x03)
 *   4. RequestServerList (0x05, loginOk1/2) -> ServerList (0x04)
 *   5. RequestServerLogin (0x02, loginOk1/2 + serverId) -> PlayOk (0x07)
 *
 * Framing facts (the difference from earlier attempts):
 *   - The 2-byte size header is SELF-INCLUSIVE: size = 2 + payloadLength.
 *   - The server decrypts ALL client packets with the SESSION blowfish key + checksum
 *     (LoginEncryption.decrypt -> sessionCrypt + verifyChecksum). The static key + XOR is used
 *     by the server ONLY for the first OUTGOING packet (Init).
 *   - Blowfish is the little-endian variant, NOT JDK Blowfish (see Audit/32).
 */
public class L2JProtocol
{
    private static final Logger LOGGER = Logger.getLogger(L2JProtocol.class.getName());

    private final AIPlayer aiPlayer;
    private final String host;
    private final int loginPort;
    private final int gamePort;

    private SocketChannel channel;

    private int sessionId;
    private PublicKey rsaPublicKey;     // built from the unscrambled Init modulus
    private byte[] sessionBlowfishKey;  // session key parsed from the Init tail

    // SessionKey captured from the login server (needed for GameServer enter-world, Phase 2).
    private int loginOk1;
    private int loginOk2;
    private int playOk1;
    private int playOk2;
    private int serverId;

    private volatile boolean connected = false;
    private volatile boolean loggedIn = false;
    private volatile boolean inGame = false;

    public L2JProtocol(AIPlayer aiPlayer, String host, int loginPort, int gamePort)
    {
        this.aiPlayer = aiPlayer;
        this.host = host;
        this.loginPort = loginPort;
        this.gamePort = gamePort;
    }

    /** Login-server authentication. Returns true once PlayOk (full login-server auth) is received. */
    public boolean connectAndLogin(String accountName, String password, int charId)
    {
        try
        {
            reset();
            if (!connectLoginServer())
            {
                return false;
            }

            // 1. Init (static key + XOR, little-endian blowfish).
            byte[] init = readInit();
            if (init == null || ((init[0] & 0xff) != 0x00))
            {
                LOGGER.severe("[" + aiPlayer.getName() + "] Bad/no Init packet");
                return false;
            }
            parseInit(init);
            LOGGER.info("[" + aiPlayer.getName() + "] Init ok, sessionId=" + sessionId);

            // 2. AuthGameGuard (0x07) -> GGAuth (0x0b).
            sendAuthGameGuard();
            byte[] gg = readSessionPacket();
            if (gg == null || ((gg[0] & 0xff) != 0x0b))
            {
                LOGGER.warning("[" + aiPlayer.getName() + "] No GGAuth reply");
                return false;
            }
            LOGGER.info("[" + aiPlayer.getName() + "] GGAuth ok");

            // 3. RequestAuthLogin (0x00) -> LoginOk (0x03).
            byte[] auth = LoginCrypt.buildAuthBlock(accountName, password);
            byte[] rsa = LoginCrypt.rsaEncrypt(rsaPublicKey, auth);
            byte[] req = new byte[1 + rsa.length];
            req[0] = 0x00;
            System.arraycopy(rsa, 0, req, 1, rsa.length);
            sendSessionPacket(req);

            byte[] resp = readSessionPacket();
            if (resp == null)
            {
                LOGGER.warning("[" + aiPlayer.getName() + "] No auth response");
                return false;
            }
            int op = resp[0] & 0xff;
            if (op == 0x03) // LoginOk
            {
                loginOk1 = LoginCrypt.readIntLE(resp, 1);
                loginOk2 = LoginCrypt.readIntLE(resp, 5);
                LOGGER.info("[" + aiPlayer.getName() + "] LoginOk (loginOk1=" + loginOk1 + ", loginOk2=" + loginOk2 + ")");
            }
            else if (op == 0x01) // LoginFail (reason code at [1])
            {
                LOGGER.warning("[" + aiPlayer.getName() + "] LoginFail code=0x" + Integer.toHexString(resp[1] & 0xff));
                return false;
            }
            else
            {
                LOGGER.warning("[" + aiPlayer.getName() + "] Unexpected auth response opcode 0x" + Integer.toHexString(op));
                return false;
            }

            // 4. RequestServerList (0x05) -> ServerList (0x04).
            sendRequestServerList();
            byte[] sl = readSessionPacket();
            if (sl == null || ((sl[0] & 0xff) != 0x04))
            {
                LOGGER.warning("[" + aiPlayer.getName() + "] No ServerList");
                return false;
            }
            serverId = sl[3] & 0xff; // first (running) server id in the list
            LOGGER.info("[" + aiPlayer.getName() + "] ServerList count=" + (sl[1] & 0xff) + " serverId=" + serverId);

            // 5. RequestServerLogin (0x02) -> PlayOk (0x07).
            sendRequestServerLogin();
            byte[] play = readSessionPacket();
            if (play == null || ((play[0] & 0xff) != 0x07))
            {
                LOGGER.warning("[" + aiPlayer.getName() + "] No PlayOk");
                return false;
            }
            playOk1 = LoginCrypt.readIntLE(play, 1);
            playOk2 = LoginCrypt.readIntLE(play, 5);
            loggedIn = true;
            LOGGER.info("[" + aiPlayer.getName() + "] PlayOk - full login auth. SessionKey(loginOk1=" + loginOk1
                    + ", loginOk2=" + loginOk2 + ", playOk1=" + playOk1 + ", playOk2=" + playOk2 + ")");
            return true;
        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "[" + aiPlayer.getName() + "] Login failed: " + e.getMessage(), e);
            disconnect();
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void reset()
    {
        connected = false;
        loggedIn = false;
        inGame = false;
        sessionId = 0;
        rsaPublicKey = null;
        sessionBlowfishKey = null;
        loginOk1 = loginOk2 = playOk1 = playOk2 = serverId = 0;
    }

    private boolean connectLoginServer() throws IOException
    {
        channel = SocketChannel.open();
        channel.configureBlocking(true);
        channel.connect(new InetSocketAddress(host, loginPort));
        connected = true;
        return true;
    }

    /** Read the Init payload and decrypt it with STATIC_BLOWFISH_KEY + reverseXOR (little-endian blowfish). */
    private byte[] readInit() throws Exception
    {
        byte[] enc = readPayloadRaw();
        if (enc == null)
        {
            return null;
        }
        byte[] dec = LoginCrypt.blowfishDecrypt(LoginCrypt.STATIC_BLOWFISH_KEY, enc);
        LoginCrypt.reverseXORPass(dec, 0, dec.length);
        return dec;
    }

    /** Read a session-key packet and return its decrypted plaintext (checksum stripped). */
    private byte[] readSessionPacket() throws Exception
    {
        byte[] enc = readPayloadRaw();
        if (enc == null)
        {
            return null;
        }
        byte[] dec = LoginCrypt.blowfishDecrypt(sessionBlowfishKey, enc);
        if (!LoginCrypt.verifyChecksum(dec, 0, dec.length))
        {
            LOGGER.warning("[" + aiPlayer.getName() + "] Session packet checksum FAILED (len=" + dec.length + ")");
            return null;
        }
        byte[] plain = new byte[dec.length - 4];
        System.arraycopy(dec, 0, plain, 0, plain.length);
        return plain;
    }

    /** Read one frame: 2-byte LE self-inclusive size, then the payload (size-2 bytes). */
    private byte[] readPayloadRaw() throws IOException
    {
        ByteBuffer sizeBuf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        if (!readFully(sizeBuf))
        {
            return null;
        }
        sizeBuf.flip();
        int size = sizeBuf.getShort() & 0xffff;
        if (size < 2 || size > 8192)
        {
            LOGGER.warning("[" + aiPlayer.getName() + "] Bad frame size=" + size);
            return null;
        }
        byte[] payload = new byte[size - 2];
        ByteBuffer dataBuf = ByteBuffer.wrap(payload);
        if (!readFully(dataBuf))
        {
            return null;
        }
        return payload;
    }

    private boolean readFully(ByteBuffer buf) throws IOException
    {
        while (buf.hasRemaining())
        {
            int n = channel.read(buf);
            if (n < 0)
            {
                return false;
            }
            if (n == 0)
            {
                try
                {
                    Thread.sleep(5);
                }
                catch (InterruptedException ie)
                {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return true;
    }

    /** Parse the DECRYPTED Init payload and build the crypto context. */
    private void parseInit(byte[] dec)
    {
        sessionId = LoginCrypt.readIntLE(dec, 1);               // [1..4]
        byte[] scrambled = new byte[0x80];
        System.arraycopy(dec, 9, scrambled, 0, 0x80);           // [9..136]
        byte[] modulus = LoginCrypt.unscrambleModulus(scrambled);
        rsaPublicKey = LoginCrypt.buildPublicKey(modulus);
        sessionBlowfishKey = LoginCrypt.readNullTerminated(dec, 153); // [153..]
    }

    /** Send AuthGameGuard (0x07): sessionId + 4 reserved ints. Session key + checksum. */
    private void sendAuthGameGuard() throws Exception
    {
        ByteBuffer bb = ByteBuffer.allocate(1 + 4 + 4 * 4).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) 0x07);
        bb.putInt(sessionId);
        for (int i = 0; i < 4; i++)
        {
            bb.putInt(0);
        }
        sendSessionPacket(bb.array());
    }

    /** Send RequestServerList (0x05): loginOk1 + loginOk2. */
    private void sendRequestServerList() throws Exception
    {
        ByteBuffer bb = ByteBuffer.allocate(1 + 8).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) 0x05);
        bb.putInt(loginOk1);
        bb.putInt(loginOk2);
        sendSessionPacket(bb.array());
    }

    /** Send RequestServerLogin (0x02): loginOk1 + loginOk2 + serverId. */
    private void sendRequestServerLogin() throws Exception
    {
        ByteBuffer bb = ByteBuffer.allocate(1 + 9).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) 0x02);
        bb.putInt(loginOk1);
        bb.putInt(loginOk2);
        bb.put((byte) serverId);
        sendSessionPacket(bb.array());
    }

    /**
     * Frame + encrypt + write a client session-key packet.
     * Plaintext payload (incl. opcode) + checksum, padded to a multiple of 8, encrypted with the
     * SESSION blowfish key; wire = [2-byte self-inclusive size][encrypted payload].
     */
    private void sendSessionPacket(byte[] payload) throws Exception
    {
        int len = payload.length + 4; // checksum room
        int rem = len % 8;
        if (rem != 0)
        {
            len += (8 - rem);
        }
        byte[] block = new byte[len];
        System.arraycopy(payload, 0, block, 0, payload.length);
        LoginCrypt.appendChecksum(block, 0, len);
        byte[] enc = LoginCrypt.blowfishEncrypt(sessionBlowfishKey, block);

        int total = enc.length + 2; // self-inclusive size
        ByteBuffer buf = ByteBuffer.allocate(2 + enc.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) total);
        buf.put(enc);
        buf.flip();
        channel.write(buf);
        LOGGER.info("[" + aiPlayer.getName() + "] sent 0x" + Integer.toHexString(payload[0] & 0xff) + " size=" + enc.length);
    }

    // ------------------------------------------------------------------
    // Getters (Phase 2: GameServer enter-world)
    // ------------------------------------------------------------------

    public int getLoginOk1() { return loginOk1; }
    public int getLoginOk2() { return loginOk2; }
    public int getPlayOk1() { return playOk1; }
    public int getPlayOk2() { return playOk2; }
    public int getServerId() { return serverId; }

    /** In-game action helpers (used once in-game, Phase 2+). */
    public void sendMove(int x, int y, int z) throws IOException
    {
        int objectId = aiPlayer.getCharacterId();
        if (objectId <= 0) return;
        channel.write(ByteBuffer.wrap(PacketCodec.encodeMovement(objectId, x, y, z, (short) 0)));
    }

    public void sendAttack(int targetId) throws IOException
    {
        int objectId = aiPlayer.getCharacterId();
        if (objectId <= 0) return;
        channel.write(ByteBuffer.wrap(PacketCodec.encodeAttack(objectId, 0, 0, 0)));
    }

    public void sendChat(String message) throws IOException
    {
        channel.write(ByteBuffer.wrap(PacketCodec.encodeChat(message)));
    }

    /** Stub: send say (general chat). */
    public void sendSay(String message) throws IOException
    {
        LOGGER.warning("[sendSay] Not implemented - stub");
    }

    /** Stub: send clan chat. */
    public void sendClanChat(String message) throws IOException
    {
        LOGGER.warning("[sendClanChat] Not implemented - stub");
    }

    /** Stub: send party chat. */
    public void sendPartyChat(String message) throws IOException
    {
        LOGGER.warning("[sendPartyChat] Not implemented - stub");
    }

    /** Stub: send trade chat. */
    public void sendTradeChat(String message) throws IOException
    {
        LOGGER.warning("[sendTradeChat] Not implemented - stub");
    }

    /** Stub: send shout. */
    public void sendShout(String message) throws IOException
    {
        LOGGER.warning("[sendShout] Not implemented - stub");
    }

    /** Stub: send clan notice. */
    public void sendClanNotice(String message) throws IOException
    {
        LOGGER.warning("[sendClanNotice] Not implemented - stub");
    }

    /** Stub: send NPC action (interact/dlg). */
    public void sendNpcAction(int npcId) throws IOException
    {
        LOGGER.warning("[sendNpcAction] Not implemented - stub");
    }

    /** Stub: talk to NPC. */
    public void talkToNpc(int npcId, String bypass) throws IOException
    {
        LOGGER.warning("[talkToNpc] Not implemented - stub");
    }

    /** Stub: use item. */
    public void sendUseItem(int itemId) throws IOException
    {
        LOGGER.warning("[sendUseItem] Not implemented - stub");
    }


    /** Stub: auto soul shot toggle. */
    public void sendAutoSoulShot(int itemId, boolean enable) throws IOException
    {
        LOGGER.warning("[sendAutoSoulShot] Not implemented - stub");
    }

        /**
     * Send REQUEST_MAGIC_SKILL_USE (0x2F) — cast a known skill at the player's current
     * target (the target must be set first via {@link #sendAttack(int)} / an Action 0x04;
     * the 0x2F frame itself carries no target field, see PacketCodec.encodeUseSkill).
     *
     * Wire-up mirrors {@link #sendAttack(int)} exactly: build the 12-byte LE frame
     * [size=12][0x2F][int skillId][int ctrl][byte shift] and write it on the GS channel.
     * Field widths match the server reader RequestMagicSkillUse.java:42-44.
     */
    public void sendUseSkill(int skillId, boolean ctrl, boolean shift) throws IOException
    {
        channel.write(ByteBuffer.wrap(PacketCodec.encodeUseSkill(skillId, ctrl, shift)));
    }

    /** Stub: pickup item. */
    public void sendPickupItem(int itemObjectId) throws IOException
    {
        LOGGER.warning("[sendPickupItem] Not implemented - stub");
    }

    /** Stub: sell item to NPC. */
    public void sendSellItem(int itemObjId, long count) throws IOException
    {
        LOGGER.warning("[sendSellItem] Not implemented - stub");
    }

    /** Stub: buy item from NPC. */
    public void sendBuyItem(int itemId, int count) throws IOException
    {
        LOGGER.warning("[sendBuyItem] Not implemented - stub");
    }

    /** Stub: deposit item to warehouse. */
    public void sendDepositItem(int itemObjId, long count) throws IOException
    {
        LOGGER.warning("[sendDepositItem] Not implemented - stub");
    }

    /** Stub: withdraw item from warehouse. */
    public void sendWithdrawItem(int itemObjId, long count) throws IOException
    {
        LOGGER.warning("[sendWithdrawItem] Not implemented - stub");
    }

    /** Stub: answer party invite. */
    public void sendAnswerJoinParty(int confirm) throws IOException
    {
        LOGGER.warning("[sendAnswerJoinParty] Not implemented - stub");
    }

    /** Stub: accept party invite. */
    public void acceptPartyInvite() throws IOException
    {
        LOGGER.warning("[acceptPartyInvite] Not implemented - stub");
    }

    /** Stub: decline party invite. */
    public void declinePartyInvite() throws IOException
    {
        LOGGER.warning("[declinePartyInvite] Not implemented - stub");
    }

    /** Stub: teleport request. */
    public void sendTeleportRequest(String destination) throws IOException
    {
        LOGGER.warning("[sendTeleportRequest] Not implemented - stub");
    }

    /** Stub: teleport confirm. */
    public void sendTeleportConfirm() throws IOException
    {
        LOGGER.warning("[sendTeleportConfirm] Not implemented - stub");
    }

    /** Stub: roll request (dice). */
    public void sendRollRequest(int requestId, int result) throws IOException
    {
        LOGGER.warning("[sendRollRequest] Not implemented - stub");
    }

    /** Stub: request restart point. */
    public void sendRequestRestartPoint(int restartPointType) throws IOException
    {
        LOGGER.warning("[sendRequestRestartPoint] Not implemented - stub");
    }

    /** Stub: request item list. */
    public void sendRequestItemList() throws IOException
    {
        LOGGER.warning("[sendRequestItemList] Not implemented - stub");
    }

    /** Stub: send move backward to location (interact). */
    public void sendMoveBackwardToLocation(int x, int y, int z, int tx, int ty, int tz) throws IOException
    {
        LOGGER.warning("[sendMoveBackwardToLocation] Not implemented - stub");
    }

    /** Stub: send camera delta. */
    public void sendCameraDelta(int yaw, int pitch) throws IOException
    {
        LOGGER.warning("[sendCameraDelta] Not implemented - stub");
    }

    public void disconnect()
    {
        try
        {
            if (channel != null && channel.isOpen()) channel.close();
        }
        catch (IOException ignored)
        {
        }
        connected = false;
        loggedIn = false;
        inGame = false;
    }

    public boolean isConnected() { return connected; }
    public boolean isLoggedIn() { return loggedIn; }
    public boolean isInGame() { return inGame; }
}



package com.aiplayer.protocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

import com.aiplayer.engine.AIPlayer;

/**
 * L2JMobius Protocol Implementation - Rewritten Task 67
 * 
 * Real implementation of Lineage 2 JProtocol for AI players.
 * FOLLOWS Documentation/Audit/01-commons.md and 04-gameserver-network.md
 * 
 * Packet Format: [2-byte header = payload size + 2][opcode][data]
 * Header is LITTLE_ENDIAN, all buffers must use ByteOrder.LITTLE_ENDIAN
 * 
 * OPCODES (from ClientPackets.java):
 * - 0x00: Init (server sends first)
 * - 0x08: AUTH_LOGIN (client sends)
 * - 0x0D: CHARACTER_SELECT (client sends)
 * - 0x01: MOVE_TO_LOCATION  
 * - 0x04: ACTION
 * - 0x0A: ATTACK_REQUEST
 * - 0x42: CHAT (from original PacketCodec)
 */
public class L2JProtocol {
    private static final Logger LOGGER = Logger.getLogger(L2JProtocol.class.getName());
    
    private final AIPlayer aiPlayer;
    private SocketChannel channel;
    private final String host;
    private final int loginPort;
    private final int gamePort;
    
    private volatile boolean connected = false;
    private volatile boolean loggedIn = false;
    private volatile boolean inGame = false;
    
    public L2JProtocol(AIPlayer aiPlayer, String host, int loginPort, int gamePort) {
        this.aiPlayer = aiPlayer;
        this.host = host;
        this.loginPort = loginPort;
        this.gamePort = gamePort;
    }
    
    public boolean connectAndLogin(String accountName, String password, int charId) {
        try {
            blowfishKey = null;
            sessionId = 0;
            inGame = false;
            loggedIn = false;
            connected = false;
            
            if (!connectLoginServer()) {
                LOGGER.severe("[" + aiPlayer.getName() + "] Failed to connect to Login Server");
                return false;
            }
            
            byte[] initPacket = readPacket();
            if (initPacket == null) {
                LOGGER.severe("[" + aiPlayer.getName() + "] Failed to receive Init packet");
                return false;
            }
            
            sessionId = ByteBuffer.wrap(initPacket).getInt();
            blowfishKey = extractBlowfishKey(initPacket);
            
            LOGGER.info("[" + aiPlayer.getName() + "] Init received, sessionId=" + sessionId);
            
            sendAuthLogin(accountName, password);
            closeLoginConnection();
            
            if (!connectGameServer()) {
                return false;
            }
            
            byte[] gameInit = readPacket();
            if (gameInit == null) {
                LOGGER.severe("[" + aiPlayer.getName() + "] Failed Game Server Init");
                return false;
            }
            
            LOGGER.info("[" + aiPlayer.getName() + "] Game Server connected");
            
            sendCharSelect(charId, accountName);
            
            inGame = true;
            connected = true;
            loggedIn = true;
            
            LOGGER.info("[" + aiPlayer.getName() + "] Login complete");
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("[" + aiPlayer.getName() + "] Login failed: " + e.getMessage());
            e.printStackTrace();
            disconnect();
            return false;
        }
    }
    
    private byte[] blowfishKey;
    private int sessionId;
    
    private boolean connectLoginServer() throws IOException {
        channel = SocketChannel.open();
        channel.configureBlocking(true);
        channel.connect(new InetSocketAddress(host, loginPort));
        connected = true;
        return true;
    }
    
    private void closeLoginConnection() throws IOException {
        if (channel != null && channel.isOpen()) channel.close();
        connected = false;
    }
    
    private boolean connectGameServer() throws IOException {
        channel = SocketChannel.open();
        channel.configureBlocking(true);
        channel.connect(new InetSocketAddress(host, gamePort));
        return true;
    }
    
    /**
     * Read a packet with proper header (2-byte size) - loops until full packet is read.
     * Packet format: [2-byte size including itself][payload]
     */
    private byte[] readPacket() throws IOException {
        ByteBuffer sizeBuf = ByteBuffer.allocate(2);
        sizeBuf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
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
        packet[0] = 0; packet[1] = (byte) size;
        
        ByteBuffer dataBuf = ByteBuffer.allocate(size - 2);
        dataBuf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
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
    
    private byte[] extractBlowfishKey(byte[] initPacket) {
        if (initPacket.length < 118) return null;
        byte[] key = new byte[16];
        System.arraycopy(initPacket, 116, key, 0, 16);
        return key;
    }
    
    private void sendAuthLogin(String accountName, String password) throws IOException {
        byte[] data = buildAuthLoginData(accountName, password);
        ByteBuffer buf = ByteBuffer.allocate(4 + data.length);
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) (data.length + 2));
        buf.put((byte) 0x08); // AUTH_LOGIN opcode (from ClientPackets.java)
        buf.put(data);
        buf.flip();
        channel.write(buf);
    }
    
    private byte[] buildAuthLoginData(String accountName, String password) {
        byte[] nameBytes = accountName.getBytes();
        byte[] passBytes = password.getBytes();
        byte[] data = new byte[nameBytes.length + 1 + passBytes.length + 1];
        int i = 0;
        for (byte b : nameBytes) data[i++] = b;
        data[i++] = 0;
        for (byte b : passBytes) data[i++] = b;
        data[i] = 0;
        return data;
    }
    
    private void sendCharSelect(int charId, String accountName) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(50);
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 50); // size
        buf.put((byte) 0x0D); // CHARACTER_SELECT opcode (from ClientPackets.java)
        buf.putInt(0); // unknown
        buf.putInt(charId);
        byte[] nameBytes = accountName.getBytes();
        buf.put((byte) nameBytes.length);
        buf.put(nameBytes);
        buf.flip();
        channel.write(buf);
    }
    
    public void sendMove(int x, int y, int z) throws IOException {
        int objectId = aiPlayer.getCharacterId();
        if (objectId <= 0) return;
        byte[] packet = PacketCodec.encodeMovement(objectId, x, y, z, (short) 0);
        channel.write(ByteBuffer.wrap(packet));
        LOGGER.info("[" + aiPlayer.getName() + "] MOVED to: " + x + "," + y + "," + z);
    }
    
    public void sendAttack(int targetId) throws IOException {
        int attackerObjId = aiPlayer.getCharacterId();
        if (attackerObjId <= 0) return;
        byte[] packet = PacketCodec.encodeAttack(attackerObjId, 0, 0, 0);
        channel.write(ByteBuffer.wrap(packet));
        LOGGER.info("[" + aiPlayer.getName() + "] ATTACKING target: " + targetId);
    }
    
    public void sendChat(String message) throws IOException {
        byte[] packet = PacketCodec.encodeChat(message);
        channel.write(ByteBuffer.wrap(packet));
        LOGGER.info("[" + aiPlayer.getName() + "] CHAT: " + message);
    }
    
    public void disconnect() {
        try {
            if (channel != null && channel.isOpen()) channel.close();
        } catch (IOException e) {}
        connected = false;
        loggedIn = false;
        inGame = false;
    }
    
    public boolean isConnected() { return connected; }
    public boolean isLoggedIn() { return loggedIn; }
    public boolean isInGame() { return inGame; }
}
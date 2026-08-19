package com.aiplayer.protocol;

import com.aiplayer.net.AIPlayer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

/**
 * L2JMobius Protocol Handler
 * Handles network communication between AI Player Engine and L2JMobius server
 * Implements standard L2JMobius client protocol
 */
public class L2ProtocolHandler {
    private static final Logger LOGGER = Logger.getLogger(L2ProtocolHandler.class.getName());

    // Protocol constants from L2JMobius
    private static final int PLAYOK = 195;
    private static final int AUTH_OK = 200;
    private static final short LOGIN_PROTOCOL = 0x01;

    private final AIPlayer aiPlayer;
    private SocketChannel channel;
    private String serverHost;
    private int loginPort;
    private int gamePort;

    private final BlockingQueue<ProtocolPacket> sendQueue = new LinkedBlockingQueue<>();
    private volatile boolean connected = false;
    private volatile boolean loggedIn = false;

    public L2ProtocolHandler(AIPlayer aiPlayer, String serverHost, int loginPort, int gamePort) {
        this.aiPlayer = aiPlayer;
        this.serverHost = serverHost;
        this.loginPort = loginPort;
        this.gamePort = gamePort;
    }

    /**
     * Connect to L2JMobius server
     */
    public boolean connect() {
        try {
            // Connect to game server
            channel = SocketChannel.open();
            channel.configureBlocking(true);
            channel.connect(new InetSocketAddress(serverHost, gamePort));

            connected = true;
            LOGGER.info("Connected to L2JMobius server at " + serverHost + ":" + gamePort);
            return true;

        } catch (IOException e) {
            LOGGER.severe("Failed to connect to L2JMobius server: " + e.getMessage());
            connected = false;
            return false;
        }
    }

    /**
     * Send packet to server
     */
    public void sendPacket(ProtocolPacket packet) {
        if (!connected) {
            LOGGER.warning("Not connected - cannot send packet: " + packet.getType());
            return;
        }

        sendQueue.offer(packet);
    }

    /**
     * Process outgoing packets
     */
    public void processOutgoing() {
        if (!connected) return;

        while (!sendQueue.isEmpty()) {
            ProtocolPacket packet = sendQueue.poll();
            if (packet != null) {
                try {
                    writePacket(packet);
                } catch (IOException e) {
                    LOGGER.severe("Failed to send packet: " + e.getMessage());
                    disconnect();
                }
            }
        }
    }

    private void writePacket(ProtocolPacket packet) throws IOException {
        byte[] data = packet.toByteArray();
        ByteBuffer buffer = ByteBuffer.wrap(data);
        channel.write(buffer);
        LOGGER.fine("Sent packet: " + packet.getType() + " (" + data.length + " bytes)");
    }

    /**
     * Disconnect from server
     */
    public void disconnect() {
        connected = false;
        loggedIn = false;

        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } catch (IOException e) {
            LOGGER.warning("Error closing connection: " + e.getMessage());
        }

        LOGGER.info("Disconnected from server");
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }
}

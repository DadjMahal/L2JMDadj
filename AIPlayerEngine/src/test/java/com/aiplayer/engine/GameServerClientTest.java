package com.aiplayer.engine;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.protocol.PacketCodec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stream C: in-process integration test for the reusable {@link GameServerClient} handshake
 * (ProtocolVersion -> KeyPacket -> AuthLogin -> CharSelectInfo -> CharacterSelect -> CharSelected
 * -> EnterWorld) using a fake GameServer socket, then a real {@code encodeAction} frame send.
 */
public class GameServerClientTest
{
    private static final byte[] KEY8 = { 1, 2, 3, 4, 5, 6, 7, 8 };

    @Test
    public void testHandshakeAndSendActionToFakeServer() throws Exception
    {
        try (ServerSocket server = new ServerSocket(0))
        {
            int port = server.getLocalPort();
            AtomicBoolean serverSawAction = new AtomicBoolean(false);

            Thread fakeServer = new Thread(() -> {
                try (Socket s = server.accept())
                {
                    s.setSoTimeout(15000);
                    DataInputStream in = new DataInputStream(s.getInputStream());
                    OutputStream out = s.getOutputStream();

                    // 1. ProtocolVersion (0x00, 746)
                    readPayload(in); // 5 bytes
                    // 2. KeyPacket (plaintext): [0x00][result][key8][packetEnc=0]
                    byte[] keyPkt = buildKeyPacket();
                    writeFrame(out, keyPkt);
                    // 3. AuthLogin
                    readPayload(in);
                    // 4. CharSelectInfo
                    writeFrame(out, new byte[]{0x13});
                    // 5. CharacterSelect
                    readPayload(in);
                    // 6. CharSelected
                    writeFrame(out, new byte[]{0x15});
                    // 7. EnterWorld
                    readPayload(in);
                    // 8. One Action frame from sendGameFrame
                    byte[] action = readPayload(in);
                    int op = action != null && action.length > 0 ? (action[0] & 0xff) : -1;
                    serverSawAction.set(op == 0x04);
                }
                catch (IOException e)
                {
                    // test will fail if the flag never set
                }
            }, "fake-gs");
            fakeServer.start();

            AIPlayer player = new AIPlayer("CombatBot_01", 100, 1, 0);
            L2JProtocol fakeLogin = new L2JProtocol(player, "127.0.0.1", 2106, port)
            {
                @Override public boolean isLoggedIn() { return true; }
                @Override public int getPlayOk2() { return 1; }
                @Override public int getPlayOk1() { return 2; }
                @Override public int getLoginOk1() { return 3; }
                @Override public int getLoginOk2() { return 4; }
            };

            GameServerClient client = new GameServerClient(player, "127.0.0.1", port);
            boolean entered = client.connectAndEnterWorld(fakeLogin, "ai_combat_01", 0);

            assertTrue(entered, "handshake should complete against the fake server");
            assertTrue(client.isInWorld(), "client should be in world after EnterWorld");

            // Send a real Action frame over the held socket; the fake server must receive it.
            client.sendGameFrame(PacketCodec.encodeAction(42, 1, 2, 3));

            fakeServer.join(5000);
            assertTrue(serverSawAction.get(), "fake server should have received the Action(0x04) frame");
            client.disconnect();
        }
    }

    @Test
    public void testRejectsWhenNotLoggedIn() throws Exception
    {
        AIPlayer player = new AIPlayer("p", 1, 1, 0);
        L2JProtocol fakeLogin = new L2JProtocol(player, "127.0.0.1", 2106, 7777)
        {
            @Override public boolean isLoggedIn() { return false; }
        };
        GameServerClient client = new GameServerClient(player, "127.0.0.1", 7777);
        assertEquals(false, client.connectAndEnterWorld(fakeLogin, "a", 0), "must refuse without login");
    }

    // ---- helpers (fake server side) ----

    private static byte[] buildKeyPacket()
    {
        ByteBuffer bb = ByteBuffer.allocate(1 + 1 + 8 + 4).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) 0x00);      // KeyPacket opcode
        bb.put((byte) 0x00);      // result
        bb.put(KEY8);             // 8-byte key
        bb.putInt(0);             // packetEncryption = 0 (crypt disabled)
        return bb.array();
    }

    private static void writeFrame(OutputStream out, byte[] payload) throws IOException
    {
        ByteBuffer bb = ByteBuffer.allocate(2 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        bb.putShort((short) (payload.length + 2));
        bb.put(payload);
        out.write(bb.array());
        out.flush();
    }

    private static byte[] readPayload(DataInputStream in) throws IOException
    {
        byte[] sizeBytes = new byte[2];
        in.readFully(sizeBytes);
        int size = (sizeBytes[0] & 0xff) | ((sizeBytes[1] & 0xff) << 8);
        byte[] payload = new byte[size - 2];
        in.readFully(payload);
        return payload;
    }
}

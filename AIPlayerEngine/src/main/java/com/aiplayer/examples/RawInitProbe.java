package com.aiplayer.examples;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/** Raw probe: dump exactly what the LoginServer sends on connect (the Init packet framing). */
public class RawInitProbe {
    public static void main(String[] args) throws Exception {
        ByteBuffer buf = ByteBuffer.allocate(300);
        try (SocketChannel ch = SocketChannel.open()) {
            ch.configureBlocking(true);
            ch.connect(new InetSocketAddress("127.0.0.1", 2106));
            long deadline = System.currentTimeMillis() + 3000;
            while (buf.position() < 300 && System.currentTimeMillis() < deadline) {
                ch.configureBlocking(false);
                int n = ch.read(buf);
                if (n < 0) break;
                if (n == 0) Thread.sleep(20);
            }
        }
        buf.flip();
        byte[] d = new byte[buf.remaining()];
        buf.get(d);
        System.out.println("RawInitProbe: read " + d.length + " bytes from LoginServer:");
        for (int i = 0; i < d.length; i++) {
            System.out.printf("%02x ", d[i] & 0xff);
            if ((i + 1) % 16 == 0) System.out.println();
        }
        System.out.println();
        if (d.length >= 2) {
            int sizeLE = (d[0] & 0xff) | ((d[1] & 0xff) << 8);
            System.out.println("First 2 bytes as LE size = " + sizeLE + "  (total read=" + d.length + ")");
        }

        // Decrypt the Init payload (first server packet uses the STATIC blowfish key + XOR pass).
        if (d.length > 2) {
            byte[] enc = new byte[d.length - 2];
            System.arraycopy(d, 2, enc, 0, enc.length);
            try {
                byte[] dec = com.aiplayer.protocol.LoginCrypt.blowfishDecrypt(com.aiplayer.protocol.LoginCrypt.STATIC_BLOWFISH_KEY, enc);
                com.aiplayer.protocol.LoginCrypt.reverseXORPass(dec, 0, dec.length);
                System.out.println("\nDecrypted Init payload (" + dec.length + " bytes):");
                for (int i = 0; i < dec.length; i++) {
                    System.out.printf("%02x ", dec[i] & 0xff);
                    if ((i + 1) % 16 == 0) System.out.println();
                }
                System.out.println("\nDecrypted[0] (opcode, expect 0x00) = 0x" + Integer.toHexString(dec[0] & 0xff));
                // GG magic check: opcode+session(4)+proto(4)=9, modulus 9..136 (128), GG 137..152
                System.out.printf("GG magic @137 (expect 4e 95 dd 29 fc 9c c3 77): %02x %02x %02x %02x %02x %02x %02x %02x%n",
                        dec[137] & 0xff, dec[138] & 0xff, dec[139] & 0xff, dec[140] & 0xff,
                        dec[141] & 0xff, dec[142] & 0xff, dec[143] & 0xff, dec[144] & 0xff);
            } catch (Exception e) {
                System.out.println("Decrypt failed: " + e);
            }
        }
    }
}

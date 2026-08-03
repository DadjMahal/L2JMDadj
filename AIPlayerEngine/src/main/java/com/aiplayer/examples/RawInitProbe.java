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
                byte[] base = com.aiplayer.protocol.LoginCrypt.blowfishDecrypt(com.aiplayer.protocol.LoginCrypt.STATIC_BLOWFISH_KEY, enc);
                System.out.println("\nBrute-force reverseXOR offset for protoRev 0x0000c621 (LE 21 c6 00 00):");
                for (int o = 0; o <= 10; o++) {
                    byte[] dec = java.util.Arrays.copyOf(base, base.length);
                    try {
                        com.aiplayer.protocol.LoginCrypt.reverseXORPass(dec, o, dec.length);
                    } catch (Exception e) {
                        continue;
                    }
                    boolean proto = (dec.length > 9 && (dec[5] & 0xff) == 0x21 && (dec[6] & 0xff) == 0xc6 && dec[7] == 0 && dec[8] == 0);
                    if (proto || o <= 4) {
                        System.out.printf("offset=%d opcode=%02x proto=%02x%02x%02x%02x %s%n",
                                o, dec[0] & 0xff, dec[5] & 0xff, dec[6] & 0xff, dec[7] & 0xff, dec[8] & 0xff,
                                proto ? " <== protoRev MATCH!" : "");
                    }
                }
            } catch (Exception e) {
                System.out.println("Decrypt/bf failed: " + e);
            }
        }
    }
}

package com.aiplayer.examples;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import com.aiplayer.protocol.LoginCrypt;

/**
 * Phase 0 diagnostic (B3 unblock): prove the live LoginServer Init frame can be
 * decoded with the STATIC_BLOWFISH_KEY + encXORPass hypothesis, using the exact
 * server-side coordinates (offset=HEADER_SIZE=2, full aligned block).
 *
 * Steps:
 *  1. Self-test the JDK "Blowfish/ECB/NoPadding" against the published Blowfish
 *     test vectors (Schneier/BC). This kills/confirms the "server's custom
 *     BlowfishEngine != JDK Blowfish" hypothesis (#1 in SESSION_HANDOFF).
 *  2. Connect to :2106, read the EXACT Init frame via its 2-byte LE size header.
 *  3. Blowfish-decrypt the aligned payload with the STATIC key, then reverseXORPass
 *     over the same coordinates the server used -> parse opcode/sessionId/protoRev/
 *     modulus/GG/session-blowfish-key, and print a definitive match finding.
 *
 * No L2JM server source is touched -- this is an external diagnostic client.
 */
public class InitDecodeProbe {

    /** Expected protoRev magic bytes (LE of 0x0000c621). */
    private static final byte[] PROTO_REV = { (byte) 0x21, (byte) 0xc6, 0x00, 0x00 };

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 2106;

        System.out.println("=== Phase 0: Live Init decode probe ===");
        System.out.println("Target: " + host + ":" + port + "\n");

        boolean vectorsOk = blowfishSelfTest();
        System.out.println("Blowfish known-answer self-test: " + (vectorsOk ? "PASS (JDK Blowfish is standard)" : "FAIL"));

        byte[] frame = readExactFrame(host, port);
        if (frame == null) {
            System.out.println("RESULT: FAIL -- could not read a complete Init frame from " + host + ":" + port);
            System.exit(2);
        }

        System.out.println("\nRaw frame: " + frame.length + " bytes:");
        for (int i = 0; i < frame.length; i++) {
            System.out.printf("%02x ", frame[i] & 0xff);
            if ((i + 1) % 16 == 0) System.out.println();
        }
        System.out.println();

        int size = (frame[0] & 0xff) | ((frame[1] & 0xff) << 8);
        byte[] payload = new byte[size - 2];
        System.arraycopy(frame, 2, payload, 0, payload.length);
        System.out.println("\nSize header LE = " + size + "; aligned encrypted payload = " + payload.length + " bytes");
        System.out.println("Payload length % 8 == 0 ? " + ((payload.length % 8) == 0));

        // Layout inside the decrypted payload:
        // [0]=opcode [1..4]=sessionId [5..8]=protoRev
        // [9..136]=RSA modulus [137..152]=GG [153..168]=session blowfish key [169]=null
        DecodeResult primary = tryDecode(payload, "decrypt THEN reverseXORPass(0,len)", "dec-then-xor");
        DecodeResult alt1 = tryDecode(payload, "reverseXORPass THEN decrypt", "xor-then-dec");
        DecodeResult alt2 = tryDecode(payload, "decrypt only (no XOR)", "dec-only");

        DecodeResult best = primary;
        if (best == null || (alt1 != null && alt1.protoRevMatched && !best.protoRevMatched)) best = alt1;
        if (alt2 != null && alt2.protoRevMatched && (best == null || !best.protoRevMatched)) best = alt2;

        if (best != null && best.protoRevMatched) {
            System.out.println("\nRESULT: MATCH -- " + best.label + " reproduces protoRev 0x0000c621.");
            System.out.println("    opcode        = 0x" + String.format("%02x", best.opcode));
            System.out.println("    sessionId     = " + best.sessionId + " (0x" + Integer.toHexString(best.sessionId) + ")");
            System.out.println("    protoRev      = 0x0000c621 confirmed");
            System.out.println("    modulus[0..7] = " + hex(best.modulus, 8));
            System.out.println("    GG[0..15]     = " + hex(best.gg, 16));
            System.out.println("    blowfishKey   = " + hex(best.blowfishKey, best.blowfishKey.length));
            System.out.println("\nUse sessionId=" + best.sessionId + " and blowfishKey="
                    + hex(best.blowfishKey, best.blowfishKey.length)
                    + " for AuthGameGuard -> RequestAuthLogin (Phase 1).");
            System.exit(0);
        } else {
            System.out.println("\nRESULT: NO MATCH -- none of the hypotheses reproduced protoRev 0x0000c621.");
            if (primary != null) printResult("decrypt-then-xor", primary);
            if (alt1 != null) printResult("xor-then-dec", alt1);
            if (alt2 != null) printResult("decrypt-only", alt2);
            System.exit(3);
        }
    }
    /** Connect and read exactly one frame using the 2-byte LE size header (blocking). */
    private static byte[] readExactFrame(String host, int port) throws Exception {
        try (SocketChannel ch = SocketChannel.open()) {
            ch.configureBlocking(true);
            ch.connect(new InetSocketAddress(host, port));
            ch.socket().setSoTimeout(5000);

            ByteBuffer sizeBuf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
            if (!readFully(ch, sizeBuf)) return null;
            sizeBuf.flip();
            int size = sizeBuf.getShort() & 0xffff;
            if (size < 2 || size > 8192) {
                System.out.println("Bad size header: " + size);
                return null;
            }
            byte[] payload = new byte[size - 2];
            ByteBuffer dataBuf = ByteBuffer.wrap(payload);
            if (!readFully(ch, dataBuf)) return null;

            byte[] frame = new byte[size];
            frame[0] = (byte) (size & 0xff);
            frame[1] = (byte) ((size >> 8) & 0xff);
            System.arraycopy(payload, 0, frame, 2, payload.length);
            return frame;
        }
    }

    private static boolean readFully(SocketChannel ch, ByteBuffer buf) throws Exception {
        while (buf.hasRemaining()) {
            int n = ch.read(buf);
            if (n < 0) return false;
            if (n == 0) Thread.sleep(10);
        }
        return true;
    }

    /** Try one decode hypothesis; returns a parsed result or null if it threw. */
    private static DecodeResult tryDecode(byte[] payload, String label, String mode) {
        byte[] work = java.util.Arrays.copyOf(payload, payload.length);
        try {
            if (mode.equals("dec-then-xor")) {
                work = LoginCrypt.blowfishDecrypt(LoginCrypt.STATIC_BLOWFISH_KEY, work);
                LoginCrypt.reverseXORPass(work, 0, work.length);
            } else if (mode.equals("xor-then-dec")) {
                LoginCrypt.reverseXORPass(work, 0, work.length);
                work = LoginCrypt.blowfishDecrypt(LoginCrypt.STATIC_BLOWFISH_KEY, work);
            } else {
                work = LoginCrypt.blowfishDecrypt(LoginCrypt.STATIC_BLOWFISH_KEY, work);
            }
        } catch (Exception e) {
            System.out.println("    [" + label + "] threw " + e);
            return null;
        }
        return parse(work, label);
    }

    private static DecodeResult parse(byte[] work, String label) {
        DecodeResult r = new DecodeResult();
        r.label = label;
        r.opcode = work[0] & 0xff;
        r.sessionId = leInt(work, 1);
        r.modulus = new byte[0x80];
        System.arraycopy(work, 9, r.modulus, 0, 0x80);
        r.gg = new byte[16];
        System.arraycopy(work, 137, r.gg, 0, 16);
        int blobLen = 0;
        while (blobLen < 32 && (153 + blobLen) < work.length && work[153 + blobLen] != 0) blobLen++;
        r.blowfishKey = java.util.Arrays.copyOfRange(work, 153, 153 + blobLen);
        r.protoRev = java.util.Arrays.copyOfRange(work, 5, 9);
        r.protoRevMatched = (r.opcode == 0x00)
                && work.length >= 9
                && work[5] == PROTO_REV[0] && work[6] == PROTO_REV[1]
                && work[7] == PROTO_REV[2] && work[8] == PROTO_REV[3];
        return r;
    }

    private static void printResult(String label, DecodeResult r) {
        System.out.println("[" + label + "] opcode=0x" + String.format("%02x", r.opcode)
                + " sessionId=" + r.sessionId
                + " protoRevBytes=" + hex(r.protoRev, 4)
                + " matched=" + r.protoRevMatched);
    }



    /** Standard Blowfish known-answer self-test (Schneier/BC vectors) via the JDK. */
    private static boolean blowfishSelfTest() throws Exception {
        Object[][] vectors = {
            { new byte[] {0,0,0,0,0,0,0,0}, new byte[] {0,0,0,0,0,0,0,0},
              new byte[] {(byte)0x4E,(byte)0xF9,(byte)0x97,(byte)0x45,(byte)0x61,(byte)0x98,(byte)0xDD,(byte)0x78} },
            { new byte[] {(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF},
              new byte[] {(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF},
              new byte[] {(byte)0x51,(byte)0x86,(byte)0x6F,(byte)0xD5,(byte)0xB8,(byte)0x5E,(byte)0xCB,(byte)0x8A} },
            { new byte[] {(byte)0x01,0x23,0x45,0x67,(byte)0x89,(byte)0xAB,(byte)0xCD,(byte)0xEF},
              new byte[] {0x11,0x11,0x11,0x11,0x11,0x11,0x11,0x11},
              new byte[] {(byte)0x61,(byte)0xF9,(byte)0xC3,(byte)0x80,0x22,(byte)0x81,(byte)0xB0,(byte)0x96} },
            { new byte[] {0x30,0,0,0,0,0,0,0}, new byte[] {0x10,0,0,0,0,0,0,1},
              new byte[] {(byte)0x7D,(byte)0x85,(byte)0x6F,(byte)0x9A,(byte)0x61,(byte)0x30,(byte)0x63,(byte)0xF2} },
            { new byte[] {0x11,0x11,0x11,0x11,0x11,0x11,0x11,0x11},
              new byte[] {0x11,0x11,0x11,0x11,0x11,0x11,0x11,0x11},
              new byte[] {0x24,0x66,(byte)0xDD,(byte)0x87,(byte)0x8B,(byte)0x96,0x3C,(byte)0x9D} },
        };
        boolean all = true;
        for (int i = 0; i < vectors.length; i++) {
            byte[] key = (byte[]) vectors[i][0];
            byte[] pt = (byte[]) vectors[i][1];
            byte[] exp = (byte[]) vectors[i][2];
            Cipher c = Cipher.getInstance("Blowfish/ECB/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "Blowfish"));
            byte[] got = c.doFinal(pt);
            boolean ok = java.util.Arrays.equals(got, exp);
            all &= ok;
            System.out.println("    vector " + (i + 1) + ": " + (ok ? "PASS" : "FAIL")
                    + " got=" + hex(got, 8) + " exp=" + hex(exp, 8));
        }
        return all;
    }

    private static int leInt(byte[] d, int i) {
        return (d[i] & 0xff) | ((d[i + 1] & 0xff) << 8) | ((d[i + 2] & 0xff) << 16) | ((d[i + 3] & 0xff) << 24);
    }

    private static String hex(byte[] d, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(String.format("%02x", d[i] & 0xff));
        return sb.toString();
    }

    private static class DecodeResult {
        String label;
        int opcode;
        int sessionId;
        byte[] modulus;
        byte[] gg;
        byte[] blowfishKey;
        byte[] protoRev = { 0, 0, 0, 0 };
        boolean protoRevMatched;
    }
}


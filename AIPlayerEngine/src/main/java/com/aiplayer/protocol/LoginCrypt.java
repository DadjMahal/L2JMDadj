package com.aiplayer.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;

/**
 * L2J Interlude login-protocol crypto helper (Task B2).
 * Ports the loginserver crypt logic (NewCrypt / ScrambledKeyPair) using the JDK
 * (Blowfish/ECB/NoPadding, RSA/ECB/NoPadding) so the AIPlayerEngine can speak the
 * real handshake. See Documentation/Audit/31-login-protocol-handshake.md.
 */
public final class LoginCrypt {
    /** L2J fixed RSA public exponent (F4 = 65537). LoginController uses RSAKeyGenParameterSpec(1024, F4). */
    public static final int RSA_PUBLIC_EXPONENT = 0x10001;
    public static final int RSA_BLOCK_SIZE = 128; // 1024-bit modulus

    /** Static bootstrap Blowfish key for the FIRST client packet (AuthGameGuard). LoginEncryption.STATIC_BLOWFISH_KEY. */
    public static final byte[] STATIC_BLOWFISH_KEY = {
        (byte) 0x6b, (byte) 0x60, (byte) 0xcb, (byte) 0x5b, (byte) 0x82, (byte) 0xce, (byte) 0x90, (byte) 0xb1,
        (byte) 0xcc, (byte) 0x2b, (byte) 0x6c, (byte) 0x55, (byte) 0x6c, (byte) 0x6c, (byte) 0x6c, (byte) 0x6c
    };

    private LoginCrypt() {}

    /**
     * Unscramble the 128-byte modulus the server sends in the Init packet.
     * Inverse of loginserver ScrambledKeyPair.scrambleModulus.
     */
    public static byte[] unscrambleModulus(byte[] scrambled) {
        if (scrambled == null || scrambled.length != 0x80) {
            throw new IllegalArgumentException("scrambled modulus must be 0x80 bytes");
        }
        byte[] m = Arrays.copyOf(scrambled, 0x80);
        // Step 4 inverse: xor last 0x40 bytes with first 0x40 bytes.
        for (int i = 0; i < 0x40; i++) m[0x40 + i] = (byte) (m[0x40 + i] ^ m[i]);
        // Step 3 inverse: xor bytes 0x0d-0x10 with bytes 0x34-0x38.
        for (int i = 0; i < 4; i++) m[0x0d + i] = (byte) (m[0x0d + i] ^ m[0x34 + i]);
        // Step 2 inverse: xor first 0x40 bytes with last 0x40 bytes.
        for (int i = 0; i < 0x40; i++) m[i] = (byte) (m[i] ^ m[0x40 + i]);
        // Step 1 inverse: swap 0x4d-0x50 with 0x00-0x04.
        for (int i = 0; i < 4; i++) {
            byte t = m[0x00 + i];
            m[0x00 + i] = m[0x4d + i];
            m[0x4d + i] = t;
        }
        return m;
    }

    /** Build the RSA public key from an (unscrambled) 128-byte modulus + the fixed L2J exponent. */
    public static PublicKey buildPublicKey(byte[] modulus) {
        byte[] unsigned = new byte[modulus.length + 1];
        unsigned[0] = 0x00;
        System.arraycopy(modulus, 0, unsigned, 1, modulus.length);
        java.math.BigInteger n = new java.math.BigInteger(unsigned);
        java.math.BigInteger e = java.math.BigInteger.valueOf(RSA_PUBLIC_EXPONENT);
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));
        } catch (Exception ex) {
            throw new RuntimeException("Failed to build RSA public key: " + ex.getMessage(), ex);
        }
    }

    /** RSA-encrypt a 128-byte block (RSA/ECB/NoPadding) with the server public key. */
    public static byte[] rsaEncrypt(PublicKey pubKey, byte[] block128) {
        if (block128 == null || block128.length != RSA_BLOCK_SIZE) {
            throw new IllegalArgumentException("block must be 128 bytes");
        }
        try {
            Cipher c = Cipher.getInstance("RSA/ECB/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, pubKey);
            byte[] out = c.doFinal(block128);
            if (out.length > RSA_BLOCK_SIZE) {
                byte[] t = new byte[RSA_BLOCK_SIZE];
                System.arraycopy(out, out.length - RSA_BLOCK_SIZE, t, 0, RSA_BLOCK_SIZE);
                out = t;
            } else if (out.length < RSA_BLOCK_SIZE) {
                byte[] t = new byte[RSA_BLOCK_SIZE];
                System.arraycopy(out, 0, t, RSA_BLOCK_SIZE - out.length, out.length);
                out = t;
            }
            return out;
        } catch (Exception ex) {
            throw new RuntimeException("RSA encrypt failed: " + ex.getMessage(), ex);
        }
    }

    /** Build the 128-byte RequestAuthLogin plaintext block: user @ 0x5E (14), password @ 0x6C (16). */
    public static byte[] buildAuthBlock(String user, String password) {
        byte[] block = new byte[RSA_BLOCK_SIZE];
        byte[] u = user.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] p = password.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(u, 0, block, 0x5E, Math.min(u.length, 14));
        System.arraycopy(p, 0, block, 0x6C, Math.min(p.length, 16));
        return block;
    }

    // ---- Blowfish (ECB, NoPadding) via JDK ----

    private static javax.crypto.Cipher blowfish(byte[] key, int mode) throws Exception {
        javax.crypto.spec.SecretKeySpec sk = new javax.crypto.spec.SecretKeySpec(key, "Blowfish");
        javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("Blowfish/ECB/NoPadding");
        c.init(mode, sk);
        return c;
    }

    /** Blowfish-encrypt (size must be a multiple of 8). Returns a new array. */
    public static byte[] blowfishEncrypt(byte[] key, byte[] data) throws Exception {
        return blowfish(key, javax.crypto.Cipher.ENCRYPT_MODE).doFinal(data);
    }

    /** Blowfish-decrypt (size must be a multiple of 8). Returns a new array. */
    public static byte[] blowfishDecrypt(byte[] key, byte[] data) throws Exception {
        return blowfish(key, javax.crypto.Cipher.DECRYPT_MODE).doFinal(data);
    }

    // ---- NewCrypt checksum / XOR pass (LE ints) ----

    /** Append XOR checksum (XOR of 4-byte words) into the final 4 bytes. size multiple of 4, > 4. */
    public static void appendChecksum(byte[] data, int offset, int size) {
        int checksum = 0;
        int count = size - 4;
        for (int i = offset; i < count; i += 4) checksum ^= readIntLE(data, i);
        writeIntLE(data, count, checksum);
    }

    /** Verify XOR checksum of a received packet. */
    public static boolean verifyChecksum(byte[] data, int offset, int size) {
        if ((size & 3) != 0 || size <= 4) return false;
        int checksum = 0;
        int count = size - 4;
        int i;
        for (i = offset; i < count; i += 4) checksum ^= readIntLE(data, i);
        return readIntLE(data, i) == checksum;
    }

    /** XOR pass for the first (static-key) packet. Port of NewCrypt.encXORPass. */
    public static void encXORPass(byte[] data, int offset, int size, int xorKey) {
        final int stop = size - 8;
        int pos = 4 + offset;
        int progressiveKey = xorKey;
        while (pos < stop) {
            int v = readIntLE(data, pos);
            progressiveKey += v;
            v ^= progressiveKey;
            writeIntLE(data, pos, v);
            pos += 4;
        }
        writeIntLE(data, pos, progressiveKey);
    }

    /**
     * Reverse the XOR pass applied by encXORPass (port of the client-side deXOR).
     * Iterates from the end using the stored progressive key.
     */
    public static void reverseXORPass(byte[] data, int offset, int size) {
        final int stop = size - 8;
        int pos = stop - 4;
        int progressiveKey = readIntLE(data, stop); // final key stored at [size-8..size-5]
        while (pos >= 4 + offset) {
            int s = readIntLE(data, pos);
            int v = s ^ progressiveKey;
            writeIntLE(data, pos, v);
            progressiveKey -= v;
            pos -= 4;
        }
    }

    static int readIntLE(byte[] d, int i) {
        return (d[i] & 0xff) | ((d[i + 1] & 0xff) << 8) | ((d[i + 2] & 0xff) << 16) | ((d[i + 3] & 0xff) << 24);
    }

    static void writeIntLE(byte[] d, int i, int v) {
        d[i] = (byte) v;
        d[i + 1] = (byte) (v >>> 8);
        d[i + 2] = (byte) (v >>> 16);
        d[i + 3] = (byte) (v >>> 24);
    }

    /** Read a null-terminated byte string from an array (parses the Init blowfish key). */
    public static byte[] readNullTerminated(byte[] data, int offset) {
        int end = offset;
        while (end < data.length && data[end] != 0) end++;
        return Arrays.copyOfRange(data, offset, end);
    }
}


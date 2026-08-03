package com.aiplayer.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Regression tests for the little-endian Blowfish port (com.aiplayer.protocol.crypt.BlowfishEngine)
 * that the LoginServer actually uses. JDK "Blowfish/ECB/NoPadding" is big-endian and is NOT
 * wire-compatible with the L2J server -- see Documentation/Audit/32-init-decode.md.
 *
 * The reference vector below was produced by the ACTUAL server engine (SourceCode
 * loginserver/crypt/BlowfishEngine + ArrayPacketBuffer, LE readInt) on 2026-08-03.
 */
class BlowfishLeParityTest {

    private static final byte[] ZEROS = new byte[8];

    /** Server-engine reference: encrypt(all-zero block) with the static login key. */
    private static final String SERVER_REF = "46d6a19b80854746";

    @Test
    void serverEngineParityEncrypt() throws Exception {
        byte[] enc = LoginCrypt.blowfishEncrypt(LoginCrypt.STATIC_BLOWFISH_KEY, ZEROS);
        assertEquals(SERVER_REF, toHex(enc),
                "little-endian Blowfish must match the server engine byte-for-byte");
    }

    @Test
    void serverEngineRoundTrip() throws Exception {
        byte[] enc = LoginCrypt.blowfishEncrypt(LoginCrypt.STATIC_BLOWFISH_KEY, ZEROS);
        byte[] dec = LoginCrypt.blowfishDecrypt(LoginCrypt.STATIC_BLOWFISH_KEY, enc);
        assertArrayEquals(ZEROS, dec, "Blowfish decrypt must invert encrypt");
    }

    @Test
    void jdkBlowfishIsNotWireCompatible() throws Exception {
        // Proves the point: JDK big-endian Blowfish does NOT yield the server's reference.
        javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("Blowfish/ECB/NoPadding");
        c.init(javax.crypto.Cipher.ENCRYPT_MODE,
                new javax.crypto.spec.SecretKeySpec(LoginCrypt.STATIC_BLOWFISH_KEY, "Blowfish"));
        byte[] jdk = c.doFinal(ZEROS);
        assertEquals("9ba1d64646478580", toHex(jdk), "JDK blowfish output (big-endian) — sanity");
        // And JDK output must NOT equal the server reference (this is the historical B3 blocker).
        org.junit.jupiter.api.Assertions.assertNotEquals(SERVER_REF, toHex(jdk));
    }

    @Test
    void rejectsNonMultipleOfEight() {
        assertThrows(IllegalArgumentException.class,
                () -> LoginCrypt.blowfishEncrypt(LoginCrypt.STATIC_BLOWFISH_KEY, new byte[7]));
    }

    private static String toHex(byte[] d) {
        StringBuilder s = new StringBuilder();
        for (byte b : d) s.append(String.format("%02x", b & 0xff));
        return s.toString();
    }
}

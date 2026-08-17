package com.aiplayer.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;

import javax.crypto.Cipher;

import org.junit.jupiter.api.Test;

/**
 * Property-style (deterministic, no IO) tests for {@link LoginCrypt} crypto primitives:
 * RSA auth-block round-trip, Blowfish block round-trip, checksum append/verify, and the
 * XOR pass scramble/discramble.
 *
 * Assumptions (driven by what the code exposes -- nothing private is fabricated):
 *  - LoginCrypt exposes no RSA private key, so the RSA round-trip is exercised through a
 *    JDK-generated KeyPair used only here to prove {@link #rsaEncrypt} inverts correctly.
 *  - Blowfish is a block cipher (NoPadding, 8-byte blocks): empty/1/7-byte payloads do NOT
 *    round-trip -- they are asserted to be rejected, while 8/16-byte payloads round-trip.
 */
class LoginCryptPropertyTest
{
    private static final byte[] STATIC_KEY = LoginCrypt.STATIC_BLOWFISH_KEY;

    // ---- (a) RSA auth-block round-trip (public/private via local JDK KeyPair) ----

    @Test
    void rsaBlockRoundTripsViaPublicPrivateKeyPair() throws Exception
    {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(LoginCrypt.RSA_BLOCK_SIZE * 8); // 1024-bit modulus -> 128-byte blocks
        KeyPair kp = gen.generateKeyPair();
        byte[] block = "user\0\0pass11112222".getBytes(StandardCharsets.UTF_8);
        block = Arrays.copyOf(block, LoginCrypt.RSA_BLOCK_SIZE);

        byte[] enc = LoginCrypt.rsaEncrypt(kp.getPublic(), block);

        Cipher c = Cipher.getInstance("RSA/ECB/NoPadding");
        c.init(Cipher.DECRYPT_MODE, kp.getPrivate());
        byte[] dec = c.doFinal(enc);
        assertArrayEquals(block, dec, "RSA-encrypted block must decrypt to the original");
    }

    @Test
    void rsaRejectsNonAlignedBlock()
    {
        assertThrows(IllegalArgumentException.class,
                () -> LoginCrypt.rsaEncrypt(null, new byte[16]),
                "RSA block must be exactly 128 bytes");
    }

    @Test
    void buildAuthBlockPlacesUserAndPasswordAtFixedOffsets()
    {
        String user = "abcdefghijklmn";
        String pass = "pqrstuvwxyz"; // p..z = 11 chars
        byte[] block = LoginCrypt.buildAuthBlock(user, pass);
        assertEquals(user, new String(block, 0x5E, user.length(), StandardCharsets.UTF_8));
        assertEquals(pass, new String(block, 0x6C, pass.length(), StandardCharsets.UTF_8)
                .replace("\0", ""));
    }

    // ---- (b) Blowfish encrypt/decrypt round-trip for block-aligned payloads ----

    @Test
    void blowfishRoundTripsEightBytes() throws Exception
    {
        byte[] plain = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        assertArrayEquals(plain, LoginCrypt.blowfishDecrypt(
                STATIC_KEY, LoginCrypt.blowfishEncrypt(STATIC_KEY, plain)));
    }

    @Test
    void blowfishRoundTripsSixteenBytes() throws Exception
    {
        byte[] plain = "abcdefghijklmnop".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(plain, LoginCrypt.blowfishDecrypt(
                STATIC_KEY, LoginCrypt.blowfishEncrypt(STATIC_KEY, plain)));
    }

    @Test
    void blowfishRejectsNonBlockAlignedPayloads()
    {
        for (int len : new int[]{0, 1, 7})
        {
            assertThrows(IllegalArgumentException.class,
                    () -> LoginCrypt.blowfishEncrypt(STATIC_KEY, new byte[len]),
                    "expected rejection of " + len + "-byte payload");
        }
    }

    // ---- (c) appendChecksum + verifyChecksum round-trip ----

    @Test
    void checksumAppendThenVerifyRoundTrips()
    {
        byte[] data = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        data = Arrays.copyOf(data, 16); // 16 % 4 == 0, > 4
        LoginCrypt.appendChecksum(data, 0, data.length);
        assertTrue(LoginCrypt.verifyChecksum(data, 0, data.length),
                "appended checksum must verify");
    }

    @Test
    void checksumVerifyFailsOnTamperedPayload()
    {
        byte[] data = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        data = Arrays.copyOf(data, 16);
        LoginCrypt.appendChecksum(data, 0, data.length);
        data[3] ^= 0x41; // flip one payload bit
        assertFalse(LoginCrypt.verifyChecksum(data, 0, data.length),
                "tampered payload must fail checksum");
    }

    // ---- (d) XOR pass scramble/discramble round-trip ----

    @Test
    void xorPassScrambleThenDiscrambleRoundTrips()
    {
        byte[] data = "1234567890123456789012345".getBytes(StandardCharsets.UTF_8);
        data = Arrays.copyOf(data, 28); // payload words at 4..20, key slot at 20..24, tail 24..28
        byte[] original = data.clone();
        LoginCrypt.encXORPass(data, 0, data.length, 0x12345678);
        LoginCrypt.reverseXORPass(data, 0, data.length);
        // The XOR pass consumes the key slot [size-8..size-5] as running-key metadata, so only
        // the payload words (offset 4 .. size-8) are restored by the reverse pass.
        assertArrayEquals(Arrays.copyOfRange(original, 4, 20),
                Arrays.copyOfRange(data, 4, 20),
                "reverseXORPass must restore all payload words inverted by encXORPass");
    }
}
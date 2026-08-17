package com.aiplayer.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.aiplayer.protocol.crypt.GameCrypt;

/**
 * Round-trip tests for {@link GameCrypt} (stateful L2J game XOR cipher).
 *
 * The engine seeds identical 16-byte inbound and outbound keys; {@code encrypt} advances
 * {@code _outKey} while {@code decrypt} advances {@code _inKey}, so encrypt(data) followed
 * by decrypt(data) on one instance round-trips cleanly. A cipher seeded with a DIFFERENT key
 * must decrypt to different output (key correctness property).
 */
class GameCryptTest
{
    private static final byte[] KEY_A = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY_B = "fedcba9876543210".getBytes(StandardCharsets.UTF_8);

    @Test
    void decryptInvertsEncryptForShortBuffer()
    {
        byte[] plain = "hello".getBytes(StandardCharsets.UTF_8);
        GameCrypt c = new GameCrypt();
        c.setKey(KEY_A);
        byte[] enc = plain.clone();
        c.encrypt(enc, 0, enc.length);
        c.decrypt(enc, 0, enc.length);
        assertArrayEquals(plain, enc, "decrypt must invert encrypt (short buffer)");
    }

    @Test
    void decryptInvertsEncryptForLongBuffer()
    {
        byte[] plain = "l2player-multiblock-ciphertest-data-long.stream".getBytes(StandardCharsets.UTF_8);
        GameCrypt c = new GameCrypt();
        c.setKey(KEY_A);
        byte[] enc = plain.clone();
        c.encrypt(enc, 0, enc.length);
        c.decrypt(enc, 0, enc.length);
        assertArrayEquals(plain, enc, "decrypt must invert encrypt (long buffer)");
    }

    @Test
    void differentKeyProducesDifferentOutput()
    {
        byte[] plain = "attack-at-dawn".getBytes(StandardCharsets.UTF_8);
        byte[] encA = plain.clone();
        GameCrypt a = new GameCrypt();
        a.setKey(KEY_A);
        a.encrypt(encA, 0, encA.length);

        byte[] encB = plain.clone();
        GameCrypt b = new GameCrypt();
        b.setKey(KEY_B);
        b.encrypt(encB, 0, encB.length);

        assertNotEquals(toHex(encA), toHex(encB), "different keys must not produce identical ciphertext");
    }

    @Test
    void wrongKeyDecryptionDiffersFromPlaintext()
    {
        byte[] plain = "secret".getBytes(StandardCharsets.UTF_8);
        GameCrypt right = new GameCrypt();
        right.setKey(KEY_A);
        byte[] enc = plain.clone();
        right.encrypt(enc, 0, enc.length);

        GameCrypt wrong = new GameCrypt();
        wrong.setKey(KEY_B);
        byte[] attempt = enc.clone();
        wrong.decrypt(attempt, 0, attempt.length);
        assertFalse(Arrays.equals(plain, attempt),
                "decrypting with a different key must not recover plaintext");
    }

    private static String toHex(byte[] d)
    {
        StringBuilder s = new StringBuilder();
        for (byte b : d) s.append(String.format("%02x", b & 0xff));
        return s.toString();
    }
}
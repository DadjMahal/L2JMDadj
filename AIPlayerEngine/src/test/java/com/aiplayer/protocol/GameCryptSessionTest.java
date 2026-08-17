package com.aiplayer.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.aiplayer.protocol.crypt.GameCrypt;

/**
 * S2-T02: {@link GameCrypt} under 50 concurrent sessions.
 *
 * Each engine session owns its own {@link GameCrypt} instance, so cipher state is per-instance
 * and never shared. Every instance is seeded with a distinct 16-byte session key reconstructed
 * from a per-session distinct 8-byte prefix plus a fixed 8-byte suffix (the documented usage:
 * {@code setKey} copies 16 bytes, so raw 8-byte keys are rejected by the real API).
 */
class GameCryptSessionTest
{
    private static final int SESSIONS = 50;
    private static final int PAYLOAD_SIZE = 16;

    @Test
    void fiftySessionsRoundTripWithoutCrossTalk()
    {
        final byte[][] keys = sessionKeys();
        final byte[] plain = payload(0);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try
        {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < SESSIONS; i++)
            {
                final int idx = i;
                final byte[] key = keys[i];
                futures.add(pool.submit(() ->
                {
                    GameCrypt c = new GameCrypt();
                    c.setKey(key);
                    byte[] data = payload(idx).clone();
                    c.encrypt(data, 0, data.length);
                    c.decrypt(data, 0, data.length);
                    return Arrays.equals(data, payload(idx));
                }));
            }
            for (Future<Boolean> f : futures)
            {
                assertTrue(f.get(10, TimeUnit.SECONDS), "session round-trip must recover exact plaintext");
            }
        }
        catch (Exception e)
        {
            throw new AssertionError("concurrent session task failed", e);
        }
        finally
        {
            pool.shutdownNow();
        }

        // Cross-key mismatch (serialized, on fresh instances): session 0's ciphertext decrypted
        // with session 1's key must NOT recover session 0's plaintext.
        GameCrypt a = new GameCrypt();
        a.setKey(keys[0]);
        byte[] cross = plain.clone();
        a.encrypt(cross, 0, cross.length);
        GameCrypt b = new GameCrypt();
        b.setKey(keys[1]);
        b.decrypt(cross, 0, cross.length);
        assertFalse(Arrays.equals(plain, cross),
                "decrypting session A's ciphertext with session B's key must not recover plaintext");
    }

    @Test
    void keylessFreshInstanceRejectsOrNoOps()
    {
        byte[] plain = "fresh-instance".getBytes(StandardCharsets.UTF_8);
        GameCrypt c = new GameCrypt(); // never setKey
        byte[] data = plain.clone();
        assertDoesNotThrow(() ->
        {
            c.encrypt(data, 0, data.length);
            c.decrypt(data, 0, data.length);
        }, "a fresh keyless instance must not throw");
        assertArrayEquals(plain, data,
                "keyless instance defaults both keys to zero (identical in/out) and round-trips like any other");
    }

    /** Distinct 16-byte key per session: distinct 8-byte prefix + fixed 8-byte suffix. */
    private static byte[][] sessionKeys()
    {
        byte[][] keys = new byte[SESSIONS][16];
        for (int i = 0; i < SESSIONS; i++)
        {
            byte[] k = keys[i];
            k[0] = (byte) i;
            k[1] = (byte) (i >> 8);
            k[2] = 0x5A;
            k[3] = 0x6B;
            k[4] = 0x7C;
            k[5] = (byte) 0x8D;
            k[6] = (byte) 0x9E;
            k[7] = (byte) 0xAF;
            Arrays.fill(k, 8, 16, (byte) 0x42); // fixed suffix
        }
        return keys;
    }

    /** Fixed 16-byte payload per session. */
    private static byte[] payload(int session)
    {
        byte[] p = new byte[PAYLOAD_SIZE];
        for (int i = 0; i < p.length; i++)
        {
            p[i] = (byte) (session * PAYLOAD_SIZE + i);
        }
        return p;
    }
}
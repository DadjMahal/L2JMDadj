package com.aiplayer.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Stream C: tests {@link GameServerFrameWriter} writes a framed packet verbatim and rejects malformed
 * frames.
 */
public class GameServerFrameWriterTest
{
    @Test
    public void testWritesFrameVerbatim() throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GameServerFrameWriter writer = new GameServerFrameWriter(out);

        byte[] action = PacketCodec.encodeAction(42, 1, 2, 3);
        writer.writeFrame(action);

        assertArrayEquals(action, out.toByteArray(), "writer should emit the frame unchanged + flush");
    }

    @Test
    public void testRejectsMalformedFrame()
    {
        GameServerFrameWriter writer = new GameServerFrameWriter(new ByteArrayOutputStream());
        assertThrows(IllegalArgumentException.class, () -> writer.writeFrame(null), "null frame");
        assertThrows(IllegalArgumentException.class, () -> writer.writeFrame(new byte[]{1, 2}), "short frame");
    }
}

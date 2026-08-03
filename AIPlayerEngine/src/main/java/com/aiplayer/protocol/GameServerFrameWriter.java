package com.aiplayer.protocol;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Stream C: writes an already-framed client packet (2-byte self-inclusive size + payload) to any
 * {@link OutputStream} — in production the GameServer socket's output stream (crypt DISABLED = plaintext,
 * per Audit/35), in tests a {@link java.io.ByteArrayOutputStream}.
 *
 * <p>The frame must be produced by {@link PacketCodec} (or a probe), which already prepends the size
 * header; this class only writes + flushes it.
 */
public class GameServerFrameWriter
{
	private final OutputStream out;

	public GameServerFrameWriter(OutputStream out)
	{
		this.out = out;
	}

	/**
	 * Write a framed packet and flush.
	 *
	 * @throws IllegalArgumentException if the frame is not a valid [size][payload] packet
	 * @throws IOException on write failure
	 */
	public synchronized void writeFrame(byte[] frame) throws IOException
	{
		if (frame == null || frame.length < 3)
		{
			throw new IllegalArgumentException("malformed frame: must contain a size header + opcode");
		}
		out.write(frame);
		out.flush();
	}
}

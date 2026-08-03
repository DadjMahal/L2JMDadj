package com.aiplayer.protocol.crypt;

/**
 * Client-side mirror of the L2JMobius GameServer "Encryption" (stateful XOR cipher).
 * Port of SourceCode gameserver/network/Encryption.java to operate on byte[] instead of Buffer.
 *
 * Semantics (must match the server exactly):
 *  - A 16-byte session key, with the rolling offset stored little-endian at key[8..11].
 *  - outbound (client->server) mirrors server.decrypt(_inKey); inbound (server->client) mirrors
 *    server.encrypt(_outKey). Both keys start identical (the 16-byte key the client reconstructs
 *    from the 8 KeyPacket bytes + the fixed suffix) and advance by packet payload size.
 *  - The client must NOT decrypt the KeyPacket itself (it is plaintext); all subsequent
 *    server->client packets go through inbound decrypt, and all client->server packets (from the
 *    first, AuthLogin) go through outbound encrypt.
 */
public class GameCrypt
{
	private static final int KEY_LENGTH = 16;
	private static final int NIBBLE_MASK = 0x0F;

	// Session keys (contents mutable; rolling offset at [8..11]).
	private final byte[] _inKey = new byte[KEY_LENGTH];
	private final byte[] _outKey = new byte[KEY_LENGTH];

	/** Seed both inbound and outbound keys (must be >= 16 bytes). */
	public void setKey(byte[] key)
	{
		System.arraycopy(key, 0, _inKey, 0, KEY_LENGTH);
		System.arraycopy(key, 0, _outKey, 0, KEY_LENGTH);
	}

	/**
	 * Encrypt a client->server packet payload in place (offset 0, length size).
	 * Mirrors server-side decryption of inbound data (always active).
	 */
	public void encrypt(byte[] data, int offset, int size)
	{
		if (size <= 0)
		{
			return;
		}
		int prev = 0;
		for (int i = 0; i < size; i++)
		{
			final int raw = data[offset + i] & 0xFF;
			prev = raw ^ (_outKey[i & NIBBLE_MASK] & 0xFF) ^ prev;
			data[offset + i] = (byte) prev;
		}
		advanceOffset(_outKey, size);
	}

	/**
	 * Decrypt a server->client packet payload in place (offset 0, length size).
	 * Mirrors server-side encryption of outbound data.
	 */
	public void decrypt(byte[] data, int offset, int size)
	{
		if (size <= 0)
		{
			return;
		}
		int last = 0;
		for (int i = 0; i < size; i++)
		{
			final int enc = data[offset + i] & 0xFF;
			data[offset + i] = (byte) (enc ^ (_inKey[i & NIBBLE_MASK] & 0xFF) ^ last);
			last = enc;
		}
		advanceOffset(_inKey, size);
	}

	private static void advanceOffset(byte[] key, int size)
	{
		int old = (key[8] & 0xFF);
		old |= (key[9] & 0xFF) << 8;
		old |= (key[10] & 0xFF) << 16;
		old |= (key[11] & 0xFF) << 24;
		old += size;
		key[8] = (byte) (old & 0xFF);
		key[9] = (byte) ((old >> 8) & 0xFF);
		key[10] = (byte) ((old >> 16) & 0xFF);
		key[11] = (byte) ((old >> 24) & 0xFF);
	}
}

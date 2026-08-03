package com.aiplayer.examples;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.aiplayer.engine.AIPlayer;
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.protocol.crypt.GameCrypt;

/**
 * B4 probe: live NPC combat proof (2026-08-03).
 *
 * <p>Extends the B3 {@link EnterWorldProbe} flow (login-server auth + GameServer enter-world) and,
 * once the player is in-world, scans {@code NPC_INFO}(0x16) packets for an attackable monster, then
 * sends {@code Action}(0x04) + {@code AttackRequest}(0x0A) to force auto-attack, and tallies the
 * server->client combat packets ({@code ATTACK} 0x05, {@code DIE} 0x06, {@code STATUS_UPDATE} 0x0E,
 * {@code DELETE_OBJECT} 0x12, {@code SYSTEM_MESSAGE} 0x64) received afterwards.
 *
 * <p>Server facts (Interlude, {@code SourceCode/...}, {@code PacketEncryption = False}):
 * <ul>
 *   <li>Game crypt is DISABLED -> all GS packets are plaintext (no Blowfish/XOR on this channel).</li>
 *   <li>AbstractNpcInfo.writeImpl (opcode 0x16) field order:
 *       {@code [0x16][objectId][displayId+1000000][isAttackable][x][y][z][heading]...}</li>
 *   <li>Action (0x04, client): {@code [0x04][targetObjId][originX][originY][originZ][actionId]}.</li>
 *   <li>AttackRequest (0x0A, client): {@code [0x0A][targetObjId][originX][originY][originZ][attackId]}.</li>
 * </ul>
 *
 * <p>Verification: this probe prints a combat opcode tally; the caller ({@code scripts/b4_combat_prove.sh})
 * also checks the player's {@code exp} in the {@code gameserver} DB before/after. B4 is PROVEN when an
 * {@code ATTACK}/{@code STATUS_UPDATE}/{@code DIE} packet is received after our attack, or {@code exp}
 * increased (a monster died).
 *
 * <p>Wire framing mirrors {@link EnterWorldProbe} verbatim (proven by B3): 2-byte self-inclusive size
 * header; plaintext payload {@code [opcode][fields]}.
 */
public class CombatProbe
{
	private static final int PROTOCOL_VERSION = 746;
	private static final byte[] KEY_TAIL =
	{ (byte) 0xC8, (byte) 0x27, (byte) 0x93, (byte) 0x01, (byte) 0xA1, (byte) 0x6C, (byte) 0x31, (byte) 0x97 };

	// Server->client opcodes (from SourceCode/.../network/ServerPackets.java)
	private static final int OP_CHAR_SELECT_INFO = 0x13;
	private static final int OP_CHAR_SELECTED = 0x15;
	private static final int OP_ATTACK = 0x05;
	private static final int OP_DIE = 0x06;
	private static final int OP_STATUS_UPDATE = 0x0E;
	private static final int OP_DELETE_OBJECT = 0x12;
	private static final int OP_NPC_INFO = 0x16;
	private static final int OP_STOP_MOVE = 0x47;
	private static final int OP_VALIDATE_LOCATION = 0x61;
	private static final int OP_SYSTEM_MESSAGE = 0x64;

	// Client->server opcodes (from SourceCode/.../network/ClientPackets.java)
	private static final int OP_C_ACTION = 0x04;
	private static final int OP_C_ATTACK_REQUEST = 0x0A;

	// Target candidate (first attackable NPC_INFO seen)
	private static int targetObjId = -1;
	private static int targetNpcType = -1;
	private static int targetX, targetY, targetZ;

	// Combat opcode tally (after we start attacking)
	private static int attackCount, dieCount, statusCount, deleteCount, stopMoveCount, validateCount, sysMsgCount, npcInfoCount;

	public static void main(String[] args) throws Exception
	{
		String account = args.length > 0 ? args[0] : "ai_combat_01";
		String password = args.length > 1 ? args[1] : "ai123pass";
		String host = args.length > 2 ? args[2] : "127.0.0.1";
		int gamePort = args.length > 3 ? Integer.parseInt(args[3]) : 7777;

		// Phase 1: login-server auth -> SessionKey (kept alive in this same process).
		AIPlayer player = new AIPlayer(account, 100, 1, 0);
		L2JProtocol login = new L2JProtocol(player, host, 2106, gamePort);
		boolean ok = login.connectAndLogin(account, password, 2);
		System.out.println("[CombatProbe] login=" + ok);
		if (!ok)
		{
			login.disconnect();
			System.exit(2);
		}

		try (SocketChannel gs = SocketChannel.open())
		{
			gs.configureBlocking(true);
			gs.connect(new InetSocketAddress(host, gamePort));
			gs.socket().setSoTimeout(2000); // short timeout; combat loop tolerates gaps
			System.out.println("[CombatProbe] connected to GS " + host + ":" + gamePort);

			sendFrame(gs, buildProtocolVersion());

			byte[] keyFrame = readFrame(gs);
			if (keyFrame == null)
			{
				System.out.println("[CombatProbe][FAIL] no KeyPacket");
				login.disconnect();
				System.exit(3);
			}
			int packetEncFlag = leInt(keyFrame, 12);
			boolean useEnc = packetEncFlag != 0;
			byte[] key = new byte[16];
			System.arraycopy(keyFrame, 4, key, 0, 8);
			System.arraycopy(KEY_TAIL, 0, key, 8, 8);
			GameCrypt crypt = new GameCrypt();
			crypt.setKey(key);
			System.out.println("[CombatProbe] KeyPacket packetEncryption=" + packetEncFlag + " useGameCrypt=" + useEnc);

			// AuthLogin + enter world.
			sendAuthLogin(gs, crypt, useEnc, account, login);

			boolean entered = false;
			long enterDeadline = System.currentTimeMillis() + 12000;
			while (System.currentTimeMillis() < enterDeadline)
			{
				byte[] payload;
				try
				{
					payload = readPayload(gs);
				}
				catch (Exception e)
				{
					continue; // timeout mid-handshake; keep trying until deadline
				}
				if (payload == null)
				{
					break;
				}
				byte[] plain = Arrays.copyOf(payload, payload.length);
				if (useEnc)
				{
					crypt.decrypt(plain, 0, plain.length);
				}
				int op = plain[0] & 0xff;
				System.out.println("[CombatProbe] GS opcode=0x" + Integer.toHexString(op) + " len=" + plain.length);
				if (op == OP_CHAR_SELECT_INFO)
				{
					sendCharacterSelect(gs, crypt, useEnc, 0);
					System.out.println("[CombatProbe] sent CharacterSelect(0x0D) slot=0");
				}
				if (op == OP_CHAR_SELECTED)
				{
					System.out.println("[CombatProbe] *** CHAR SELECTED — entering combat phase");
					entered = true;
					break;
				}
			}
			if (!entered)
			{
				System.out.println("[CombatProbe][FAIL] did not enter world (no CharSelected)");
				login.disconnect();
				System.exit(4);
			}

		// Player origin (Action/AttackRequest origin coords) — defaults to CombatBot_01 spawn.
		int px = args.length > 4 ? Integer.parseInt(args[4]) : 16600;
		int py = args.length > 5 ? Integer.parseInt(args[5]) : 17000;
		int pz = args.length > 6 ? Integer.parseInt(args[6]) : 434;

			// Combat phase 1: scan NPC_INFO ~6s to acquire an attackable monster target.
			long scanDeadline = System.currentTimeMillis() + 6000;
			while (System.currentTimeMillis() < scanDeadline)
			{
				byte[] payload;
				try
				{
					payload = readPayload(gs);
				}
				catch (Exception e)
				{
					continue;
				}
				if (payload == null)
				{
					break;
				}
				byte[] plain = Arrays.copyOf(payload, payload.length);
				if (useEnc)
				{
					crypt.decrypt(plain, 0, plain.length);
				}
				int op = plain[0] & 0xff;
				tally(op);
				if (op == OP_NPC_INFO && plain.length >= 25)
				{
					// Real AbstractNpcInfo offsets: [0x16][objectId][npcType][isAttackable][x][y][z]...
					int objId = leInt(plain, 1);
					int npcType = leInt(plain, 5);
					int isAttackable = leInt(plain, 9);
					int nx = leInt(plain, 13);
					int ny = leInt(plain, 17);
					int nz = leInt(plain, 21);
					System.out.println("[CombatProbe] NPC_INFO objId=" + objId + " npcType=" + npcType
						+ " attackable=" + isAttackable + " pos=(" + nx + "," + ny + "," + nz + ")");
					if (isAttackable != 0 && targetObjId < 0)
					{
						targetObjId = objId;
						targetNpcType = npcType;
						targetX = nx;
						targetY = ny;
						targetZ = nz;
					}
				}
			}

			if (targetObjId < 0)
			{
				System.out.println("[CombatProbe][WARN] no attackable NPC_INFO seen in scan window"
					+ " (npcInfoCount=" + npcInfoCount + "). Cannot attack; tallying idle traffic only.");
			}
			else
			{
				System.out.println("[CombatProbe] TARGET acquired objId=" + targetObjId + " npcType=" + targetNpcType
					+ " at (" + targetX + "," + targetY + "," + targetZ + ")");
				// Action(0x04): select + auto-attack (sets ATTACK intention; auto-runs into range).
				sendAction(gs, crypt, useEnc, targetObjId, px, py, pz);
				System.out.println("[CombatProbe] sent Action(0x04) target=" + targetObjId);
				Thread.sleep(1000); // respect flood protector canPerformPlayerAction()
				// AttackRequest(0x0A): force attack on the now-selected target.
				sendAttackRequest(gs, crypt, useEnc, targetObjId);
				System.out.println("[CombatProbe] sent AttackRequest(0x0A) target=" + targetObjId);
			}

			// Combat phase 2: read server packets ~20s, tally combat opcodes.
			long combatDeadline = System.currentTimeMillis() + 20000;
			while (System.currentTimeMillis() < combatDeadline)
			{
				byte[] payload;
				try
				{
					payload = readPayload(gs);
				}
				catch (Exception e)
				{
					continue; // timeout gap; keep reading until combat deadline
				}
				if (payload == null)
				{
					break;
				}
				byte[] plain = Arrays.copyOf(payload, payload.length);
				if (useEnc)
				{
					crypt.decrypt(plain, 0, plain.length);
				}
				int op = plain[0] & 0xff;
				tally(op);
				if (op == OP_ATTACK || op == OP_DIE || op == OP_DELETE_OBJECT || op == OP_SYSTEM_MESSAGE)
				{
					System.out.println("[CombatProbe] COMBAT pkt 0x" + Integer.toHexString(op) + " len=" + plain.length);
				}
			}

			System.out.println("[CombatProbe] === COMBAT TALLY (after attack) ===");
			System.out.println("  ATTACK(0x05)=" + attackCount);
			System.out.println("  DIE(0x06)=" + dieCount);
			System.out.println("  STATUS_UPDATE(0x0E)=" + statusCount);
			System.out.println("  DELETE_OBJECT(0x12)=" + deleteCount);
			System.out.println("  STOP_MOVE(0x47)=" + stopMoveCount);
			System.out.println("  VALIDATE_LOCATION(0x61)=" + validateCount);
			System.out.println("  SYSTEM_MESSAGE(0x64)=" + sysMsgCount);
			System.out.println("  NPC_INFO(0x16)=" + npcInfoCount);
			boolean combatProven = (attackCount > 0) || (dieCount > 0) || (statusCount > 0);
			System.out.println("[CombatProbe] COMBAT PROVEN (attack|die|status > 0) = " + combatProven);
			System.out.println("[CombatProbe] done");
		}
		login.disconnect();
	}


	// ------------------------------------------------------------------
	// Combat packet builders (plaintext; game crypt disabled on this server)
	// ------------------------------------------------------------------

	/** Action (0x04): [0x04][targetObjId][originX][originY][originZ][actionId]. */
	private static void sendAction(SocketChannel ch, GameCrypt crypt, boolean useEnc, int targetObjId, int ox, int oy, int oz) throws Exception
	{
		ByteBuffer bb = ByteBuffer.allocate(1 + 4 + 4 + 4 + 4 + 1).order(ByteOrder.LITTLE_ENDIAN);
		bb.put((byte) OP_C_ACTION);
		bb.putInt(targetObjId);
		bb.putInt(ox);
		bb.putInt(oy);
		bb.putInt(oz);
		bb.put((byte) 0); // actionId = 0 (simple click)
		sendPayload(ch, crypt, useEnc, bb);
	}

	/** AttackRequest (0x0A): [0x0A][targetObjId][originX][originY][originZ][attackId]. */
	private static void sendAttackRequest(SocketChannel ch, GameCrypt crypt, boolean useEnc, int targetObjId) throws Exception
	{
		ByteBuffer bb = ByteBuffer.allocate(1 + 4 + 4 + 4 + 4 + 1).order(ByteOrder.LITTLE_ENDIAN);
		bb.put((byte) OP_C_ATTACK_REQUEST);
		bb.putInt(targetObjId);
		bb.putInt(0);
		bb.putInt(0);
		bb.putInt(0);
		bb.put((byte) 0); // attackId = 0
		sendPayload(ch, crypt, useEnc, bb);
	}

	private static void sendPayload(SocketChannel ch, GameCrypt crypt, boolean useEnc, ByteBuffer bb) throws Exception
	{
		byte[] plain = new byte[bb.position()];
		bb.flip();
		bb.get(plain);
		if (useEnc)
		{
			crypt.encrypt(plain, 0, plain.length);
		}
		sendFrame(ch, plain);
	}

	private static void tally(int op)
	{
		switch (op)
		{
			case OP_ATTACK:
				attackCount++;
				break;
			case OP_DIE:
				dieCount++;
				break;
			case OP_STATUS_UPDATE:
				statusCount++;
				break;
			case OP_DELETE_OBJECT:
				deleteCount++;
				break;
			case OP_STOP_MOVE:
				stopMoveCount++;
				break;
			case OP_VALIDATE_LOCATION:
				validateCount++;
				break;
			case OP_SYSTEM_MESSAGE:
				sysMsgCount++;
				break;
			case OP_NPC_INFO:
				npcInfoCount++;
				break;
			default:
				break;
		}
	}


	// ---------------- enter-world helpers (mirror proven EnterWorldProbe) ----------------

	private static byte[] buildProtocolVersion() throws Exception
	{
		ByteBuffer bb = ByteBuffer.allocate(1 + 4).order(ByteOrder.LITTLE_ENDIAN);
		bb.put((byte) 0x00);
		bb.putInt(PROTOCOL_VERSION);
		return bb.array();
	}

	/** Build AuthLogin (0x08) plaintext then encrypt (if game crypt enabled) and send. */
	private static void sendAuthLogin(SocketChannel ch, GameCrypt crypt, boolean useEnc, String account, L2JProtocol login) throws Exception
	{
		byte[] name = account.getBytes(StandardCharsets.UTF_16LE);
		ByteBuffer bb = ByteBuffer.allocate(1 + name.length + 2 + 16).order(ByteOrder.LITTLE_ENDIAN);
		bb.put((byte) 0x08);
		bb.put(name);
		bb.putShort((short) 0); // null terminator
		bb.putInt(login.getPlayOk2()); // playKey2
		bb.putInt(login.getPlayOk1()); // playKey1
		bb.putInt(login.getLoginOk1()); // loginKey1
		bb.putInt(login.getLoginOk2()); // loginKey2
		byte[] plain = new byte[bb.position()];
		bb.flip();
		bb.get(plain);
		if (useEnc)
		{
			crypt.encrypt(plain, 0, plain.length);
		}
		sendFrame(ch, plain);
	}

	/** Send CharacterSelect (0x0D): charSlot(int), unk1(short), unk2/3/4(ints) — unks are zeros. */
	private static void sendCharacterSelect(SocketChannel ch, GameCrypt crypt, boolean useEnc, int charSlot) throws Exception
	{
		ByteBuffer bb = ByteBuffer.allocate(1 + 4 + 2 + 4 + 4 + 4).order(ByteOrder.LITTLE_ENDIAN);
		bb.put((byte) 0x0D);
		bb.putInt(charSlot);
		bb.putShort((short) 0);
		bb.putInt(0);
		bb.putInt(0);
		bb.putInt(0);
		byte[] plain = new byte[bb.position()];
		bb.flip();
		bb.get(plain);
		if (useEnc)
		{
			crypt.encrypt(plain, 0, plain.length);
		}
		sendFrame(ch, plain);
	}

	// ---------------- GS wire helpers ----------------

	private static void sendFrame(SocketChannel ch, byte[] payload) throws Exception
	{
		ByteBuffer buf = ByteBuffer.allocate(2 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
		buf.putShort((short) (payload.length + 2)); // self-inclusive size
		buf.put(payload);
		buf.flip();
		ch.write(buf);
	}

	private static byte[] readFrame(SocketChannel ch) throws Exception
	{
		byte[] payload = readPayload(ch);
		if (payload == null)
		{
			return null;
		}
		byte[] frame = new byte[payload.length + 2];
		frame[0] = (byte) ((payload.length + 2) & 0xff);
		frame[1] = (byte) (((payload.length + 2) >> 8) & 0xff);
		System.arraycopy(payload, 0, frame, 2, payload.length);
		return frame;
	}

	private static byte[] readPayload(SocketChannel ch) throws Exception
	{
		ByteBuffer sizeBuf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
		long deadline = System.currentTimeMillis() + 9000;
		while (sizeBuf.hasRemaining())
		{
			int n = ch.read(sizeBuf);
			if (n < 0)
			{
				return null;
			}
			if (n == 0)
			{
				if (System.currentTimeMillis() > deadline)
				{
					return null;
				}
				Thread.sleep(10);
			}
		}
		sizeBuf.flip();
		int size = sizeBuf.getShort() & 0xffff;
		if (size < 2 || size > 65535)
		{
			return null;
		}
		byte[] payload = new byte[size - 2];
		ByteBuffer dataBuf = ByteBuffer.wrap(payload);
		deadline = System.currentTimeMillis() + 9000;
		while (dataBuf.hasRemaining())
		{
			int n = ch.read(dataBuf);
			if (n < 0)
			{
				return null;
			}
			if (n == 0)
			{
				if (System.currentTimeMillis() > deadline)
				{
					return null;
				}
				Thread.sleep(10);
			}
		}
		return payload;
	}

	private static int leInt(byte[] d, int i)
	{
		return (d[i] & 0xff) | ((d[i + 1] & 0xff) << 8) | ((d[i + 2] & 0xff) << 16) | ((d[i + 3] & 0xff) << 24);
	}
}


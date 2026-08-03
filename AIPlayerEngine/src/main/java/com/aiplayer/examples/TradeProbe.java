package com.aiplayer.examples;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.aiplayer.engine.AIPlayer;
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.protocol.crypt.GameCrypt;

/**
 * B7 trade probe: prove an AI player buys an item from a merchant NPC over the external socket.
 *
 * <p>Enters CombatBot_01 (positioned at Trader 30003 / Silvia), scans `NPC_INFO`(0x16) for a Trader NPC
 * (npcType = id+1000000 → 1003003), sends `Action`(0x04) to target it, reads the `BuyList`(0x11) reply,
 * parses the listId + a cheap affordable item, then sends `RequestBuyItem`(0x1F)` and the server deducts
 * adena / adds the item (verified in DB by the caller).
 *
 * <p>Server facts (SourceCode): BuyList(0x11) = `[money][listId][size:short]` + per-item
 * `[type1:short][objId:int][itemId:int][count:int][type2:short][eq:short][body:int][enchant:short][s][s][price:int]`;
 * RequestBuyItem(0x1F) = `[listId:int][n:int][{itemId:int,count:int}]`. Adena item_id=57. No L2JM server source changed.
 */
public class TradeProbe
{
	private static final int PROTOCOL_VERSION = 746;
	private static final byte[] KEY_TAIL =
	{ (byte) 0xC8, (byte) 0x27, (byte) 0x93, (byte) 0x01, (byte) 0xA1, (byte) 0x6C, (byte) 0x31, (byte) 0x97 };

	// Server->client opcodes
	private static final int OP_CHAR_SELECT_INFO = 0x13;
	private static final int OP_CHAR_SELECTED = 0x15;
	private static final int OP_NPC_INFO = 0x16;
	private static final int OP_BUY_LIST = 0x11;
	private static final int OP_NPC_HTML = 0x0F;
	private static final int OP_SYSTEM_MESSAGE = 0x64;

	// Client->server opcodes
	private static final int OP_C_CHARACTER_SELECT = 0x0D;
	private static final int OP_C_ENTER_WORLD = 0x03;
	private static final int OP_C_ACTION = 0x04;
	private static final int OP_C_REQUEST_BUY_ITEM = 0x1F;
	private static final int OP_C_REQUEST_BYPASS_TO_SERVER = 0x21;

	private static final int TRADER_NPC_ID = 30003; // Silvia (Talking Island); we look for npcType = id+1000000

	private static int buyListId = -1;
	private static int buyItemId = -1;
	private static long buyItemPrice = -1;
	private static int buyCount = -1;
	private static volatile int traderObjId = -1;
	private static volatile String buyBypass = null;

	private static synchronized void recordTrader(int objId, int npcId, byte[] pl)
	{
		if (traderObjId < 0 && npcId == TRADER_NPC_ID)
		{
			traderObjId = objId;
			System.out.println("[TradeProbe] found Trader " + npcId + " objectId=" + objId);
		}
	}

	/** Extract the first `action="..."` bypass from an NpcHtmlMessage that looks like a Buy link. */
	private static synchronized void findBuyBypass(String html)
	{
		if (buyBypass != null || html == null)
		{
			return;
		}
		int idx = html.toLowerCase().indexOf("action=\"");
		if (idx < 0)
		{
			idx = html.toLowerCase().indexOf("action = \"");
		}
		if (idx < 0)
		{
			return;
		}
		int start = html.indexOf('"', idx) + 1;
		int end = html.indexOf('"', start);
		if (start > 0 && end > start)
		{
			String action = html.substring(start, end);
			System.out.println("[TradeProbe] merchant menu action=\"" + action + "\"");
			String lower = action.toLowerCase();
			// Prefer a Buy/shop action; fall back to any bypass.
			if (lower.contains("shop") || lower.contains("buy") || lower.contains("merchant"))
			{
				buyBypass = action;
			}
		}
	}

	private static int getTraderObjId()
	{
		return traderObjId;
	}

	/** A live GameServer connection. */
	private static class GsConn
	{
		final Socket socket;
		final OutputStream out;
		final InputStream in;
		final boolean useEnc;
		final L2JProtocol login;

		GsConn(Socket socket, OutputStream out, InputStream in, boolean useEnc, L2JProtocol login)
		{
			this.socket = socket;
			this.out = out;
			this.in = in;
			this.useEnc = useEnc;
			this.login = login;
		}

		void close()
		{
			try
			{
				socket.close();
			}
			catch (IOException ignored)
			{
			}
			login.disconnect();
		}
	}

	public static void main(String[] args) throws Exception
	{
		String account = args.length > 0 ? args[0] : "ai_combat_01";
		String pass = args.length > 1 ? args[1] : "ai123pass";
		String host = args.length > 2 ? args[2] : "127.0.0.1";
		int port = args.length > 3 ? Integer.parseInt(args[3]) : 7777;

		System.out.println("[TradeProbe] entering world: " + account);
		GsConn cn = enterWorld(account, pass, host, port);
		System.out.println("[TradeProbe] IN WORLD (" + account + ")");

		// Reader thread: capture the Trader's objectId (from NPC_INFO) and the BuyList item/price.
		Thread reader = new Thread(() -> readLoop(cn.in), "tradeReader");
		reader.start();

		Thread.sleep(3000); // let the world populate (NPC_INFO with the trader)

		// Find the Trader's objectId (captured by the reader as npcType 1003003). If not seen,
		// fall back to any merchant (1003xxx) objectId the reader recorded.
		int traderObjId = getTraderObjId();
		System.out.println("[TradeProbe] trader objectId = " + traderObjId);
		if (traderObjId > 0)
		{
			sendAction(cn.out, cn.useEnc, traderObjId, -83789, 240799, -3717);
			System.out.println("[TradeProbe] sent Action(0x04) on trader " + traderObjId);
			Thread.sleep(2000); // server should reply NpcHtmlMessage menu (and maybe BuyList)
			if (buyBypass != null)
			{
				System.out.println("[TradeProbe] sending buy bypass: " + buyBypass);
				sendBypass(cn.out, cn.useEnc, buyBypass);
				Thread.sleep(2000); // merchant opens BuyList(0x11)
			}
		}

		// Send the buy once we parsed a BuyList (listId + item + price).
		if (buyListId > 0 && buyItemId > 0)
		{
			System.out.println("[TradeProbe] sending RequestBuyItem(0x1F) listId=" + buyListId + " item=" + buyItemId + " x1");
			sendBuyRequest(cn.out, cn.useEnc, buyListId, buyItemId, 1);
			Thread.sleep(3000);
		}
		else
		{
			System.out.println("[TradeProbe][WARN] no BuyList captured — did not send a buy.");
		}

		cn.close();
		reader.join(3000);

		System.out.println("[TradeProbe] === RESULT ===");
		System.out.println("  BuyList: listId=" + buyListId + " item=" + buyItemId + " price=" + buyItemPrice
			+ " (count=" + buyCount + ")");
		boolean tradeSent = (buyListId > 0) && (buyItemId > 0);
		System.out.println("[TradeProbe] TRADE PROBED (BuyList captured + buy sent) = " + tradeSent
			+ " — DB verify (adena/item) via scripts/b7_trade_prove.sh");
		System.out.println("[TradeProbe] done");
	}

	/** Reader loop: record Trader objectId from NPC_INFO and parse BuyList(0x11). */
	private static void readLoop(InputStream in)
	{
		while (true)
		{
			byte[] pl;
			try
			{
				pl = readPayload(in);
			}
			catch (java.net.SocketTimeoutException e)
			{
				continue;
			}
			catch (IOException e)
			{
				break;
			}
			catch (Exception e)
			{
				break;
			}
			if (pl == null)
			{
				break;
			}
			int op = pl[0] & 0xff;
			if ((op == OP_NPC_INFO) && (pl.length >= 25))
			{
				int npcType = leInt(pl, 5); // displayId+1000000 (AbstractNpcInfo [objId][type][attackable][x][y][z])
				int objId = leInt(pl, 1);
				int isAttackable = leInt(pl, 9);
				if ((npcType >= 1000001) && (isAttackable == 0) && isValidTrader(npcType - 1000000))
				{
					recordTrader(objId, npcType - 1000000, pl);
				}
			}
			else if (op == OP_BUY_LIST)
			{
				parseBuyList(pl);
			}
			else if (op == OP_NPC_HTML)
			{
				// [0x0F][npcObjId:int][html:string][itemId:int]...
				String html = readString(pl, 5);
				if (html != null && !html.isEmpty())
				{
					System.out.println("[TradeProbe] merchant NpcHtmlMessage(len=" + html.length() + "): " + firstNonEmptyBypassLine(html));
					findBuyBypass(html);
				}
			}
			else if (op == OP_SYSTEM_MESSAGE)
			{
				// (debug) a message likely confirms/denies the buy
			}
		}
	}

	private static boolean isValidTrader(int npcId)
	{
		return (npcId >= 30001) && (npcId <= 30099); // Talking Island / general Traders
	}

	/** BuyList(0x11): [money:int][listId:int][size:short] then per-item [..][itemId:int][..][price:int]. */
	private static synchronized void parseBuyList(byte[] pl)
	{
		if (pl.length < 8)
		{
			return;
		}
		int money = leInt(pl, 1);
		int listId = leInt(pl, 5);
		int size = (pl[9] & 0xff) | ((pl[10] & 0xff) << 8);
		buyListId = listId;
		System.out.println("[TradeProbe] BuyList(0x11): listId=" + listId + " size=" + size + " money=" + money);
		// Parse first affordable item: entries are variable (type1/quest vs normal). We scan block-by-block.
		// block header: type1(short) objId(int) itemId(int) count(int) type2(short) eq(short) body(int) e(short) s(short) s(short) price(int) = 2+4+4+4+2+2+4+2+2+2+4 = 32 bytes (normal item)
		int off = 11;
		for (int i = 0; i < size && (off + 32) <= pl.length; i++)
		{
			int itemId = leInt(pl, off + 6);
			int price = leInt(pl, off + 28);
			if (buyItemId < 0)
			{
				buyItemId = itemId;
				buyItemPrice = price;
			}
			off += 32;
		}
		if (buyItemId > 0)
		{
			System.out.println("[TradeProbe]   first buyable item id=" + buyItemId + " price=" + buyItemPrice);
		}
	}


	/** Login + GameServer enter-world; returns a live GsConn after EnterWorld(0x03). */
	private static GsConn enterWorld(String account, String pass, String host, int port) throws Exception
	{
		AIPlayer player = new AIPlayer(account, 100, 1, 0);
		L2JProtocol login = new L2JProtocol(player, host, 2106, port);
		if (!login.connectAndLogin(account, pass, 0))
		{
			login.disconnect();
			throw new RuntimeException("[TradeProbe] login failed for " + account);
		}
		System.out.println("[TradeProbe] " + account + " login OK");

		Socket s = new Socket(host, port);
		s.setSoTimeout(2000);
		OutputStream out = s.getOutputStream();
		InputStream in = s.getInputStream();

		sendFrame(out, buildProtocolVersion());
		byte[] keyFrame = readFrame(in);
		if (keyFrame == null)
		{
			s.close();
			throw new RuntimeException("[TradeProbe] no KeyPacket for " + account);
		}
		int encFlag = leInt(keyFrame, 12);
		boolean useEnc = encFlag != 0;
		byte[] key = new byte[16];
		System.arraycopy(keyFrame, 4, key, 0, 8);
		System.arraycopy(KEY_TAIL, 0, key, 8, 8);
		GameCrypt crypt = new GameCrypt();
		crypt.setKey(key);

		sendAuthLogin(out, crypt, useEnc, account, login);
		boolean spawned = false;
		long deadline = System.currentTimeMillis() + 12000;
		while (System.currentTimeMillis() < deadline)
		{
			byte[] pl;
			try
			{
				pl = readPayload(in);
			}
			catch (Exception e)
			{
				continue;
			}
			if (pl == null)
			{
				break;
			}
			byte[] plain = Arrays.copyOf(pl, pl.length);
			if (useEnc)
			{
				crypt.decrypt(plain, 0, plain.length);
			}
			int op = plain[0] & 0xff;
			if (op == OP_CHAR_SELECT_INFO)
			{
				sendCharacterSelect(out, crypt, useEnc, 0);
			}
			if (op == OP_CHAR_SELECTED)
			{
				sendEnterWorld(out, crypt, useEnc);
				System.out.println("[TradeProbe] " + account + " sent EnterWorld(0x03)");
				spawned = true;
				break;
			}
		}
		if (!spawned)
		{
			s.close();
			throw new RuntimeException("[TradeProbe] no CharSelected for " + account);
		}
		return new GsConn(s, out, in, useEnc, login);
	}

	/** Action (0x04): [0x04][targetObjId][originX][originY][originZ][actionId]. */
	private static void sendAction(OutputStream out, boolean useEnc, int targetObjId, int ox, int oy, int oz) throws Exception
	{
		ByteBuffer bb = ByteBuffer.allocate(1 + 4 + 4 + 4 + 4 + 1).order(ByteOrder.LITTLE_ENDIAN);
		bb.put((byte) OP_C_ACTION);
		bb.putInt(targetObjId);
		bb.putInt(ox);
		bb.putInt(oy);
		bb.putInt(oz);
		bb.put((byte) 0);
		sendPayloadFrame(out, bb);
	}

	/** RequestBuyItem (0x1F): [0x1F][listId:int][n:int][{itemId:int,count:int}]. */
	private static void sendBuyRequest(OutputStream out, boolean useEnc, int listId, int itemId, int count) throws Exception
	{
		ByteBuffer bb = ByteBuffer.allocate(1 + 4 + 4 + 4 + 4).order(ByteOrder.LITTLE_ENDIAN);
		bb.put((byte) OP_C_REQUEST_BUY_ITEM);
		bb.putInt(listId);
		bb.putInt(1); // one item entry
		bb.putInt(itemId);
		bb.putInt(count);
		sendPayloadFrame(out, bb);
	}

	/** RequestBypassToServer (0x21): [0x21][bypass string UTF-16LE + null]. */
	private static void sendBypass(OutputStream out, boolean useEnc, String bypass) throws Exception
	{
		ByteBuffer bb = ByteBuffer.allocate(1 + bypass.length() * 2 + 2).order(ByteOrder.LITTLE_ENDIAN);
		bb.put((byte) OP_C_REQUEST_BYPASS_TO_SERVER);
		for (char c : bypass.toCharArray())
		{
			bb.putChar(c);
		}
		bb.putChar('\000');
		sendPayloadFrame(out, bb);
	}

	private static void sendPayloadFrame(OutputStream out, ByteBuffer bb) throws Exception
	{
		byte[] plain = new byte[bb.position()];
		bb.flip();
		bb.get(plain);
		sendFrame(out, plain);
	}

	/** Read a UTF-16LE null-terminated string starting at `off` (L2J writeString = bytes + writeChar(0)). */
	private static String readString(byte[] d, int off)
	{
		StringBuilder sb = new StringBuilder();
		int i = off;
		while ((i + 1) < d.length)
		{
			int c = (d[i] & 0xff) | ((d[i + 1] & 0xff) << 8);
			if (c == 0)
			{
				break;
			}
			sb.append((char) c);
			i += 2;
		}
		return sb.toString();
	}

	/** Return a compact printable line from HTML: first 'action=' bypass, else first ~100 chars. */
	private static String firstNonEmptyBypassLine(String html)
	{
		int a = html.toLowerCase().indexOf("action=\"");
		if (a >= 0)
		{
			int s = html.indexOf('"', a) + 1;
			int e = html.indexOf('"', s);
			if (e > s)
			{
				return "action=\"" + html.substring(s, e) + "\"";
			}
		}
		String flat = html.replaceAll("\\s+", " ");
		return flat.length() > 100 ? flat.substring(0, 100) : flat;
	}



	// ---------------- enter-world + wire helpers (classic Socket) ----------------

	private static byte[] buildProtocolVersion() throws Exception
	{
		ByteBuffer bb = ByteBuffer.allocate(1 + 4).order(ByteOrder.LITTLE_ENDIAN);
		bb.put((byte) 0x00);
		bb.putInt(PROTOCOL_VERSION);
		return bb.array();
	}

	private static void sendAuthLogin(OutputStream out, GameCrypt crypt, boolean useEnc, String account, L2JProtocol login) throws Exception
	{
		byte[] name = account.getBytes(StandardCharsets.UTF_16LE);
		ByteBuffer bb = ByteBuffer.allocate(1 + name.length + 2 + 16).order(ByteOrder.LITTLE_ENDIAN);
		bb.put((byte) 0x08);
		bb.put(name);
		bb.putShort((short) 0);
		bb.putInt(login.getPlayOk2());
		bb.putInt(login.getPlayOk1());
		bb.putInt(login.getLoginOk1());
		bb.putInt(login.getLoginOk2());
		byte[] plain = new byte[bb.position()];
		bb.flip();
		bb.get(plain);
		if (useEnc)
		{
			crypt.encrypt(plain, 0, plain.length);
		}
		sendFrame(out, plain);
	}

	private static void sendCharacterSelect(OutputStream out, GameCrypt crypt, boolean useEnc, int slot) throws Exception
	{
		ByteBuffer bb = ByteBuffer.allocate(1 + 4 + 2 + 4 + 4 + 4).order(ByteOrder.LITTLE_ENDIAN);
		bb.put((byte) OP_C_CHARACTER_SELECT);
		bb.putInt(slot);
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
		sendFrame(out, plain);
	}

	private static void sendEnterWorld(OutputStream out, GameCrypt crypt, boolean useEnc) throws Exception
	{
		ByteBuffer bb = ByteBuffer.allocate(1 + 32 + 16 + 32 + 4 + 20).order(ByteOrder.LITTLE_ENDIAN);
		bb.put((byte) OP_C_ENTER_WORLD);
		for (int i = 0; i < 32; i++)
		{
			bb.put((byte) 0);
		}
		bb.putInt(0);
		bb.putInt(0);
		bb.putInt(0);
		bb.putInt(0);
		for (int i = 0; i < 32; i++)
		{
			bb.put((byte) 0);
		}
		bb.putInt(0);
		for (int i = 0; i < 20; i++)
		{
			bb.put((byte) 0);
		}
		byte[] plain = new byte[bb.position()];
		bb.flip();
		bb.get(plain);
		if (useEnc)
		{
			crypt.encrypt(plain, 0, plain.length);
		}
		sendFrame(out, plain);
	}

	private static void sendFrame(OutputStream out, byte[] payload) throws Exception
	{
		ByteBuffer buf = ByteBuffer.allocate(2 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
		buf.putShort((short) (payload.length + 2));
		buf.put(payload);
		buf.flip();
		byte[] data = new byte[buf.remaining()];
		buf.get(data);
		out.write(data);
		out.flush();
	}

	private static byte[] readFrame(InputStream in) throws Exception
	{
		byte[] payload = readPayload(in);
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

	/** Read one GS packet payload after the 2-byte self-inclusive size header (honors SO_TIMEOUT). */
	private static byte[] readPayload(InputStream in) throws Exception
	{
		DataInputStream dis = (in instanceof DataInputStream) ? (DataInputStream) in : new DataInputStream(in);
		byte[] sizeBytes = new byte[2];
		dis.readFully(sizeBytes);
		int size = (sizeBytes[0] & 0xff) | ((sizeBytes[1] & 0xff) << 8);
		if (size < 2 || size > 65535)
		{
			return null;
		}
		byte[] payload = new byte[size - 2];
		dis.readFully(payload);
		return payload;
	}

	private static int leInt(byte[] d, int i)
	{
		return (d[i] & 0xff) | ((d[i + 1] & 0xff) << 8) | ((d[i + 2] & 0xff) << 16) | ((d[i + 3] & 0xff) << 24);
	}
}


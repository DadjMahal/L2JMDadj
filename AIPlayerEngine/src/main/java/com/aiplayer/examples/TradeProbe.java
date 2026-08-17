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
 * B7 trade probe: prove an AI player buys an item from a merchant NPC over the external socket (genuine path).
 *
 * <p>Enters CombatBot_01 (positioned at Trader Silvia 30003, Talking Island, within interaction distance),
 * scans `NPC_INFO`(0x16) for Silvia (npcType = 30003+1000000 = 1003003), sends `Action`(0x04) to open her
 * shop HTML (`NpcHtmlMessage` 0x0F, data/html/merchant/30003.htm). The bot parses the HTML and extracts the
 * validated Buy bypass the server registered (the `bypass -h npc_<objId>_Buy <listId>` action → cached as
 * `npc_<objId>_Buy <listId>` after stripping `-h ` per `HtmlUtil.buildHtmlBypassCache`), then sends it via
 * `RequestBypassToServer`(0x21). The server validates the bypass (exact match), routes `npc_<objId>_` →
 * `Npc.onBypassFeedback("Buy <listId>")` → `BypassHandler`/`Buy.java` → `Merchant.showBuyWindow` →
 * `BuyList`(0x11). The bot parses the BuyList, picks the first affordable item, and sends
 * `RequestBuyItem`(0x1F); the server deducts adena / adds the item (verified in DB by the caller).
 *
 * <p>Server facts (SourceCode, no server source changed):
 * <ul>
 *   <li>RequestBypassToServer(0x21) = `[0x21][command:readString]`; `validateHtmlAction` exact-matches the
 *       cached bypass (`HtmlUtil.buildHtmlBypassCache` strips `-h ` → caches `npc_<objId>_Buy <listId>`),
 *       then range-checks INTERACTION_DISTANCE.</li>
 *   <li>BuyList(0x11) writeImpl = `[0x11][money:int][listId:int][size:short]` + per item
 *       `[type1:short][objId:int][itemId:int][count:int][type2:short][eq:short][body:int][enchant:short][0][0][price:int]`.</li>
 *   <li>RequestBuyItem(0x1F) = `[0x1F][listId:int][n:int][{itemId:int,count:int}]`. Adena item_id=57.</li>
 * </ul>
 */
@Deprecated // S10-T06: superseded by examples.FleetPlay
public class TradeProbe
{
    private static final int PROTOCOL_VERSION = 746;
    private static final byte[] KEY_TAIL =
    { (byte) 0xC8, (byte) 0x27, (byte) 0x93, (byte) 0x01, (byte) 0xA1, (byte) 0x6C, (byte) 0x31, (byte) 0x97 };

    // Server->client opcodes
    private static final int OP_CHAR_SELECT_INFO = 0x13;
    private static final int OP_CHAR_SELECTED = 0x15;
    private static final int OP_NPC_INFO = 0x16;
    private static final int OP_NPC_HTML = 0x0F;
    private static final int OP_BUY_LIST = 0x11;
    private static final int OP_ITEM_LIST = 0x1B;
    private static final int OP_SYSTEM_MESSAGE = 0x64;

    // Client->server opcodes
    private static final int OP_C_CHARACTER_SELECT = 0x0D;
    private static final int OP_C_ENTER_WORLD = 0x03;
    private static final int OP_C_ACTION = 0x04;
    private static final int OP_C_REQUEST_BYPASS_TO_SERVER = 0x21;
    private static final int OP_C_REQUEST_BUY_ITEM = 0x1F;

    private static final int TRADER_NPC_ID = 30003; // Silvia; npcType in NPC_INFO = id+1000000
    private static final int SILVIA_X = -83789;
    private static final int SILVIA_Y = 240799;
    private static final int SILVIA_Z = -3717;

    // Shared state set by the reader thread, consumed by main.
    private static volatile int traderObjId = -1;
    private static volatile String buyBypass = null; // e.g. "npc_12345_Buy 3000301"
    private static volatile int buyListId = -1;
    private static volatile int buyItemId = -1;
    private static volatile int buyItemPrice = -1;
    private static volatile long playerMoney = -1;
    private static volatile boolean buyListParsed = false;
    private static volatile boolean sentBuy = false;

    public static void main(String[] args) throws Exception
    {
        String account = args.length > 0 ? args[0] : "ai_combat_01";
        String password = args.length > 1 ? args[1] : "ai123pass";
        String host = args.length > 2 ? args[2] : "127.0.0.1";
        int port = args.length > 3 ? Integer.parseInt(args[3]) : 7777;

        AIPlayer player = new AIPlayer(account, 100, 1, 0);
        L2JProtocol login = new L2JProtocol(player, host, 2106, port);
        if (!login.connectAndLogin(account, password, 0))
        {
            login.disconnect();
            throw new RuntimeException("[TradeProbe] login failed for " + account);
        }
        System.out.println("[TradeProbe] login OK");

        try (Socket gs = new Socket(host, port))
        {
            gs.setSoTimeout(2000);
            final OutputStream out = gs.getOutputStream();
            final InputStream in = gs.getInputStream();

            // Handshake + auth (proven flow).
            sendFrame(out, buildProtocolVersion());
            byte[] keyFrame = readFrame(in);
            int encFlag = leInt(keyFrame, 12);
            boolean useEnc = encFlag != 0;
            byte[] key = new byte[16];
            System.arraycopy(keyFrame, 4, key, 0, 8);
            System.arraycopy(KEY_TAIL, 0, key, 8, 8);
            GameCrypt crypt = new GameCrypt();
            crypt.setKey(key);
            System.out.println("[TradeProbe] KeyPacket packetEncryption=" + encFlag);

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
                    spawned = true;
                    break;
                }
            }
            if (!spawned)
            {
                throw new RuntimeException("[TradeProbe] no CharSelected");
            }
            System.out.println("[TradeProbe] in world");

            // Reader thread: handles NPC_INFO, NpcHtmlMessage, BuyList.
            Thread reader = new Thread(() -> readLoop(in, useEnc, crypt), "tradeReader");
            reader.start();

            // 1) Scan for Silvia (NPC_INFO 1003003) ~6s.
            long scanEnd = System.currentTimeMillis() + 6000;
            while (System.currentTimeMillis() < scanEnd && traderObjId < 0)
            {
                Thread.sleep(100);
            }
            if (traderObjId < 0)
            {
                System.out.println("[TradeProbe][WARN] Silvia NPC_INFO not seen — walking toward her spot");
            }
            else
            {
                System.out.println("[TradeProbe] found Silvia objId=" + traderObjId);
            }

            // 2) Click Silvia TWICE: 1st Action TARGETS her, 2nd Action opens the merchant HTML (NpcClick.onAction
            //    only shows the dialog on the 2nd click when the NPC is already the target). Respect action flood protector.
            sendAction(out, crypt, useEnc, Math.max(traderObjId, 0), SILVIA_X, SILVIA_Y, SILVIA_Z);
            System.out.println("[TradeProbe] sent Action(0x04) #1 (target Silvia)");
            try { Thread.sleep(700); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            sendAction(out, crypt, useEnc, Math.max(traderObjId, 0), SILVIA_X, SILVIA_Y, SILVIA_Z);
            System.out.println("[TradeProbe] sent Action(0x04) #2 (open merchant HTML)");

            // 3) Wait for the HTML + extract the validated Buy bypass.
            long htmlEnd = System.currentTimeMillis() + 6000;
            while (System.currentTimeMillis() < htmlEnd && buyBypass == null)
            {
                Thread.sleep(100);
            }
            if (buyBypass == null)
            {
                System.out.println("[TradeProbe][FAIL] no NpcHtmlMessage / no Buy bypass");
                gs.close();
                login.disconnect();
                return;
            }
            System.out.println("[TradeProbe] extracted Buy bypass from HTML: \"" + buyBypass + "\"");

            // 4) Send the validated bypass → server opens BuyList(0x11).
            sendBypass(out, crypt, useEnc, buyBypass);
            System.out.println("[TradeProbe] sent RequestBypassToServer(0x21) with the Buy bypass");

            // 5) Wait for BuyList parse.
            long buyEnd = System.currentTimeMillis() + 6000;
            while (System.currentTimeMillis() < buyEnd && !buyListParsed)
            {
                Thread.sleep(100);
            }
            if (!buyListParsed || buyItemId < 0)
            {
                System.out.println("[TradeProbe][FAIL] no BuyList / no affordable item (money=" + playerMoney + ")");
                gs.close();
                login.disconnect();
                return;
            }
            System.out.println("[TradeProbe] BuyList listId=" + buyListId + " money=" + playerMoney + " -> buy itemId=" + buyItemId + " price=" + buyItemPrice);

            // 6) Buy one of the item.
            sendBuyItem(out, crypt, useEnc, buyListId, buyItemId, 1);
            sentBuy = true;
            System.out.println("[TradeProbe] sent RequestBuyItem(0x1F) listId=" + buyListId + " itemId=" + buyItemId + " count=1");

            Thread.sleep(4000); // let the server process the purchase
            gs.close();
            reader.join(3000);
            login.disconnect();
            System.out.println("[TradeProbe] done — verify DB: adena(57) decreased / new item row added");
        }
    }

    private static synchronized void recordTrader(int objId, int npcType, byte[] pl)
    {
        if (traderObjId < 0 && npcType == (TRADER_NPC_ID + 1000000) && pl.length >= 25)
        {
            traderObjId = objId;
            System.out.println("[TradeProbe] NPC_INFO Silvia objId=" + objId + " pos=(" + leInt(pl, 13) + "," + leInt(pl, 17) + "," + leInt(pl, 21) + ")");
        }
    }

    /** Read loop: parse NPC_INFO (Silvia), NpcHtmlMessage (extract Buy bypass), BuyList (pick affordable item). */
    private static void readLoop(InputStream in, boolean useEnc, GameCrypt crypt)
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
            catch (Exception e)
            {
                break;
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

            if (op == OP_NPC_INFO && plain.length >= 25)
            {
                recordTrader(leInt(plain, 1), leInt(plain, 5), plain);
            }
            else if (op == OP_NPC_HTML)
            {
                String html = decodeHtmlString(plain);
String bypass = extractBuyBypass(html);
                if (bypass != null)
                {
                    synchronized (TradeProbe.class)
                    {
                        if (buyBypass == null)
                        {
                            buyBypass = bypass;
                            System.out.println("[TradeProbe] NpcHtmlMessage(0x0F) len=" + plain.length + " -> Buy link found");
                        }
                    }
                }
            }
            else if (op == OP_BUY_LIST && !buyListParsed)
            {
                parseBuyList(plain);
            }
        }
    }

    /** NpcHtmlMessage(0x0F): [0x0F][npcObjId:int][html:UTF-16LE null-term][itemId:int]. */
    private static String decodeHtmlString(byte[] plain)
    {
        if (plain.length < 6)
        {
            return null;
        }
        int p = 1; // opcode
        p += 4; // npcObjId
        StringBuilder sb = new StringBuilder();
        while (p + 1 < plain.length)
        {
            int ch = (plain[p] & 0xff) | ((plain[p + 1] & 0xff) << 8);
            p += 2;
            if (ch == 0)
            {
                break;
            }
            sb.append((char) ch);
        }
        return sb.toString();
    }

    /** Extract the validated Buy bypass the server cached: `bypass -h npc_<objId>_Buy <listId>` -> `npc_<objId>_Buy <listId>`. */
    private static String extractBuyBypass(String html)
    {
        if (html == null)
        {
            return null;
        }
        String lower = html.toLowerCase();
        int idx = lower.indexOf("bypass");
        while (idx >= 0)
        {
            // Opening quote is the one that PRECEDES "bypass" (action="bypass -h npc_X_Buy <list>").
            int openQuote = html.lastIndexOf('"', idx);
            if (openQuote < 0)
            {
                break;
            }
            int closeQuote = html.indexOf('"', openQuote + 1);
            if (closeQuote < 0)
            {
                break;
            }
            String action = html.substring(openQuote + 1, closeQuote).trim();
            // Mirror HtmlUtil.buildHtmlBypassCache: strip a leading "bypass " then a leading "-h ".
            if (action.toLowerCase().startsWith("bypass "))
            {
                action = action.substring(7).trim();
            }
            if (action.toLowerCase().startsWith("-h "))
            {
                action = action.substring(3).trim();
            }
            if (action.toLowerCase().startsWith("npc_") && action.toLowerCase().contains("_buy "))
            {
                return action; // e.g. "npc_268439739_Buy 3000301" (exactly what the server cached)
            }
            idx = lower.indexOf("bypass", closeQuote);
        }
        return null;
    }


    /** BuyList(0x11): [0x11][money:int][listId:int][size:short] + per-item 32B; pick first affordable item. */
    private static synchronized void parseBuyList(byte[] plain)
    {
        if (plain.length < 11)
        {
            return;
        }
        long money = leInt(plain, 1) & 0xFFFFFFFFL;
        int listId = leInt(plain, 5);
        int size = leInt(plain, 9) & 0xFFFF; // writeShort (2 bytes) but read as int-friendly
        playerMoney = money;
        buyListId = listId;
        System.out.println("[TradeProbe] BuyList(0x11) money=" + money + " listId=" + listId + " size=" + size);
        int off = 11;
        for (int i = 0; i < size && off + 32 <= plain.length; i++)
        {
            int itemId = leInt(plain, off + 6);
            int price = leInt(plain, off + 28);
            if (price > 0 && price <= money && buyItemId < 0)
            {
                buyItemId = itemId;
                buyItemPrice = price;
                System.out.println("[TradeProbe]   item#" + i + " itemId=" + itemId + " price=" + price + " (affordable)");
            }
            off += 32;
        }
        buyListParsed = true;
    }

    // ------------------------------------------------------------------
    // Client packet builders (plaintext; game crypt disabled on this server)
    // ------------------------------------------------------------------

    /** Action (0x04): [0x04][targetObjId][originX][originY][originZ][actionId]. */
    private static void sendAction(OutputStream out, GameCrypt crypt, boolean useEnc, int targetObjId, int ox, int oy, int oz) throws Exception
    {
        ByteBuffer bb = ByteBuffer.allocate(1 + 4 + 4 + 4 + 4 + 1).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) OP_C_ACTION);
        bb.putInt(targetObjId);
        bb.putInt(ox);
        bb.putInt(oy);
        bb.putInt(oz);
        bb.put((byte) 0); // actionId = 0
        sendPayloadFrame(out, crypt, useEnc, bb);
    }

    /** RequestBypassToServer (0x21): [0x21][command:UTF-16LE null-term]. */
    private static void sendBypass(OutputStream out, GameCrypt crypt, boolean useEnc, String command) throws Exception
    {
        byte[] cmd = (command + "\0").getBytes(StandardCharsets.UTF_16LE);
        ByteBuffer bb = ByteBuffer.allocate(1 + cmd.length).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) OP_C_REQUEST_BYPASS_TO_SERVER);
        bb.put(cmd);
        sendPayloadFrame(out, crypt, useEnc, bb);
    }

    /** RequestBuyItem (0x1F): [0x1F][listId:int][n:int][{itemId:int,count:int}]. */
    private static void sendBuyItem(OutputStream out, GameCrypt crypt, boolean useEnc, int listId, int itemId, int count) throws Exception
    {
        ByteBuffer bb = ByteBuffer.allocate(1 + 4 + 4 + 4 + 4).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) OP_C_REQUEST_BUY_ITEM);
        bb.putInt(listId);
        bb.putInt(1); // n = 1
        bb.putInt(itemId);
        bb.putInt(count);
        sendPayloadFrame(out, crypt, useEnc, bb);
    }

    private static void sendPayloadFrame(OutputStream out, GameCrypt crypt, boolean useEnc, ByteBuffer bb) throws Exception
    {
        byte[] plain = new byte[bb.position()];
        bb.flip();
        bb.get(plain);
        if (useEnc)
        {
            crypt.encrypt(plain, 0, plain.length);
        }
        sendFrame(out, plain);
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
        for (int i = 0; i < 32; i++) bb.put((byte) 0);
        bb.putInt(0); bb.putInt(0); bb.putInt(0); bb.putInt(0);
        for (int i = 0; i < 32; i++) bb.put((byte) 0);
        bb.putInt(0);
        for (int i = 0; i < 20; i++) bb.put((byte) 0);
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
        if (payload == null) return null;
        byte[] frame = new byte[payload.length + 2];
        frame[0] = (byte) ((payload.length + 2) & 0xff);
        frame[1] = (byte) (((payload.length + 2) >> 8) & 0xff);
        System.arraycopy(payload, 0, frame, 2, payload.length);
        return frame;
    }

    private static byte[] readPayload(InputStream in) throws Exception
    {
        DataInputStream dis = (in instanceof DataInputStream) ? (DataInputStream) in : new DataInputStream(in);
        byte[] sizeBytes = new byte[2];
        dis.readFully(sizeBytes);
        int size = (sizeBytes[0] & 0xff) | ((sizeBytes[1] & 0xff) << 8);
        if (size < 2 || size > 65535) return null;
        byte[] payload = new byte[size - 2];
        dis.readFully(payload);
        return payload;
    }

    private static int leInt(byte[] d, int i)
    {
        return (d[i] & 0xff) | ((d[i + 1] & 0xff) << 8) | ((d[i + 2] & 0xff) << 16) | ((d[i + 3] & 0xff) << 24);
    }
}

package com.aiplayer.examples;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.aiplayer.engine.AIPlayer;
import com.aiplayer.engine.GameServerClient;
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.protocol.PacketCodec;
import com.aiplayer.protocol.PacketLogger;

/**
 * Stream C7/C8/B6b: GENUINE NPC-talk quest driver (rewrite after honest audit).
 *
 * The previous version was a false positive: it sent a cold RequestBypassToServer for
 * Q00255_Tutorial and claimed "QUEST_COMPLETED" whenever activeQuests==0 (which is always,
 * because the tutorial quest is Ex-flagged and excluded from QuestList). The server never
 * accepted it — no character_quests row appeared.
 *
 * Root cause (verified in the L2JMobius source):
 *   1. RequestBypassToServer runs validateHtmlAction(): a bypass is silently dropped unless
 *      the exact command string was previously SHOWN to the player in an NpcHtmlMessage from
 *      that NPC, and the player is within Npc.INTERACTION_DISTANCE (250).
 *   2. Q00255_Tutorial has no onTalk / no addStartNpc — it auto-starts on enter-world (B6),
 *      so there is nothing to bypass.
 *
 * This driver therefore performs a REAL quest that is startable via NPC dialog:
 *   Q00101_SwordOfSolidarity, given by Roien (NPC 30008) at (-71384, 258304, -3104).
 * Dialog chain (verified from script + html):
 *   Action(0x04) on Roien -> default html (contains link "bypass Script")
 *   send "Script"                    -> quest choose window
 *   send "Script Q00101_SwordOfSolidarity" -> onTalk (30008-02.htm if Human + level>=9)
 *   follow links: 30008-02a.htm -> 30008-02b.htm -> 30008-03.htm (startQuest + gives item 796)
 * Every bypass is read from the html the server actually sent us, so it is always validated.
 */
public class QuestFlowLoop {
    private static final Logger LOGGER = Logger.getLogger(QuestFlowLoop.class.getName());
    private static final int LOGIN_PORT = 2106;
    private static final long LOOP_SLEEP_MS = 300;

    public static void main(String[] args) throws Exception {
        Logger.getLogger("com.aiplayer").setLevel(Level.INFO);

        String account = args.length > 0 ? args[0] : "ai_combat_01";
        String password = args.length > 1 ? args[1] : "ai123pass";
        String host = args.length > 2 ? args[2] : "127.0.0.1";
        int gamePort = args.length > 3 ? Integer.parseInt(args[3]) : 7777;
        int charId = args.length > 4 ? Integer.parseInt(args[4]) : 2;
        int charSlot = args.length > 5 ? Integer.parseInt(args[5]) : 0;
        int seedX = args.length > 6 ? Integer.parseInt(args[6]) : -71384;
        int seedY = args.length > 7 ? Integer.parseInt(args[7]) : 258304;
        int seedZ = args.length > 8 ? Integer.parseInt(args[8]) : -3104;
        int seconds = args.length > 9 ? Integer.parseInt(args[9]) : 30;
        int targetNpcId = args.length > 10 ? Integer.parseInt(args[10]) : 30008; // Roien
        String targetQuest = args.length > 11 ? args[11] : "Q00101_SwordOfSolidarity";
        String startEvent = args.length > 12 ? args[12] : "30008-03.htm"; // triggers startQuest()

        AIPlayer player = new AIPlayer(account, 100, 1, 0);
        player.setPosition(seedX, seedY, seedZ);

        L2JProtocol login = new L2JProtocol(player, host, LOGIN_PORT, gamePort);
        System.out.println("[QuestFlowLoop] login...");
        boolean ok = login.connectAndLogin(account, password, charId);
        if (!ok) { System.out.println("[QuestFlowLoop] FAIL login"); System.exit(2); }

        player.setLoggedIn(true);
        player.setCharacterId(charId);

        GameServerClient gs = new GameServerClient(player, host, gamePort);
        boolean entered = gs.connectAndEnterWorld(login, account, charSlot);
        if (!entered) { System.out.println("[QuestFlowLoop] FAIL enter-world"); login.disconnect(); System.exit(3); }

        gs.startReader();
        PacketLogger pl = gs.getPacketLogger();
        pl.setSelfObjectId(charId);
        player.getQuestAI().setPacketLogger(pl);

        long deadline = System.currentTimeMillis() + seconds * 1000L;
        int sentBypasses = 0;
        int sentActions = 0;
        boolean startEventSent = false;
        boolean dialogOpen = false;
        Set<String> sentLinks = new HashSet<>();
        String lastHtml = null;
        long lastDiag = 0;
        // NpcClick.onAction opens the dialog only on the SECOND click (first click SELECTS the
        // NPC via setTarget). So we keep re-sending Action until the server actually shows us an
        // NpcHtmlMessage for that NPC, then switch to the dialog-following phase.
        String dialogHtml = null;

        System.out.println("[QuestFlowLoop] in world — quest flow: " + targetQuest
            + " via NPC " + targetNpcId + " startEvent=" + startEvent);


        while (System.currentTimeMillis() < deadline) {
            int px = pl.getPlayerX();
            int py = pl.getPlayerY();
            int pz = pl.getPlayerZ();
            if (px != 0 || py != 0 || pz != 0) {
                player.setPosition(px, py, pz);
            }

            // 1. Find the quest-giver NPC and TALK to it: keep clicking (Action) until the
            //    server responds with the first NpcHtmlMessage from that NPC.
            PacketLogger.EntityInfo npc = pl.findEntityByNpcId(targetNpcId);
            if (npc == null) {
                if (System.currentTimeMillis() - lastDiag > 3000) {
                    System.out.println("[QuestFlowLoop] waiting for NPC " + targetNpcId
                        + " ... entities=" + pl.getEntityCount());
                    lastDiag = System.currentTimeMillis();
                }
                Thread.sleep(LOOP_SLEEP_MS);
                continue;
            }
            if (!dialogOpen) {
                String candidate = pl.getLastNpcHtml();
                int origin = pl.getLastNpcHtmlOriginObjId();
                if (candidate != null && origin == npc.objectId) {
                    dialogOpen = true;
                    lastHtml = null; // force processing of this first dialog next iteration
                    continue;
                }
                gs.sendGameFrame(PacketCodec.encodeAction(npc.objectId, player.getX(), player.getY(), player.getZ()));
                sentActions++;
                System.out.println("[QuestFlowLoop] SENT opcode=0x04 Action on NPC " + targetNpcId
                    + " objId=" + npc.objectId + " TALK_TO_NPC (click #" + sentActions + ")");
                Thread.sleep(500);
                continue;
            }


            // 2. Process the dialog the server SHOWED us (NpcHtmlMessage) for that NPC.
            String current = pl.getLastNpcHtml();
            if (current == null || current.equals(lastHtml)) {
                Thread.sleep(LOOP_SLEEP_MS);
                continue;
            }
            lastHtml = current;

            String[] links = PacketLogger.extractBypassLinks(current);
            if (links.length == 0) {
                System.out.println("[QuestFlowLoop] html has no bypass links (maybe level/race gate): "
                    + excerpt(current));
                Thread.sleep(LOOP_SLEEP_MS);
                continue;
            }
            System.out.println("[QuestFlowLoop] NPC_HTML links=" + links.length
                + " first=" + (links.length > 0 ? links[0] : ""));
            System.out.println("[QuestFlowLoop] html excerpt: " + excerpt(current));

            // 3. Pick the next validated bypass:
            //    - "Script" (open quest window) if never sent
            //    - the link containing our target quest name
            //    - otherwise the first link that ends with the start event / an unvisited .htm
            String next = null;
            for (String link : links) {
                if (link.equals("Script") && !sentLinks.contains(link)) { next = link; break; }
                if (link.contains(targetQuest) && !sentLinks.contains(link)) { next = link; break; }
            }
            if (next == null) {
                for (String link : links) {
                    if (!sentLinks.contains(link) && (link.contains(".htm") || link.contains("Quest"))) {
                        next = link; break;
                    }
                }
            }
            if (next == null) {
                System.out.println("[QuestFlowLoop] no new validated bypass; pausing");
                Thread.sleep(LOOP_SLEEP_MS);
                continue;
            }

            sentLinks.add(next);
            gs.sendGameFrame(PacketCodec.encodeBypass(next));
            sentBypasses++;
            System.out.println("[QuestFlowLoop] SENT opcode=0x21 RequestBypassToServer: \"" + next + "\"");
            if (next.contains(startEvent)) {
                startEventSent = true;
                System.out.println("[QuestFlowLoop] START_EVENT_SENT (" + startEvent + ") — quest acceptance path triggered");
            }
            lastHtml = current;
            Thread.sleep(700);
        }

        System.out.println("[QuestFlowLoop] QUEST FLOW COMPLETE sentActions=" + sentActions
            + " sentBypasses=" + sentBypasses + " startEventSent=" + startEventSent);
        System.out.println("[QuestFlowLoop] CHECK_QUEST_STATE (verify DB: character_quests Q00101 + item 796)");
        System.out.println("[QuestFlowLoop] DONE");
        gs.disconnect();
        login.disconnect();
        // Honest exit: only claim success when the quest-start event was actually sent over
        // a validated dialog chain. DB verification is done by the proof script (source of truth).
        System.exit(startEventSent ? 0 : 4);
    }

    private static String excerpt(String html) {
        if (html == null) return "";
        String t = html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return t.length() > 160 ? t.substring(0, 160) + "..." : t;
    }
}


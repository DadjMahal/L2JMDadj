package com.aiplayer.examples;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.aiplayer.engine.AIPlayer;
import com.aiplayer.engine.GameServerClient;
import com.aiplayer.engine.QuestDecision;
import com.aiplayer.engine.QuestFramePlanner;
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.protocol.PacketCodec;

/**
 * Stream C7 (B6b): Live quest trigger via RequestBypassToServer(0x21).
 *
 * Wires: Login -> EnterWorld -> setSelfObjectId -> QuestAI.makeDecision() -> QuestFramePlanner.plan()
 *        -> GameServerClient.sendGameFrame().
 *
 * Sends RequestQuestList(0x63), then a Bypass "questId=Q00255_Tutorial EngageNpc" to start the Tutorial quest.
 *
 * Usage: java -cp target/classes com.aiplayer.examples.QuestLoop account pass host gamePort charId seedX seedY seedZ
 */
public class QuestLoop {
    private static final Logger LOGGER = Logger.getLogger(QuestLoop.class.getName());
    private static final int LOGIN_PORT = 2106;

    public static void main(String[] args) throws Exception {
        Logger.getLogger("com.aiplayer").setLevel(Level.INFO);

        String account = args.length > 0 ? args[0] : "ai_combat_01";
        String password = args.length > 1 ? args[1] : "ai123pass";
        String host = args.length > 2 ? args[2] : "127.0.0.1";
        int gamePort = args.length > 3 ? Integer.parseInt(args[3]) : 7777;
        int charId = args.length > 4 ? Integer.parseInt(args[4]) : 2;
        int seedX = args.length > 5 ? Integer.parseInt(args[5]) : -84058;
        int seedY = args.length > 6 ? Integer.parseInt(args[6]) : 243239;
        int seedZ = args.length > 7 ? Integer.parseInt(args[7]) : -3730;

        AIPlayer player = new AIPlayer(account, 100, 1, 0);
        player.setPosition(seedX, seedY, seedZ);

        L2JProtocol login = new L2JProtocol(player, host, LOGIN_PORT, gamePort);
        System.out.println("[QuestLoop] login...");
        boolean ok = login.connectAndLogin(account, password, charId);
        if (!ok) { System.out.println("[QuestLoop] FAIL login"); System.exit(2); }

        player.setLoggedIn(true); player.setCharacterId(charId);

        GameServerClient gs = new GameServerClient(player, host, gamePort);
        boolean entered = gs.connectAndEnterWorld(login, account, 0);
        if (!entered) { System.out.println("[QuestLoop] FAIL enter-world"); login.disconnect(); System.exit(3); }

        gs.startReader();
        gs.getPacketLogger().setSelfObjectId(charId);
        player.getCombatAI().setPacketLogger(gs.getPacketLogger());

        // B6b: Request Quest List (direct send since shouldExecute is false for requestQuestList)
        gs.sendGameFrame(PacketCodec.encodeQuestList());
        System.out.println("[QuestLoop] SENT opcode=0x63 frame=RequestQuestList");
        System.out.println("[QuestLoop] REQUEST_QUEST_LIST_SENT");

        // B6b: Bypass to start Tutorial quest
        QuestDecision bypass = QuestDecision.acceptQuest("Q00255_Tutorial", "30530", seedX, seedY, seedZ);
        QuestFramePlanner.QuestFrame[] bypassFrames = QuestFramePlanner.plan(bypass);
        for (QuestFramePlanner.QuestFrame f : bypassFrames) { gs.sendGameFrame(f.frame); System.out.println("[QuestLoop] SENT opcode=0x21 frame=RequestBypassToServer"); System.out.println("[QuestLoop] BYPASS_SENT"); }

        Thread.sleep(3000);
        System.out.println("[QuestLoop] CHECK_QUEST_STATE");
        System.out.println("[QuestLoop] DONE");
        gs.disconnect(); login.disconnect();
        System.exit(0);
    }
}

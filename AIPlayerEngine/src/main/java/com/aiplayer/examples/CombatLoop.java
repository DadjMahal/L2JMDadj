package com.aiplayer.examples;

import com.aiplayer.core.FleetConfig;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.aiplayer.net.AIPlayer;
import com.aiplayer.behavior.combat.CombatDecision;
import com.aiplayer.behavior.combat.CombatFramePlanner;
import com.aiplayer.net.GameServerClient;
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.protocol.PacketLogger;
import com.aiplayer.behavior.combat.CombatAI;

/**
 * Stream C slice 5: LIVE combat loop driver.
 *
 * <p>Wires the in-engine pieces together end-to-end against the running L2JM server (external socket,
 * no server source changes):
 * <pre>
 *   L2JProtocol (login-server auth, SessionKey)
 *     -&gt; GameServerClient.connectAndEnterWorld (GS handshake + EnterWorld)
 *     -&gt; startReader() (feeds PacketLogger: CharInfo/NPC_INFO/StatusUpdate)
 *     -&gt; loop: sync player pos -&gt; CombatAI.makeDecision() -&gt; CombatFramePlanner.plan()
 *            -&gt; gs.sendGameFrame(...)  (real Action 0x04 / AttackRequest 0x0A)
 * </pre>
 *
 * <p>Prints grep-able markers for the proof script:
 * {@code [CombatLoop] ENGAGED target=..}, {@code [CombatLoop] LIVE COMBAT LOOP COMPLETE sentActions=..}.
 *
 * <p>Usage: {@code java -cp target/classes com.aiplayer.examples.CombatLoop <account> <pass>
 * [host] [gamePort] [charId] [charSlot] [durationSeconds]}
 */
@Deprecated // S10-T06: superseded by examples.FleetPlay
public class CombatLoop
{
    private static final Logger LOGGER = Logger.getLogger(CombatLoop.class.getName());

    private static final int LOGIN_PORT = 2106;
    private static final long LOOP_SLEEP_MS = 500;

    public static void main(String[] args) throws Exception
    {
        Logger.getLogger("com.aiplayer").setLevel(Level.INFO);

        String account = args.length > 0 ? args[0] : "ai_combat_01";
        String password = args.length > 1 ? args[1] : FleetConfig.accountPassword();
        String host = args.length > 2 ? args[2] : "127.0.0.1";
        int gamePort = args.length > 3 ? Integer.parseInt(args[3]) : 7777;
        int charId = args.length > 4 ? Integer.parseInt(args[4]) : 2;   // CombatBot_01 objId
        int charSlot = args.length > 5 ? Integer.parseInt(args[5]) : 0; // CharacterSelect slot
        int seedX = args.length > 6 ? Integer.parseInt(args[6]) : -82759; // wolf-zone spawn (B4)
        int seedY = args.length > 7 ? Integer.parseInt(args[7]) : 250149;
        int seedZ = args.length > 8 ? Integer.parseInt(args[8]) : -3600;
        int seconds = args.length > 9 ? Integer.parseInt(args[9]) : 20;

        AIPlayer player = new AIPlayer(account, 100, 1, 0);
        // Seed position from the known spawn so distance-to-enemy works even before the first
        // CharInfo arrives (live runs showed the server never sent a self CharInfo in this flow).
        player.setPosition(seedX, seedY, seedZ);
        L2JProtocol login = new L2JProtocol(player, host, LOGIN_PORT, gamePort);
        System.out.println("[CombatLoop] login...");
        boolean ok = login.connectAndLogin(account, password, charId);
        if (!ok)
        {
            System.out.println("[CombatLoop] FAIL login");
            System.exit(2);
        }
        player.setLoggedIn(true);
        player.setCharacterId(charId);

        GameServerClient gs = new GameServerClient(player, host, gamePort);
        boolean entered = gs.connectAndEnterWorld(login, account, charSlot);
        if (!entered)
        {
            System.out.println("[CombatLoop] FAIL enter-world");
            login.disconnect();
            System.exit(3);
        }
        gs.startReader();

        // Slice 5 fix: CombatAI must decide from the SAME buffer the reader feeds, not its own empty
        // private one. Attach the live logger (also lets StatusUpdate drive HP in shouldDefend()).
        player.getCombatAI().setPacketLogger(gs.getPacketLogger());
        // Slice 6: tell the logger which objId is the bot itself (the charId) so ONLY our own
        // StatusUpdate drives self HP — a target wolf's StatusUpdate must not clobber bot HP.
        gs.getPacketLogger().setSelfObjectId(charId);

        CombatFramePlanner planner = new CombatFramePlanner();
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        int sentActions = 0;
        long lastDiag = 0;
        boolean wasAlive = true;

        System.out.println("[CombatLoop] in world at (" + seedX + "," + seedY + "," + seedZ
            + ") — running live combat loop for " + seconds + "s");
        while (System.currentTimeMillis() < deadline)
        {
            // Follow real movement when a CharInfo has actually been parsed (else keep the seed).
            int px = gs.getPacketLogger().getPlayerX();
            int py = gs.getPacketLogger().getPlayerY();
            int pz = gs.getPacketLogger().getPlayerZ();
            if (px != 0 || py != 0 || pz != 0)
            {
                player.setPosition(px, py, pz);
            }

            CombatDecision decision = player.getCombatAI().makeDecision();

            // Slice 6 death feedback: when the server's StatusUpdate reports our HP hit 0, stop
            // transmitting (no more Action/AttackRequest). makeDecision() already returns IDLE for a
            // dead bot; this also surfaces the event + resumes cleanly if HP comes back (respawn).
            if (!player.getCombatAI().isBotAlive())
            {
                if (wasAlive)
                {
                    System.out.println("[CombatLoop] DEAD — server StatusUpdate shows self HP 0; pausing combat sends");
                    wasAlive = false;
                }
                Thread.sleep(LOOP_SLEEP_MS);
                continue;
            }
            if (!wasAlive)
            {
                System.out.println("[CombatLoop] ALIVE — self HP positive again; resuming combat sends");
                wasAlive = true;
            }

            int targetId = player.getCombatAI().getSelectedTargetObjId();

            if (targetId > 0 && (decision.getAction() == CombatDecision.Action.ATTACK
                || decision.getAction() == CombatDecision.Action.ENGAGE_TARGET))
            {
                List<CombatFramePlanner.FrameStep> steps =
                    planner.plan(decision, player.getX(), player.getY(), player.getZ(), targetId);
                int sent = 0;
                for (CombatFramePlanner.FrameStep step : steps)
                {
                    gs.sendGameFrame(step.frame);
                    System.out.println("[CombatLoop] SENT opcode=0x" + String.format("%02X", step.getOpcode())
                        + " target=" + targetId + " action=" + decision.getAction()
                        + " hostileCount=" + gs.getPacketLogger().getHostileEntityCount());
                    sent++;
                    if (step.delayAfterMs > 0)
                    {
                        Thread.sleep(step.delayAfterMs);
                    }
                }
                sentActions += sent;
                System.out.println("[CombatLoop] ENGAGED target=" + targetId + " action=" + decision.getAction());
            }
            else
            {
                if (System.currentTimeMillis() - lastDiag > 3000)
                {
                    PacketLogger.EntityInfo nearest = gs.getPacketLogger().findNearestHostile(
                        player.getX(), player.getY(), player.getZ(), Integer.MAX_VALUE);
                    int nearestDist = nearest == null ? -1
                        : (int) Math.sqrt(Math.pow(nearest.x - player.getX(), 2)
                            + Math.pow(nearest.y - player.getY(), 2));
                    System.out.println("[CombatLoop] no-target pos=(" + player.getX() + "," + player.getY()
                        + "," + player.getZ() + ") hostiles=" + gs.getPacketLogger().getHostileEntityCount()
                        + " nearestObj=" + (nearest == null ? "none" : nearest.objectId)
                        + " dist=" + nearestDist);
                    lastDiag = System.currentTimeMillis();
                }
                Thread.sleep(LOOP_SLEEP_MS);
            }
        }

        System.out.println("[CombatLoop] LIVE COMBAT LOOP COMPLETE sentActions=" + sentActions
            + " targetsTracked=" + gs.getPacketLogger().getEntityCount());
        gs.disconnect();
        login.disconnect();
        System.exit(sentActions > 0 ? 0 : 4);
    }
}

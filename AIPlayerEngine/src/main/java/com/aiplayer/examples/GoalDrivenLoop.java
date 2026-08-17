package com.aiplayer.examples;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.aiplayer.engine.AIPlayer;
import com.aiplayer.engine.ActivityScheduler;
import com.aiplayer.engine.CombatDecision;
import com.aiplayer.engine.CombatFramePlanner;
import com.aiplayer.engine.GameServerClient;
import com.aiplayer.engine.GoalTree;
import com.aiplayer.engine.LiveFeedbackBridge;
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.protocol.PacketCodec;

/**
 * Stream G (G-Live) LIVE driver — goal-aware, scheduler-aware, feedback-wired live loop.
 *
 * <p>Completes the G-Live gap: Streams D/E/F proved their outcome chains with unit tests, but
 * nothing drove them live. This mirrors the proven {@code CombatLoop} handshake/frame path and adds
 * the Stream G wiring on top:
 * <pre>
 *   L2JProtocol (login-server auth) -&gt; GameServerClient.connectAndEnterWorld -&gt; startReader()
 *     -&gt; LiveFeedbackBridge (fires D/E/F onKill/onLevelUp/onDeath/respawn on real packet deltas)
 *     -&gt; every tick: bridge.handleTick()
 *       -&gt; activityScheduler.nextActivity()  (rotate GRIND/MERCHANT/QUEST/SOCIAL/REST)
 *       -&gt; goalTree.selectActiveGoal()       (SURVIVE &gt; ACTIVE_QUEST &gt; GRIND_XP &gt; EXPLORE ...)
 *     -&gt; CombatAI.makeDecision() (personality/emotion-biased, via getEffectiveEngageDistance)
 *       -&gt; RangedKiteAI.applyKiteBehavior() may override with a FLEE kite decision
 *     -&gt; CombatFramePlanner -&gt; sendGameFrame (real Action 0x04 / AttackRequest 0x0A / Move 0x01)
 * </pre>
 * Prints grep-able markers: {@code [GoalLoop] ACTIVE_GOAL=..}, {@code [GoalLoop] ACTIVITY=..},
 * {@code [GoalLoop] MOVE opcode=0x01 ..}, {@code [GoalLoop] LIVE GOAL LOOP COMPLETE}.
 *
 * <p>Usage: {@code java -cp target/classes com.aiplayer.examples.GoalDrivenLoop &lt;account&gt;
 * &lt;pass&gt; [host] [gamePort] [charId] [charSlot] [seedX] [seedY] [seedZ] [seconds]}
 */
@Deprecated // S10-T06: superseded by examples.FleetPlay
public class GoalDrivenLoop
{
    private static final Logger LOGGER = Logger.getLogger(GoalDrivenLoop.class.getName());

    private static final int LOGIN_PORT = 2106;
    private static final long LOOP_SLEEP_MS = 500;

    // Stream G (G-Live, 2026-08-08): live goal-driven MOVEMENT. When the selected goal is EXPLORE
    // (or GRIND perceives an empty/dead zone), walk to a fresh nearby offset via the B8-proven
    // MoveToLocation(0x01) wire format (PacketCodec.encodeMoveToLocation). Geo-aware escape routing
    // is a documented follow-up (StreamGDisposition §4), not required for a demonstrative movement.
    private static final int ROAM_STEP = 200;
    private static final long ROAM_COOLDOWN_MS = 8000;

    public static void main(String[] args) throws Exception
    {
        Logger.getLogger("com.aiplayer").setLevel(Level.INFO);

        String account = args.length > 0 ? args[0] : "ai_combat_01";
        String password = args.length > 1 ? args[1] : "ai123pass";
        String host = args.length > 2 ? args[2] : "127.0.0.1";
        int gamePort = args.length > 3 ? Integer.parseInt(args[3]) : 7777;
        int charId = args.length > 4 ? Integer.parseInt(args[4]) : 2;
        int charSlot = args.length > 5 ? Integer.parseInt(args[5]) : 0;
        int seedX = args.length > 6 ? Integer.parseInt(args[6]) : -82759;
        int seedY = args.length > 7 ? Integer.parseInt(args[7]) : 250149;
        int seedZ = args.length > 8 ? Integer.parseInt(args[8]) : -3600;
        int seconds = args.length > 9 ? Integer.parseInt(args[9]) : 20;

        AIPlayer player = new AIPlayer(account, 100, 1, 0);
        player.setPosition(seedX, seedY, seedZ);
        L2JProtocol login = new L2JProtocol(player, host, LOGIN_PORT, gamePort);
        System.out.println("[GoalLoop] login...");
        boolean ok = login.connectAndLogin(account, password, charId);
        if (!ok)
        {
            System.out.println("[GoalLoop] FAIL login");
            System.exit(2);
        }
        player.setLoggedIn(true);
        player.setCharacterId(charId);

        GameServerClient gs = new GameServerClient(player, host, gamePort);
        boolean entered = gs.connectAndEnterWorld(login, account, charSlot);
        if (!entered)
        {
            System.out.println("[GoalLoop] FAIL enter-world");
            login.disconnect();
            System.exit(3);
        }
        gs.startReader();

        // Attach the LIVE reader's buffer to CombatAI (same as CombatLoop, slice 5).
        player.getCombatAI().setPacketLogger(gs.getPacketLogger());
        gs.getPacketLogger().setSelfObjectId(charId);

        // Stream G (G-Live): the bridge that turns real packet deltas into D/E/F outcome hooks.
        LiveFeedbackBridge bridge = new LiveFeedbackBridge(player, gs.getPacketLogger());


        CombatFramePlanner planner = new CombatFramePlanner();
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        int sentActions = 0;
        int sentMoves = 0;
        long lastMoveMs = 0;
        long lastDiag = 0;

        System.out.println("[GoalLoop] in world at (" + seedX + "," + seedY + "," + seedZ
            + ") — running goal-aware live loop for " + seconds + "s");


        while (System.currentTimeMillis() < deadline)
        {
            // G-Live: fire D/E/F outcome hooks from real packet state transitions.
            if (bridge.handleTick())
            {
                System.out.println("[GoalLoop] FEEDBACK fired (kill/level/death/respawn) level="
                    + gs.getPacketLogger().getLevel() + " hostiles="
                    + gs.getPacketLogger().getHostileEntityCount());
            }

            // Follow real movement when CharInfo has been parsed.
            int px = gs.getPacketLogger().getPlayerX();
            int py = gs.getPacketLogger().getPlayerY();
            int pz = gs.getPacketLogger().getPlayerZ();
            if (px != 0 || py != 0 || pz != 0)
            {
                player.setPosition(px, py, pz);
            }

            // G-Live: rotate activity via ActivityScheduler (Stream E task 88).
            ActivityScheduler.Activity activity = player.getActivityScheduler().nextActivity();
            // G-Live: select the ONE active goal before deciding (Stream D task 65/68/69).
            GoalTree.ShortTermGoal goal = player.getGoalTree().selectActiveGoal();
            if (System.currentTimeMillis() - lastDiag > 3000)
            {
                System.out.println("[GoalLoop] ACTIVITY=" + activity + " ACTIVE_GOAL=" + goal
                    + " engageDist=" + player.getCombatAI().getEffectiveEngageDistance()
                    + " hostiles=" + gs.getPacketLogger().getHostileEntityCount());
                lastDiag = System.currentTimeMillis();
            }

            if (!player.getCombatAI().isBotAlive())
            {
                Thread.sleep(LOOP_SLEEP_MS);
                continue;
            }

            CombatDecision decision = player.getCombatAI().makeDecision();

            // G-Combat: RangedKiteAI may override with a FLEE kite decision when low HP + far.
            CombatDecision kite = player.getCombatAI().applyKiteBehavior();
            if (kite != null)
            {
                decision = kite;
                System.out.println("[GoalLoop] KITING — low HP + beyond safe range (RangedKiteAI)");
            }

            int targetId = player.getCombatAI().getSelectedTargetObjId();

            // Stream G (G-Live, 2026-08-08): LIVE goal-driven MOVEMENT. When the active goal is
            // EXPLORE, or GRIND perceives no hostiles at all (dead zone), walk to a fresh nearby
            // offset instead of idling. Uses the B8-proven MoveToLocation(0x01) frame so the server
            // physically walks the character (CHAR_MOVE packets + DB x/y/z change on logout).
            boolean exploreRoam = goal == GoalTree.ShortTermGoal.EXPLORE;
            boolean deadZoneRoam = goal == GoalTree.ShortTermGoal.GRIND_XP
                && gs.getPacketLogger().getHostileEntityCount() == 0;
            if ((exploreRoam || deadZoneRoam) && targetId <= 0
                && System.currentTimeMillis() - lastMoveMs > ROAM_COOLDOWN_MS)
            {
                int nx = player.getX() + ROAM_STEP;
                int ny = player.getY() + ROAM_STEP;
                gs.sendGameFrame(PacketCodec.encodeMoveToLocation(nx, ny, player.getZ(),
                    player.getX(), player.getY(), player.getZ(), 0));
                System.out.println("[GoalLoop] MOVE opcode=0x01 to (" + nx + "," + ny + "," + player.getZ()
                    + ") goal=" + goal + " hostiles=" + gs.getPacketLogger().getHostileEntityCount());
                sentMoves++;
                lastMoveMs = System.currentTimeMillis();
            }

            if (targetId > 0 && (decision.getAction() == CombatDecision.Action.ATTACK
                || decision.getAction() == CombatDecision.Action.ENGAGE_TARGET
                || decision.getAction() == CombatDecision.Action.FLEE))
            {
                List<CombatFramePlanner.FrameStep> steps =
                    planner.plan(decision, player.getX(), player.getY(), player.getZ(), targetId);
                int sent = 0;
                for (CombatFramePlanner.FrameStep step : steps)
                {
                    gs.sendGameFrame(step.frame);
                    System.out.println("[GoalLoop] SENT opcode=0x" + String.format("%02X", step.getOpcode())
                        + " target=" + targetId + " action=" + decision.getAction());
                    sent++;
                    if (step.delayAfterMs > 0)
                    {
                        Thread.sleep(step.delayAfterMs);
                    }
                }
                sentActions += sent;
                System.out.println("[GoalLoop] ENGAGED target=" + targetId + " action=" + decision.getAction());
            }
            else
            {
                Thread.sleep(LOOP_SLEEP_MS);
            }
        }

        System.out.println("[GoalLoop] LIVE GOAL LOOP COMPLETE sentActions=" + sentActions
            + " sentMoves=" + sentMoves
            + " level=" + gs.getPacketLogger().getLevel());
        gs.disconnect();
        login.disconnect();
        System.exit((sentActions > 0 || sentMoves > 0) ? 0 : 4);
    }
}


package com.aiplayer.examples;

/** MODE: COMPLETE. Reference driver for one AI Player; not a fleet manager. */

import com.aiplayer.net.AIPlayer;
import com.aiplayer.behavior.combat.CombatDecision;
import com.aiplayer.net.GameServerClient;
import com.aiplayer.core.EngineConfig;
import com.aiplayer.core.EngineWiring;
import com.aiplayer.phase0.BotSnapshot;
import com.aiplayer.phase0.Phase0Wiring;
import com.aiplayer.phase0.combat.TargetSelector;
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.protocol.PacketLogger;

import java.util.logging.Logger;
import com.aiplayer.behavior.AIBrain;
import com.aiplayer.behavior.combat.CombatAI;
import com.aiplayer.behavior.combat.CombatFramePlanner;
import com.aiplayer.core.AIPlayerManager;

/**
 * Composes the phase0 modules with the real, proven engine — CombatAI,
 * GameServerClient, PacketLogger — without editing any of them. This is the
 * one file the review's integration brief asked for in place of patching
 * CombatAI/AIPlayer/AIBrain directly.
 *
 * Follows the exact connect/enter-world sequence already proven live in
 * examples/CombatLoop.java. Everything after that point reads state from
 * BotSnapshot (backed by the real PacketLogger) and writes actions through
 * Phase0Wiring (which only sends proven frames).
 *
 * This is a reference driver for one AI Player, not a fleet manager — it
 * demonstrates the correct wiring pattern. AIPlayerManager already handles
 * spawning multiple AI Players; adapting this loop into that manager instead
 * of running standalone is a follow-up task, not done here.
 */
@Deprecated // S10-T06: superseded by examples.FleetPlay
public final class Phase0Driver {
    private static final Logger LOGGER = Logger.getLogger(Phase0Driver.class.getName());
    private static final long TICK_INTERVAL_MS = 300;
    private static long lastAdviceLogMs = 0;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: Phase0Driver <account> <password> <charId> [host] [gamePort]");
            return;
        }
        String account = args[0];
        String password = args[1];
        int charId = Integer.parseInt(args[2]);
        String host = args.length > 3 ? args[3] : "localhost";
        int gamePort = args.length > 4 ? Integer.parseInt(args[4]) : 7777;
        int loginPort = 2106;

        AIPlayer player = new AIPlayer(account, 100, 1, 0);

        L2JProtocol login = new L2JProtocol(player, host, loginPort, gamePort);
        boolean loggedIn = login.connectAndLogin(account, password, charId);
        if (!loggedIn) {
            LOGGER.severe("[Phase0Driver] LoginServer auth failed for " + account);
            return;
        }

        GameServerClient gs = new GameServerClient(player, host, gamePort);
        boolean entered = gs.connectAndEnterWorld(login, account, 0);
        if (!entered) {
            LOGGER.severe("[Phase0Driver] GameServer enter-world failed for " + account);
            return;
        }
        gs.startReader();

        PacketLogger logger = gs.getPacketLogger();
        player.getCombatAI().setPacketLogger(logger);
        logger.setSelfObjectId(charId);

        Phase0Wiring wiring = new Phase0Wiring(gs, account);
        TargetSelector targetSelector = new TargetSelector(account, logger.getLevel());
        EngineWiring phase0 = player.getCombatAI().getPhase0Integration();

        LOGGER.info("[Phase0Driver] " + account + " entered world, starting decision loop");

        while (true) {
            BotSnapshot snapshot = BotSnapshot.from(account, logger);

            String advice = phase0 != null ? phase0.inventoryAdvice(snapshot) : null;
            if (advice != null && (System.currentTimeMillis() - lastAdviceLogMs) > 60_000) {
                LOGGER.info("[Phase0Driver] " + account + " " + advice);
                lastAdviceLogMs = System.currentTimeMillis();
            }

            if (snapshot.hpCurrent <= 0) {
                String deathNote = phase0 != null && EngineConfig.getInstance().isDeathRecoveryEnabled()
                    ? phase0.deathRecoveryStatus() : null;
                LOGGER.info("[Phase0Driver] " + account + " STATE dead, pausing actions"
                    + (deathNote != null ? " — " + deathNote : ""));
                Thread.sleep(TICK_INTERVAL_MS * 10);
                continue;
            }

            CombatDecision decision = player.getCombatAI().makeDecision();
            LOGGER.fine("[Phase0Driver] " + account + " STATE hp=" + snapshot.hpCurrent + "/" + snapshot.hpMax
                        + " decision=" + decision.getAction());

            int targetObjId = 0;
            String targetIdStr = decision.getTargetId();
            if (targetIdStr != null) {
                try {
                    targetObjId = Integer.parseInt(targetIdStr);
                } catch (NumberFormatException ignored) {
                    // CombatAI's targetId isn't always a numeric objId in every action type;
                    // not every decision has a real target to resolve.
                }
            }

            switch (decision.getAction()) {
                case ATTACK:
                case ENGAGE_TARGET:
                    wiring.executeCombat(decision, snapshot.x, snapshot.y, snapshot.z, targetObjId);
                    break;

                case USE_SKILL:
                    // Logged as SKIP-UNPROVEN inside Phase0Wiring/CombatFramePlanner —
                    // no real skill-cast opcode exists yet. See INTEGRATION_GAPS.md.
                    wiring.executeCombat(decision, snapshot.x, snapshot.y, snapshot.z, targetObjId);
                    break;

                case FLEE:
                case RETREAT: {
                    var nearest = snapshot.findNearestHostile(2000, logger);
                    if (nearest != null) {
                        int fx = snapshot.x + (snapshot.x - nearest.x) * 2;
                        int fy = snapshot.y + (snapshot.y - nearest.y) * 2;
                        wiring.moveTo(snapshot.x, snapshot.y, snapshot.z, fx, fy, snapshot.z);
                    }
                    break;
                }

                case IDLE:
                default:
                    // No target yet — ask TargetSelector, matching Task 2's intended
                    // integration point, now reading from BotSnapshot instead of the
                    // old GameStateMirror.
                    int newTarget = (phase0 != null && EngineConfig.getInstance().isTargetingEnabled())
                        ? phase0.selectTarget() : targetSelector.selectTarget();
                    if (newTarget != 0) {
                        wiring.executeCombat(CombatDecision.attackTarget(String.valueOf(newTarget)),
                                              snapshot.x, snapshot.y, snapshot.z, newTarget);
                    }
                    break;
            }

            Thread.sleep(TICK_INTERVAL_MS + (phase0 != null ? phase0.reactionDelayMs() : 0));
        }
    }
}

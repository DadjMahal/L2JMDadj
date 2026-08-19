package com.aiplayer.examples;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.aiplayer.net.AIPlayer;
import com.aiplayer.behavior.combat.CombatDecision;
import com.aiplayer.behavior.combat.CombatFramePlanner;
import com.aiplayer.net.GameServerClient;
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.behavior.combat.CombatAI;

/**
 * MultiPlayerSession — ONE lean JVM running N live AI players on N threads.
 *
 * <p>Optimized for a small box (2 cores): a single process, one {@link AIPlayer} +
 * {@link GameServerClient} + reader thread per account, instead of one JVM per bot.
 * Each player runs "session slices": login -&gt; enter world -&gt; real combat loop
 * (CombatAI.makeDecision -&gt; CombatFramePlanner -&gt; sendGameFrame) -&gt; slice timeout
 * or death-grace -&gt; clean disconnect -&gt; reconnect (fresh spawn, full HP). This keeps
 * level-1 bots producing live behavior even when they die repeatedly in the wolf zone.
 *
 * <p>Grep-able markers: {@code [MP] <account> ...} for login/enter/engage/death/session,
 * plus {@code [MP-SUM] <account> ...} summaries.
 *
 * <p>Usage: {@code java -cp target/classes com.aiplayer.examples.MultiPlayerSession
 * [runMinutes] [sliceSeconds]}
 */
@Deprecated // S10-T06: superseded by examples.FleetPlay
public class MultiPlayerSession
{
    private static final long LOOP_SLEEP_MS = 500;
    private static final long DEAD_GRACE_MS = 30_000;
    private static final long RECONNECT_GAP_MS = 4_000;
    private static final long SENT_PRINT_INTERVAL_MS = 5_000;

    /** Roster: account, password, charId(GS objectId), spawn dx, spawn dy offsets. */
    private static final String[][] ROSTER = {
        {"ai_combat_01",   "ai123pass", "2",  "0",  "0"},
        {"ai_combat_02",   "ai123pass", "3",  "-30", "-25"},
        {"ai_explorer_01", "ai123pass", "4",  "25", "-40"},
        {"ai_merchant_01", "ai123pass", "5",  "-40", "20"},
        {"ai_quest_01",    "ai123pass", "6",  "40", "-15"},
        {"ai_quest_02",    "ai123pass", "7",  "-20", "40"},
        {"ai_social_01",   "ai123pass", "8",  "55", "5"},
        {"ai_combat_03",   "ai123pass", "9",  "-55", "-10"},
        {"ai_combat_04",   "ai123pass", "10", "60", "-30"},
        {"ai_combat_05",   "ai123pass", "11", "-10", "60"},
        {"ai_combat_06",   "ai123pass", "12", "15", "65"},
        {"ai_merchant_02", "ai123pass", "17", "-60", "35"},
    };

    private static final String HOST = "127.0.0.1";
    private static final int LOGIN_PORT = 2106;
    private static final int GAME_PORT = 7777;
    // Wolf pack cluster (live NPC_INFO 2026-08-09): spawn near the densest hostile spawns
    // (-83501,251494 / -84399,251764 / -84204,253037 ...) so targets are inside engage range.
    private static final int WX = -84400;
    private static final int WY = 251500;
    private static final int WZ = -3596;

    // Per-account stats: [0]=sessions [1]=reserved [2]=actions [3]=reserved
    // [4]=deaths [5]=engages [6]=levelUps [7]=onlineSec [8]=loginFails [9]=events
    private static final ConcurrentHashMap<String, long[]> STATS = new ConcurrentHashMap<>();

    private static long[] stats(String account)
    {
        return STATS.computeIfAbsent(account, k -> new long[10]);
    }

    private static void bump(String account, int idx)
    {
        stats(account)[idx]++;
    }

    private static void add(String account, int idx, long value)
    {
        stats(account)[idx] += value;
    }

    public static void main(String[] args) throws Exception
    {
        Logger.getLogger("com.aiplayer").setLevel(Level.INFO);

        int runMinutes = args.length > 0 ? Integer.parseInt(args[0]) : 165;
        int sliceSeconds = args.length > 1 ? Integer.parseInt(args[1]) : 600;
        long runEnd = System.currentTimeMillis() + runMinutes * 60_000L;
        long sliceMs = sliceSeconds * 1000L;

        System.out.println("[MP] MultiPlayerSession starting: " + ROSTER.length + " players, "
            + runMinutes + " min, slice=" + sliceSeconds + "s, zone=(" + WX + "," + WY + "," + WZ + ")");

        CountDownLatch latch = new CountDownLatch(ROSTER.length);
        for (final String[] row : ROSTER)
        {
            Thread t = new Thread(() -> runPlayer(row, runEnd, sliceMs, latch), "mp-" + row[0]);
            t.setDaemon(true);
            t.start();
        }

        long lastSum = 0;
        while (System.currentTimeMillis() < runEnd + 30_000)
        {
            Thread.sleep(60_000);
            if (System.currentTimeMillis() - lastSum >= 60_000)
            {
                printSummary();
                lastSum = System.currentTimeMillis();
            }
        }
        printSummary();
        latch.await(20, TimeUnit.SECONDS);
        System.out.println("[MP] MultiPlayerSession END");
        System.exit(0);
    }

    private static void runPlayer(String[] row, long runEnd, long sliceMs, CountDownLatch latch)
    {
        try
        {
            String account = row[0];
            String pass = row[1];
            int charId = Integer.parseInt(row[2]);
            int seedX = WX + Integer.parseInt(row[3]);
            int seedY = WY + Integer.parseInt(row[4]);

            while (System.currentTimeMillis() < runEnd)
            {
                long sliceOk = playSlice(account, pass, charId, seedX, seedY, sliceMs, runEnd);
                if (sliceOk == 0)
                {
                    Thread.sleep(10_000); // login never succeeded; back off
                }
                else
                {
                    Thread.sleep(RECONNECT_GAP_MS);
                }
            }
        }
        catch (Throwable t)
        {
            System.out.println("[MP] " + row[0] + " FATAL " + t);
        }
        finally
        {
            latch.countDown();
        }
    }

    /** Returns total online ms for the slice (0 if never got in world). */
    private static long playSlice(String account, String pass, int charId,
        int seedX, int seedY, long sliceMs, long runEnd)
    {
        long onlineMs = 0;
        try
        {
            AIPlayer player = new AIPlayer(account, 100, 1, 0);
            player.setPosition(seedX, seedY, WZ);

            L2JProtocol login = new L2JProtocol(player, HOST, LOGIN_PORT, GAME_PORT);
            boolean ok = login.connectAndLogin(account, pass, charId);
            if (!ok)
            {
                bump(account, 8);
                login.disconnect();
                System.out.println("[MP] " + account + " LOGIN-FAIL");
                return 0;
            }
            player.setLoggedIn(true);
            player.setCharacterId(charId);

            GameServerClient gs = new GameServerClient(player, HOST, GAME_PORT);
            boolean entered = gs.connectAndEnterWorld(login, account, 0);
            if (!entered)
            {
                bump(account, 8);
                login.disconnect();
                System.out.println("[MP] " + account + " ENTER-FAIL");
                return 0;
            }
            gs.startReader();
            player.getCombatAI().setPacketLogger(gs.getPacketLogger());
            gs.getPacketLogger().setSelfObjectId(charId);
            bump(account, 0); // sessions

            System.out.println("[MP] " + account + " IN-WORLD at (" + seedX + "," + seedY + "," + WZ + ")");

            CombatFramePlanner planner = new CombatFramePlanner();
            long sliceStart = System.currentTimeMillis();
            long sliceEnd = Math.min(sliceStart + sliceMs, runEnd);
            boolean wasAlive = true;
            long deathSince = 0;
            long lastSentPrint = 0;
            long lastDiagPrint = 0;
            int lastLevel = 1;

            while (System.currentTimeMillis() < sliceEnd && System.currentTimeMillis() < runEnd)
            {
                int px = gs.getPacketLogger().getPlayerX();
                int py = gs.getPacketLogger().getPlayerY();
                int pz = gs.getPacketLogger().getPlayerZ();
                // Sanity guard (2026-08-09, multi-player run): PacketLogger.parseCharInfo overwrites
                // playerX/Y/Z from ANY CharInfo packet (not filtered by selfObjectId). With many bots
                // online that corrupts our origin (seen: pos=(250149,-3600,0)) which breaks enemy
                // distance detection. Our bots never move this run, so seed position IS the real one;
                // only adopt parsed coords that are plausibly our own spawn cell.
                boolean sane = Math.abs(px - seedX) < 4000 && Math.abs(py - seedY) < 4000
                    && Math.abs(pz - WZ) < 4000;
                if (px != 0 && sane)
                {
                    player.setPosition(px, py, pz);
                }

                int level = gs.getPacketLogger().getLevel();
                if (level > lastLevel)
                {
                    bump(account, 6); // levelUp
                    System.out.println("[MP] " + account + " LEVEL-UP level=" + level);
                    lastLevel = level;
                }

                if (!player.getCombatAI().isBotAlive())
                {
                    if (wasAlive)
                    {
                        wasAlive = false;
                        deathSince = System.currentTimeMillis();
                        bump(account, 4); // deaths
                        System.out.println("[MP] " + account + " DEAD session="
                            + stats(account)[0] + " onlineMs=" + onlineMs);
                    }
                    if (System.currentTimeMillis() - deathSince > DEAD_GRACE_MS)
                    {
                        break; // end slice -> relog fresh with full HP
                    }
                    Thread.sleep(LOOP_SLEEP_MS);
                    continue;
                }
                if (!wasAlive)
                {
                    wasAlive = true;
                    System.out.println("[MP] " + account + " ALIVE-AGAIN");
                }

                CombatDecision decision = player.getCombatAI().makeDecision();
                CombatDecision kite = player.getCombatAI().applyKiteBehavior();
                if (kite != null)
                {
                    decision = kite;
                }

                int targetId = player.getCombatAI().getSelectedTargetObjId();
                long nowDiag = System.currentTimeMillis();
                if (nowDiag - lastDiagPrint > 10_000)
                {
                    lastDiagPrint = nowDiag;
                    System.out.println("[MP] " + account + " DIAG action=" + decision.getAction()
                        + " target=" + targetId
                        + " pos=(" + player.getX() + "," + player.getY() + "," + player.getZ() + ")"
                        + " hostiles=" + gs.getPacketLogger().getHostileEntityCount()
                        + " alive=" + player.getCombatAI().isBotAlive());
                }
                if (targetId > 0 && (decision.getAction() == CombatDecision.Action.ATTACK
                    || decision.getAction() == CombatDecision.Action.ENGAGE_TARGET))
                {
                    List<CombatFramePlanner.FrameStep> steps =
                        planner.plan(decision, player.getX(), player.getY(), player.getZ(), targetId);
                    int sent = 0;
                    for (CombatFramePlanner.FrameStep step : steps)
                    {
                        gs.sendGameFrame(step.frame);
                        sent++;
                        if (step.delayAfterMs > 0)
                        {
                            Thread.sleep(step.delayAfterMs);
                        }
                    }
                    add(account, 2, sent);
                    add(account, 5, 1);
                    if (System.currentTimeMillis() - lastSentPrint > SENT_PRINT_INTERVAL_MS)
                    {
                        lastSentPrint = System.currentTimeMillis();
                        System.out.println("[MP] " + account + " ENGAGE target=" + targetId
                            + " actions=" + stats(account)[2]
                            + " hostileCount=" + gs.getPacketLogger().getHostileEntityCount());
                    }
                }
                else
                {
                    Thread.sleep(LOOP_SLEEP_MS);
                }
                onlineMs += LOOP_SLEEP_MS;
            }

            add(account, 7, onlineMs / 1000);
            System.out.println("[MP] " + account + " END-SLICE onlineSec=" + (onlineMs / 1000)
                + " level=" + gs.getPacketLogger().getLevel()
                + " hostiles=" + gs.getPacketLogger().getHostileEntityCount());
            gs.disconnect();
            login.disconnect();
        }
        catch (Throwable t)
        {
            System.out.println("[MP] " + account + " SLICE-ERR " + t);
        }
        return onlineMs;
    }

    private static void printSummary()
    {
        StringBuilder sb = new StringBuilder("\n[MP-SUM] == snapshot ==\n");
        for (String[] row : ROSTER)
        {
            long[] s = stats(row[0]);
            sb.append("[MP-SUM] ").append(row[0])
              .append(" sessions=").append(s[0])
              .append(" engages=").append(s[5])
              .append(" actions=").append(s[2])
              .append(" deaths=").append(s[4])
              .append(" levelUps=").append(s[6])
              .append(" onlineSec=").append(s[7])
              .append(" loginFails=").append(s[8])
              .append('\n');
        }
        System.out.println(sb);
    }
}
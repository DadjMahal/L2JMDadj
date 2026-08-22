package com.aiplayer.examples;

/** MODE: COMPLETE. Real fleet launcher + live web dashboard. STEP 1C: quest-NPC navigation is wired —
 *  the BotPlayController authors the goal each idle tick and the session routes toward the chosen
 *  destination (quest NPC / acquire / hunt) via the proven hop machinery (see ZoneRouter.routeTo).
 *
 *  EP-4: this is now a THIN launcher. The per-bot session machine lives in com.aiplayer.core.BotSession,
 *  arg/config parsing in com.aiplayer.core.FleetConfig, dashboard boot in com.aiplayer.web.DashboardBoot,
 *  and the live per-bot row in com.aiplayer.core.BotInfo. main = parse -> provision -> boot -> spawn. */

import java.util.concurrent.ConcurrentHashMap;

import com.aiplayer.core.BotInfo;
import com.aiplayer.core.BotSession;
import com.aiplayer.core.FleetConfig;
import com.aiplayer.knowledge.PlayerRace;
import com.aiplayer.monitor.AIMonitorDashboard;
import com.aiplayer.web.DashboardBoot;
import com.aiplayer.web.EventRing;
import com.aiplayer.web.FleetMetrics;
import com.aiplayer.web.HistoryRing;

import com.aiplayer.behavior.lifecycle.FleetDrain;
import com.aiplayer.behavior.lifecycle.FleetShutdown;

/**
 * Launches a fleet of real AI Players against the live Interlude stack and exposes a light
 * web dashboard. Each bot runs the same proven login -> enter-world -> decision loop
 * as EngineDriver, on its own thread:
 *   - combat: target attackables via TargetSelector and execute the proven
 *     Action/AttackRequest frames (the server walks the char into range),
 *   - level-up through kills (= the char's exp rises in gameserver.characters),
 *   - idle: "mostly smart" random wander around the current position (passes NPCs).
 * Dashboard: GET / (HTML, auto-refresh), /json (live stats), /report (text) served by DashboardBoot;
 * every JSON route (/api/v1/*, /api/players, /api/landmarks) is wired through com.aiplayer.web.DashboardApi.
 *
 * Usage: FleetPlay [count] [host] [gamePort] [loginPort] [dashPort] [movement] [accountPrefix]
 *                  [charIdBase] [races]   (see FleetConfig.parse for exact semantics)
 */
public final class FleetPlay
{
    public static void main(String[] args) throws Exception
    {
        FleetConfig cfg = FleetConfig.parse(args); // prints the race-mode line when arg 9 present
        cfg.applyRuntimeOverrides();               // TIM-001: "movement" forces engine.movement ON

        System.out.println("[FleetPlay] launching " + cfg.count + " bots vs " + cfg.host + ":" + cfg.gamePort
            + " dashboard=http://localhost:" + cfg.dashPort);

        // Shared fleet structures: one instance set, written by the bot sessions, read by the dashboard.
        ConcurrentHashMap<String, BotInfo> bots = new ConcurrentHashMap<>();
        EventRing events = new EventRing();
        HistoryRing history = new HistoryRing();
        FleetMetrics metrics = new FleetMetrics(System.currentTimeMillis());

        DashboardBoot.boot(cfg.dashPort, cfg.count, bots, events, history, metrics);
        AIMonitorDashboard.getInstance().start();

        // EB-10: read the previous exit marker so this boot knows whether the last stop was
        // graceful (resume fully) or a crash (guarded resume). The marker file is /tmp, never
        // committed, written by the shutdown hook below (or left absent on a hard kill).
        FleetShutdown resume = readExitMarker();
        System.out.println("[FleetPlay] last exit: " + resume.exitLabel()
            + (resume.reason.isEmpty() ? "" : " (" + resume.reason + ")")
            + (resume.resumeFully() ? " — resuming fully" : " — guarded resume"));

        Thread[] botThreads = spawnFleet(cfg, bots, events, history, metrics);
        clearExitMarker();

        // EB-10: graceful fleet shutdown. On SIGTERM/SIGINT the JVM runs this hook: drain each
        // bot thread at its next safe boundary (interrupt; BotSession.run checks it each loop),
        // wait a bounded time, then record a GRACEFUL marker so keep_alive.sh / the next boot
        // can restart cleanly instead of assuming a crash.
        final Thread[] fleetThreads = botThreads;
        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().name("fleet-drain-hook").unstarted(() ->
        {
            FleetShutdown fs = FleetShutdown.idle().requestDrain("signal");
            writeExitMarker(fs); // DRAINING first: a kill mid-drain leaves a CRASHED-on-next-boot marker
            int drained = FleetDrain.drain(fleetThreads, FleetDrain.DEFAULT_WAIT_MS);
            fs = fs.completeDrain();
            System.out.println("[FleetPlay] drained " + drained + "/" + fleetThreads.length
                + " bot threads; exit=" + fs.exitLabel());
            writeExitMarker(fs);
        }));

        synchronized (FleetPlay.class)
        {
            while (true)
            {
                FleetPlay.class.wait();
            }
        }
    }

    /**
     * EP-7: one virtual thread per bot session (Thread.ofVirtual — blocking-socket loops are the
     * virtual-thread design point; fleet size no longer caps at platform-thread counts). Returns
     * the threads so tests can assert they are virtual/interruptible.
     */
    static Thread[] spawnFleet(FleetConfig cfg, ConcurrentHashMap<String, BotInfo> bots,
                               EventRing events, HistoryRing history, FleetMetrics metrics)
    {
        Thread[] threads = new Thread[cfg.count];
        for (int i = 1; i <= cfg.count; i++)
        {
            final String account = cfg.accountPrefix + String.format("%02d", i);
            final int charId = cfg.charIdBase + i - 1; // ai_combat_01 -> 100000 ... ai_combat_05 -> 100004
            final PlayerRace race = (cfg.raceRotation.length > 0)
                ? cfg.raceRotation[(i - 1) % cfg.raceRotation.length] : PlayerRace.HUMAN;
            BotInfo info = new BotInfo(account, charId);
            bots.put(account, info);
            threads[i - 1] = Thread.ofVirtual().name("bot-" + account)
                .start(new BotSession(account, charId, info, cfg.host, cfg.loginPort, cfg.gamePort, race,
                    bots, events, history, metrics));
        }
        return threads;
    }
// ================================================================
    // EB-10 — exit-marker file (CLI-layer IO; FleetShutdown stays pure).
    // ================================================================

    /** Marker location: /tmp, never committed, never a secret. */
    static final java.nio.file.Path EXIT_MARKER = java.nio.file.Path.of("/tmp/l24lude_fleet_last_exit");

    /** Read + interpret the last exit marker. Absent → IDLE (fresh/unknown). */
    static FleetShutdown readExitMarker()
    {
        try
        {
            if (!java.nio.file.Files.exists(EXIT_MARKER))
            {
                return FleetShutdown.idle();
            }
            java.util.List<String> lines = java.nio.file.Files.readAllLines(EXIT_MARKER);
            String label = lines.isEmpty() ? "" : lines.get(0).trim();
            String reason = lines.size() > 1 ? lines.get(1).trim() : "";
            return FleetShutdown.fromLabel(label, reason);
        }
        catch (java.io.IOException e)
        {
            return FleetShutdown.idle(); // unreadable marker — don't block boot on it
        }
    }

    /** Persist the exit marker so the next boot + keep_alive see a GRACEFUL vs CRASHED. */
    static void writeExitMarker(FleetShutdown fs)
    {
        try
        {
            java.nio.file.Files.write(EXIT_MARKER,
                (fs.exitLabel() + "\n" + fs.reason).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        catch (java.io.IOException e)
        {
            System.out.println("[FleetPlay] could not write exit marker: " + e.getMessage());
        }
    }

    /** Clear the marker once a new boot has read it (opened fresh). */
    static void clearExitMarker()
    {
        try
        {
            java.nio.file.Files.deleteIfExists(EXIT_MARKER);
        }
        catch (java.io.IOException e)
        {
            // not fatal — stale marker is re-read next boot
        }
    }
}

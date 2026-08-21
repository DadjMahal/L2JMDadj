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

        spawnFleet(cfg, bots, events, history, metrics);

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
}

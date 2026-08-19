package com.aiplayer.examples;

/** MODE: COMPLETE. Real fleet launcher + live web dashboard. STEP 1C: quest-NPC navigation is wired —
 *  the BotPlayController authors the goal each idle tick and FleetPlay routes toward the chosen
 *  destination (quest NPC / acquire / hunt) via the proven hop machinery (see ZoneRouter.routeTo). */

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.aiplayer.engine.AIConfiguration;
import com.aiplayer.engine.AIPlayer;
import com.aiplayer.engine.CombatDecision;
import com.aiplayer.engine.GameServerClient;
import com.aiplayer.engine.Phase0Config;
import com.aiplayer.monitor.AIMonitorDashboard;
import com.aiplayer.phase0.BotSnapshot;
import com.aiplayer.phase0.Phase0Wiring;
import com.aiplayer.phase0.combat.TargetSelector;
import com.aiplayer.phase0.guide.PlayerRace;
import com.aiplayer.phase0.movement.HopGate;
import com.aiplayer.phase0.movement.MoveTelemetry;
import com.aiplayer.phase0.movement.RelocationPlanner;
import com.aiplayer.phase0.movement.ZoneRouter;
import com.aiplayer.phase0.movement.ZoneRouter.RouteGoal;
import com.aiplayer.phase0.play.AcquireCooldown;
import com.aiplayer.phase0.play.BotPlayController;
import com.aiplayer.phase0.play.BotPlayController.Hostile;
import com.aiplayer.phase0.play.BotPlayController.PlayContext;
import com.aiplayer.phase0.play.GoalAction;
import com.aiplayer.phase0.play.GoalDecision;
import com.aiplayer.phase0.play.PlayerGoal;
import com.aiplayer.phase0.play.QuestDialogDriver;
import com.aiplayer.phase0.play.QuestDialogDriver.Objective;
import com.aiplayer.phase0.play.QuestDialogDriver.QuestDialog;
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.protocol.PacketLogger;
import com.aiplayer.protocol.PacketLogger.EntityInfo;
import com.aiplayer.web.DashboardApi;
import com.aiplayer.web.EventRing;
import com.aiplayer.web.FleetMetrics;
import com.aiplayer.web.HistoryRing;

/**
 * Launches a fleet of real AI Players against the live Interlude stack and exposes a light
 * web dashboard. Each bot runs the same proven login -> enter-world -> decision loop
 * as Phase0Driver, on its own thread:
 *   - combat: target attackables via TargetSelector and execute the proven
 *     Action/AttackRequest frames (the server walks the char into range),
 *   - level-up through kills (= the char's exp rises in gameserver.characters),
 *   - idle: "mostly smart" random wander around the current position (passes NPCs).
 * Dashboard: GET / (HTML, auto-refresh), /json (live stats), /report (text) served by FleetPlay;
 * every JSON route (/api/v1/*, /api/players, /api/landmarks) is wired through com.aiplayer.web.DashboardApi.
 */
public final class FleetPlay
{
    private static final Logger LOGGER = Logger.getLogger(FleetPlay.class.getName());
    /** S1-T07: bot accounts' shared password, overridable via ai.account.password (default same as before). */
    private static String accountPassword()
    {
        return AIConfiguration.getInstance().getProperty("ai.account.password", "ai123pass");
    }
    // S1-T08: live-loop tuning knobs read from AIConfiguration (defaults = the proven live values)
    private static final long TICK_MS = AIConfiguration.getInstance().getLongProperty("bot.tickMs", 300);
    private static final long WANDER_INTERVAL_MS =
        AIConfiguration.getInstance().getLongProperty("bot.wanderIntervalMs", 8000);
    private static final int WANDER_RADIUS =
        AIConfiguration.getInstance().getIntProperty("bot.wanderRadius", 900);
    // STEP 3 gap-close: a combat target closer than this is "in melee reach" (attack normally); farther
    // and the bot advances toward it once per CHASE_INTERVAL_MS. CHASE_HOP is capped well under the server's
    // ~9900u single-move rejection (MoveToLocation.java:156-163) so each chase move persists server-side.
    private static final int CHASE_REACH =
        AIConfiguration.getInstance().getIntProperty("bot.chaseReach", 150);
    private static final int CHASE_HOP =
        AIConfiguration.getInstance().getIntProperty("bot.chaseHop", 4800);
    private static final long CHASE_INTERVAL_MS =
        AIConfiguration.getInstance().getLongProperty("bot.chaseIntervalMs", 1500);
    // STEP 3 follow-up: if a combat target produces NO XP within this budget, the engagement is
    // force-abandoned so the bot re-acquires a farmable target instead of chasing an un-killable
    // or stale one (town NPCs, despawned mobs). 15s ≈ 10 chase-hop intervals.
    private static final long STALE_TARGET_BUDGET_MS =
        AIConfiguration.getInstance().getLongProperty("bot.staleTargetBudgetMs", 15_000);
    // S6-T02: low-level killers are slow, so give fresh bots a longer no-XP budget (30s vs 15s).
    private static long staleBudgetMs(int level)
    {
        long normal = STALE_TARGET_BUDGET_MS;
        long low = AIConfiguration.getInstance().getLongProperty("bot.staleTargetBudgetLowLevelMs", normal * 2);
        return level < 6 ? low : normal;
    }

    /** S6-T03/T07: current HP fraction (1.0 when unknown) used by the survival guards. */
    private static double hpFrac(com.aiplayer.phase0.BotSnapshot s)
    {
        return s.hpMax > 0 ? (double) s.hpCurrent / s.hpMax : 1.0;
    }
    // S6-T03/T06/T07/T10: survival guards — post-retreat regen hold, overwhelm back-off, death-loop guard.
    private static final long REGEN_HOLD_MS =
        AIConfiguration.getInstance().getLongProperty("bot.regenHoldMs", 8000);
    private static final int SURROUND_CAP =
        AIConfiguration.getInstance().getIntProperty("bot.surroundCap", 6);
    private static final int DEATH_GUARD_DEATHS =
        AIConfiguration.getInstance().getIntProperty("bot.deathGuardDeaths", 3);
    private static final long DEATH_GUARD_MS =
        AIConfiguration.getInstance().getLongProperty("bot.deathGuardMs", 90_000);
    // S2-T07: reconnect backoff — 5s base, doubling to 120s max, +jitter, reset on a clean enter-world.
    private static final long RECONNECT_BASE_MS =
        AIConfiguration.getInstance().getLongProperty("bot.reconnectBaseMs", 5000);
    private static final long RECONNECT_MAX_MS =
        AIConfiguration.getInstance().getLongProperty("bot.reconnectMaxMs", 120_000);

    /** Live bot rows live in com.aiplayer.examples.BotInfo (WPT-01 extraction) - shared with DashboardApi. */

    private static final java.util.concurrent.ConcurrentHashMap<String, BotInfo> BOTS =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** WPT-03/04/08 live evidence structures shared by every bot loop + DashboardApi (single writer = bot threads). */
    private static final EventRing EVENTS = new EventRing();
    private static final HistoryRing HISTORY = new HistoryRing();
    private static final FleetMetrics METRICS = new FleetMetrics(System.currentTimeMillis());

    public static void main(String[] args) throws Exception
    {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        String host = args.length > 1 ? args[1] : "127.0.0.1";
        int gamePort = args.length > 2 ? Integer.parseInt(args[2]) : 7777;
        int loginPort = args.length > 3 ? Integer.parseInt(args[3]) : 2106;
        int dashPort = args.length > 4 ? Integer.parseInt(args[4]) : 8080;
        // TIM-001 proof hook: optional 6th arg "movement" force-enables phase0.movement at runtime
        // (never edits config/ai-player.properties; the default remains OFF).
        boolean forceMovement = args.length > 5 && "movement".equalsIgnoreCase(args[5]);
        // Optional 7th/8th args: account prefix + charId base, so an operator can point the launch at a
        // SPECIFIC (e.g. brand-new) account rather than the default ai_combat_01..05 pool. Defaults keep
        // the original behaviour (ai_combat_%02d / charId 100000+).
        String accountPrefix = args.length > 6 ? args[6] : "ai_combat_";
        int charIdBase = args.length > 7 ? Integer.parseInt(args[7]) : 100000;
        // Optional 9th arg: race distribution for the launched bots. "random" -> each bot gets a
        // uniformly random race; a comma list like "ELF,ORC,DWARF" rotates across those races;
        // absent/empty -> every bot HUMAN (preserves the original all-Human-Fighter behaviour).
        PlayerRace[] raceRotation = resolveRaces(args.length > 8 ? args[8] : "");
        if (args.length > 8)
        {
            System.out.println("[FleetPlay] race mode: "
                + ("random".equalsIgnoreCase(args[8]) ? "random per bot" : args[8]));
        }
        if (forceMovement)
        {
            AIConfiguration cfg = AIConfiguration.getInstance();
            cfg.setProperty("phase0.enabled", "true");
            cfg.setProperty("phase0.movement", "true");
            System.out.println("[FleetPlay] phase0.movement FORCED ON for this run (6th arg 'movement')");
        }

        System.out.println("[FleetPlay] launching " + count + " bots vs " + host + ":" + gamePort
            + " dashboard=http://localhost:" + dashPort);
        startDashboard(dashPort, count);
        AIMonitorDashboard.getInstance().start();

        for (int i = 1; i <= count; i++)
        {
            final String account = accountPrefix + String.format("%02d", i);
            final int charId = charIdBase + i - 1; // ai_combat_01 -> 100000 ... ai_combat_05 -> 100004
            final PlayerRace race = (raceRotation.length > 0)
                ? raceRotation[(i - 1) % raceRotation.length] : PlayerRace.HUMAN;
            BotInfo info = new BotInfo(account, charId);
            BOTS.put(account, info);
            new Thread(new BotLoop(account, charId, info, host, loginPort, gamePort, race),
                "bot-" + account).start();
        }

        synchronized (FleetPlay.class)
        {
            while (true)
            {
                FleetPlay.class.wait();
            }
        }
    }

    /**
     * Parse the 9th launcher arg into a race rotation. "random" -> the 5 races shuffled (balanced
     * 10-each over a 50-bot fleet); a comma list like "ELF,ORC,DWARF" rotates across those races;
     * absent/empty -> empty array (callers fall back to all HUMAN, preserving old behaviour).
     */
    private static PlayerRace[] resolveRaces(String spec)
    {
        if (spec == null || spec.trim().isEmpty())
        {
            return new PlayerRace[0];
        }
        if ("random".equalsIgnoreCase(spec.trim()))
        {
            java.util.List<PlayerRace> l = new java.util.ArrayList<>();
            for (PlayerRace r : PlayerRace.values())
            {
                l.add(r);
            }
            java.util.Collections.shuffle(l, new Random());
            return l.toArray(new PlayerRace[0]);
        }
        java.util.List<PlayerRace> out = new java.util.ArrayList<>();
        for (String p : spec.split(","))
        {
            try
            {
                out.add(PlayerRace.valueOf(p.trim().toUpperCase()));
            }
            catch (IllegalArgumentException ignore)
            {
                // unknown race token -> skip
            }
        }
        return out.toArray(new PlayerRace[0]);
    }

    private static final class BotLoop implements Runnable
    {
        private final String account;
        private final int charId;
        private final BotInfo info;
        private final String host;
        private final int loginPort;
        private final int gamePort;
        /** Per-bot race: drives the guide landmark + restock vendor so non-Humans stay in their own zone. */
        private final PlayerRace race;
        private final Random rng;
        // STEP 2: per-session quest-dialog driver state (only used when phase0.quest.npcId is set).
        private boolean questDialogOpen = false;
        private String questLastHtml = null;
        private final Set<String> questSentLinks = new HashSet<>();
        // ACQUIRE-failure cooldown (per-bot, per-session state; reset() at each runSession start). Stops
        // the "re-plan the same geo-unreachable ACQUIRE giver forever" loop — e.g. Wolf Hunt at Gludio,
        // ~148k units across the ocean: after maxUnreachable abandoned goal:acquire:* routes the bot falls
        // back to plain farming for cooldownMs instead of re-issuing the dead ocean-hop. See AcquireCooldown.
        private final AcquireCooldown acquireCooldown = new AcquireCooldown();
        // S2-T07: grows on repeated failures, reset to base after a clean enter-world.
        private long reconnectDelayMs = RECONNECT_BASE_MS;
        // S6-T03/T06/T07/T10: survival-guard state (retreat regen hold, death-loop guard).
        private long regenHoldUntilMs = 0;
        private long deathGuardUntilMs = 0;
        private long lastDeathMs = 0;
        private int recentDeaths = 0;
        // S3-T07: per-bot controller tuning (race for restock, varietySeed for diverse quest picks).
        private final BotPlayController.BotPlayConfig cfg;

        private BotLoop(String account, int charId, BotInfo info, String host, int loginPort, int gamePort,
                        PlayerRace race)
        {
            this.account = account;
            this.charId = charId;
            this.info = info;
            this.host = host;
            this.loginPort = loginPort;
            this.gamePort = gamePort;
            this.race = race != null ? race : PlayerRace.HUMAN;
            this.info.race = this.race.name(); // S9-T05 dashboard race badge/filter
            this.cfg = new BotPlayController.BotPlayConfig(0.25, 400, 2000, 300, 100,
                this.race, Math.abs(account.hashCode()));
            this.rng = new Random(account.hashCode());
        }

        @Override
        public void run()
        {
            while (!Thread.interrupted())
            {
                try
                {
                    runSession();
                }
                catch (Exception e)
                {
                    info.connected = false;
                    info.loggedIn = false;
                    info.state = "disconnected";
                    emit(EventRing.TYPE_DISCONNECT, "reason", e.getClass().getSimpleName());
                    METRICS.noteReconnect();
                    LOGGER.warning("[FleetPlay] " + account + " session ended: " + e.getMessage());
                    try
                    {
                        // S2-T07: exponential backoff + jitter so 50 bots don't stampede the server
                        // on a shared outage; escalate on repeated failures.
                        Thread.sleep(reconnectDelayMs + Math.abs(rng.nextInt() % 2000));
                    }
                    catch (InterruptedException ie)
                    {
                        return;
                    }
                    reconnectDelayMs = Math.min(reconnectDelayMs * 2, RECONNECT_MAX_MS);
                }
            }
        }

        /** QUEST-PRIORITY: is the configured quest giver nearby (< QUEST_PRIORITY_DIST)? */
        private boolean nearQuestNpc(BotSnapshot s, PacketLogger logger, int questNpcId)
        {
            if (questNpcId <= 0)
            {
                return false;
            }
            PacketLogger.EntityInfo giver = logger.findEntityByNpcId(questNpcId);
            if (giver == null)
            {
                return false;
            }
            double dx = s.x - giver.x;
            double dy = s.y - giver.y;
            return Math.sqrt(dx * dx + dy * dy) <= BotPlayController.QUEST_PRIORITY_DIST;
        }

        /**
         * STEP 2: drive an NPC quest dialog directly inside the fleet loop, off by default. Only runs
         * when the controller returns BYPASS at the configured giver (phase0.quest.npcId). Sequence:
         * click the NPC (Action) -> wait for its NpcHtmlMessage -> push the displayed bypass links
         * through {@link QuestDialogDriver}, which returns the ONE next validated command; send it via
         * wiring.bypass and pause until the server shows the next dialog. Never fabricates a command the
         * server did not display and never re-sends a link already sent in this session.
         */
        private void driveQuestDialog(BotSnapshot snapshot, PacketLogger logger, Phase0Wiring wiring,
                                      GoalDecision goal, int questNpcId, String questNameProp)
        {
            PacketLogger.EntityInfo giver = logger.findEntityByNpcId(questNpcId);
            if (giver == null)
            {
                LOGGER.fine("[FleetPlay] " + account + " quest NPC " + questNpcId + " not tracked yet; waiting");
                return;
            }
            Objective objective = (goal != null && goal.goal == PlayerGoal.ACQUIRE)
                ? Objective.ACCEPT : Objective.TURN_IN;
            QuestDialog dialogDef = new QuestDialog(0, questNameProp, objective, "", "");

            if (!questDialogOpen)
            {
                // If this NPC's dialog is already on screen, begin driving it; otherwise click it open.
                if (logger.getLastNpcHtmlOriginObjId() == giver.objectId)
                {
                    questDialogOpen = true;
                    questLastHtml = null;
                    LOGGER.info("[FleetPlay] " + account + " quest dialog on screen for objId=" + giver.objectId);
                }
                else
                {
                    wiring.actionOn(giver.objectId, snapshot.x, snapshot.y, snapshot.z);
                    LOGGER.info("[FleetPlay] " + account + " clicking quest NPC objId=" + giver.objectId);
                }
                return;
            }

            // Dialog open: consume a NEW NpcHtmlMessage from this session, if the server sent one.
            String html = logger.getLastNpcHtml();
            if (html == null || html.equals(questLastHtml))
            {
                return; // no new dialog content yet -> pause for the server's next message
            }
            questLastHtml = html;
            String[] links = PacketLogger.extractBypassLinks(html);
            String next = QuestDialogDriver.next(links, dialogDef, questSentLinks);
            if (next.isEmpty())
            {
                LOGGER.info("[FleetPlay] " + account + " quest dialog: no new validated bypass; pausing");
                return;
            }
            questSentLinks.add(next);
            boolean done = QuestDialogDriver.completes(dialogDef, next);
            wiring.bypass(next);
            LOGGER.info("[FleetPlay] " + account + " quest dialog -> bypass[" + (done ? "done" : "...") + "] "
                + next);
            if (done)
            {
                questDialogOpen = false;
                questSentLinks.clear();
                questLastHtml = null;
                // ACQUIRE-failure cooldown: the dialog BYPASS actually drove an accept/turn-in — real quest
                // progress, so cancel any active cooldown and let the planner author ACQUIRE/QUEST freely.
                acquireCooldown.reset();
                LOGGER.info("[FleetPlay] " + account + " quest dialog session complete (accept/turn-in sent); "
                    + "journal will refresh");
            }
        }

        private void runSession() throws Exception
        {
            AIPlayer player = new AIPlayer(account, 100 + charId % 100, 0, 0); // Human Fighter
            L2JProtocol login = new L2JProtocol(player, host, loginPort, gamePort);
            if (!login.connectAndLogin(account, accountPassword(), charId))
            {
                info.state = "login-failed";
                throw new IllegalStateException("login-server auth failed");
            }
            GameServerClient gs = new GameServerClient(player, host, gamePort);
            if (!gs.connectAndEnterWorld(login, account, 0))
            {
                info.state = "enter-failed";
                throw new IllegalStateException("enter-world failed");
            }
            gs.startReader();
            PacketLogger logger = gs.getPacketLogger();
            player.getCombatAI().setPacketLogger(logger);
            logger.setSelfObjectId(charId);

            Phase0Wiring wiring = new Phase0Wiring(gs, account);
            TargetSelector targetSelector = new TargetSelector(account, logger.getLevel());
            Phase0Config phase0 = Phase0Config.getInstance();
            ZoneRouter zoneRouter = new ZoneRouter(account);
            // STEP 6: idle-relocation decision maker — prefers last-XP / nearest-mate when frozen,
            // real guide-map landmark otherwise, with a consecutive-abandon escape gate.
            RelocationPlanner relocation = new RelocationPlanner(account);
            MoveTelemetry telemetry = MoveTelemetry.getInstance();
            info.connected = true;
            info.loggedIn = true;
            info.packetsRead = gs.packetsRead;   // S2-T04: expose per-bot packet health
            info.idleTimeouts = gs.idleTimeouts;
            emit(EventRing.TYPE_CONNECT, "level", info.level);
            LOGGER.info("[FleetPlay] " + account + " ENTERED WORLD"
                + (phase0.isMovementEnabled() ? " [phase0.movement ON]" : ""));
            reconnectDelayMs = RECONNECT_BASE_MS; // S2-T07: clean enter resets the backoff

            long lastWander = 0;
            long lastRoute = 0;
            // STEP 2: quest-dialog gating. When the operator sets phase0.quest.npcId (>0) the bot
            // treats "being at the giver" as "open its dialog and follow the 1-validated-bypass driver".
            int questNpcId = AIConfiguration.getInstance().getIntProperty("phase0.quest.npcId", 0);
            String questNameProp = AIConfiguration.getInstance().getProperty("phase0.quest.name", "");
            ZoneRouter.RouteGoal activeRoute = null;
            int[] pendingHop = null;
            long hopSentAtMs = 0;
            int hopTimeouts = 0; // TIM-001: consecutive timeouts on the current hop (stuck-hop recovery)
            // ACQUIRE-failure cooldown is per-session run state: a fresh session (reconnect) starts clean.
            acquireCooldown.reset();
            int prevLevel = info.level;
            long prevExp = -1; // EVIDENCE-H5: StatusUpdate-driven per-kill XP-gain trail
            int prevHp = info.hp;
            boolean firstTick = true;
            long lastHistoryMs = 0;
            // STEP 3: gap-close gate. Only chase toward an out-of-melee combat target once per interval
            // (like wander) so a chasing bot re-routes without flooding the server every frame.
            long lastChaseMs = 0;
            // WPT-22: drain newly-parsed SystemMessage / chat broadcasts into the shared event ring
            // (only the count delta is emitted once per tick; message bodies are read back from logger).
            long lastSysCount = logger.getSystemMessageCount();
            long lastChatCount = logger.getChatCount();
            while (true)
            {
                // Broken-pipe / zombie guard: if the game-server reader thread died (EOF / reset /
                // IOException), the socket is gone. Phase0Wiring.send() swallows the write failure, so
                // WITHOUT this check the loop would keep planning HOPs against a dead server for hours
                // (the ~2h CLOSE-WAIT zombie loop in the 2026-08-15 run). Throwing here lets the outer
                // run() reconnect loop (15s sleep -> fresh runSession) rebuild the connection.
                if (!gs.isOpen())
                {
                    info.connected = false;
                    throw new IOException("GS connection lost (reader stopped)");
                }
                BotSnapshot snapshot = BotSnapshot.from(account, logger);
                // Dashboard data layer — feed BotInfo from the REAL PacketLogger state
                // (UserInfo 0x04 / StatusUpdate 0x0E / ValidateLocation 0x61 / ItemList 0x1B).
                info.level = logger.getLevel();
                info.charName = logger.getCharName() != null ? logger.getCharName() : info.account;
                info.exp = logger.getExp();
                info.sp = logger.getSp();
                info.hp = logger.getCurHp();
                info.hpMax = logger.getMaxHp();
                info.mp = logger.getCurMp();
                info.mpMax = logger.getMaxMp();
                info.cp = logger.getCurCp();
                info.cpMax = logger.getMaxCp();
                info.x = logger.getPlayerX();
                info.y = logger.getPlayerY();
                info.z = logger.getPlayerZ();
                info.heading = logger.getPlayerHeading();
                info.load = logger.getCurrentLoad();
                info.maxLoad = logger.getMaxLoad();
                info.weapon = logger.isWeaponEquipped();
                info.adena = logger.getAdena();
                info.invPct = logger.getInventoryUsagePercent();
                info.itemCount = logger.getInventoryItems().size();
                info.packetsRead = gs.packetsRead;   // S2-T04: refresh packet health each tick
                info.idleTimeouts = gs.idleTimeouts;
                info.hopSuccessPct = telemetry.hopSuccessPct(account); // S5-T06
                info.items = topItems(logger);
                info.mobs = logger.getHostileEntityCount();
                info.npcs = logger.getEntityCountTotal();
                info.ents = entitySnapshot(logger);
                // WPT-27: feed the quest journal from the real QUEST_LIST (0x80) parse.
                java.util.List<int[]> ql = logger.getActiveQuestList();
                if (!ql.isEmpty())
                {
                    info.totalQuestCount = ql.size();
                    info.activeQuests = ql.toArray(new int[ql.size()][]);
                    int act = 0;
                    for (int[] q : ql) if (q[1] != 0) act++;
                    info.questCount = act;
                }
                info.lastSeenMs = System.currentTimeMillis();
                AIMonitorDashboard.getInstance().updatePlayerStats(player);

                // TIM-001: feed the movement-evidence harness every tick with the real server-acked
                // position + exp (ValidateLocation 0x61 / CharInfo 0x03 parse in PacketLogger).
                telemetry.recordPosition(account, snapshot.x, snapshot.y, snapshot.z, info.exp);

                // WPT-03/04/08: live evidence emission from the SAME tick data (no fake packets).
                int tickLevel = logger.getLevel();
                if (prevLevel > 0 && tickLevel > prevLevel)
                {
                    emit(EventRing.TYPE_LEVEL_UP, "from", prevLevel, "to", tickLevel);
                }
                prevLevel = tickLevel;
                if (!firstTick && info.hp < prevHp)
                {
                    emit(EventRing.TYPE_DAMAGE, "dmg", prevHp - info.hp, "hp", info.hp);
                }
                firstTick = false;
                prevHp = info.hp;
                // EVIDENCE-H5: emit a human-readable per-kill XP-gain line whenever the server's
                // StatusUpdate EXP for this bot rises (level showed in logs above). This is the engine's
                // packet-level receipt of the same XP the server persisted to gameserver.characters.
                if (prevExp >= 0 && info.exp > prevExp)
                {
                    long gained = info.exp - prevExp;
                    System.out.println("[EVIDENCE-H5] " + account + " EXP +" + gained
                        + " (now " + info.exp + ", level=" + tickLevel + ")");
                    // S9-T06: count the kill (an EXP receipt == the server granted a kill's XP).
                    info.killCount++;
                    // TOP-NOTCH AI: feed the real learning chain — rewardKill -> ReinforcementEngine
                    // -> AdaptiveLearner -> DeepLearning (kills drive what the AI "learns to prefer").
                    String targetName = (info.targetLabel != null && !info.targetLabel.isEmpty())
                        ? info.targetLabel : ("mob#" + info.targetObjId);
                    player.getCombatAI().onKill(targetName, gained);
                    // STEP 6: remember where this bot earned XP (hostiles live there) and clear any
                    // relocation-freeze counter — earning XP proves the char can move/fight here.
                    relocation.recordLastXp(info.x, info.y, info.z);
                    relocation.noteProgress();
                }
                prevExp = info.exp;
                // WPT-22: emit newly-parsed SystemMessage / chat broadcasts (real server text).
                long sysNow = logger.getSystemMessageCount();
                long chatNow = logger.getChatCount();
                if (sysNow > lastSysCount)
                {
                    java.util.List<PacketLogger.SystemMessageEvent> sysmsgs = logger.getSystemMessageEvents();
                    for (int i = 0; i < sysmsgs.size() && lastSysCount < sysNow; i++)
                    {
                        PacketLogger.SystemMessageEvent sm = sysmsgs.get(i);
                        if (sm == null) continue;
                        // EVIDENCE-H5: echo every server SystemMessage to stdout so the persisted run log
                        // carries the server's own per-kill cues (e.g. sysmsg#213 "You have earned X
                        // experience." / adena / level-up) — independent of the DB outcome delta.
                        System.out.println("[EVIDENCE-H5] " + account + " SYSMSG " + sm.msgId + " " + sm.text);
                        java.util.Map<String, Object> d = new java.util.LinkedHashMap<>();
                        d.put("msgId", sm.msgId);
                        d.put("text", sm.text);
                        EVENTS.add(EventRing.TYPE_SYSMSG, account, d);
                        lastSysCount++;
                    }
                }
                lastSysCount = sysNow;
                if (chatNow > lastChatCount)
                {
                    java.util.List<PacketLogger.ChatEvent> chats = logger.getChatEvents();
                    for (int i = 0; i < chats.size() && lastChatCount < chatNow; i++)
                    {
                        PacketLogger.ChatEvent c = chats.get(i);
                        if (c == null) continue;
                        java.util.Map<String, Object> d = new java.util.LinkedHashMap<>();
                        d.put("kind", c.kind);
                        d.put("speaker", c.speaker);
                        d.put("text", c.text);
                        EVENTS.add(EventRing.TYPE_CHAT, account, d);
                        lastChatCount++;
                    }
                }
                lastChatCount = chatNow;
                long nowTick = System.currentTimeMillis();
                if (nowTick - lastHistoryMs >= 2000)
                {
                    HISTORY.register(account, snapshot.x, snapshot.y, snapshot.z,
                        info.level, info.exp, info.hp, info.hpMax);
                    lastHistoryMs = nowTick;
                }
                METRICS.setBotCount(BOTS.size());
                int onlineCount = 0;
                for (BotInfo b : BOTS.values())
                {
                    if (b.connected && b.loggedIn) onlineCount++;
                }
                METRICS.setOnlineCount(onlineCount);
                if (info.getPktAgeMs() >= 0)
                {
                    METRICS.notePktAgeMs(info.getPktAgeMs());
                }

                if (snapshot.hpCurrent <= 0)
                {
                    if (!"dead".equals(info.state))
                    {
                        info.state = "dead";
                        // STEP 4: notify CombatAI so it clears target/aggression state
                        player.getCombatAI().onDeath();
                        // S6-T06/T10: track death rate; 3+ deaths in 60s = death-loop -> force a regen hold.
                        long nowMs = System.currentTimeMillis();
                        if (nowMs - lastDeathMs < 60_000) recentDeaths++;
                        else recentDeaths = 1;
                        lastDeathMs = nowMs;
                        if (recentDeaths >= DEATH_GUARD_DEATHS)
                        {
                            deathGuardUntilMs = nowMs + DEATH_GUARD_MS;
                            LOGGER.warning("[FleetPlay] " + account + " DEATH-LOOP guard: " + recentDeaths
                                + " deaths in 60s -> " + (DEATH_GUARD_MS / 1000) + "s regen/relocate hold");
                        }
                        emit(EventRing.TYPE_DEATH, "died",
                            snapshot.hpCurrent + "/" + snapshot.hpMax + " HP; lvl " + snapshot.level
                                + " combat target " + info.targetObjId,
                            "objId", info.targetObjId);
                        wiring.revive();
                    }
                    Thread.sleep(TICK_MS * 10);
                    continue;
                }
                else if ("dead".equals(info.state))
                {
                    info.state = "alive";
                    // STEP 4: notify CombatAI the bot is back in the world
                    player.getCombatAI().onRespawn(snapshot.level);
                    emit(EventRing.TYPE_RESPAWN, "respawned",
                        "back at " + snapshot.x + "," + snapshot.y + " lvl " + snapshot.level,
                        "lvl", snapshot.level);
                }

                // TIM-001 H5: keep the CombatAI's AIPlayer position in sync with the LIVE snapshot.
                // detectNearbyEnemy() computes distance from aiPlayer.getX/Y/Z, which were seeded at
                // (0,0,0) at construction and never updated from packets, so every hostile read as
                // ~83k units away -> the bot never engaged (stuck at AUTO_PLAY, no kills / no XP).
                player.setPosition(snapshot.x, snapshot.y, snapshot.z);
                CombatDecision decision = player.getCombatAI().makeDecision();
                info.state = decision.getAction().toString();
                info.action = decision.getAction().toString();
                info.thought = decision.getReason();
                // "thoughts / target": resolve the bot's current target into a label + distance.
                int selTargetId = player.getCombatAI().getSelectedTargetObjId();
                // STEP 3 follow-up: stale-target watchdog. If the engaged enemy produced NO XP within
                // STALE_TARGET_BUDGET_MS, abandon it so detectNearbyEnemy() re-acquires a farmable target
                // next tick (kills the "chase the merchant / dead mob forever" stall with live proof).
                if (player.getCombatAI().checkStaleTarget(logger.getExp(), (int) staleBudgetMs(snapshot.level)))
                {
                    emit(EventRing.TYPE_TARGET_ABANDON, "objId", selTargetId, "exp", logger.getExp());
                    LOGGER.info("[FLEET] " + account + " abandoned stale target objId=" + selTargetId);
                    selTargetId = 0;
                    info.state = "idle";
                    info.thought = "abandoned un-advancing target, re-acquiring";
                }
                info.targetObjId = selTargetId;
                if (selTargetId > 0)
                {
                    EntityInfo target = logger.getEntity(selTargetId);
                    if (target != null)
                    {
                        info.targetX = target.x;
                        info.targetY = target.y;
                        info.targetZ = target.z;
                        info.targetKind = target.isHostile ? 1 : (target.name != null ? 2 : 0);
                        info.targetLabel = target.name != null ? target.name : ("npc#" + target.npcId);
                        info.targetDist = Math.sqrt(Math.pow(target.x - info.x, 2)
                            + Math.pow(target.y - info.y, 2) + Math.pow(target.z - info.z, 2));
                    }
                }
                else
                {
                    info.targetLabel = "";
                    info.targetDist = 0;
                }

                switch (decision.getAction())
                {
                    case ATTACK:
                    case ENGAGE_TARGET:
                    case USE_SKILL:
                        // QUEST-PRIORITY: if the bot is near its quest NPC (or mid-dialog), yield to the
                        // quest route instead of fighting — the CombatAI switch runs before the goal
                        // routing, so combat would otherwise steal the bot away from the giver forever.
                        if (questDialogOpen || nearQuestNpc(snapshot, logger, questNpcId))
                        {
                            info.state = "quest-route";
                            info.thought = "near quest NPC — routing to talk (no combat)";
                            break;
                        }
                        // FINAL-MILE: while the quest dialog is open, HOLD (don't fight) — a real
                        // player in a conversation doesn't run off to attack. Keeps the bot standing
                        // at the giver through the multi-tick click -> html -> bypass -> accept flow.
                        if (questDialogOpen)
                        {
                            info.state = "quest-dialog";
                            info.thought = "in NPC dialog — holding (no combat while talking)";
                            break;
                        }
                        // TIM-001 H5: ATTACK/ENGAGE_TARGET decisions from CombatAI carry no explicit
                        // targetId (engageEnemy returns plain CombatDecision.attack()), so pass the
                        // target the CombatAI actually selected (getSelectedTargetObjId). Otherwise the
                        // combat planner gets targetObjId=0 and emits NO Action/AttackRequest frames
                        // (the "SKIP-UNPROVEN ... planner produced no frames" stall -> no kills, no XP).
                        int targetId = selTargetId > 0 ? selTargetId : parseObjId(decision.getTargetId());
                        if (targetId > 0)
                        {
                            // STEP 3 gap-close: CombatAI engages any hostile within combat.target_distance
                            // (default 1500) and never lowers a still-far mob (it only compares against 1500),
                            // so a bot stands still spamming an unreachable target -> 0 XP, 0 movement. When
                            // phase0.movement is ON (default OFF => existing behaviour preserved), advance one
                            // hop toward an out-of-melee target so the fleet actually closes distance and farms.
                            boolean chasing = false;
                            if (phase0.isMovementEnabled() && System.currentTimeMillis() - lastChaseMs > CHASE_INTERVAL_MS)
                            {
                                PacketLogger.EntityInfo chaseTarget = logger.getEntity(targetId);
                                if (chaseTarget != null)
                                {
                                    double cdx = chaseTarget.x - snapshot.x;
                                    double cdy = chaseTarget.y - snapshot.y;
                                    double cdz = chaseTarget.z - snapshot.z;
                                    double cdist = Math.sqrt(cdx * cdx + cdy * cdy + cdz * cdz);
                                    if (cdist > CHASE_REACH)
                                    {
                                        lastChaseMs = System.currentTimeMillis();
                                        BotPlayController.Chase hop = BotPlayController.chaseStep(snapshot.x,
                                            snapshot.y, snapshot.z, chaseTarget.x, chaseTarget.y, chaseTarget.z,
                                            CHASE_HOP);
                                        telemetry.recordMove(account, snapshot.x, snapshot.y, snapshot.z,
                                            hop.x, hop.y, hop.z, "chase", "advance to target " + targetId);
                                        wiring.moveTo(snapshot.x, snapshot.y, snapshot.z, hop.x, hop.y, hop.z);
                                        emit(EventRing.TYPE_MOVE, "to", hop.x + "," + hop.y, "route", "chase");
                                        info.state = "chase";
                                        info.thought = "closing on target " + targetId;
                                        chasing = true;
                                    }
                                }
                            }
                            // Always send the combat frames even while chasing (fix for the no-kills
                            // stall): the server receives AttackRequest, routes the character to the
                            // target, and resolves melee range from SERVER-side positions. If we
                            // skipped attacks while chasing, a lagging local snapshot meant the bot
                            // chased forever and never landed a hit. Range checks are server-authoritative,
                            // so an out-of-range AttackRequest is simply routed, not rejected.
                            // USE_SKILL fallback (2026-08-17 fresh-bot fix): a level-1 fighter has no
                            // learned offensive skill, so CombatFramePlanner returns NO frames for
                            // USE_SKILL and executeCombat no-ops -> the bot stands still, takes hits,
                            // and STARVES (0 damage, 0 XP, STALE-TARGET abandon loop). Fall back to the
                            // proven plain melee attack frame so it actually deals damage and farms.
                            if (decision.getAction() == CombatDecision.Action.USE_SKILL)
                            {
                                decision = CombatDecision.attackTarget(String.valueOf(targetId));
                            }
                            wiring.executeCombat(decision, snapshot.x, snapshot.y, snapshot.z, targetId);
                        }
                        break;

                    case FLEE:
                    case RETREAT:
                        EntityInfo nearest = snapshot.findNearestHostile(2000, logger);
                        if (nearest != null)
                        {
                            int fx = snapshot.x + (snapshot.x - nearest.x) * 2;
                            int fy = snapshot.y + (snapshot.y - nearest.y) * 2;
                            wiring.moveTo(snapshot.x, snapshot.y, snapshot.z, fx, fy, snapshot.z);
                        }
                        // S6-T03: after a low-HP retreat, hold for regen before re-engaging.
                        regenHoldUntilMs = System.currentTimeMillis() + REGEN_HOLD_MS;
                        break;

                    case IDLE:
                    default:
                        info.state = "idle";
                        int newTarget = 0;
                        long nowMs = System.currentTimeMillis();
                        boolean survivalGuard = nowMs < deathGuardUntilMs
                            || (nowMs < regenHoldUntilMs && hpFrac(snapshot) < 0.60)
                            || (info.mobs > SURROUND_CAP && hpFrac(snapshot) < 0.70);
                        if (survivalGuard)
                        {
                            // S6-T03/T06/T07/T10: regen/death-loop/overwhelm guard — skip re-engaging
                            // so the bot recovers (or relocates) instead of farming itself to death.
                            info.state = "regen";
                            info.thought = "survival guard (regen/death-loop/overwhelm)";
                        }
                        else
                        {
                            newTarget = targetSelector.selectTarget();
                        }
                        if (newTarget != 0)
                        {
                            info.state = "engage";
                            wiring.executeCombat(CombatDecision.attackTarget(String.valueOf(newTarget)),
                                snapshot.x, snapshot.y, snapshot.z, newTarget);
                        }
                        else if (phase0.isMovementEnabled())
                        {
                            // TIM-001 fix (flag OFF by default): proactive FAR travel when idle.
                            // The server rejects single moves > 9900u (MoveToLocation.java:156-163),
                            // so a route is sent as a sequence of <=4800u hops; a new hop is sent
                            // only after the server acked us near the previous one (ValidateLocation).
                            long now = System.currentTimeMillis();
                            // STEP 2: the controller authors the goal authoritatively, every tick (it is
                            // cheap and pure). If it says BYPASS at a quest NPC and the operator configured
                            // phase0.quest.npcId, drive the dialog (click NPC -> read html -> send the
                            // single validated bypass) instead of routing; otherwise behave exactly as
                            // STEP 1 (MOVE_TO quest NPC / random far-travel fallback).
                            GoalDecision goal = BotPlayController.decide(buildPlayContext(snapshot, logger), cfg);
                            // ACQUIRE-failure cooldown: with an empty journal the planner keeps re-issuing the
                            // same ACQUIRE giver, but a geo-unreachable one (Wolf Hunt at Gludio, ~148k across the
                            // ocean) only produces abandoned routes. While the cooldown is armed, null the goal so
                            // the ZoneRouter.plan fallback below runs plain far-travel farming until the window
                            // expires, instead of re-launching the dead ocean-hop route every 300ms tick.
                            if (goal != null && goal.goal == PlayerGoal.ACQUIRE
                                    && acquireCooldown.isSuppressed(System.currentTimeMillis()))
                            {
                                LOGGER.info("[FleetPlay] " + account + " ACQUIRE suppressed by cooldown until "
                                    + acquireCooldown.cooldownUntilMs()
                                    + "; plain farming while the giver is geo-unreachable");
                                goal = null;
                            }
                            // STEP 4: RETREAT is urgent — flee immediately via the controller's
                            // clamped retreat hop. Skip route planning (the destination is one hop).
                            if (goal != null && goal.action == GoalAction.RETREAT
                                    && (goal.targetX != 0 || goal.targetY != 0))
                            {
                                telemetry.recordMove(account, snapshot.x, snapshot.y, snapshot.z,
                                    goal.targetX, goal.targetY, goal.targetZ,
                                    goal.label, goal.reason);
                                wiring.moveTo(snapshot.x, snapshot.y, snapshot.z,
                                    goal.targetX, goal.targetY, goal.targetZ);
                                emit(EventRing.TYPE_MOVE, "to",
                                    goal.targetX + "," + goal.targetY, "route", goal.label);
                                info.state = "retreat";
                                info.thought = goal.reason;
                                activeRoute = null;
                                pendingHop = null;
                                break; // skip the rest of this IDLE tick
                            }
                            // STEP 4: restock intent — log and hold; don't route (the bot stays put
                            // until inventory drops or the operator intervenes).
                            if (goal != null && goal.action == GoalAction.WAIT
                                    && goal.reason != null && goal.reason.contains("restock"))
                            {
                                info.state = "restock";
                                info.thought = goal.reason;
                                activeRoute = null;
                                pendingHop = null;
                                break;
                            }
                            boolean dialogDriven = false;
                            if (questNpcId > 0 && activeRoute == null
                                    && goal != null && goal.action == GoalAction.BYPASS)
                            {
                                driveQuestDialog(snapshot, logger, wiring, goal, questNpcId, questNameProp);
                                activeRoute = null;
                                pendingHop = null;
                                lastRoute = now;
                                info.state = "quest-dialog:" + goal.questTargetId;
                                info.thought = "STEP2 talking to quest NPC " + questNpcId;
                                dialogDriven = true;
                            }
                            if (!dialogDriven && activeRoute == null && now - lastRoute > phase0.getMovementIdleRouteMs())
                            {
                                lastRoute = now;
                                // If the controller picked a concrete MOVE destination (quest NPC /
                                // acquire / hunt), route toward it via the same hop machinery; otherwise
                                // fall back to the proven random far-travel plan (REST hold still).
                                activeRoute = null;
                                if (goal != null && goal.action == GoalAction.MOVE_TO
                                        && goal.targetX != 0 && goal.targetY != 0)
                                {
                                    activeRoute = ZoneRouter.routeTo(snapshot.x, snapshot.y, snapshot.z,
                                        goal.targetX, goal.targetY, goal.targetZ,
                                        "goal:" + (goal.label != null ? goal.label : goal.goal.toString()),
                                        goal.reason);
                                }
                                if (activeRoute == null)
                                {
                                    // STEP 6: pick an idle-relocation aim. When frozen (previous route
                                    // abandoned = zero server movement) this routes BACK toward the last-XP
                                    // spot or nearest fleet mate instead of a random far point; otherwise it
                                    // prefers a real guide-map landmark. The consecutive-abandon escape gate
                                    // returns null -> stay put (no doomed far move re-issued every tick).
                                    java.util.List<int[]> mates = new java.util.ArrayList<>();
                                    for (BotInfo b : BOTS.values())
                                    {
                                        if (b == null || b.account == null || b.account.equals(account)
                                                || !b.connected || !b.loggedIn || (b.x == 0 && b.y == 0))
                                        {
                                            continue; // skip self / offline / not-in-world mates
                                        }
                                        mates.add(new int[] { b.x, b.y, b.z });
                                    }
                                    // Route toward THIS bot's race's guide landmark, so an Orc/Dwarf/Elf
                                    // relocates to its OWN village zone (never drifts to Talking Island).
                                    RelocationPlanner.Target reloc = relocation.choose(snapshot.level,
                                        snapshot.x, snapshot.y, snapshot.z,
                                        relocation.isFrozen(), mates, race,
                                        phase0.getMovementMinRadius(), phase0.getMovementMaxRadius());
                                    if (reloc != null)
                                    {
                                        activeRoute = ZoneRouter.routeTo(snapshot.x, snapshot.y, snapshot.z,
                                            reloc.x, reloc.y, reloc.z, reloc.label, reloc.reason);
                                        if (activeRoute != null)
                                        {
                                            info.thought = reloc.reason;
                                        }
                                    }
                                    else if (relocation.escapeHoldActive())
                                    {
                                        // STEP 6 escape gate: hold still — break the frozen far-travel loop.
                                        info.state = "idle";
                                        info.thought = "escape-hold (frozen relocation aborted)";
                                    }
                                }
                                if (activeRoute != null)
                                {
                                    info.state = "travel:" + activeRoute.label;
                                    info.thought = activeRoute.reason;
                                }
                            }

                            // Pull the next hop whenever nothing is in flight.
                            if (activeRoute != null && pendingHop == null && activeRoute.hasMoreHops())
                            {
                                pendingHop = activeRoute.nextHop();
                                hopSentAtMs = 0; // "not sent yet" trigger below
                                hopTimeouts = 0; // fresh hop: reset the stuck-hop counter
                            }

                            final int arriveDist = 150;
                            if (pendingHop != null)
                            {
                                boolean nearHop = Math.hypot(pendingHop[0] - snapshot.x,
                                    pendingHop[1] - snapshot.y) <= arriveDist;
                                // Audit 46 P0 #2: the send/advance/resend decision is extracted into
                                // the pure HopGate helper so it is unit-testable; only the stuck-hop
                                // abandonment (timeout counter + MAX_HOP_TIMEOUTS) stays here.
                                HopGate.Action hopAction = HopGate.nextAction(now, nearHop, hopSentAtMs);

                                if (hopAction == HopGate.Action.ADVANCE)
                                {
                                    // Arrived at this hop: complete the route, or advance to the next one.
                                    hopTimeouts = 0; // TIM-001: a reached hop is not stuck
                                    relocation.noteProgress(); // STEP 6: server moved us — not frozen
                                    telemetry.recordHopResult(account, true); // S5-T06
                                    if (activeRoute.hasMoreHops())
                                    {
                                        pendingHop = activeRoute.nextHop();
                                        hopSentAtMs = 0;
                                        hopTimeouts = 0;
                                    }
                                    else
                                    {
                                        activeRoute = null;
                                        pendingHop = null;
                                        info.state = "idle";
                                    }
                                }
                                else if (hopAction == HopGate.Action.SEND || hopAction == HopGate.Action.RESEND)
                                {
                                    // TIM-001 stuck-hop recovery: a waypoint the server never walks us
                                    // toward would otherwise resend forever every 45s (the movesSent=2
                                    // in 2 min stall). Resend up to ZoneRouter.MAX_HOP_TIMEOUTS, then
                                    // abandon the route so the bot re-plans a fresh far point.
                                    boolean resend = hopAction == HopGate.Action.SEND; // never sent -> send fresh
                                    if (hopAction == HopGate.Action.RESEND)
                                    {
                                        hopTimeouts++;
                                        telemetry.recordHopResult(account, false); // S5-T06
                                        resend = !ZoneRouter.isRouteStuck(hopTimeouts);
                                        if (!resend)
                                        {
                                            LOGGER.warning("[FleetPlay] " + account + " hop unreachable after "
                                                + hopTimeouts + " timeouts -> abandoning route "
                                                + activeRoute.label + " (" + pendingHop[0] + "," + pendingHop[1] + ")");
                                            // Remember this destination as geo-unreachable so ZoneRouter.plan
                                            // won't deterministically re-select the SAME zone/far point on the
                                            // next re-plan (post-respawn 54-HOP / 27-abandon loop fix).
                                            if (activeRoute != null)
                                            {
                                                zoneRouter.noteUnreachableDestination(activeRoute.destX, activeRoute.destY);
                                            }
                                            // STEP 6: count this consecutive frozen-route abandon so the
                                            // escape gate eventually holds the bot still (breaks the churn).
                                            relocation.noteAbandonedRoute();
                                            // S5-T01 (root cause: server rejects moves while isOutOfControl()
                                            // — MoveToLocation sends ActionFailed, no ValidateLocation). If
                                            // hostiles are near the bot is likely CC'd (stunned/rooted): hold
                                            // to recover instead of churning far re-plans.
                                            if (info.mobs > 0)
                                            {
                                                regenHoldUntilMs = now + REGEN_HOLD_MS;
                                                info.state = "regen";
                                                info.thought = "route abandoned near hostiles (CC?) — holding";
                                            }
                                            // ACQUIRE-failure cooldown: an abandoned goal:acquire:* route means the
                                            // giver is geo-unreachable from here (empty journal + ~148k-unit ocean hop
                                            // the server never walks the char toward). Count the abort; once the
                                            // threshold is hit, ACQUIRE is suppressed below so the ZoneRouter.plan
                                            // fallback runs plain far-travel farming instead of re-issuing this route.
                                            if (activeRoute.label != null
                                                    && activeRoute.label.startsWith("goal:acquire:")
                                                    && info.mobs == 0)
                                            {
                                                // Only a route abandoned with NO hostiles near is truly
                                                // geo-unreachable (the ocean-hop loop). A combat
                                                // interruption is NOT unreachable — the bot just needs
                                                // to get back to the giver; don't arm the cooldown.
                                                acquireCooldown.recordUnreachableAbort(now);
                                                if (acquireCooldown.isSuppressed(now))
                                                {
                                                    LOGGER.warning("[FleetPlay] " + account + " ACQUIRE cooldown armed: "
                                                        + acquireCooldown.unreachableCount() + "/"
                                                        + acquireCooldown.maxUnreachable()
                                                        + " unreachable abandons; falling back to plain farming");
                                                }
                                                else
                                                {
                                                    LOGGER.info("[FleetPlay] " + account + " ACQUIRE unreachable abort "
                                                        + acquireCooldown.unreachableCount() + "/"
                                                        + acquireCooldown.maxUnreachable()
                                                        + "; one more abort suppresses the ocean-hop goal");
                                                }
                                            }
                                            activeRoute = null;
                                            pendingHop = null;
                                            hopSentAtMs = 0;
                                            hopTimeouts = 0;
                                            lastRoute = now; // re-plan on the next idle tick
                                            break; // end this IDLE tick; don't resend the dead hop
                                        }
                                    }
                                    if (resend)
                                    {
                                        hopSentAtMs = now;
                                        telemetry.recordMove(account, snapshot.x, snapshot.y, snapshot.z,
                                            pendingHop[0], pendingHop[1], pendingHop[2],
                                            activeRoute.label, activeRoute.reason);
                                        wiring.moveTo(snapshot.x, snapshot.y, snapshot.z,
                                            pendingHop[0], pendingHop[1], pendingHop[2]);
                                        emit(EventRing.TYPE_MOVE, "to", pendingHop[0] + "," + pendingHop[1],
                                            "route", activeRoute.label);
                                        info.state = "travel:" + activeRoute.label;
                                        info.thought = activeRoute.reason;
                                        LOGGER.info("[FleetPlay] " + account + " HOP -> " + activeRoute.label
                                            + " (" + pendingHop[0] + "," + pendingHop[1] + "," + pendingHop[2] + ") "
                                            + activeRoute.reason);
                                    }
                                }
                            }
                        }
                        else if (System.currentTimeMillis() - lastWander > WANDER_INTERVAL_MS)
                        {
                            lastWander = System.currentTimeMillis();
                            int nx = snapshot.x + span(WANDER_RADIUS);
                            int ny = snapshot.y + span(WANDER_RADIUS);
                            info.state = "wander";
                            emit(EventRing.TYPE_MOVE, "to", nx + "," + ny, "route", "wander");
                            wiring.moveTo(snapshot.x, snapshot.y, snapshot.z, nx, ny, snapshot.z);
                        }
                        break;
                }

                Thread.sleep(TICK_MS + Math.abs(rng.nextInt() % 150));
            }
        }

        private int span(int radius)
        {
            return rng.nextInt(radius * 2 + 1) - radius;
        }

        /** WPT-03: push a typed event into the shared ring (thread-safe write from the bot thread). */
        private void emit(String type, Object... kv)
        {
            java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
            for (int i = 0; i + 1 < kv.length; i += 2)
            {
                data.put(String.valueOf(kv[i]), kv[i + 1]);
            }
            EVENTS.add(type, account, data);
        }

        /** STEP 1C: build the controller's pure input straight from the live snapshot + packet logger. */
        private PlayContext buildPlayContext(BotSnapshot s, PacketLogger logger)
        {
            java.util.List<Hostile> hostiles = new java.util.ArrayList<>();
            if (s.hostileEntities != null)
            {
                for (EntityInfo e : s.hostileEntities)
                {
                    if (e == null)
                    {
                        continue;
                    }
                    hostiles.add(new Hostile(e.objectId, e.x, e.y, e.z));
                }
            }
            java.util.List<int[]> journal = logger.getActiveQuestList();
            // ACQUIRE-failure cooldown: a non-empty journal is real quest progress (an accept/turn-in the
            // server actually recorded) — clear any suppression so the planner authors ACQUIRE/QUEST freely.
            if (journal != null && !journal.isEmpty())
            {
                acquireCooldown.reset();
            }
            return new PlayContext(s.level, s.x, s.y, s.z, s.hpCurrent, s.hpMax,
                journal != null ? journal : java.util.Collections.<int[]>emptyList(),
                hostiles, 0, s.inventoryUsagePercent);
        }

        private int parseObjId(String targetId)
        {
            if (targetId == null)
            {
                return 0;
            }
            String prefix = "objId=";
            int idx = targetId.indexOf(prefix);
            if (idx < 0)
            {
                return 0;
            }
            try
            {
                return Integer.parseInt(targetId.substring(idx + prefix.length()));
            }
            catch (NumberFormatException e)
            {
                return 0;
            }
        }
    }

    // ============================ light web dashboard ============================

    /** Webserver. FleetPlay itself registers ONLY / (SPA), /json (legacy combined payload) and /report
     *  (text). Every JSON route - /api/v1/* plus the pre-v1 /api/players + /api/landmarks aliases - is
     *  wired exclusively through DashboardApi (WPT-01 extraction: all dashboard serialization lives there). */
    private static void startDashboard(int port, int fleetSize) throws IOException
    {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        DashboardApi.Config cfg = new DashboardApi.Config(fleetSize);
        DashboardApi api = new DashboardApi(BOTS, System.currentTimeMillis(), cfg, MoveTelemetry.getInstance(),
            EVENTS, HISTORY, METRICS);
        server.createContext("/", exchange -> respond(exchange, 200, "text/html; charset=utf-8", loadDashboard()));
        server.createContext("/json", exchange -> respond(exchange, 200, DashboardApi.JSON, api.legacyJson()));
        server.createContext("/report", exchange -> respond(exchange, 200, "text/plain; charset=utf-8",
            AIMonitorDashboard.getInstance().generateReport().getBytes(StandardCharsets.UTF_8)));
        /* WPT-21 (TIM-001 evidence instrument): serve MoveTelemetry.report() so scripts/tim001_move_probe.sh
           can curl EVIDENCE-H1/H2/H5 lines (far-travel + movement-persistence + organic-XP proof). */
        server.createContext("/telemetry", exchange -> respond(exchange, 200, "text/plain; charset=utf-8",
            MoveTelemetry.getInstance().report().getBytes(StandardCharsets.UTF_8)));
        api.register(server);
        server.start();
        LOGGER.info("[FleetPlay] dashboard live on http://localhost:" + port);
    }

    /** Serve the map+grid SPA from the classpath resource (src/main/resources/dashboard/index.html). */
    private static byte[] loadDashboard()
    {
        try (java.io.InputStream in = FleetPlay.class.getClassLoader().getResourceAsStream("dashboard/index.html"))
        {
            if (in != null)
            {
                return in.readAllBytes();
            }
        }
        catch (IOException e)
        {
            LOGGER.warning("[FleetPlay] dashboard resource unreadable: " + e.getMessage());
        }
        return "<html><body><h1>dashboard resource missing</h1><p>rebuild after mvn resources</p></body></html>"
            .getBytes(StandardCharsets.UTF_8);
    }

    // Town landmarks are served by DashboardApi (/api/v1/landmarks + the legacy /api/landmarks alias).
    private static void respond(HttpExchange exchange, int code, String contentType, byte[] body) throws IOException
    {
        byte[] bytes = body != null ? body : new byte[0];
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody())
        {
            out.write(bytes);
        }
    }

    /** Top-5 inventory items by count: {itemId, count} pairs, from the real ItemList (0x1B) parse. */
    private static int[][] topItems(PacketLogger logger)
    {
        java.util.List<java.util.Map.Entry<Integer, Long>> inv =
            new java.util.ArrayList<>(logger.getInventoryItems().entrySet());
        inv.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        int cap = Math.min(5, inv.size());
        int[][] out = new int[cap][2];
        for (int i = 0; i < cap; i++)
        {
            out[i][0] = inv.get(i).getKey();
            out[i][1] = inv.get(i).getValue().intValue();
        }
        return out;
    }

    /** Snapshot of nearby entities for the map: {objId, kind(0 npc 1 hostile 2 player), x, y, z}, bounded. */
    private static int[][] entitySnapshot(PacketLogger logger)
    {
        int total = logger.getEntityCountTotal();
        int cap = Math.min(120, total);
        int[][] out = new int[cap][5];
        int i = 0;
        for (PacketLogger.EntityInfo e : logger.getEntities())
        {
            if (i >= cap) break;
            out[i][0] = e.objectId;
            out[i][1] = e.isHostile ? 1 : (e.name != null ? 2 : 0);
            out[i][2] = e.x;
            out[i][3] = e.y;
            out[i][4] = e.z;
            i++;
        }
        return cap == i ? out : java.util.Arrays.copyOf(out, i);
    }

    // Combined /json + /api/players serialization moved to DashboardApi.legacyJson() (WPT-01 extraction).
}
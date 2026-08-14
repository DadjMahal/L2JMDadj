package com.aiplayer.examples;

/** MODE: COMPLETE. Real fleet launcher + live web dashboard. STEP 1C: quest-NPC navigation is wired —
 *  the BotPlayController authors the goal each idle tick and FleetPlay routes toward the chosen
 *  destination (quest NPC / acquire / hunt) via the proven hop machinery (see ZoneRouter.routeTo). */

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Random;
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
import com.aiplayer.phase0.movement.HopGate;
import com.aiplayer.phase0.movement.MoveTelemetry;
import com.aiplayer.phase0.movement.ZoneRouter;
import com.aiplayer.phase0.movement.ZoneRouter.RouteGoal;
import com.aiplayer.phase0.play.BotPlayController;
import com.aiplayer.phase0.play.BotPlayController.Hostile;
import com.aiplayer.phase0.play.BotPlayController.PlayContext;
import com.aiplayer.phase0.play.GoalAction;
import com.aiplayer.phase0.play.GoalDecision;
import com.aiplayer.phase0.play.PlayerGoal;
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
    private static final String PASSWORD = "ai123pass";
    private static final long TICK_MS = 300;
    private static final long WANDER_INTERVAL_MS = 8000;
    private static final int WANDER_RADIUS = 900;

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
            final String account = String.format("ai_combat_%02d", i);
            final int charId = 100000 + i - 1; // ai_combat_01 -> 100000 ... ai_combat_05 -> 100004
            BotInfo info = new BotInfo(account, charId);
            BOTS.put(account, info);
            new Thread(new BotLoop(account, charId, info, host, loginPort, gamePort), "bot-" + account).start();
        }

        synchronized (FleetPlay.class)
        {
            while (true)
            {
                FleetPlay.class.wait();
            }
        }
    }

    private static final class BotLoop implements Runnable
    {
        private final String account;
        private final int charId;
        private final BotInfo info;
        private final String host;
        private final int loginPort;
        private final int gamePort;
        private final Random rng;

        private BotLoop(String account, int charId, BotInfo info, String host, int loginPort, int gamePort)
        {
            this.account = account;
            this.charId = charId;
            this.info = info;
            this.host = host;
            this.loginPort = loginPort;
            this.gamePort = gamePort;
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
                        Thread.sleep(15000); // reconnect loop
                    }
                    catch (InterruptedException ie)
                    {
                        return;
                    }
                }
            }
        }

        private void runSession() throws Exception
        {
            AIPlayer player = new AIPlayer(account, 100 + charId % 100, 0, 0); // Human Fighter
            L2JProtocol login = new L2JProtocol(player, host, loginPort, gamePort);
            if (!login.connectAndLogin(account, PASSWORD, charId))
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
            MoveTelemetry telemetry = MoveTelemetry.getInstance();
            info.connected = true;
            info.loggedIn = true;
            emit(EventRing.TYPE_CONNECT, "level", info.level);
            LOGGER.info("[FleetPlay] " + account + " ENTERED WORLD"
                + (phase0.isMovementEnabled() ? " [phase0.movement ON]" : ""));

            long lastWander = 0;
            long lastRoute = 0;
            ZoneRouter.RouteGoal activeRoute = null;
            int[] pendingHop = null;
            long hopSentAtMs = 0;
            int hopTimeouts = 0; // TIM-001: consecutive timeouts on the current hop (stuck-hop recovery)
            int prevLevel = info.level;
            long prevExp = -1; // EVIDENCE-H5: StatusUpdate-driven per-kill XP-gain trail
            int prevHp = info.hp;
            boolean firstTick = true;
            long lastHistoryMs = 0;
            // WPT-22: drain newly-parsed SystemMessage / chat broadcasts into the shared event ring
            // (only the count delta is emitted once per tick; message bodies are read back from logger).
            long lastSysCount = logger.getSystemMessageCount();
            long lastChatCount = logger.getChatCount();
            while (true)
            {
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
                    System.out.println("[EVIDENCE-H5] " + account + " EXP +" + (info.exp - prevExp)
                        + " (now " + info.exp + ", level=" + tickLevel + ")");
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
                        wiring.revive();
                    }
                    Thread.sleep(TICK_MS * 10);
                    continue;
                }
                else if ("dead".equals(info.state))
                {
                    info.state = "alive";
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
                        // TIM-001 H5: ATTACK/ENGAGE_TARGET decisions from CombatAI carry no explicit
                        // targetId (engageEnemy returns plain CombatDecision.attack()), so pass the
                        // target the CombatAI actually selected (getSelectedTargetObjId). Otherwise the
                        // combat planner gets targetObjId=0 and emits NO Action/AttackRequest frames
                        // (the "SKIP-UNPROVEN ... planner produced no frames" stall -> no kills, no XP).
                        int targetId = selTargetId > 0 ? selTargetId : parseObjId(decision.getTargetId());
                        if (targetId > 0)
                        {
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
                        break;

                    case IDLE:
                    default:
                        info.state = "idle";
                        // "Mostly smart" wander: only when no hostile target is available.
                        int newTarget = targetSelector.selectTarget();
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
                            if (activeRoute == null && now - lastRoute > phase0.getMovementIdleRouteMs())
                            {
                                lastRoute = now;
                                // STEP 1C: the BotPlayController authors the goal authoritatively. If it
                                // picks a concrete MOVE destination (quest NPC / acquire / hunt), route
                                // toward it via the same hop machinery; otherwise fall back to the proven
                                // random far-travel plan (high-level REST holds still farm via far-points).
                                GoalDecision goal = BotPlayController.decide(buildPlayContext(snapshot, logger));
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
                                    activeRoute = zoneRouter.plan(snapshot.level,
                                        snapshot.x, snapshot.y, snapshot.z,
                                        phase0.getMovementMinRadius(), phase0.getMovementMaxRadius());
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
                                        resend = !ZoneRouter.isRouteStuck(hopTimeouts);
                                        if (!resend)
                                        {
                                            LOGGER.warning("[FleetPlay] " + account + " hop unreachable after "
                                                + hopTimeouts + " timeouts -> abandoning route "
                                                + activeRoute.label + " (" + pendingHop[0] + "," + pendingHop[1] + ")");
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
            return new PlayContext(s.level, s.x, s.y, s.z, s.hpCurrent, s.hpMax,
                journal != null ? journal : java.util.Collections.<int[]>emptyList(),
                hostiles, 0);
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
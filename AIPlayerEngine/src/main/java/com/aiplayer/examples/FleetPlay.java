package com.aiplayer.examples;

/** MODE: COMPLETE (except quest-NPC navigation — that seam is queued; see Audit/43). Real fleet launcher + live web dashboard. */

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
import com.aiplayer.phase0.movement.MoveTelemetry;
import com.aiplayer.phase0.movement.ZoneRouter;
import com.aiplayer.phase0.movement.ZoneRouter.RouteGoal;
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.protocol.PacketLogger;
import com.aiplayer.protocol.PacketLogger.EntityInfo;

/**
 * Launches a fleet of real AI Players against the live Interlude stack and exposes a light
 * web dashboard. Each bot runs the same proven login -> enter-world -> decision loop
 * as Phase0Driver, on its own thread:
 *   - combat: target attackables via TargetSelector and execute the proven
 *     Action/AttackRequest frames (the server walks the char into range),
 *   - level-up through kills (= the char's exp rises in gameserver.characters),
 *   - idle: "mostly smart" random wander around the current position (passes NPCs).
 * Dashboard: GET / (HTML, auto-refresh), /json (live stats), /report (text).
 */
public final class FleetPlay
{
    private static final Logger LOGGER = Logger.getLogger(FleetPlay.class.getName());
    private static final String PASSWORD = "ai123pass";
    private static final long TICK_MS = 300;
    private static final long WANDER_INTERVAL_MS = 8000;
    private static final int WANDER_RADIUS = 900;

    /** Live per-bot row shown on the dashboard. */
    public static final class BotInfo
    {
        public final String account;
        public final int charId;
        public volatile String charName = "";
        public volatile int level;
        public volatile long exp;
        public volatile int sp;
        public volatile int hp, hpMax, mp, mpMax, cp, cpMax;
        public volatile int x, y, z, heading;
        public volatile double movedLast60;   // server-acked movement (u) in last 60s — TIM-001 truth metric
        public volatile int movesSent;        // MoveToLocation frames sent this session
        public volatile int load, maxLoad;
        public volatile boolean weapon;
        public volatile int adena, invPct, itemCount;
        public volatile int[][] items;      // {itemId, count} top-5 by count
        public volatile int mobs, npcs;
        public volatile int[][] ents;       // {objId, kind(0 npc 1 hostile 2 player), x, y, z}
        public volatile String action = "";
        public volatile String thought = "";
        public volatile int targetObjId;
        public volatile String targetLabel = "";
        public volatile int targetKind;
        public volatile int targetX, targetY, targetZ;
        public volatile double targetDist;
        public volatile long sessionStartMs = System.currentTimeMillis();
        public volatile String state = "connecting";
        public volatile boolean connected;
        public volatile boolean loggedIn;
        public volatile long lastSeenMs = System.currentTimeMillis();

        public BotInfo(String account, int charId)
        {
            this.account = account;
            this.charId = charId;
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, BotInfo> BOTS =
        new java.util.concurrent.ConcurrentHashMap<>();

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
        startDashboard(dashPort);
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
            LOGGER.info("[FleetPlay] " + account + " ENTERED WORLD"
                + (phase0.isMovementEnabled() ? " [phase0.movement ON]" : ""));

            long lastWander = 0;
            long lastRoute = 0;
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
                info.movedLast60 = telemetry.movedLast(60_000, account);
                info.movesSent = telemetry.moveCount(account);
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
                info.lastSeenMs = System.currentTimeMillis();
                AIMonitorDashboard.getInstance().updatePlayerStats(player);

                // TIM-001: feed the movement-evidence harness every tick with the real server-acked
                // position + exp (ValidateLocation 0x61 / CharInfo 0x03 parse in PacketLogger).
                telemetry.recordPosition(account, snapshot.x, snapshot.y, snapshot.z, info.exp);

                if (snapshot.hpCurrent <= 0)
                {
                    info.state = "dead";
                    Thread.sleep(TICK_MS * 10);
                    continue;
                }

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
                        int targetId = parseObjId(decision.getTargetId());
                        wiring.executeCombat(decision, snapshot.x, snapshot.y, snapshot.z, targetId);
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
                            // TIM-001 fix (flag OFF by default): proactive FAR travel when idle —
                            // route to a real farm-zone center / far point instead of a ±900 hop.
                            long now = System.currentTimeMillis();
                            if (now - lastRoute > phase0.getMovementIdleRouteMs())
                            {
                                lastRoute = now;
                                RouteGoal goal = zoneRouter.pick(snapshot.level,
                                    snapshot.x, snapshot.y, snapshot.z,
                                    phase0.getMovementMinRadius(), phase0.getMovementMaxRadius());
                                if (goal != null)
                                {
                                    telemetry.recordMove(account, snapshot.x, snapshot.y, snapshot.z,
                                        goal.x, goal.y, goal.z, goal.label, goal.reason);
                                    wiring.moveTo(snapshot.x, snapshot.y, snapshot.z,
                                        goal.x, goal.y, goal.z);
                                    info.state = "travel:" + goal.label;
                                    info.thought = goal.reason;
                                    LOGGER.info("[FleetPlay] " + account + " ROUTE -> " + goal.label
                                        + " (" + goal.x + "," + goal.y + "," + goal.z + ") "
                                        + goal.reason);
                                }
                            }
                        }
                        else if (System.currentTimeMillis() - lastWander > WANDER_INTERVAL_MS)
                        {
                            lastWander = System.currentTimeMillis();
                            int nx = snapshot.x + span(WANDER_RADIUS);
                            int ny = snapshot.y + span(WANDER_RADIUS);
                            info.state = "wander";
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

    private static void startDashboard(int port) throws IOException
    {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> respond(exchange, 200, "text/html; charset=utf-8", loadDashboard()));
        server.createContext("/api/players", exchange -> respond(exchange, 200, "application/json; charset=utf-8", playersJson()));
        server.createContext("/api/landmarks", exchange -> respond(exchange, 200, "application/json; charset=utf-8", landmarksJson()));
        server.createContext("/telemetry", exchange -> respond(exchange, 200, "text/plain; charset=utf-8",
            MoveTelemetry.getInstance().report().getBytes(StandardCharsets.UTF_8)));
        server.createContext("/json", exchange -> respond(exchange, 200, "application/json; charset=utf-8", playersJson()));
        server.createContext("/report", exchange -> respond(exchange, 200, "text/plain; charset=utf-8",
            AIMonitorDashboard.getInstance().generateReport().getBytes(StandardCharsets.UTF_8)));
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

    // Real Interlude town landmarks (source: phase0/town/VendorDatabase.java centers + TI creation point).
    private static final String[] TOWN_NAMES = { "TalkingIsland", "Gludio", "Dion", "Giran", "Oren", "HuntersVillage", "Aden" };
    private static final int[][] TOWNS =
    {
        { -71338, 258271, -3104 },   // TalkingIsland (creation point)
        { -14674, 123346, -3112 },   // Gludio
        { 18574, 145346, -3096 },    // Dion
        { 80874, 147346, -3464 },    // Giran
        { 83074, 53246, -1568 },     // Oren
        { 116974, 76246, -2728 },    // HuntersVillage
        { 147446, 27046, -2208 }     // Aden
    };

    private static byte[] landmarksJson()
    {
        StringBuilder sb = new StringBuilder("{\"towns\":[");
        for (int i = 0; i < TOWNS.length; i++)
        {
            if (i > 0) sb.append(',');
            sb.append("{\"name\":\"").append(TOWN_NAMES[i])
              .append("\",\"x\":").append(TOWNS[i][0])
              .append(",\"y\":").append(TOWNS[i][1])
              .append(",\"z\":").append(TOWNS[i][2]).append('}');
        }
        sb.append("]}");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

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

    private static String jsonEscape(String s)
    {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static byte[] playersJson()
    {
        StringBuilder sb = new StringBuilder("{\"bots\":[");
        boolean first = true;
        for (BotInfo b : BOTS.values())
        {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"account\":\"").append(jsonEscape(b.account))
              .append("\",\"charId\":").append(b.charId)
              .append(",\"name\":\"").append(jsonEscape(b.charName)).append('"')
              .append(",\"level\":").append(b.level)
              .append(",\"exp\":").append(b.exp)
              .append(",\"sp\":").append(b.sp)
              .append(",\"hp\":").append(b.hp).append(",\"hpMax\":").append(b.hpMax)
              .append(",\"mp\":").append(b.mp).append(",\"mpMax\":").append(b.mpMax)
              .append(",\"cp\":").append(b.cp).append(",\"cpMax\":").append(b.cpMax)
              .append(",\"x\":").append(b.x).append(",\"y\":").append(b.y).append(",\"z\":").append(b.z)
              .append(",\"movedLast60\":").append(Math.round(b.movedLast60))
              .append(",\"movesSent\":").append(b.movesSent)
              .append(",\"heading\":").append(b.heading)
              .append(",\"load\":").append(b.load).append(",\"maxLoad\":").append(b.maxLoad)
              .append(",\"weapon\":").append(b.weapon ? "true" : "false")
              .append(",\"adena\":").append(b.adena)
              .append(",\"invPct\":").append(b.invPct).append(",\"itemCount\":").append(b.itemCount)
              .append(",\"mobs\":").append(b.mobs).append(",\"npcs\":").append(b.npcs);
            sb.append(",\"items\":[");
            int[][] it = b.items;
            if (it != null)
            {
                for (int i = 0; i < it.length; i++)
                {
                    if (i > 0) sb.append(',');
                    sb.append('[').append(it[i][0]).append(',').append(it[i][1]).append(']');
                }
            }
            sb.append("],\"ents\":[");
            int[][] en = b.ents;
            if (en != null)
            {
                for (int i = 0; i < en.length; i++)
                {
                    if (i > 0) sb.append(',');
                    sb.append('[').append(en[i][0]).append(',').append(en[i][1])
                      .append(',').append(en[i][2]).append(',').append(en[i][3]).append(',').append(en[i][4]).append(']');
                }
            }
            sb.append("],\"target\":");
            if (b.targetObjId > 0 && b.targetDist > 0)
            {
                sb.append("{\"objId\":").append(b.targetObjId)
                  .append(",\"kind\":").append(b.targetKind)
                  .append(",\"label\":\"").append(jsonEscape(b.targetLabel)).append('"')
                  .append(",\"x\":").append(b.targetX).append(",\"y\":").append(b.targetY)
                  .append(",\"z\":").append(b.targetZ)
                  .append(",\"d\":").append((int) b.targetDist).append('}');
            }
            else
            {
                sb.append("null");
            }
            sb.append(",\"action\":\"").append(jsonEscape(b.action))
              .append("\",\"thought\":\"").append(jsonEscape(b.thought))
              .append("\",\"state\":\"").append(jsonEscape(b.state)).append('"')
              .append(",\"online\":").append(b.connected && b.loggedIn ? "true" : "false")
              .append(",\"uptimeSec\":").append((System.currentTimeMillis() - b.sessionStartMs) / 1000)
              .append('}');
        }
        sb.append("],\"entities\":[");
        // Merge all bots' entity views, deduplicated by object id, so the map draws mobs/NPCs once.
        java.util.LinkedHashMap<Integer, int[]> merged = new java.util.LinkedHashMap<>();
        for (BotInfo b : BOTS.values())
        {
            int[][] en = b.ents;
            if (en == null) continue;
            for (int[] e : en)
            {
                merged.putIfAbsent(e[0], e);
            }
        }
        boolean firstEntity = true;
        for (int[] e : merged.values())
        {
            if (!firstEntity) sb.append(',');
            firstEntity = false;
            String label = e[1] == 1 ? "mob#" + e[0] : (e[1] == 2 ? "player#" + e[0] : "npc#" + e[0]);
            sb.append("{\"objId\":").append(e[0]).append(",\"kind\":").append(e[1])
              .append(",\"label\":\"").append(label).append('"')
              .append(",\"x\":").append(e[2]).append(",\"y\":").append(e[3]).append(",\"z\":").append(e[4]).append('}');
        }
        sb.append("]}");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
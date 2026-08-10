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

import com.aiplayer.engine.AIPlayer;
import com.aiplayer.engine.CombatDecision;
import com.aiplayer.engine.GameServerClient;
import com.aiplayer.monitor.AIMonitorDashboard;
import com.aiplayer.phase0.BotSnapshot;
import com.aiplayer.phase0.Phase0Wiring;
import com.aiplayer.phase0.combat.TargetSelector;
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.protocol.PacketLogger;
import com.aiplayer.protocol.PacketLogger.EntityInfo;

/**
 * Launches a fleet of real AI Players against the live H5 stack and exposes a light
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
        public volatile int level;
        public volatile int hp;
        public volatile int hpMax;
        public volatile int x;
        public volatile int y;
        public volatile int z;
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
            info.connected = true;
            info.loggedIn = true;
            LOGGER.info("[FleetPlay] " + account + " ENTERED WORLD");

            long lastWander = 0;
            while (true)
            {
                BotSnapshot snapshot = BotSnapshot.from(account, logger);
                info.level = snapshot.level;
                info.hp = snapshot.hpCurrent;
                info.hpMax = snapshot.hpMax;
                info.x = snapshot.x;
                info.y = snapshot.y;
                info.z = snapshot.z;
                info.lastSeenMs = System.currentTimeMillis();
                AIMonitorDashboard.getInstance().updatePlayerStats(player);

                if (snapshot.hpCurrent <= 0)
                {
                    info.state = "dead";
                    Thread.sleep(TICK_MS * 10);
                    continue;
                }

                CombatDecision decision = player.getCombatAI().makeDecision();
                info.state = decision.getAction().toString();

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
        server.createContext("/", exchange -> respond(exchange, 200, "text/html; charset=utf-8", htmlPage()));
        server.createContext("/json", exchange -> respond(exchange, 200, "application/json", jsonStats()));
        server.createContext("/report", exchange -> respond(exchange, 200, "text/plain; charset=utf-8",
            AIMonitorDashboard.getInstance().generateReport().getBytes(StandardCharsets.UTF_8)));
        server.start();
        LOGGER.info("[FleetPlay] dashboard live on http://localhost:" + port);
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

    private static byte[] htmlPage()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\"><meta http-equiv=\"refresh\" content=\"2\"><title>AI Fleet Dashboard</title><style>")
          .append("body{font-family:monospace;background:#111;color:#cfc;padding:20px}")
          .append("h1{color:#8f8}.up{color:#6f6}.bad{color:#f66}.dim{color:#888}")
          .append("table{border-collapse:collapse;margin-top:12px}td,th{border:1px solid #444;padding:4px 10px;text-align:left}")
          .append("</style></head><body>")
          .append("<h1>🤖 AI Fleet — Live</h1>")
          .append("<p class=\"dim\">server 127.0.0.1:7777 · ").append(BOTS.size()).append(" bots · auto-refresh 2s · level '-' = H5 StatusUpdate carries no LEVEL attr yet (known parse gap)</p>")
          .append("<table><tr><th>Account</th><th>charId</th><th>Level</th><th>HP</th><th>State</th><th>Connected</th></tr>");
        for (BotInfo b : BOTS.values())
        {
            String status = b.connected && b.loggedIn ? "<span class=\"up\">ONLINE</span>" : "<span class=\"bad\">OFFLINE</span>";
            sb.append("<tr><td>").append(b.account).append("</td><td>").append(b.charId)
              .append("</td><td>").append(b.level > 0 ? String.valueOf(b.level) : "-").append("</td><td>")
              .append(b.hp).append('/').append(b.hpMax).append("</td><td>")
              // self-coords omitted from the dashboard: PacketLogger's self-position parse is offset for H5 (known gap, Audit/43).
              .append("</td><td class=\"dim\">").append(b.state).append("</td><td>").append(status).append("</td></tr>");
        }
        sb.append("</table></body></html>");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] jsonStats()
    {
        StringBuilder sb = new StringBuilder("{\"bots\":[");
        boolean first = true;
        for (BotInfo b : BOTS.values())
        {
            if (!first)
            {
                sb.append(',');
            }
            first = false;
            sb.append("{\"account\":\"").append(b.account)
              .append("\",\"charId\":").append(b.charId)
              .append(",\"level\":").append(b.level)
              .append(",\"hp\":").append(b.hp)
              .append(",\"hpMax\":").append(b.hpMax)
              .append(",\"state\":\"").append(b.state).append('"')
              .append(",\"online\":").append(b.connected && b.loggedIn ? "true" : "false")
              .append('}');
        }
        sb.append("]}");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
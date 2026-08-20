package com.aiplayer.web;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.aiplayer.core.BotInfo;
import com.aiplayer.behavior.movement.MoveTelemetry;
import com.aiplayer.monitor.AIMonitorDashboard;

/**
 * EP-4 extraction from FleetPlay: boots the fleet dashboard webserver. Registers ONLY / (SPA),
 * /json (legacy combined payload) and /report (text) here — every JSON route (/api/v1/* plus the
 * pre-v1 /api/players + /api/landmarks aliases) is wired exclusively through {@link DashboardApi}.
 * The shared fleet structures (bot map, rings, metrics) are passed in by the launcher; they are
 * the same instances the bot sessions write to, so the dashboard reads live state.
 */
public final class DashboardBoot
{
    private static final Logger LOGGER = Logger.getLogger(DashboardBoot.class.getName());

    private DashboardBoot()
    {
    }

    public static void boot(int port, int fleetSize, ConcurrentHashMap<String, BotInfo> bots,
                            EventRing events, HistoryRing history, FleetMetrics metrics) throws IOException
    {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        DashboardApi.Config cfg = new DashboardApi.Config(fleetSize);
        DashboardApi api = new DashboardApi(bots, System.currentTimeMillis(), cfg,
            MoveTelemetry.getInstance(), events, history, metrics);
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
        try (java.io.InputStream in = DashboardBoot.class.getClassLoader().getResourceAsStream("dashboard/index.html"))
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
}

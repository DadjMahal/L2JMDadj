package com.aiplayer.web;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.aiplayer.core.BotInfo;
import com.aiplayer.behavior.movement.MoveTelemetry;
import com.aiplayer.behavior.town.VendorDatabase;
import com.aiplayer.web.EventRing;
import com.aiplayer.web.EventRing.Json;
import com.aiplayer.protocol.PacketLogger;

/**
 * WPT-01 — versioned REST API (/api/v1/*) plus every dashboard JSON serializer,
 * extracted out of FleetPlay so the launcher only wires routes to it.
 *
 * <p>The v1 contract is FROZEN in Documentation/TASKS.md ?11 (the single web-panel task
 * board): /api/v1/bots, /entities, /landmarks, /events, /health, /config. Cline#2 (SPA)
 * and Cline#3 (protocol telemetry) build against this exact shape. Legacy combined
 * payloads (/json, /api/players) keep the pre-v1 SPA shape: bots + merged entities.
 */
public final class DashboardApi
{
    public static final String JSON = "application/json; charset=utf-8";

    /** Tunables exposed via /api/v1/config (WPT-05: POST applies live to these volatile fields). */
    public static final class Config
    {
        public final int fleetSize;
        public volatile int wanderRadius = 900;
        public volatile long wanderIntervalMs = 8000;
        public volatile long pollMs = 2000;
        public volatile String bind = "127.0.0.1";
        public volatile boolean tokenAuth = false;
        /** WPT-07: bearer token; when set, every mutating POST requires Authorization: Bearer <token>. */
        public volatile String token = null;

        public Config(int fleetSize)
        {
            this.fleetSize = fleetSize;
        }
    }

    /** Real Interlude town landmarks (behavior.town/VendorDatabase.java centers + TI creation point). */
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

    private final Map<String, BotInfo> bots;
    private final long startedAtMs;
    private final Config config;

    /** TIM-001 movement-evidence source for the legacy /json + /api/players payloads; null in tests/standalone. */
    private final MoveTelemetry telemetry;

    /** WPT-03 event feed ring (kill/level-up/skill-cast/damage/move/connect/disconnect); null in tests. */
    private final EventRing events;

    /** WPT-04 state-history ring -> /api/v1/history trails + CSV; null in tests. */
    private final HistoryRing history;

    /** WPT-08 aggregate fleet metrics -> /api/v1/health extended fields; created on demand. */
    private final FleetMetrics metrics;

    private final AtomicLong requests = new AtomicLong();

    /** Full wiring: legacy combined payloads also carry the live movedLast60/movesSent counters. */
    public DashboardApi(Map<String, BotInfo> bots, long startedAtMs, Config config, MoveTelemetry telemetry,
                        EventRing events, HistoryRing history, FleetMetrics metrics)
    {
        this.bots = bots;
        this.startedAtMs = startedAtMs;
        this.config = config;
        this.telemetry = telemetry;
        this.events = events;
        this.history = history;
        this.metrics = metrics;
    }

    /** Convenience: telemetry only, rings/metrics born on demand. */
    public DashboardApi(Map<String, BotInfo> bots, long startedAtMs, Config config, MoveTelemetry telemetry)
    {
        this(bots, startedAtMs, config, telemetry, null, null, null);
    }

    /** Tests / embedded use: legacy routes simply omit the movement counters. */
    public DashboardApi(Map<String, BotInfo> bots, long startedAtMs, Config config)
    {
        this(bots, startedAtMs, config, null, null, null, null);
    }

    /** WPT-08: get-or-create the shared metrics aggregate (lazily, so plain-constructor tests stay happy). */
    public synchronized FleetMetrics metrics()
    {
        if (metrics != null)
        {
            return metrics;
        }
        return new FleetMetrics(startedAtMs);
    }

    /** Registers every /api/v1/* context plus the pre-v1 SPA aliases (com.sun uses longest-prefix matching). */
    public void register(HttpServer server)
    {
        server.createContext("/api/v1/bots", this::handle);
        server.createContext("/api/v1/entities", this::handle);
        server.createContext("/api/v1/landmarks", this::handle);
        server.createContext("/api/v1/events", this::handle);
        server.createContext("/api/v1/history", this::handle);
        server.createContext("/api/v1/health", this::handle);
        server.createContext("/api/v1/config", this::handle);
        server.createContext("/api/v1/stream", this::handle);
        server.createContext("/api/v1", this::handle);
        // pre-v1 SPA aliases — src/main/resources/dashboard/index.html still polls these until Cline#2
        // migrates to /api/v1/*:
        server.createContext("/api/players", this::handle);
        server.createContext("/api/landmarks", this::handle);
    }

    private void handle(HttpExchange exchange) throws IOException
    {
        requests.incrementAndGet();
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        /* EP-6/S2: when a token is configured (DASH_TOKEN), every /api/v1/* request must carry it
           (Authorization: Bearer ... or ?token=...) — reads included, not just mutating POSTs. */
        if (!authorized(exchange))
        {
            respond(exchange, 401, "{\"error\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (path.equals("/api/v1/stream"))
        {
            stream(exchange);
            return;
        }
        if (method.equals("POST") && path.equals("/api/v1/config"))
        {
            // WPT-07: mutating endpoints require the bearer token when configured (default off).
            if (!authorized(exchange))
            {
                respond(exchange, 401, "{\"error\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            respond(exchange, 200, applyConfig(exchange));
            return;
        }
        if (path.equals("/api/v1/bots"))
        {
            respond(exchange, 200, botsJson());
        }
        else if (path.equals("/api/v1/entities"))
        {
            respond(exchange, 200, entitiesJson());
        }
        else if (path.equals("/api/v1/landmarks"))
        {
            respond(exchange, 200, landmarksJson());
        }
        else if (path.equals("/api/v1/events"))
        {
            respond(exchange, 200, eventsJson(exchange));
        }
        else if (path.equals("/api/v1/history"))
        {
            respond(exchange, 200, historyJson(exchange));
        }
        else if (path.equals("/api/v1/health"))
        {
            respond(exchange, 200, healthJson());
        }
        else if (path.equals("/api/v1/config"))
        {
            respond(exchange, 200, configJson());
        }
        else if (path.equals("/api/v1"))
        {
            respond(exchange, 200, indexJson());
        }
        else if (path.equals("/api/players"))
        {
            respond(exchange, 200, legacyJson());
        }
        else if (path.equals("/api/landmarks"))
        {
            respond(exchange, 200, landmarksJson());
        }
        else
        {
            respond(exchange, 404, "{\"error\":\"not found\"}".getBytes(StandardCharsets.UTF_8));
        }
    }

    // ============================ v1 serializers (FROZEN contract ?11) ============================

    /** GET /api/v1/bots -> {"bots":[{...}]} per-bot live state. */
    public byte[] botsJson()
    {
        return wrapArray("bots", botsBody());
    }

    /** GET /api/v1/entities -> {"entities":[{objId,kind,label,x,y,z}]} merged once per object id. */
    public byte[] entitiesJson()
    {
        return wrapArray("entities", entitiesBody());
    }

    /** GET /api/v1/landmarks -> {"towns":[{name,x,y,z}]} real coords (Interlude map). */
    public byte[] landmarksJson()
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

    /** GET /api/v1/events -> event feed (WPT-03 ring). Optional ?since=<seq> for idempotent replay. */
    public byte[] eventsJson(HttpExchange exchange)
    {
        long since = 0;
        String q = exchange != null && exchange.getRequestURI() != null ? exchange.getRequestURI().getRawQuery() : null;
        if (q != null)
        {
            for (String kv : q.split("&"))
            {
                if (kv.startsWith("since="))
                {
                    try
                    {
                        since = Long.parseLong(kv.substring(6));
                    }
                    catch (NumberFormatException ignored)
                    {
                        // malformed -> replay from start
                    }
                }
            }
        }
        if (events != null)
        {
            return events.eventsJsonSince(since).getBytes(StandardCharsets.UTF_8);
        }
        return "{\"events\":[]}".getBytes(StandardCharsets.UTF_8);
    }

    /** GET /api/v1/stream -> Server-Sent Events push: field-delta JSON per bot + new ring events (WPT-02). */
    private void stream(HttpExchange exchange) throws IOException
    {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);
        java.util.Map<String, String> last = new java.util.HashMap<>();
        long lastSeq = 0;
        OutputStream out = exchange.getResponseBody();
        try
        {
            long deadline = System.currentTimeMillis() + 100_000; // server-side max window; client reconnects
            while (System.currentTimeMillis() < deadline)
            {
                StringBuilder frame = new StringBuilder();
                // 1) per-bot field deltas (changed fields only)
                for (BotInfo b : bots.values())
                {
                    String sig = b.level + "|" + b.exp + "|" + b.hp + "|" + b.mp + "|"
                        + b.x + "|" + b.y + "|" + b.z + "|" + b.state + "|" + (b.connected && b.loggedIn);
                    String prev = last.put(b.account, sig);
                    if (prev != null && prev.equals(sig))
                    {
                        continue;
                    }
                    frame.append("data: {\"type\":\"delta\",\"bot\":\"").append(jsonEscape(b.account))
                        .append("\",\"level\":").append(b.level)
                        .append(",\"exp\":").append(b.exp)
                        .append(",\"hp\":").append(b.hp)
                        .append(",\"mp\":").append(b.mp)
                        .append(",\"x\":").append(b.x)
                        .append(",\"y\":").append(b.y)
                        .append(",\"z\":").append(b.z)
                        .append(",\"state\":\"").append(jsonEscape(b.state)).append('"')
                        .append(",\"online\":").append(b.connected && b.loggedIn ? "true" : "false")
                        .append("}\n\n");
                }
                // 2) fresh ring events (sequential replay, idempotent)
                if (events != null)
                {
                    long nowMax = events.lastSeq();
                    if (nowMax > lastSeq)
                    {
                        String sinceBody = events.eventsJsonSince(lastSeq);
                        frame.append("data: ").append(sinceBody).append("\n\n");
                        lastSeq = nowMax;
                    }
                }
                if (frame.length() > 0)
                {
                    out.write(frame.toString().getBytes(StandardCharsets.UTF_8));
                }
                out.write(": ping\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                Thread.sleep(1500);
            }
        }
        catch (InterruptedException ignored)
        {
            // stream socket closed
        }
        catch (IOException ignored)
        {
            // client disconnected
        }
        finally
        {
            try
            {
                out.close();
            }
            catch (IOException ignored)
            {
            }
        }
    }

    /** WPT-05: POST /api/v1/config body {"wanderRadius":..,"wanderIntervalMs":..,"pollMs":..} applied live. */
    private byte[] applyConfig(HttpExchange exchange) throws IOException
    {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Long wr = longValue(body, "wanderRadius");
        Long wi = longValue(body, "wanderIntervalMs");
        Long pm = longValue(body, "pollMs");
        if (wr != null && wr.longValue() >= 0) config.wanderRadius = wr.intValue();
        if (wi != null && wi.longValue() >= 0) config.wanderIntervalMs = wi.longValue();
        if (pm != null && pm.longValue() >= 0) config.pollMs = pm.longValue();
        return configJson();
    }

    /** WPT-07 + EP-6: when config.token is set, requests must present it — Authorization:
     *  Bearer <token> header or ?token=<token> query param (so browsers/scripts can authenticate). */
    private boolean authorized(HttpExchange exchange)
    {
        String token = config != null ? config.token : null;
        if (token == null || token.isEmpty())
        {
            return true;
        }
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.equals("Bearer " + token))
        {
            return true;
        }
        String query = exchange.getRequestURI().getRawQuery();
        if (query != null)
        {
            for (String pair : query.split("&"))
            {
                if (pair.equals("token=" + token))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static Long longValue(String body, String key)
    {
        int idx = body.indexOf("\"" + key + "\"");
        if (idx < 0)
        {
            return null;
        }
        int colon = body.indexOf(':', idx);
        if (colon < 0)
        {
            return null;
        }
        int end = colon + 1;
        while (end < body.length() && (Character.isDigit(body.charAt(end)) || body.charAt(end) == '-')) end++;
        try
        {
            return Long.parseLong(body.substring(colon + 1, end).trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /** GET /api/v1/history -> per-bot state trail (WPT-04 ring). Query: ?bot=<name>&from=<epochMs>&to=<epochMs>[&csv=1]. */
    public byte[] historyJson(HttpExchange exchange) throws IOException
    {
        if (history == null)
        {
            return "{\"bot\":\"\",\"history\":[]}".getBytes(StandardCharsets.UTF_8);
        }
        String bot = null;
        long from = 0;
        long to = Long.MAX_VALUE;
        boolean csv = false;
        String q = exchange != null && exchange.getRequestURI() != null ? exchange.getRequestURI().getRawQuery() : null;
        if (q != null)
        {
            for (String kv : q.split("&"))
            {
                if (kv.startsWith("bot="))
                {
                    bot = kv.substring(4);
                }
                else if (kv.startsWith("from="))
                {
                    from = parseLongSafe(kv.substring(5), 0L);
                }
                else if (kv.startsWith("to="))
                {
                    to = parseLongSafe(kv.substring(3), Long.MAX_VALUE);
                }
                else if (kv.equals("csv=1") || kv.equals("csv=true"))
                {
                    csv = true;
                }
            }
        }
        if (csv)
        {
            exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=utf-8");
            return history.toCsv(bot, from, to).getBytes(StandardCharsets.UTF_8);
        }
        return history.toJson(bot, from, to).getBytes(StandardCharsets.UTF_8);
    }

    /** GET /api/v1/health -> process + fleet health (WPT-08 metrics + WPT-03 event counts). */
    public byte[] healthJson()
    {
        int online = 0;
        for (BotInfo b : bots.values())
        {
            if (b.connected && b.loggedIn) online++;
        }
        FleetMetrics m = metrics();
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"status\":\"ok\"")
          .append(",\"uptimeSec\":").append((System.currentTimeMillis() - startedAtMs) / 1000)
          .append(",\"startedAtEpochMs\":").append(startedAtMs)
          .append(",\"botCount\":").append(bots.size())
          .append(",\"onlineCount\":").append(online)
          .append(",\"requestCount\":").append(m.requestCount())
          .append(",\"reconnectCount\":").append(m.reconnectCount())
          .append(",\"pktAgeLastMs\":").append(EventRing.Json.value(m.pktAgeLastMs()))
          .append(",\"pktAgeMaxMs\":").append(EventRing.Json.value(m.pktAgeMaxMs()))
          .append(",\"latencyAvgMs\":").append(EventRing.Json.value(m.latencyAvgMs()))
          .append(",\"events\":").append(events != null ? events.size() : 0)
          .append(",\"routes\":[\"/api/v1/bots\",\"/api/v1/entities\",\"/api/v1/landmarks\",")
          .append("\"/api/v1/events\",\"/api/v1/history\",\"/api/v1/health\",\"/api/v1/config\",")
          .append("\"/json\",\"/report\"]}");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static long parseLongSafe(String s, long dflt)
    {
        try
        {
            return Long.parseLong(s);
        }
        catch (NumberFormatException ignored)
        {
            return dflt;
        }
    }

    /** GET /api/v1/config -> live tunables (WPT-05 makes them settable). */
    public byte[] configJson()
    {
        return ("{\"fleetSize\":" + config.fleetSize
            + ",\"wanderRadius\":" + config.wanderRadius
            + ",\"wanderIntervalMs\":" + config.wanderIntervalMs
            + ",\"pollMs\":" + config.pollMs
            + ",\"bind\":\"" + config.bind + "\""
            + ",\"tokenAuth\":" + config.tokenAuth + '}')
            .getBytes(StandardCharsets.UTF_8);
    }

    /** GET /api/v1 -> route index. */
    public byte[] indexJson()
    {
        return ("{\"api\":\"v1\",\"note\":\"contract frozen in Documentation/TASKS.md section 11\""
            + ",\"routes\":[\"/api/v1/bots\",\"/api/v1/entities\",\"/api/v1/landmarks\","
            + "\"/api/v1/events\",\"/api/v1/health\",\"/api/v1/config\"]}")
            .getBytes(StandardCharsets.UTF_8);
    }

    // ============================ legacy combined payload (pre-v1 SPA) ============================

    /** Old combined shape used by /json and /api/players: {"bots":[...],"entities":[...]}. */
    /** Per-bot {prevExp, prevKills, prevMs} so the dashboard can report XP/min + kills/min (S9-T06). */
    private final ConcurrentHashMap<String, long[]> rateTrack = new ConcurrentHashMap<>();

    public byte[] legacyJson()
    {
        return ("{\"bots\":[" + legacyBotsBody() + "],\"entities\":[" + entitiesBody() + "]}").getBytes(StandardCharsets.UTF_8);
    }

    // ============================ builders ============================

    private byte[] wrapArray(String key, String body)
    {
        return ("{\"" + key + "\":[" + body + "]}").getBytes(StandardCharsets.UTF_8);
    }

    /** Comma-separated bot objects (no outer array) - the frozen section 11 /api/v1/bots shape. */
    private String botsBody()
    {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (BotInfo b : bots.values())
        {
            if (!first) sb.append(',');
            first = false;
            sb.append(botObject(b, false));
        }
        return sb.toString();
    }

    /** Comma-separated bot objects for the legacy combined payload (/json, /api/players): the same
     *  v1 bot object plus the TIM-001 movement-evidence counters INSIDE the object (valid fields,
     *  not array siblings) when a MoveTelemetry is wired. */
    private String legacyBotsBody()
    {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (BotInfo b : bots.values())
        {
            if (!first) sb.append(',');
            first = false;
            sb.append(botObject(b, true));
        }
        return sb.toString();
    }

    /** Single bot object, exact frozen section 11 /api/v1/bots shape. When {@code includeTelemetry}
     *  is true and a MoveTelemetry is wired, the TIM-001 movement-evidence counters are emitted as
     *  fields INSIDE the object (before the closing brace) — not appended after it, which would
     *  produce invalid JSON (bare comma values in the array). null in tests/standalone. */
    /** Compute and cache per-bot xp/min + kills/min deltas since the previous dashboard poll (S9-T06). */
    private void appendRateFields(StringBuilder sb, BotInfo b)
    {
        long now = System.currentTimeMillis();
        long[] prev = rateTrack.get(b.account);
        double xpm = 0, kpm = 0;
        if (prev != null && prev.length == 3)
        {
            long dt = Math.max(1000, now - prev[2]);
            long dexp = Math.max(0, b.exp - prev[0]);
            long dk = Math.max(0, b.killCount - prev[1]);
            double mins = dt / 60000.0;
            if (mins > 0)
            {
                xpm = dexp / mins;
                kpm = dk / mins;
            }
        }
        rateTrack.put(b.account, new long[] { b.exp, b.killCount, now });
        sb.append(",\"xpPerMin\":").append(Math.round(xpm))
          .append(",\"killsPerMin\":").append(Math.round(kpm * 10) / 10.0);
    }

    private String botObject(BotInfo b, boolean includeTelemetry)
    {
        StringBuilder sb = new StringBuilder();
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
          .append(",\"heading\":").append(b.heading)
          .append(",\"load\":").append(b.load).append(",\"maxLoad\":").append(b.maxLoad)
          .append(",\"weapon\":").append(b.weapon ? "true" : "false")
          .append(",\"adena\":").append(b.adena)
          .append(",\"invPct\":").append(b.invPct).append(",\"itemCount\":").append(b.itemCount)
          .append(",\"mobs\":").append(b.mobs).append(",\"npcs\":").append(b.npcs)
          .append(",\"race\":\"").append(jsonEscape(b.race)).append('"')
          .append(",\"killCount\":").append(b.killCount)
          .append(",\"packetsRead\":").append(b.packetsRead)
          .append(",\"idleTimeouts\":").append(b.idleTimeouts)
          .append(",\"hopSuccessPct\":").append(b.hopSuccessPct);
        appendRateFields(sb, b);
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
        sb.append("],\"quests\":{\"active\":").append(b.questCount)
          .append(",\"total\":").append(b.totalQuestCount).append(",\"list\":[");
        int[][] aq = b.activeQuests;
        if (aq != null)
        {
            for (int i = 0; i < aq.length; i++)
            {
                if (i > 0) sb.append(',');
                sb.append('[').append(aq[i][0]).append(',').append(aq[i][1]).append(']');
            }
        }
        sb.append("]},\"target\":");
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
          .append(",\"pktAgeMs\":").append(b.getPktAgeMs())
          .append(",\"lastSeenMs\":").append(b.lastSeenMs);
        if (includeTelemetry && telemetry != null)
        {
            sb.append(",\"movedLast60\":").append(Math.round(telemetry.movedLast(60_000, b.account)))
              .append(",\"movesSent\":").append(telemetry.moveCount(b.account));
        }
        if (includeTelemetry)
        {
            // EB-14: structured goal/sub-goal + nearest cooldown — extended row only (/json,
            // /api/players); the frozen /api/v1/bots object keeps its exact section-11 shape.
            sb.append(",\"goal\":\"").append(jsonEscape(b.goal)).append('"')
              .append(",\"subGoal\":\"").append(jsonEscape(b.subGoal)).append('"')
              .append(",\"cooldownSec\":").append(b.cooldownUntilSec);
        }
        sb.append('}');
        return sb.toString();
    }

    /** Comma-separated merged entity objects (deduped by object id), no outer array. */
    private String entitiesBody()
    {
        StringBuilder sb = new StringBuilder();
        LinkedHashMap<Integer, int[]> merged = new LinkedHashMap<>();
        for (BotInfo b : bots.values())
        {
            int[][] en = b.ents;
            if (en == null) continue;
            for (int[] e : en)
            {
                merged.putIfAbsent(e[0], e);
            }
        }
        boolean first = true;
        for (int[] e : merged.values())
        {
            if (!first) sb.append(',');
            first = false;
            String label = e[1] == 1 ? "mob#" + e[0] : (e[1] == 2 ? "player#" + e[0] : "npc#" + e[0]);
            sb.append("{\"objId\":").append(e[0]).append(",\"kind\":").append(e[1])
              .append(",\"label\":\"").append(label).append('"')
              .append(",\"x\":").append(e[2]).append(",\"y\":").append(e[3]).append(",\"z\":").append(e[4]).append('}');
        }
        return sb.toString();
    }

    static String jsonEscape(String s)
    {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void respond(HttpExchange exchange, int code, byte[] body) throws IOException
    {
        byte[] bytes = body != null ? body : new byte[0];
        if (exchange.getResponseHeaders().getFirst("Content-Type") == null)
        {
            exchange.getResponseHeaders().set("Content-Type", JSON);
        }
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody())
        {
            out.write(bytes);
        }
    }

    /** Top-5 inventory items by count: {itemId, count} pairs, from the real ItemList (0x1B) parse.
     *  EP-4: moved from FleetPlay — dashboard payload builder, fed by the session loop. */
    public static int[][] topItems(PacketLogger logger)
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

    /** Snapshot of nearby entities for the map: {objId, kind(0 npc 1 hostile 2 player), x, y, z}, bounded.
     *  EP-4: moved from FleetPlay — dashboard payload builder, fed by the session loop. */
    public static int[][] entitySnapshot(PacketLogger logger)
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
}

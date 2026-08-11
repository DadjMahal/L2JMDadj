package com.aiplayer.web;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.aiplayer.examples.BotInfo;

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

    /** Tunables exposed via /api/v1/config (WPT-05 extends into a live editor). */
    public static final class Config
    {
        public final int fleetSize;
        public int wanderRadius = 900;
        public long wanderIntervalMs = 8000;
        public long pollMs = 2000;
        public String bind = "127.0.0.1";
        public boolean tokenAuth = false;

        public Config(int fleetSize)
        {
            this.fleetSize = fleetSize;
        }
    }

    /** Real Interlude town landmarks (phase0/town/VendorDatabase.java centers + TI creation point). */
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
    private final AtomicLong requests = new AtomicLong();

    public DashboardApi(Map<String, BotInfo> bots, long startedAtMs, Config config)
    {
        this.bots = bots;
        this.startedAtMs = startedAtMs;
        this.config = config;
    }

    /** Registers every /api/v1/* context (com.sun HttpServer uses longest-prefix matching). */
    public void register(HttpServer server)
    {
        server.createContext("/api/v1/bots", this::handle);
        server.createContext("/api/v1/entities", this::handle);
        server.createContext("/api/v1/landmarks", this::handle);
        server.createContext("/api/v1/events", this::handle);
        server.createContext("/api/v1/health", this::handle);
        server.createContext("/api/v1/config", this::handle);
        server.createContext("/api/v1", this::handle);
    }

    private void handle(HttpExchange exchange) throws IOException
    {
        requests.incrementAndGet();
        String path = exchange.getRequestURI().getPath();
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
            respond(exchange, 200, eventsJson());
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

    /** GET /api/v1/events -> event feed; WPT-03 fills the ring buffer, empty now. */
    public byte[] eventsJson()
    {
        return "{\"events\":[]}".getBytes(StandardCharsets.UTF_8);
    }

    /** GET /api/v1/health -> process + fleet health (WPT-08 expands metrics). */
    public byte[] healthJson()
    {
        int online = 0;
        for (BotInfo b : bots.values())
        {
            if (b.connected && b.loggedIn) online++;
        }
        return ("{\"status\":\"ok\",\"uptimeSec\":" + (System.currentTimeMillis() - startedAtMs) / 1000
            + ",\"startedAtEpochMs\":" + startedAtMs
            + ",\"botCount\":" + bots.size()
            + ",\"onlineCount\":" + online
            + ",\"requestCount\":" + requests.get()
            + ",\"routes\":[\"/api/v1/bots\",\"/api/v1/entities\",\"/api/v1/landmarks\","
            + "\"/api/v1/events\",\"/api/v1/health\",\"/api/v1/config\",\"/json\",\"/report\"]}")
            .getBytes(StandardCharsets.UTF_8);
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
    public byte[] legacyJson()
    {
        return wrapArray("bots", botsBody() + ",\"entities\":[" + entitiesBody() + "]");
    }

    // ============================ builders ============================

    private byte[] wrapArray(String key, String body)
    {
        return ("{\"" + key + "\":[" + body + "]}").getBytes(StandardCharsets.UTF_8);
    }

    /** Comma-separated bot objects (no outer array). */
    private String botsBody()
    {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (BotInfo b : bots.values())
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
              .append(",\"pktAgeMs\":").append(b.getPktAgeMs())
              .append(",\"lastSeenMs\":").append(b.lastSeenMs)
              .append('}');
        }
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
        exchange.getResponseHeaders().set("Content-Type", JSON);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody())
        {
            out.write(bytes);
        }
    }
}
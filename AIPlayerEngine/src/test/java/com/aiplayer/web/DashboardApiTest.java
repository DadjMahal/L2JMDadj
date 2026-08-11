package com.aiplayer.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;

import com.aiplayer.examples.BotInfo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WPT-01 — endpoint smoke for the frozen v1 API contract (Documentation/TASKS.md section 11).
 *
 * <p>Boots the real DashboardApi on an ephemeral loopback port (com.sun.net.httpserver, port 0)
 * and GETs every route over a real HTTP stack (no mocks): asserts HTTP 200, Content-Type
 * application/json and the required top-level key for the events/landmarks/config/health routes
 * (plus the bots/entities routes and the legacy /api/players + /api/landmarks aliases). Every
 * body is additionally validated as well-formed JSON by the dependency-free MiniJson parser.
 */
public class DashboardApiTest
{
    private HttpServer server;
    private DashboardApi api;

    @BeforeEach
    void startApi() throws IOException
    {
        Map<String, BotInfo> bots = new LinkedHashMap<>();

        BotInfo b1 = new BotInfo("ai_dash_01", 100001);
        b1.charName = "DashBot1";
        b1.level = 21;
        b1.exp = 1_400_000;
        b1.hp = 500; b1.hpMax = 1000;
        b1.mp = 90; b1.mpMax = 200;
        b1.cp = 700; b1.cpMax = 1000;
        b1.x = -71338; b1.y = 258271; b1.z = -3104; b1.heading = 500;
        b1.load = 1000; b1.maxLoad = 50_000;
        b1.weapon = true;
        b1.adena = 12_345; b1.invPct = 12; b1.itemCount = 8;
        b1.items = new int[][] { { 57, 12345 }, { 4720, 50 } };
        b1.mobs = 3; b1.npcs = 5;
        b1.ents = new int[][] { { 900, 1, -71300, 258200, -3104 }, { 901, 0, -71400, 258300, -3104 } };
        b1.targetObjId = 900;
        b1.targetKind = 1;
        b1.targetLabel = "mob#900";
        b1.targetX = -71300; b1.targetY = 258200; b1.targetZ = -3104; b1.targetDist = 120.0;
        b1.action = "attack";
        b1.thought = "kill it";
        b1.state = "ATTACK";
        b1.connected = true;
        b1.loggedIn = true;
        bots.put(b1.account, b1);

        BotInfo b2 = new BotInfo("ai_dash_02", 100002);
        b2.charName = "DashBot2";
        bots.put(b2.account, b2);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api = new DashboardApi(bots, System.currentTimeMillis(), new DashboardApi.Config(2));
        api.register(server);
        server.start();
    }

    @AfterEach
    void stopApi()
    {
        if (server != null)
        {
            server.stop(0);
        }
    }

    @Test
    void eventsLandmarksConfigHealthRoutesReturn200JsonWithRequiredKeys()
    {
        Map<String, Object> events = assertJsonRoute("/api/v1/events", "events");
        assertTrue(((List<?>) events.get("events")).isEmpty(), "events ring is empty until WPT-03");

        Map<String, Object> landmarks = assertJsonRoute("/api/v1/landmarks", "towns");
        List<?> towns = (List<?>) landmarks.get("towns");
        assertEquals(7, towns.size(), "one entry per real Interlude town");
        Map<?, ?> firstTown = (Map<?, ?>) towns.get(0);
        assertTrue(firstTown.containsKey("name"));
        assertTrue(firstTown.containsKey("x"));
        assertTrue(firstTown.containsKey("y"));
        assertTrue(firstTown.containsKey("z"));

        Map<String, Object> config = assertJsonRoute("/api/v1/config", "fleetSize");
        assertEquals(2L, config.get("fleetSize"));
        assertEquals(900L, config.get("wanderRadius"));
        assertEquals(Boolean.FALSE, config.get("tokenAuth"));
        assertTrue(config.containsKey("bind"));

        Map<String, Object> health = assertJsonRoute("/api/v1/health", "status");
        assertEquals("ok", health.get("status"));
        assertEquals(2L, health.get("botCount"));
        assertEquals(1L, health.get("onlineCount"));
        assertTrue(health.containsKey("uptimeSec"));
        assertTrue(health.containsKey("startedAtEpochMs"));
        List<?> routes = (List<?>) health.get("routes");
        assertTrue(routes.contains("/api/v1/bots"));
        assertTrue(routes.contains("/json"));
    }

    @Test
    void botsAndEntitiesRoutesReturnFrozenShapes()
    {
        Map<String, Object> index = assertJsonRoute("/api/v1", "routes");
        assertEquals("v1", index.get("api"));
        assertEquals(6, ((List<?>) index.get("routes")).size());

        Map<String, Object> bots = assertJsonRoute("/api/v1/bots", "bots");
        List<?> botList = (List<?>) bots.get("bots");
        assertEquals(2, botList.size());
        Map<?, ?> first = (Map<?, ?>) botList.get(0);
        assertEquals("DashBot1", first.get("name"));
        assertEquals(21L, first.get("level"));
        assertEquals("ATTACK", first.get("state"));
        assertTrue((Boolean) first.get("online"));
        assertTrue(first.containsKey("pktAgeMs"));
        assertTrue(first.containsKey("lastSeenMs"));
        assertFalse(first.containsKey("movedLast60"),
            "frozen /api/v1/bots objects must stay exact per section 11");

        Map<String, Object> entities = assertJsonRoute("/api/v1/entities", "entities");
        List<?> entList = (List<?>) entities.get("entities");
        assertEquals(2, entList.size(), "both bots' entity views merged and deduped");
        Map<?, ?> ent = (Map<?, ?>) entList.get(0);
        assertEquals(900L, ent.get("objId"));
        assertTrue(ent.containsKey("kind"));
        assertTrue(ent.containsKey("x"));
        assertTrue(ent.containsKey("y"));
        assertTrue(ent.containsKey("z"));
    }

    @Test
    void legacyRoutesAndCombinedPayloadStayValid()
    {
        Map<String, Object> players = assertJsonRoute("/api/players", "bots");
        assertTrue(players.containsKey("entities"));
        Map<String, Object> land = assertJsonRoute("/api/landmarks", "towns");
        assertEquals(7, ((List<?>) land.get("towns")).size());

        // FleetPlay serves exactly this payload at /json
        Map<String, Object> combined = MiniJson.parse(new String(api.legacyJson(), StandardCharsets.UTF_8));
        assertEquals(2, ((List<?>) combined.get("bots")).size());
        assertTrue(combined.containsKey("entities"));
    }

    @Test
    void unknownV1PathReturns404ErrorJson()
    {
        Resp r = get("/api/v1/does-not-exist");
        assertEquals(404, r.status);
        Map<String, Object> err = MiniJson.parse(r.body);
        assertTrue(err.containsKey("error"));
    }

    @Test
    void emptyFleetStillReturnsWellFormedEmptyArrays() throws IOException
    {
        server.stop(0);
        Map<String, BotInfo> empty = new LinkedHashMap<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api = new DashboardApi(empty, System.currentTimeMillis(), new DashboardApi.Config(0));
        api.register(server);
        server.start();

        Map<String, Object> bots = assertJsonRoute("/api/v1/bots", "bots");
        assertTrue(((List<?>) bots.get("bots")).isEmpty());
        Map<String, Object> entities = assertJsonRoute("/api/v1/entities", "entities");
        assertTrue(((List<?>) entities.get("entities")).isEmpty());
        Map<String, Object> health = assertJsonRoute("/api/v1/health", "status");
        assertEquals(0L, health.get("botCount"));
    }

    @Test
    void wiredRingsExposeEventsAndHistoryViaV1Routes() throws IOException
    {
        server.stop(0);
        Map<String, BotInfo> bots2 = new LinkedHashMap<>(bots());
        EventRing rings = new EventRing();
        rings.add(EventRing.TYPE_CONNECT, "ai_dash_01", java.util.Map.of("level", 21));
        rings.add(EventRing.TYPE_MOVE, "ai_dash_01", java.util.Map.of("to", "-70000,258000"));
        HistoryRing historyRing = new HistoryRing();
        historyRing.register("ai_dash_01", -71300, 258200, -3104, 21, 1_400_000, 500, 1000);
        historyRing.register("ai_dash_01", -71200, 258100, -3104, 21, 1_400_000, 490, 1000);
        FleetMetrics m = new FleetMetrics(System.currentTimeMillis());
        api = new DashboardApi(bots2, System.currentTimeMillis(), new DashboardApi.Config(2),
            null, rings, historyRing, m);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api.register(server);
        server.start();

        Map<String, Object> events = assertJsonRoute("/api/v1/events", "events");
        List<?> ev = (List<?>) events.get("events");
        assertEquals(2, ev.size(), "ring events appear on the v1 feed");
        Map<?, ?> first = (Map<?, ?>) ev.get(0);
        assertTrue(first.containsKey("seq"));
        assertTrue(first.containsKey("t"));
        assertTrue(first.containsKey("type"));
        assertTrue(first.containsKey("bot"));
        assertTrue(first.containsKey("data"));
        assertEquals("connect", first.get("type"));

        // ?since=<lastSeq> replay returns only newer events (none), still well-formed JSON.
        long lastSeq = (Long) ((Map<?, ?>) ev.get(ev.size() - 1)).get("seq");
        Map<String, Object> replay = assertJsonRoute("/api/v1/events?since=" + lastSeq, "events");
        assertTrue(((List<?>) replay.get("events")).isEmpty(), "replay since last seq is empty");

        Map<String, Object> history = assertJsonRoute("/api/v1/history?bot=ai_dash_01", "history");
        assertEquals(2, ((List<?>) history.get("history")).size());
        Map<String, Object> trail = assertJsonRoute("/api/v1/history?bot=ai_dash_01&from=0&to=9999999999999", "history");
        assertEquals("ai_dash_01", trail.get("bot"));
        assertTrue(trail.containsKey("from"));
        assertTrue(trail.containsKey("to"));

        // CSV export: text/csv content type + header row.
        Resp csv = get("/api/v1/history?bot=ai_dash_01&csv=1");
        assertEquals(200, csv.status);
        assertNotNull(csv.contentType);
        assertTrue(csv.contentType.startsWith("text/csv"), "CSV route content type was '" + csv.contentType + "'");
        assertTrue(csv.body.startsWith("t,bot,x,y,z,level,exp,hp,hpMax"), "CSV header row present in: " + csv.body.substring(0, Math.min(60, csv.body.length())));

        // Metrics visible on /api/v1/health once samples exist.
        m.notePktAgeMs(42);
        Map<String, Object> health = assertJsonRoute("/api/v1/health", "status");
        assertEquals(42L, health.get("pktAgeLastMs"));
        assertTrue(health.containsKey("reconnectCount"));
    }

    private Map<String, BotInfo> bots()
    {
        Map<String, BotInfo> out = new LinkedHashMap<>();
        BotInfo b1 = new BotInfo("ai_dash_01", 100001);
        b1.charName = "DashBot1";
        b1.level = 21;
        b1.exp = 1_400_000;
        b1.hp = 500; b1.hpMax = 1000;
        b1.mp = 90; b1.mpMax = 200;
        b1.cp = 700; b1.cpMax = 1000;
        b1.x = -71338; b1.y = 258271; b1.z = -3104; b1.heading = 500;
        b1.load = 1000; b1.maxLoad = 50_000;
        b1.weapon = true;
        b1.adena = 12_345; b1.invPct = 12; b1.itemCount = 8;
        b1.items = new int[][] { { 57, 12345 }, { 4720, 50 } };
        b1.mobs = 3; b1.npcs = 5;
        b1.ents = new int[][] { { 900, 1, -71300, 258200, -3104 }, { 901, 0, -71400, 258300, -3104 } };
        b1.targetObjId = 900;
        b1.targetKind = 1;
        b1.targetLabel = "mob#900";
        b1.targetX = -71300; b1.targetY = 258200; b1.targetZ = -3104; b1.targetDist = 120.0;
        b1.action = "attack";
        b1.thought = "kill it";
        b1.state = "ATTACK";
        b1.connected = true;
        b1.loggedIn = true;
        out.put(b1.account, b1);

        BotInfo b2 = new BotInfo("ai_dash_02", 100002);
        b2.charName = "DashBot2";
        out.put(b2.account, b2);
        return out;
    }

    // ================================ helpers ================================

    private static final class Resp
    {
        int status;
        String contentType;
        String body;
    }

    private Resp get(String path)
    {
        try
        {
            URL url = new URL("http://127.0.0.1:" + server.getAddress().getPort() + path);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            Resp r = new Resp();
            r.status = conn.getResponseCode();
            r.contentType = conn.getContentType();
            try (InputStream in = r.status >= 400 ? conn.getErrorStream() : conn.getInputStream())
            {
                if (in == null) throw new IOException("no response stream for " + path);
                r.body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            return r;
        }
        catch (IOException e)
        {
            throw new AssertionError("HTTP GET " + path + " failed: " + e.getMessage(), e);
        }
    }

    /** GET a route, assert 200 + application/json, return the parsed body (which also proves well-formed JSON). */
    private Map<String, Object> assertJsonRoute(String path, String requiredKey)
    {
        Resp r = get(path);
        assertEquals(200, r.status, "HTTP status for " + path);
        assertNotNull(r.contentType, "Content-Type header for " + path);
        assertTrue(r.contentType.startsWith("application/json"),
            "Content-Type for " + path + " was '" + r.contentType + "'");
        Map<String, Object> obj = MiniJson.parse(r.body);
        assertTrue(obj.containsKey(requiredKey),
            "route " + path + " missing required key '" + requiredKey + "' in: " + r.body);
        return obj;
    }

    /** Minimal strict JSON parser: validates well-formedness and yields nested structures for assertions. */
    static final class MiniJson
    {
        private final String s;
        private int i;

        private MiniJson(String text)
        {
            this.s = text;
        }

        @SuppressWarnings("unchecked")
        static Map<String, Object> parse(String text)
        {
            MiniJson p = new MiniJson(text);
            Object v = p.value();
            p.skipWs();
            if (p.i != p.s.length())
                throw new IllegalArgumentException("trailing garbage after JSON value at offset " + p.i);
            if (!(v instanceof Map))
                throw new IllegalArgumentException("expected a top-level JSON object");
            return (Map<String, Object>) v;
        }

        private Object value()
        {
            skipWs();
            char c = peek();
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (c == 't') { expect("true"); return Boolean.TRUE; }
            if (c == 'f') { expect("false"); return Boolean.FALSE; }
            if (c == 'n') { expect("null"); return null; }
            if (c == '-' || (c >= '0' && c <= '9')) return number();
            throw err("unexpected character '" + c + "'");
        }

        private Map<String, Object> object()
        {
            Map<String, Object> m = new LinkedHashMap<>();
            expect("{");
            skipWs();
            if (peek() == '}') { i++; return m; }
            while (true)
            {
                skipWs();
                String k = string();
                skipWs();
                expect(":");
                m.put(k, value());
                skipWs();
                char c = next();
                if (c == ',') continue;
                if (c == '}') return m;
                throw err("expected ',' or '}' in object");
            }
        }

        private List<Object> array()
        {
            List<Object> l = new ArrayList<>();
            expect("[");
            skipWs();
            if (peek() == ']') { i++; return l; }
            while (true)
            {
                l.add(value());
                skipWs();
                char c = next();
                if (c == ',') continue;
                if (c == ']') return l;
                throw err("expected ',' or ']' in array");
            }
        }

        private String string()
        {
            expect("\"");
            StringBuilder b = new StringBuilder();
            while (i < s.length())
            {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c == '\\')
                {
                    if (i >= s.length()) throw err("unterminated escape");
                    char e = s.charAt(i++);
                    switch (e)
                    {
                        case '"': b.append('"'); break;
                        case '\\': b.append('\\'); break;
                        case '/': b.append('/'); break;
                        case 'b': b.append('\b'); break;
                        case 'f': b.append('\f'); break;
                        case 'n': b.append('\n'); break;
                        case 'r': b.append('\r'); break;
                        case 't': b.append('\t'); break;
                        case 'u':
                            if (i + 4 > s.length()) throw err("short \\u escape");
                            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                            break;
                        default: throw err("invalid escape '\\" + e + "'");
                    }
                }
                else
                {
                    b.append(c);
                }
            }
            throw err("unterminated string");
        }

        private Object number()
        {
            skipWs();
            int start = i;
            if (peek() == '-') i++;
            while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            if (i < s.length() && s.charAt(i) == '.')
            {
                i++;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            }
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E'))
            {
                i++;
                if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            }
            String tok = s.substring(start, i);
            try
            {
                if (tok.indexOf('.') < 0 && tok.indexOf('e') < 0 && tok.indexOf('E') < 0)
                {
                    return Long.parseLong(tok);
                }
                return Double.parseDouble(tok);
            }
            catch (NumberFormatException e)
            {
                throw err("invalid number '" + tok + "'");
            }
        }

        private void skipWs()
        {
            while (i < s.length())
            {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
                else break;
            }
        }

        private void expect(String lit)
        {
            if (!s.startsWith(lit, i)) throw err("expected '" + lit + "'");
            i += lit.length();
        }

        private char peek()
        {
            if (i >= s.length()) throw err("unexpected end of input");
            return s.charAt(i);
        }

        private char next()
        {
            char c = peek();
            i++;
            return c;
        }

        private IllegalArgumentException err(String msg)
        {
            return new IllegalArgumentException(msg + " at offset " + i + " in: " + s);
        }
    }
}
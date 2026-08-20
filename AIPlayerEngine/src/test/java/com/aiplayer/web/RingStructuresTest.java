package com.aiplayer.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import com.aiplayer.web.EventRing.Event;
import com.aiplayer.web.HistoryRing.Snapshot;

/**
 * WPT-03 / WPT-04 / WPT-08 — EventRing, HistoryRing and FleetMetrics acceptance tests.
 *
 * <p>Covers the frozen v1 contract shape for /api/v1/events, ring eviction, idempotent replay,
 * non-decreasing timestamps, history trails + CSV export, and the WPT-08 metric windows, plus
 * thread-safety smoke tests for all three structures.
 */
public class RingStructuresTest
{
    /** Deterministic test clock so timing assertions are exact. */
    private static final class MutableClock implements LongSupplier
    {
        private long now;

        MutableClock(long start)
        {
            this.now = start;
        }

        void set(long ms)
        {
            now = ms;
        }

        void advance(long ms)
        {
            now += ms;
        }

        @Override
        public long getAsLong()
        {
            return now;
        }
    }

    private static Map<String, Object> data(Object... kv)
    {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2)
        {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    // ================================================================== EventRing

    @Test
    public void testTypedEventsAreSequencedAndOrdered()
    {
        EventRing ring = new EventRing();
        EventRing.Event kill = ring.add(EventRing.TYPE_KILL, "CombatBot_01", data("hp", 500L));
        EventRing.Event lvl = ring.add(EventRing.TYPE_LEVEL_UP, "CombatBot_02", data("level", 22L));
        ring.add(EventRing.TYPE_SKILL_CAST, "CombatBot_01", data("skill", "Power Strike"));
        ring.add(EventRing.TYPE_DAMAGE, "CombatBot_01", data("dmg", 137L));
        ring.add(EventRing.TYPE_MOVE, "CombatBot_02", data("x", 100, "y", 200));
        EventRing.Event con = ring.add(EventRing.TYPE_CONNECT, "CombatBot_03", null);
        EventRing.Event dis = ring.add(EventRing.TYPE_DISCONNECT, "CombatBot_03", null);

        assertEquals(1, kill.seq);
        assertEquals(2, lvl.seq);
        assertEquals(6, con.seq);
        assertEquals(7, dis.seq);
        assertEquals(7, ring.size());
        assertEquals(1, ring.firstSeq());
        assertEquals(7, ring.lastSeq());
        assertEquals(512, ring.capacity());
        assertEquals(500L, kill.data.get("hp"));
        assertEquals("level-up", lvl.type);
        assertEquals("CombatBot_02", lvl.bot);

        List<EventRing.Event> all = ring.events();
        assertEquals(7, all.size());
        for (int i = 1; i < all.size(); i++)
        {
            assertTrue(all.get(i).seq > all.get(i - 1).seq, "seq must strictly increase");
            assertTrue(all.get(i).t >= all.get(i - 1).t, "t must never regress");
        }
    }

    @Test
    public void testRingEvictsOldestBeyondCapacity()
    {
        EventRing ring = new EventRing(5);
        for (int i = 1; i <= 8; i++)
        {
            ring.add(EventRing.TYPE_MOVE, "Bot", data("step", i));
        }
        assertEquals(5, ring.size());
        assertEquals(4, ring.firstSeq(), "oldest surviving seq should be 4");
        assertEquals(8, ring.lastSeq());
        assertNull(ring.get(3), "seq 3 was evicted");
        assertNotNull(ring.get(4));
        assertNotNull(ring.get(8));

        List<EventRing.Event> replay = ring.replaySince(0);
        assertEquals(5, replay.size());
        long expected = 4;
        for (EventRing.Event e : replay)
        {
            assertEquals(expected++, e.seq);
        }
    }

    @Test
    public void testReplaySinceIsIdempotentAndOrdered()
    {
        EventRing ring = new EventRing();
        for (int i = 0; i < 10; i++)
        {
            ring.add("move", "Bot", data("step", i));
        }
        List<EventRing.Event> first = ring.replaySince(3);
        List<EventRing.Event> second = ring.replaySince(3);
        assertEquals(first, second, "replaySince must be idempotent");
        assertTrue(first.size() == 7, "seqs 4..10 = 7 events");
        assertEquals(4, first.get(0).seq);
        assertEquals(10, first.get(6).seq);
        assertTrue(ring.replaySince(10).isEmpty());
        assertEquals(10, ring.replaySince(0).size());
    }

    @Test
    public void testEventsJsonMatchesFrozenContractShape()
    {
        EventRing ring = new EventRing();
        ring.add(EventRing.TYPE_KILL, "CombatBot_01", data("mobId", 20544));
        String json = ring.eventsJson();
        assertTrue(json.startsWith("{\"events\":["), json);
        assertTrue(json.endsWith("]}"), json);
        assertTrue(json.contains("{\"seq\":1,\"t\":"), json);
        assertTrue(json.contains("\"type\":\"kill\""), json);
        assertTrue(json.contains("\"bot\":\"CombatBot_01\""), json);
        assertTrue(json.contains("\"data\":{\"mobId\":20544}"), json);

        String since = ring.eventsJsonSince(1);
        assertTrue(since.contains("\"events\":[]"), since);
    }

    @Test
    public void testInOrderTimestampsNeverRegress()
    {
        MutableClock clock = new MutableClock(5000);
        EventRing ring = new EventRing(16, clock);
        ring.add("move", "Bot", null);
        clock.set(4000); // wall clock jumps backwards
        ring.add("damage", "Bot", null);
        clock.advance(2000); // 6000
        ring.add("kill", "Bot", null);

        List<EventRing.Event> all = ring.events();
        assertEquals(3, all.size());
        assertEquals(5000, all.get(0).t);
        assertEquals(5000, all.get(1).t, "backward clock read must clamp to last t");
        assertEquals(6000, all.get(2).t);
        assertTrue(all.get(1).t >= all.get(0).t);
        assertTrue(all.get(2).t >= all.get(1).t);
    }

    @Test
    public void testAddRejectsBlankType()
    {
        EventRing ring = new EventRing();
        assertThrows(IllegalArgumentException.class, () -> ring.add(null, "Bot", null));
        assertThrows(IllegalArgumentException.class, () -> ring.add("", "Bot", null));
        assertThrows(IllegalArgumentException.class, () -> ring.add("   ", "Bot", null));
    }

    @Test
    public void testAddDataIsDefensivelyCopied()
    {
        EventRing ring = new EventRing();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hp", 500L);
        EventRing.Event e = ring.add(EventRing.TYPE_DAMAGE, "BotX", data);
        data.put("hp", 999L);
        data.put("extra", "sneaky");
        assertEquals(500L, e.data.get("hp"), "mutation after add must not leak in");
        assertFalse(e.data.containsKey("extra"));
    }

    @Test
    public void testJsonEscapesStringsNumbersAndNested()
    {
        EventRing ring = new EventRing();
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("x", 1);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hp", 512L);
        data.put("dmg", 37.5d);
        data.put("crit", true);
        data.put("nil", null);
        data.put("note", "say \"hi\" \\ ok\nnext");
        data.put("nested", nested);
        data.put("list", java.util.Arrays.asList(1L, 2L, 3L));
        data.put("arr", new int[] { 4, 5 });
        EventRing.Event e = ring.add(EventRing.TYPE_DAMAGE, "CombatBot_01", data);

        String json = e.toJson();
        assertTrue(json.contains("\"hp\":512"), json);
        assertTrue(json.contains("\"dmg\":37.5"), json);
        assertTrue(json.contains("\"crit\":true"), json);
        assertTrue(json.contains("\"nil\":null"), json);
        assertTrue(json.contains("\"note\":\"say \\\"hi\\\" \\\\ ok\\nnext\""), json);
        assertTrue(json.contains("\"nested\":{\"x\":1}"), json);
        assertTrue(json.contains("\"list\":[1,2,3]"), json);
        assertTrue(json.contains("\"arr\":[4,5]"), json);
    }

    @Test
    public void testConcurrentAddsStayOrderedAndBounded()
    {
        final EventRing ring = new EventRing(512);
        final int threads = 8;
        final int perThread = 500;
        runParallel(threads, () ->
        {
            for (int i = 0; i < perThread; i++)
            {
                ring.add("move", "Bot", data("step", i));
            }
        });
        assertEquals(threads * perThread, ring.lastSeq(), "no seq may be lost/duplicated");
        assertEquals(512, ring.size(), "retained size must honor capacity");
        long first = ring.firstSeq();
        List<EventRing.Event> all = ring.events();
        assertEquals(512, all.size());
        for (int i = 0; i < all.size(); i++)
        {
            assertTrue(all.get(i).seq == first + i, "retained batch must be contiguous");
        }
    }

    /** Runs {@code task} concurrently on {@code threads} workers and awaits completion. */
    private static void runParallel(int threads, Runnable task)
    {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++)
        {
            pool.execute(task);
        }
        pool.shutdown();
        try
        {
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "tasks must finish in time");
        }
        catch (InterruptedException ie)
        {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for workers", ie);
        }
    }

    // ================================================================== HistoryRing

    @Test
    public void testRegisterAndTrailOrder()
    {
        MutableClock clock = new MutableClock(1000);
        HistoryRing history = new HistoryRing(3600, clock);
        HistoryRing.Snapshot s1 = history.register("CombatBot_01", 10, 20, 30, 21, 1_400_000L, 500L, 700L);
        clock.advance(200);
        history.register("CombatBot_01", 14, 24, -4, 21, 1_400_100L, 480L, 700L);
        clock.advance(200);
        HistoryRing.Snapshot s3 = history.register("CombatBot_01", 18, 28, -9, 22, 1_500_000L, 700L, 700L);

        assertEquals(1000, s1.t);
        assertEquals(10, s1.x);
        assertEquals(20, s1.y);
        assertEquals(30, s1.z);
        assertEquals(21, s1.level);
        assertEquals(1_400_000L, s1.exp);
        assertEquals(500L, s1.hp);
        assertEquals(700L, s1.hpMax);

        List<HistoryRing.Snapshot> trail = history.trail("CombatBot_01");
        assertEquals(3, trail.size());
        assertEquals(s3.t, trail.get(2).t);
        assertEquals(3, history.size());
        assertEquals(3600, history.capacity());
        assertEquals(s1.t, history.firstT());
        assertEquals(s3.t, history.lastT());
    }

    @Test
    public void testQueryAppliesInclusiveTimeWindow()
    {
        MutableClock clock = new MutableClock(1000);
        HistoryRing history = new HistoryRing(64, clock);
        history.register("Bot", 0, 0, 0, 1, 0, 10, 10); // t=1000
        clock.set(1100);
        history.register("Bot", 1, 1, 1, 1, 0, 9, 10); // t=1100
        clock.set(1200);
        history.register("Bot", 2, 2, 2, 1, 0, 8, 10); // t=1200
        clock.set(1300);
        history.register("Bot", 3, 3, 3, 1, 0, 7, 10); // t=1300

        List<HistoryRing.Snapshot> mid = history.query("Bot", 1100, 1200);
        assertEquals(2, mid.size());
        assertEquals(1100, mid.get(0).t);
        assertEquals(1200, mid.get(1).t);
        assertTrue(history.query("Bot", 1500, 1000).isEmpty(), "inverted window -> empty");
        assertTrue(history.query("Nobody", 0, Long.MAX_VALUE).isEmpty());
    }

    @Test
    public void testHistoryRingEvictsOldestSnapshots()
    {
        MutableClock clock = new MutableClock(1000);
        HistoryRing history = new HistoryRing(3, clock);
        for (int i = 0; i < 5; i++)
        {
            clock.advance(100);
            history.register("Bot", i, i, i, 1, i, 10, 10);
        }
        assertEquals(3, history.size());
        assertEquals(1300, history.firstT(), "only t=1300/1400/1500 survive");
        assertEquals(1500, history.lastT());
        List<HistoryRing.Snapshot> trail = history.trail("Bot");
        assertEquals(3, trail.size());
        assertEquals(1300, trail.get(0).t);
    }

    @Test
    public void testMultiBotTrailsAreIndependent()
    {
        MutableClock clock = new MutableClock(1000);
        HistoryRing history = new HistoryRing(64, clock);
        history.register("Alpha", 1, 1, 1, 1, 0, 10, 10);
        clock.advance(100);
        history.register("Beta", 2, 2, 2, 2, 0, 9, 10);
        clock.advance(100);
        history.register("Alpha", 3, 3, 3, 1, 0, 8, 10);

        List<HistoryRing.Snapshot> alpha = history.trail("Alpha");
        List<HistoryRing.Snapshot> beta = history.trail("Beta");
        assertEquals(2, alpha.size());
        assertEquals(1, beta.size());
        for (HistoryRing.Snapshot s : alpha)
        {
            assertEquals("Alpha", s.bot);
        }
        assertEquals("Beta", beta.get(0).bot);
    }

    @Test
    public void testHistoryCsvExport()
    {
        MutableClock clock = new MutableClock(1000);
        HistoryRing history = new HistoryRing(16, clock);
        history.register("Bot", 10, 20, 30, 21, 1_400_000L, 500L, 700L);
        clock.set(1200);
        history.register("Bot", 11, 21, -5, 22, 1_500_000L, 700L, 700L);

        String csv = history.toCsv("Bot", 1000, 2000);
        String[] lines = csv.split("\n");
        assertEquals(3, lines.length);
        assertEquals("t,bot,x,y,z,level,exp,hp,hpMax", lines[0]);
        assertTrue(lines[1].startsWith("1000,Bot,10,20,30,21,1400000,500,700"), lines[1]);
        assertTrue(lines[2].startsWith("1200,Bot,11,21,-5,22,1500000,700,700"), lines[2]);

        String filtered = history.toCsv("Bot", 1100, 1199);
        assertEquals("t,bot,x,y,z,level,exp,hp,hpMax", filtered.trim(), "header only outside window");
    }

    @Test
    public void testHistoryJsonShape()
    {
        MutableClock clock = new MutableClock(1000);
        HistoryRing history = new HistoryRing(16, clock);
        history.register("CombatBot_01", 10, 20, 30, 21, 1_400_000L, 500L, 700L);

        String json = history.toJson("CombatBot_01", 0, Long.MAX_VALUE);
        assertTrue(json.startsWith("{\"bot\":\"CombatBot_01\","), json);
        assertTrue(json.contains("\"history\":["), json);
        assertTrue(json.contains("\"t\":1000"), json);
        assertTrue(json.contains(",\"bot\":\"CombatBot_01\",\"x\":10"), json);
        assertTrue(json.contains("\"x\":10"), json);
        assertTrue(json.contains("\"y\":20"), json);
        assertTrue(json.contains("\"z\":30"), json);
        assertTrue(json.contains("\"level\":21"), json);
        assertTrue(json.contains("\"exp\":1400000"), json);
        assertTrue(json.contains("\"hp\":500"), json);
        assertTrue(json.contains("\"hpMax\":700"), json);
    }

    @Test
    public void testHistoryMonotonicTimestampsClamped()
    {
        MutableClock clock = new MutableClock(1000);
        HistoryRing history = new HistoryRing(8, clock);
        history.register("Bot", 0, 0, 0, 1, 0, 10, 10);
        clock.set(500); // wall clock regression
        history.register("Bot", 1, 1, 1, 1, 0, 9, 10);
        clock.advance(700); // 1200
        history.register("Bot", 2, 2, 2, 1, 0, 8, 10);

        List<HistoryRing.Snapshot> trail = history.trail("Bot");
        assertEquals(1000, trail.get(0).t);
        assertEquals(1000, trail.get(1).t);
        assertEquals(1200, trail.get(2).t);
        for (int i = 1; i < trail.size(); i++)
        {
            assertTrue(trail.get(i).t >= trail.get(i - 1).t);
        }
    }

    @Test
    public void testConcurrentRegistersBoundedAndConsistent()
    {
        final HistoryRing history = new HistoryRing(1000);
        final int threads = 8;
        final int perThread = 500;
        final AtomicInteger seen = new AtomicInteger();
        runParallel(threads, () ->
        {
            for (int i = 0; i < perThread; i++)
            {
                String bot = (i % 2 == 0) ? "Alpha" : "Beta";
                HistoryRing.Snapshot s = history.register(bot, i, i, i, 20, i, 100, 100);
                if (s != null)
                {
                    seen.incrementAndGet();
                }
            }
        });
        assertEquals(threads * perThread, seen.get(), "register must return a snapshot for every call");
        assertEquals(1000, history.size(), "retained size must honor capacity");
        assertEquals(history.trail("Alpha").size() + history.trail("Beta").size(), 1000);
        List<HistoryRing.Snapshot> trail = history.trail("Alpha");
        for (int i = 1; i < trail.size(); i++)
        {
            assertTrue(trail.get(i).t >= trail.get(i - 1).t);
        }
    }

    // ================================================================== FleetMetrics

    @Test
    public void testUptimeAndStartInstant()
    {
        MutableClock clock = new MutableClock(15_000);
        FleetMetrics m = new FleetMetrics(10_000, 8, 8, clock);
        assertEquals(10_000, m.startedAtEpochMs());
        assertEquals(5, m.uptimeSec());
        clock.set(15_100); // +100ms -> still 5s
        assertEquals(5, m.uptimeSec());
        clock.set(16_000); // 6s
        assertEquals(6, m.uptimeSec());
    }

    @Test
    public void testRequestAndReconnectCounters()
    {
        FleetMetrics m = new FleetMetrics(1000, 8, 8, new MutableClock(2000));
        for (int i = 0; i < 5; i++)
        {
            m.noteRequest();
        }
        assertEquals(5, m.requestCount());
        m.noteReconnect();
        m.noteReconnect();
        assertEquals(2, m.reconnectCount());
        assertEquals(0, m.botCount());
        assertEquals(0, m.onlineCount());
    }

    @Test
    public void testBotAndOnlineCounts()
    {
        FleetMetrics m = new FleetMetrics();
        m.setBotCount(5);
        m.setOnlineCount(3);
        assertEquals(5, m.botCount());
        assertEquals(3, m.onlineCount());
        m.setBotCount(-7); // clamped
        assertEquals(0, m.botCount());
    }

    @Test
    public void testPktAgeWindowOverflowAndStats()
    {
        FleetMetrics m = new FleetMetrics(10_000L, 4, 4);
        m.notePktAgeMs(10);
        m.notePktAgeMs(20);
        m.notePktAgeMs(30);
        m.notePktAgeMs(40);
        assertEquals(4, m.pktAgeSamples());
        assertEquals(40L, m.pktAgeLastMs().longValue());
        assertEquals(40L, m.pktAgeMaxMs().longValue());
        assertEquals(25L, m.pktAgeAvgMs().longValue());
        assertEquals(java.util.Arrays.asList(10L, 20L, 30L, 40L), m.pktAgeHistoryMs());

        m.notePktAgeMs(50); // overflow evicts 10
        assertEquals(4, m.pktAgeSamples());
        assertEquals(50L, m.pktAgeLastMs().longValue());
        assertEquals(50L, m.pktAgeMaxMs().longValue());
        assertEquals(35L, m.pktAgeAvgMs().longValue());
        assertEquals(java.util.Arrays.asList(20L, 30L, 40L, 50L), m.pktAgeHistoryMs());
    }

    @Test
    public void testLatencyStats()
    {
        FleetMetrics m = new FleetMetrics();
        assertNull(m.latencyAvgMs());
        assertEquals(0, m.latencySamples());
        m.noteLatencyMs(1);
        m.noteLatencyMs(2);
        m.noteLatencyMs(3);
        assertEquals(3, m.latencySamples());
        assertEquals(2L, m.latencyAvgMs().longValue());
        assertEquals(3L, m.latencyMaxMs().longValue());
        assertEquals(3L, m.latencyLastMs().longValue());
        assertEquals(java.util.Arrays.asList(1L, 2L, 3L), m.latencyHistoryMs());
    }

    @Test
    public void testEmptyWindowsReturnNulls()
    {
        FleetMetrics m = new FleetMetrics();
        assertEquals(0, m.pktAgeSamples());
        assertNull(m.pktAgeLastMs());
        assertNull(m.pktAgeMaxMs());
        assertNull(m.pktAgeAvgMs());
        assertTrue(m.pktAgeHistoryMs().isEmpty());
        assertTrue(m.latencyHistoryMs().isEmpty());
    }

    @Test
    public void testConcurrentRequestCounterIsExact()
    {
        FleetMetrics m = new FleetMetrics();
        final int threads = 8;
        final int perThread = 1000;
        runParallel(threads, () ->
        {
            for (int i = 0; i < perThread; i++)
            {
                m.noteRequest();
            }
        });
        assertEquals(threads * perThread, m.requestCount());
    }

    @Test
    public void testConcurrentPktAgePushStaysBounded()
    {
        FleetMetrics m = new FleetMetrics(10_000L, 16, 16);
        final int threads = 8;
        final int perThread = 1000;
        final int window = 16;
        runParallel(threads, () ->
        {
            for (int i = 0; i < perThread; i++)
            {
                m.notePktAgeMs(i % 100);
            }
        });
        assertEquals(window, m.pktAgeSamples(), "window must cap retained samples");
        assertEquals(window, m.pktAgeHistoryMs().size());
        assertNotNull(m.pktAgeMaxMs(), "window must be non-empty after pushes");
        for (long v : m.pktAgeHistoryMs())
        {
            assertTrue(v >= 0 && v < 100, "sample must be one of the pushed values");
        }
    }

    @Test
    public void testToJsonHealthShape()
    {
        MutableClock clock = new MutableClock(15_000);
        FleetMetrics m = new FleetMetrics(10_000, 8, 8, clock);
        m.noteRequest();
        m.noteRequest();
        m.noteRequest();
        m.noteRequest();
        m.noteReconnect();
        m.noteReconnect();
        m.setBotCount(4);
        m.setOnlineCount(2);
        m.notePktAgeMs(120);
        m.notePktAgeMs(300);
        m.noteLatencyMs(4);
        m.noteLatencyMs(6);

        String json = m.toJson();
        assertTrue(json.startsWith("{"), json);
        assertTrue(json.endsWith("}"), json);
        assertTrue(json.contains("\"status\":\"ok\""), json);
        assertTrue(json.contains("\"uptimeSec\":5"), json);
        assertTrue(json.contains("\"startedAtEpochMs\":10000"), json);
        assertTrue(json.contains("\"botCount\":4"), json);
        assertTrue(json.contains("\"onlineCount\":2"), json);
        assertTrue(json.contains("\"requestCount\":4"), json);
        assertTrue(json.contains("\"reconnectCount\":2"), json);
        assertTrue(json.contains("\"pktAgeMs\":300"), json);
        assertTrue(json.contains("\"pktAgeMaxMs\":300"), json);
        assertTrue(json.contains("\"latencyAvgMs\":5"), json);
        assertTrue(json.contains("\"latencyMaxMs\":6"), json);
    }
}
package com.aiplayer.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import com.aiplayer.web.EventRing;
import com.aiplayer.web.HistoryRing;
import com.aiplayer.web.EventRing.Json;
import com.aiplayer.web.HistoryRing.Snapshot;

/**
 * WPT-08 — process/fleet health & metrics for the v1 {@code /api/v1/health} route.
 *
 * <p>Exposes uptime, fleet size, online count, request and reconnect counters, a sliding
 * pktAge (packet-age) history window and a request-latency window. Snapshot getters never mutate;
 * {@link #toJson()} emits an extended health object the orchestrator (Cline#1) can splice into
 * DashboardApi. None of the DashboardApi code is touched here (out of lane).
 *
 * <p>Thread-safety: counters are {@code Atomic*}; the small sample windows are guarded by
 * {@code synchronized} blocks (shortest critical sections only, no coarse locks).
 */
public final class FleetMetrics
{
    /** Default number of pktAge samples retained in the sliding window. */
    public static final int DEFAULT_PKT_AGE_WINDOW = 128;

    /** Default number of request-latency samples retained in the sliding window. */
    public static final int DEFAULT_LATENCY_WINDOW = 128;

    private final long startedAtMs;
    private final LongSupplier clock;

    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong reconnects = new AtomicLong();
    private final AtomicInteger botCount = new AtomicInteger();
    private final AtomicInteger onlineCount = new AtomicInteger();

    private final int pktWindow;
    private final long[] pktAges;
    private int pktHead;  // next write index, guarded by this
    private int pktCount; // retained samples, guarded by this

    private final int latencyWindow;
    private final long[] latencies;
    private int latHead;  // next write index, guarded by this
    private int latCount; // retained samples, guarded by this

    /** Creates metrics starting now with default sample windows. */
    public FleetMetrics()
    {
        this(System.currentTimeMillis());
    }

    /**
     * Creates metrics with a fixed start instant.
     *
     * @param startedAtEpochMs server start instant, epoch milliseconds
     */
    public FleetMetrics(long startedAtEpochMs)
    {
        this(startedAtEpochMs, DEFAULT_PKT_AGE_WINDOW, DEFAULT_LATENCY_WINDOW);
    }

    /**
     * Creates metrics with explicit sample-window sizes.
     *
     * @param startedAtEpochMs server start instant, epoch milliseconds
     * @param pktWindow        pktAge history depth (&gt; 0)
     * @param latencyWindow    latency history depth (&gt; 0)
     * @throws IllegalArgumentException on non-positive window sizes
     */
    public FleetMetrics(long startedAtEpochMs, int pktWindow, int latencyWindow)
    {
        this(startedAtEpochMs, pktWindow, latencyWindow, System::currentTimeMillis);
    }

    /** Package-private: injectable clock for deterministic tests. */
    FleetMetrics(long startedAtEpochMs, int pktWindow, int latencyWindow, LongSupplier clock)
    {
        if (pktWindow <= 0 || latencyWindow <= 0)
        {
            throw new IllegalArgumentException("sample windows must be > 0");
        }
        this.startedAtMs = startedAtEpochMs;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.pktWindow = pktWindow;
        this.pktAges = new long[pktWindow];
        this.latencyWindow = latencyWindow;
        this.latencies = new long[latencyWindow];
    }

    /** Uptime in whole seconds since start (clamped at 0 before the start instant). */
    public long uptimeSec()
    {
        return Math.max(0, (clock.getAsLong() - startedAtMs) / 1000);
    }

    /** Start instant, epoch milliseconds. */
    public long startedAtEpochMs()
    {
        return startedAtMs;
    }

    /** Counts one HTTP request. */
    public void noteRequest()
    {
        requests.incrementAndGet();
    }

    /** Total requests recorded so far. */
    public long requestCount()
    {
        return requests.get();
    }

    /** Counts one bot reconnect. */
    public void noteReconnect()
    {
        reconnects.incrementAndGet();
    }

    /** Total reconnects recorded so far. */
    public long reconnectCount()
    {
        return reconnects.get();
    }

    /** Sets the current registered-fleet size (from the fleet leader). */
    public void setBotCount(int n)
    {
        botCount.set(Math.max(0, n));
    }

    /** Current registered-fleet size. */
    public int botCount()
    {
        return botCount.get();
    }

    /** Sets the current connected-and-logged-in bot count. */
    public void setOnlineCount(int n)
    {
        onlineCount.set(Math.max(0, n));
    }

    /** Current online bot count. */
    public int onlineCount()
    {
        return onlineCount.get();
    }

    /** Records one pktAge sample (ms). Overflow evicts the oldest sample. */
    public void notePktAgeMs(long ms)
    {
        synchronized (this)
        {
            pktAges[pktHead] = Math.max(0, ms);
            pktHead = (pktHead + 1) % pktWindow;
            if (pktCount < pktWindow)
            {
                pktCount++;
            }
        }
    }

    /** pktAge samples currently retained (0..window). */
    public synchronized int pktAgeSamples()
    {
        return pktCount;
    }

    /** Most recent pktAge sample (ms), or null when none recorded yet. */
    public synchronized Long pktAgeLastMs()
    {
        return pktCount == 0 ? null : pktAges[(pktHead - 1 + pktWindow) % pktWindow];
    }

    /** Maximum pktAge within the retained window (ms), or null when empty. */
    public synchronized Long pktAgeMaxMs()
    {
        if (pktCount == 0)
        {
            return null;
        }
        long max = Long.MIN_VALUE;
        for (int i = 0; i < pktCount; i++)
        {
            max = Math.max(max, pktAges[(pktHead - pktCount + i + pktWindow) % pktWindow]);
        }
        return max;
    }

    /** Arithmetic mean pktAge over the retained window (ms, rounded), or null when empty. */
    public synchronized Long pktAgeAvgMs()
    {
        if (pktCount == 0)
        {
            return null;
        }
        long sum = 0;
        for (int i = 0; i < pktCount; i++)
        {
            sum += pktAges[(pktHead - pktCount + i + pktWindow) % pktWindow];
        }
        return Math.round(sum / (double) pktCount);
    }

    /** Copy of the retained pktAge window, oldest to newest. */
    public synchronized List<Long> pktAgeHistoryMs()
    {
        List<Long> out = new ArrayList<>(pktCount);
        for (int i = 0; i < pktCount; i++)
        {
            out.add(pktAges[(pktHead - pktCount + i + pktWindow) % pktWindow]);
        }
        return Collections.unmodifiableList(out);
    }

    /** Records one request-latency sample (ms). Overflow evicts the oldest sample. */
    public void noteLatencyMs(long ms)
    {
        synchronized (this)
        {
            latencies[latHead] = Math.max(0, ms);
            latHead = (latHead + 1) % latencyWindow;
            if (latCount < latencyWindow)
            {
                latCount++;
            }
        }
    }

    /** Latency samples currently retained (0..window). */
    public synchronized int latencySamples()
    {
        return latCount;
    }

    /** Most recent latency sample (ms), or null when empty. */
    public synchronized Long latencyLastMs()
    {
        return latCount == 0 ? null : latencies[(latHead - 1 + latencyWindow) % latencyWindow];
    }

    /** Mean latency over the retained window (ms, rounded), or null when empty. */
    public synchronized Long latencyAvgMs()
    {
        if (latCount == 0)
        {
            return null;
        }
        long sum = 0;
        for (int i = 0; i < latCount; i++)
        {
            sum += latencies[(latHead - latCount + i + latencyWindow) % latencyWindow];
        }
        return Math.round(sum / (double) latCount);
    }

    /** Maximum latency within the retained window (ms), or null when empty. */
    public synchronized Long latencyMaxMs()
    {
        if (latCount == 0)
        {
            return null;
        }
        long max = Long.MIN_VALUE;
        for (int i = 0; i < latCount; i++)
        {
            max = Math.max(max, latencies[(latHead - latCount + i + latencyWindow) % latencyWindow]);
        }
        return max;
    }

    /** Copy of the retained latency window, oldest to newest. */
    public synchronized List<Long> latencyHistoryMs()
    {
        List<Long> out = new ArrayList<>(latCount);
        for (int i = 0; i < latCount; i++)
        {
            out.add(latencies[(latHead - latCount + i + latencyWindow) % latencyWindow]);
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Extended health JSON object (superset of the DashboardApi fields): status, uptimeSec,
     * startedAtEpochMs, botCount, onlineCount, requestCount, reconnectCount, pktAgeMs (last),
     * pktAgeMaxMs, latencyAvgMs, latencyMaxMs. Route keys stay in Cline#1's DashboardApi.
     */
    public String toJson()
    {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"status\":\"ok\"")
          .append(",\"uptimeSec\":").append(uptimeSec())
          .append(",\"startedAtEpochMs\":").append(startedAtMs)
          .append(",\"botCount\":").append(botCount())
          .append(",\"onlineCount\":").append(onlineCount())
          .append(",\"requestCount\":").append(requestCount())
          .append(",\"reconnectCount\":").append(reconnectCount())
          .append(",\"pktAgeMs\":").append(EventRing.Json.value(pktAgeLastMs()))
          .append(",\"pktAgeMaxMs\":").append(EventRing.Json.value(pktAgeMaxMs()))
          .append(",\"latencyAvgMs\":").append(EventRing.Json.value(latencyAvgMs()))
          .append(",\"latencyMaxMs\":").append(EventRing.Json.value(latencyMaxMs()))
          .append('}');
        return sb.toString();
    }
}
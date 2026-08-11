package com.aiplayer.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * WPT-04 — in-memory state history ring for bot trails / playback / review.
 *
 * <p>Holds at most {@value #DEFAULT_CAPACITY} snapshots (~1h @ 2s); when full the oldest snapshot
 * is silently evicted. Snapshots are time-ordered with non-decreasing {@code t} (epoch ms, clamped
 * like the event ring so a poller cadence can never regress the trail time axis).
 *
 * <p>Intended routing: {@code GET /api/v1/history?bot=&from=&to=} (WPT-04 spec note). This class
 * only owns the storage and serialization; the route wiring stays in Cline#1's DashboardApi.
 *
 * <p>Thread-safety: {@link #register} and reads are {@code synchronized}; returned {@link Snapshot}
 * objects are immutable value records.
 */
public final class HistoryRing
{
    /** Default ring capacity (WPT-04 acceptance: cap ~3600 snapshots). */
    public static final int DEFAULT_CAPACITY = 3600;

    /** One state snapshot row: {t,bot,x,y,z,level,exp,hp,hpMax}. */
    public static final class Snapshot
    {
        /** Epoch milliseconds at capture. */
        public final long t;

        /** Bot name this snapshot belongs to. */
        public final String bot;

        /** World coordinates. */
        public final int x, y, z;

        /** Character vitals at capture time. */
        public final long level, exp, hp, hpMax;

        Snapshot(long t, String bot, int x, int y, int z, long level, long exp, long hp, long hpMax)
        {
            this.t = t;
            this.bot = bot;
            this.x = x;
            this.y = y;
            this.z = z;
            this.level = level;
            this.exp = exp;
            this.hp = hp;
            this.hpMax = hpMax;
        }

        /** JSON object row, key order: t, bot, x, y, z, level, exp, hp, hpMax. */
        public String toJson()
        {
            StringBuilder sb = new StringBuilder(128);
            sb.append("{\"t\":").append(t)
              .append(",\"bot\":");
            EventRing.Json.writeString(sb, bot);
            sb.append(",\"x\":").append(x)
              .append(",\"y\":").append(y)
              .append(",\"z\":").append(z)
              .append(",\"level\":").append(level)
              .append(",\"exp\":").append(exp)
              .append(",\"hp\":").append(hp)
              .append(",\"hpMax\":").append(hpMax)
              .append('}');
            return sb.toString();
        }

        /** One CSV line (no trailing newline) matching the {@link HistoryRing#toCsv} header. */
        public String toCsv()
        {
            return t + "," + bot + "," + x + "," + y + "," + z + "," + level + "," + exp + "," + hp + "," + hpMax;
        }
    }

    private final int capacity;
    private final LongSupplier clock;
    private final Object[] slot;
    private int head;   // next write index
    private int count;  // retained snapshots
    private long lastT; // order-clamped timestamp of the newest snapshot

    /** Creates a history ring with the default capacity (3600) using the system clock. */
    public HistoryRing()
    {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates a history ring with the given capacity.
     *
     * @param capacity positive ring size
     * @throws IllegalArgumentException if {@code capacity <= 0}
     */
    public HistoryRing(int capacity)
    {
        this(capacity, System::currentTimeMillis);
    }

    /** Package-private: injectable clock for deterministic tests. */
    HistoryRing(int capacity, LongSupplier clock)
    {
        if (capacity <= 0)
        {
            throw new IllegalArgumentException("capacity must be > 0: " + capacity);
        }
        this.capacity = capacity;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.slot = new Object[capacity];
        this.lastT = Long.MIN_VALUE;
    }

    /**
     * Records a state snapshot for a bot and returns its immutable record. May evict the oldest
     * retained snapshot when the ring is full.
     *
     * @param bot   bot name (null &rarr; empty string)
     * @param x,y,z world coordinates
     * @param level,exp,hp,hpMax character vitals
     * @return the added {@link Snapshot}
     */
    public synchronized Snapshot register(String bot, int x, int y, int z, long level, long exp, long hp, long hpMax)
    {
        long now = clock.getAsLong();
        if (now < lastT)
        {
            now = lastT; // trail time axis must never regress
        }
        lastT = now;
        Snapshot s = new Snapshot(now, bot == null ? "" : bot, x, y, z, level, exp, hp, hpMax);
        slot[head] = s;
        head = (head + 1) % capacity;
        if (count < capacity)
        {
            count++;
        }
        return s;
    }

    /**
     * Ordered trail for one bot within an inclusive time window [{@code from}, {@code to}] epoch
     * milliseconds. Snapshots older than the ring cap are unrecoverable. An empty list is returned
     * when the window is inverted ({@code from > to}) or the bot has no snapshots in range.
     *
     * @return unmodifiable snapshot list, oldest to newest
     */
    public synchronized List<Snapshot> query(String bot, long from, long to)
    {
        String target = bot == null ? "" : bot;
        List<Snapshot> out = new ArrayList<>();
        if (from > to)
        {
            return Collections.unmodifiableList(out);
        }
        for (int i = 0; i < count; i++)
        {
            Snapshot s = (Snapshot) slot[(head - count + i + capacity) % capacity];
            if (s.bot.equals(target) && s.t >= from && s.t <= to)
            {
                out.add(s);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Full retained trail for one bot (equivalent to {@link #query(String, long, long)} with an open
     * window).
     *
     * @return unmodifiable snapshot list, oldest to newest
     */
    public synchronized List<Snapshot> trail(String bot)
    {
        return query(bot, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    /** Retained snapshot count (0..capacity). */
    public synchronized int size()
    {
        return count;
    }

    /** Ring capacity (constant). */
    public int capacity()
    {
        return capacity;
    }

    /** Timestamp of the oldest retained snapshot, or 0 when empty. */
    public synchronized long firstT()
    {
        return count == 0 ? 0 : ((Snapshot) slot[(head - count + capacity) % capacity]).t;
    }

    /** Timestamp of the newest retained snapshot, or 0 when empty. */
    public synchronized long lastT()
    {
        return count == 0 ? 0 : ((Snapshot) slot[(head - 1 + capacity) % capacity]).t;
    }

    /**
     * JSON body for {@code /api/v1/history}: {@code {"bot":...,"history":[{"t","x","y","z","level",
     * "exp","hp","hpMax"}]}} over the {@code query(bot, from, to)} window.
     */
    public synchronized String toJson(String bot, long from, long to)
    {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"bot\":");
        EventRing.Json.writeString(sb, bot == null ? "" : bot);
        sb.append(",\"from\":").append(from)
          .append(",\"to\":").append(to)
          .append(",\"history\":[");
        boolean first = true;
        for (Snapshot s : query(bot, from, to))
        {
            if (!first)
            {
                sb.append(',');
            }
            first = false;
            sb.append(s.toJson());
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * CSV export for a trail window (WPT-04 "optional CSV export"). Header line:
     * {@code t,bot,x,y,z,level,exp,hp,hpMax}, one snapshot per row, CR/LF-free.
     */
    public synchronized String toCsv(String bot, long from, long to)
    {
        StringBuilder sb = new StringBuilder(256);
        sb.append("t,bot,x,y,z,level,exp,hp,hpMax\n");
        for (Snapshot s : query(bot, from, to))
        {
            sb.append(s.toCsv()).append('\n');
        }
        return sb.toString();
    }
}
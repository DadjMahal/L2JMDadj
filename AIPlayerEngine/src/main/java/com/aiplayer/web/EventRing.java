package com.aiplayer.web;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * WPT-03 — in-memory typed event ring backing the frozen {@code /api/v1/events} contract.
 *
 * <p>Holds at most {@value #DEFAULT_CAPACITY} events; when full the oldest event is silently
 * evicted. Every event carries an immutable, monotonically increasing {@code seq} (starting at 1)
 * and a non-decreasing {@code t} (epoch milliseconds, order-clamped so concurrent or jittery
 * clock reads can never regress the feed). {@link #replaySince(long)} is <b>idempotent</b>: for
 * a fixed {@code since} it returns exactly the same retained event list on every call, so a
 * poller can replay its own feed with {@code since} = last seen seq without gaps or duplicates.
 *
 * <p>Frozen contract (Documentation/TASKS.md section 11):
 * {@code GET /api/v1/events &rarr; {"events":[{"seq","t","type","bot","data":{...}}]}}.
 *
 * <p>Thread-safety: mutations are {@code synchronized}; returned {@link Event} instances and
 * their data maps are immutable snapshots, safe to read from any thread.
 */
public final class EventRing
{
    /** Ring capacity used by the default constructor (WPT-03 acceptance: cap 512). */
    public static final int DEFAULT_CAPACITY = 512;

    /** Known typed events (WPT-03 list), exposed as constants for compile-time names. */
    public static final String TYPE_KILL = "kill";
    public static final String TYPE_LEVEL_UP = "level-up";
    public static final String TYPE_SKILL_CAST = "skill-cast";
    public static final String TYPE_DAMAGE = "damage";
    public static final String TYPE_MOVE = "move";
    public static final String TYPE_CONNECT = "connect";
    public static final String TYPE_DISCONNECT = "disconnect";
    /** WPT-22: real server SystemMessage (0x64) text surfaced into the feed. */
    public static final String TYPE_SYSMSG = "sysmsg";
    /** WPT-22: server SAY/Chat broadcast (NPC or player) surfaced into the feed. */
    public static final String TYPE_CHAT = "chat";

    /**
     * Immutable record for one event feed row. Field names and JSON key order are frozen by the
     * v1 contract (seq, t, type, bot, data).
     */
    public static final class Event
    {
        /** Monotonic sequence number (&ge; 1), strictly increasing across the whole ring life. */
        public final long seq;

        /** Epoch milliseconds; never decreases across consecutive events. */
        public final long t;

        /** Event type, e.g. one of the {@code TYPE_*} constants ("kill", "level-up", ...). */
        public final String type;

        /** Bot account/name this event belongs to (never null; empty string when unknown). */
        public final String bot;

        /** Defensive copy of the caller's payload, iteration order preserved. */
        public final Map<String, Object> data;

        Event(long seq, long t, String type, String bot, Map<String, Object> data)
        {
            this.seq = seq;
            this.t = t;
            this.type = type;
            this.bot = bot == null ? "" : bot;
            this.data = data;
        }

        /** Single JSON object in the exact frozen contract key order. */
        public String toJson()
        {
            StringBuilder sb = new StringBuilder(128);
            sb.append("{\"seq\":").append(seq)
              .append(",\"t\":").append(t)
              .append(",\"type\":");
            Json.writeString(sb, type);
            sb.append(",\"bot\":");
            Json.writeString(sb, bot);
            sb.append(",\"data\":");
            Json.writeValue(sb, data);
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * Minimal well-formed JSON writer shared by the ring structures (EventRing / HistoryRing /
     * FleetMetrics). Values supported: String, Number, Boolean, Character, Enum, Map (keys
     * stringified, insertion order), Collection, Iterable, and any array (primitive or boxed).
     * Non-finite floats/doubles and ambiguous objects are encoded as {@code null} / quoted string
     * respectively so output stays parseable.
     */
    public static final class Json
    {
        private Json() { }

        /** Quoted, escaped string literal (unprintable control chars become hex escape sequences). */
        public static void writeString(StringBuilder sb, String s)
        {
            if (s == null)
            {
                sb.append("null");
                return;
            }
            sb.append('"');
            for (int i = 0; i < s.length(); i++)
            {
                char c = s.charAt(i);
                switch (c)
                {
                    case '"': sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    case '\b': sb.append("\\b"); break;
                    case '\f': sb.append("\\f"); break;
                    default:
                        if (c < 0x20)
                        {
                            sb.append(String.format("\\u%04x", (int) c));
                        }
                        else
                        {
                            sb.append(c);
                        }
                }
            }
            sb.append('"');
        }

        /** String literal (see {@link #writeString(StringBuilder, String)}). */
        public static String quote(String s)
        {
            StringBuilder sb = new StringBuilder(s == null ? 4 : s.length() + 2);
            writeString(sb, s);
            return sb.toString();
        }

        /** Append any supported value as JSON. */
        public static void writeValue(StringBuilder sb, Object v)
        {
            if (v == null)
            {
                sb.append("null");
            }
            else if (v instanceof String)
            {
                writeString(sb, (String) v);
            }
            else if (v instanceof Boolean)
            {
                sb.append(v);
            }
            else if (v instanceof Number)
            {
                double d = ((Number) v).doubleValue();
                if (Double.isNaN(d) || Double.isInfinite(d))
                {
                    sb.append("null");
                }
                else
                {
                    sb.append(v);
                }
            }
            else if (v instanceof Character || v instanceof Enum)
            {
                writeString(sb, String.valueOf(v));
            }
            else if (v instanceof Map)
            {
                writeMap(sb, (Map<?, ?>) v);
            }
            else if (v instanceof Iterable)
            {
                writeIterable(sb, (Iterable<?>) v);
            }
            else if (v.getClass().isArray())
            {
                writeArray(sb, v);
            }
            else
            {
                writeString(sb, String.valueOf(v));
            }
        }

        /** JSON value for any supported object (see {@link #writeValue}). */
        public static String value(Object v)
        {
            StringBuilder sb = new StringBuilder(64);
            writeValue(sb, v);
            return sb.toString();
        }

        /** Recursive defensive copy used to freeze caller-supplied payloads. */
        @SuppressWarnings("unchecked")
        public static Object deepCopyValue(Object v)
        {
            if (v instanceof Map)
            {
                Map<Object, Object> m = (Map<Object, Object>) v;
                LinkedHashMap<String, Object> copy = new LinkedHashMap<>(Math.max(4, m.size() * 2));
                for (Map.Entry<Object, Object> e : m.entrySet())
                {
                    copy.put(String.valueOf(e.getKey()), deepCopyValue(e.getValue()));
                }
                return copy;
            }
            if (v instanceof Iterable)
            {
                ArrayList<Object> copy = new ArrayList<>();
                for (Object o : (Iterable<?>) v)
                {
                    copy.add(deepCopyValue(o));
                }
                return copy;
            }
            if (v != null && v.getClass().isArray())
            {
                int len = Array.getLength(v);
                ArrayList<Object> copy = new ArrayList<>(len);
                for (int i = 0; i < len; i++)
                {
                    copy.add(deepCopyValue(Array.get(v, i)));
                }
                return copy;
            }
            return v;
        }

        private static void writeMap(StringBuilder sb, Map<?, ?> map)
        {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet())
            {
                if (!first) sb.append(',');
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                writeValue(sb, e.getValue());
            }
            sb.append('}');
        }

        private static void writeIterable(StringBuilder sb, Iterable<?> it)
        {
            sb.append('[');
            boolean first = true;
            for (Object o : it)
            {
                if (!first) sb.append(',');
                first = false;
                writeValue(sb, o);
            }
            sb.append(']');
        }

        private static void writeArray(StringBuilder sb, Object arr)
        {
            int len = Array.getLength(arr);
            sb.append('[');
            for (int i = 0; i < len; i++)
            {
                if (i > 0) sb.append(',');
                writeValue(sb, Array.get(arr, i));
            }
            sb.append(']');
        }
    }

    private final int capacity;
    private final LongSupplier clock;
    private final Object[] slot;
    private int head;      // next write index
    private int count;     // retained entries
    private long nextSeq;  // seq to assign to the next event (starts at 1)
    private long lastT;    // order-clamped timestamp of the newest event

    /** Creates a ring with the default capacity (512) using the system clock. */
    public EventRing()
    {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates a ring with the given capacity.
     *
     * @param capacity positive ring size
     * @throws IllegalArgumentException if {@code capacity <= 0}
     */
    public EventRing(int capacity)
    {
        this(capacity, System::currentTimeMillis);
    }

    /** Package-private: injectable clock for deterministic tests. */
    EventRing(int capacity, LongSupplier clock)
    {
        if (capacity <= 0)
        {
            throw new IllegalArgumentException("capacity must be > 0: " + capacity);
        }
        this.capacity = capacity;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.slot = new Object[capacity];
        this.nextSeq = 1;
        this.lastT = Long.MIN_VALUE;
    }

    /**
     * Appends a typed event and returns its immutable record. May evict the oldest retained event
     * when the ring is full.
     *
     * @param type event type, conventionally one of the {@code TYPE_*} constants; must be non-blank
     * @param bot  owning bot name/account (null &rarr; empty string)
     * @param data optional payload; a defensive copy is stored (top-level map preserved as
     *             insertion-ordered LinkedHashMap)
     * @return the added {@link Event}
     * @throws IllegalArgumentException if {@code type} is null or blank
     */
    public synchronized Event add(String type, String bot, Map<String, Object> data)
    {
        if (type == null || type.trim().isEmpty())
        {
            throw new IllegalArgumentException("event type must be non-blank");
        }
        long now = clock.getAsLong();
        if (now < lastT)
        {
            now = lastT; // timestamps must never regress
        }
        lastT = now;
        Event e = new Event(nextSeq, now, type, bot, copyData(data));
        slot[head] = e;
        head = (head + 1) % capacity;
        if (count < capacity)
        {
            count++;
        }
        nextSeq++;
        return e;
    }

    /** Deep defensive copy of a caller-supplied data map (insertion order preserved). */
    private static Map<String, Object> copyData(Map<String, Object> data)
    {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        if (data != null)
        {
            for (Map.Entry<String, Object> e : data.entrySet())
            {
                copy.put(e.getKey(), Json.deepCopyValue(e.getValue()));
            }
        }
        return copy;
    }

    /** Retained event count (0..capacity). */
    public synchronized int size()
    {
        return count;
    }

    /** Ring capacity (constant). */
    public int capacity()
    {
        return capacity;
    }

    /** Seq of the last event added, or 0 when the ring is empty. */
    public synchronized long lastSeq()
    {
        return nextSeq - 1;
    }

    /** Seq of the oldest retained event, or 0 when the ring is empty. */
    public synchronized long firstSeq()
    {
        return count == 0 ? 0 : nextSeq - count;
    }

    /**
     * All retained events in insertion order (oldest &rarr; newest).
     *
     * @return unmodifiable ordered snapshot list
     */
    public synchronized List<Event> events()
    {
        return orderedSnapshot(0);
    }

    /**
     * Idempotent ordered replay: every <b>retained</b> event with {@code seq > since}, oldest to
     * newest. Calling this twice with the same {@code since} returns identical content. Events
     * already evicted by the ring are not recoverable — pass {@code since >= firstSeq() - 1} to
     * see the whole retained tail.
     *
     * @param since return only events strictly newer than this seq (0 or negative &rarr; all retained)
     * @return unmodifiable ordered snapshot list
     */
    public synchronized List<Event> replaySince(long since)
    {
        return orderedSnapshot(since);
    }

    /**
     * Immutable lookup of a single event by sequence number.
     *
     * @return the event or {@code null} when not retained
     */
    public synchronized Event get(long seq)
    {
        for (int i = 0; i < count; i++)
        {
            Event e = (Event) slot[(head - count + i + capacity) % capacity];
            if (e.seq == seq)
            {
                return e;
            }
        }
        return null;
    }

    /**
     * JSON body in the frozen contract shape for this instance's retained events:
     * {@code {"events":[{"seq","t","type","bot","data"}]}}.
     */
    public String eventsJson()
    {
        return eventsJsonSince(0);
    }

    /**
     * JSON body in the frozen contract shape containing the {@link #replaySince(long)} result:
     * {@code {"events":[{"seq","t","type","bot","data"}]}}.
     */
    public synchronized String eventsJsonSince(long since)
    {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"events\":[");
        boolean first = true;
        for (int i = 0; i < count; i++)
        {
            Event e = (Event) slot[(head - count + i + capacity) % capacity];
            if (e.seq <= since)
            {
                continue;
            }
            if (!first)
            {
                sb.append(',');
            }
            first = false;
            sb.append(e.toJson());
        }
        sb.append("]}");
        return sb.toString();
    }

    /** Ordered snapshot of retained events with seq strictly greater than {@code since}. */
    private List<Event> orderedSnapshot(long since)
    {
        List<Event> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            Event e = (Event) slot[(head - count + i + capacity) % capacity];
            if (e.seq > since)
            {
                out.add(e);
            }
        }
        return Collections.unmodifiableList(out);
    }
}
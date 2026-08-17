package com.aiplayer.phase0.cabinet;

/** MODE: COMPLETE. In-memory replacement for CabinetService+RedisCache. No Postgres/Redis. */

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory replacement for CabinetService (PostgreSQL) and RedisCache
 * (Redis). At the current scale (a single JVM, tens of AI Players, not
 * thousands across multiple processes), a second database and a cache
 * server are operational weight with no benefit — this was flagged in the
 * external review and matches this project's own earlier stated position on
 * Postgres/Redis (see the architecture doc from the previous session).
 *
 * Same public method names as CabinetService/RedisCache where practical, so
 * callers change their import and instantiation but not their call sites.
 *
 * LEGIT_TODO: profiles are lost on JVM restart. If persistence across
 * restarts is actually needed later, add JSON serialization to/from a flat
 * file here — do not reach for Postgres+Redis again without a concrete,
 * measured reason tied to real scale, not anticipated scale.
 */
public final class ProfileStore {
    private static ProfileStore instance;

    private final Map<String, BotProfile> profilesByAccount = new ConcurrentHashMap<>();
    private final Map<String, List<String>> chatHistory = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> botState = new ConcurrentHashMap<>();
    private final List<Episode> episodes = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, List<Relation>> relations = new ConcurrentHashMap<>();

    private static final int CHAT_HISTORY_LIMIT = 10;

    private ProfileStore() {
    }

    public static synchronized ProfileStore getInstance() {
        if (instance == null) {
            instance = new ProfileStore();
        }
        return instance;
    }

    // --- CabinetService-equivalent API ---

    public BotProfile loadProfile(String accountName) {
        return profilesByAccount.get(accountName);
    }

    public void saveProfile(BotProfile profile) {
        profilesByAccount.put(profile.getAccountName(), profile);
    }

    public List<BotProfile> loadAllActive() {
        return new ArrayList<>(profilesByAccount.values());
    }

    public void recordEpisode(UUID botId, String eventType, String description,
                              String location, String involvedPlayer, String emotionalTag) {
        episodes.add(new Episode(botId, eventType, description, location, involvedPlayer,
                                  emotionalTag, System.currentTimeMillis()));
    }

    public void updateRelation(UUID botId, String targetName, int trustDelta, String tag) {
        relations.computeIfAbsent(botId.toString(), k -> Collections.synchronizedList(new ArrayList<>()))
                 .add(new Relation(targetName, trustDelta, tag, System.currentTimeMillis()));
    }

    // --- RedisCache-equivalent API ---

    public void cacheProfile(BotProfile p) {
        saveProfile(p); // no separate hot/cold tier without an actual cache server
    }

    public BotProfile getProfile(String accountName) {
        return loadProfile(accountName);
    }

    public void setBotState(String accountName, String field, String value) {
        botState.computeIfAbsent(accountName, k -> new ConcurrentHashMap<>()).put(field, value);
    }

    public String getBotState(String accountName, String field) {
        Map<String, String> fields = botState.get(accountName);
        return fields == null ? null : fields.get(field);
    }

    public void pushChat(String accountName, String line) {
        List<String> lines = chatHistory.computeIfAbsent(accountName,
            k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (lines) {
            lines.add(line);
            while (lines.size() > CHAT_HISTORY_LIMIT) {
                lines.remove(0);
            }
        }
    }

    public List<String> getChatHistory(String accountName) {
        return new ArrayList<>(chatHistory.getOrDefault(accountName, Collections.emptyList()));
    }

    public static final class Episode {
        public final UUID botId;
        public final String eventType, description, location, involvedPlayer, emotionalTag;
        public final long timestamp;

        Episode(UUID botId, String eventType, String description, String location,
                String involvedPlayer, String emotionalTag, long timestamp) {
            this.botId = botId;
            this.eventType = eventType;
            this.description = description;
            this.location = location;
            this.involvedPlayer = involvedPlayer;
            this.emotionalTag = emotionalTag;
            this.timestamp = timestamp;
        }
    }

    public static final class Relation {
        public final String targetName;
        public final int trustDelta;
        public final String tag;
        public final long timestamp;

        Relation(String targetName, int trustDelta, String tag, long timestamp) {
            this.targetName = targetName;
            this.trustDelta = trustDelta;
            this.tag = tag;
            this.timestamp = timestamp;
        }
    }
}

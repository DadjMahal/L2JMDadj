package com.aiplayer.knowledge;

/**
 * GK-6 — KnowledgeBase: load the generated datapack JSON into id-indexed memory and answer
 * mission queries. Loaded at first {@link #getInstance()} (sub-second); logs the required boot
 * line: {@code knowledge loaded: N npcs, M quests, K items in Xms}.
 */
public final class KnowledgeBase
{
    private static final String TAG = "knowledge";
    private static KnowledgeBase instance;

    private java.util.Map<Integer, Item> items;
    private java.util.Map<Integer, Npc> npcs;
    private java.util.Map<Integer, Quest> quests;
    private java.util.Map<Integer, java.util.List<Skill>> skillsByClass;
    private java.util.Set<Integer> soldItemIds;
    private java.util.Map<Integer, ClassInfo> classesById;
    private java.util.Map<Integer, java.util.List<ClassInfo>> classTreeByBase;
    private java.util.Map<Integer, java.util.List<ChainStep>> chainsByBase;
    private final long loadNanos;

    private KnowledgeBase()
    {
        long t0 = System.nanoTime();
        load();
        loadNanos = System.nanoTime() - t0;
    }

    public static synchronized KnowledgeBase getInstance()
    {
        if (instance == null)
        {
            instance = new KnowledgeBase();
        }
        return instance;
    }

    /** items.json record (JSON-true). */
    public static final class Item
    {
        public final int id, price, crystal;
        public final String name, grade, type, slot, weaponType;
        Item(int id, String name, String grade, String type, String slot, int price, int crystal, String weaponType)
        {
            this.id = id; this.name = name; this.grade = grade; this.type = type;
            this.slot = slot; this.price = price; this.crystal = crystal; this.weaponType = weaponType;
        }
    }

    /** npcs.json record. */
    public static final class Npc
    {
        public final int id, aggroRange;
        public final String name, type;
        public final Integer level, hp;
        public final boolean isAggressive;
        public final java.util.List<Drop> drops;
        public final java.util.List<Spawn> spawns;
        Npc(int id, String name, Integer level, Integer hp, int aggroRange, boolean aggressive,
            String type, java.util.List<Drop> drops, java.util.List<Spawn> spawns)
        {
            this.id = id; this.name = name; this.level = level; this.hp = hp;
            this.aggroRange = aggroRange; this.isAggressive = aggressive; this.type = type;
            this.drops = drops; this.spawns = spawns;
        }
    }

    /** A drop row (chance in (0,1]). */
    public static final class Drop
    {
        public final int itemId; public final Double chance; public final Integer min, max;
        Drop(int itemId, Double chance, Integer min, Integer max)
        { this.itemId = itemId; this.chance = chance; this.min = min; this.max = max; }
    }

    /** A spawn point (world coords + zone hint). */
    public static final class Spawn
    {
        public final int x, y; public final Integer z; public final String zoneHint;
        Spawn(int x, int y, Integer z, String zoneHint)
        { this.x = x; this.y = y; this.z = z; this.zoneHint = zoneHint; }
    }

    /** quests.json record. */
    public static final class Quest
    {
        public final int id;
        public final String name;
        public final Integer minLevel, maxLevel, startNpc;
        public final java.util.List<Integer> talkNpcs, rewards;
        public final java.util.List<String> races;
        public final boolean needsReview;
        Quest(int id, String name, Integer minLevel, Integer maxLevel, Integer startNpc,
              java.util.List<Integer> talkNpcs, java.util.List<Integer> rewards,
              java.util.List<String> races, boolean needsReview)
        {
            this.id = id; this.name = name; this.minLevel = minLevel; this.maxLevel = maxLevel;
            this.startNpc = startNpc; this.talkNpcs = talkNpcs; this.rewards = rewards;
            this.races = races; this.needsReview = needsReview;
        }
    }

/** skills.json row. */
    public static final class Skill
    {
        public final int id, classId, skillLevel, cost;
        public final Integer parentClass, level;
        Skill(int id, int classId, Integer parentClass, Integer level, int skillLevel, int cost)
        {
            this.id = id; this.classId = classId; this.parentClass = parentClass;
            this.level = level; this.skillLevel = skillLevel; this.cost = cost;
        }
    }

    /** GK-8: classes.json chain row (one class inside a base's tier tree). */
    public static final class ClassInfo
    {
        public final int classId, tier, baseClassId;
        public final String name, baseName;
        ClassInfo(int classId, String name, int tier, int baseClassId, String baseName)
        {
            this.classId = classId; this.name = name; this.tier = tier;
            this.baseClassId = baseClassId; this.baseName = baseName;
        }
    }

    /** GK-8: chains.json step row (one quest milestone on a race×base zero→hero chain). */
    public static final class ChainStep
    {
        public final int level, npc, questId;
        public final String kind, name;
        ChainStep(String kind, int level, String name, int npc, int questId)
        {
            this.kind = kind; this.level = level; this.name = name;
            this.npc = npc; this.questId = questId;
        }
    }
    // ================================================================
    // Queries
    // ================================================================

    public Item item(int id) { return items.get(id); }
    public Npc npc(int id) { return npcs.get(id); }
    public Quest quest(int id) { return quests.get(id); }
    public java.util.List<Skill> skillLadder(int classId)
    {
        return skillsByClass.getOrDefault(classId, java.util.Collections.emptyList());
    }
    public int npcCount() { return npcs.size(); }
    public int questCount() { return quests.size(); }
    public int itemCount() { return items.size(); }
    public long loadMillis() { return loadNanos / 1_000_000; }

    /** All loaded npcs (for anchor/dropper scans). */
    public java.util.Collection<Npc> allNpcs()
    {
        return java.util.Collections.unmodifiableCollection(npcs.values());
    }

    /** GK-8: all loaded items (for gear-candidate scans). */
    public java.util.Collection<Item> allItems()
    {
        return java.util.Collections.unmodifiableCollection(items.values());
    }

    /** GK-8: class info by classId, or null when unknown (name/tier/base for build choices). */
    public ClassInfo classInfo(int classId)
    {
        return classesById.get(classId);
    }

    /** GK-8: the full tier tree (tier 0..3) of a base class, ordered as listed in classes.json. */
    public java.util.List<ClassInfo> classTree(int baseClassId)
    {
        return classTreeByBase.getOrDefault(baseClassId, java.util.Collections.emptyList());
    }

    /** GK-8: zero→hero quest chain steps of a race×base (GK-7 chains.json), in level order. */
    public java.util.List<ChainStep> chainSteps(int baseClassId)
    {
        return chainsByBase.getOrDefault(baseClassId, java.util.Collections.emptyList());
    }

    /** Every npc whose drop table includes {@code itemId}. */
    public java.util.List<Npc> droppersOf(int itemId)
    {
        java.util.List<Npc> out = new java.util.ArrayList<>();
        for (Npc n : npcs.values())
        {
            for (Drop d : n.drops)
            {
                if (d.itemId == itemId)
                {
                    out.add(n);
                    break;
                }
            }
        }
        return out;
    }

    /** Quests a bot at {@code level} may take, optionally filtered by required race name. */
    public java.util.List<Quest> questsFor(int level, String raceName)
    {
        java.util.List<Quest> out = new java.util.ArrayList<>();
        for (Quest q : quests.values())
        {
            if (q.needsReview || q.startNpc == null)
            {
                continue;
            }
            if (q.minLevel != null && level < q.minLevel)
            {
                continue;
            }
            if (raceName != null && !q.races.isEmpty() && !q.races.contains(raceName))
            {
                continue;
            }
            out.add(q);
        }
        out.sort((a, b) -> Integer.compare(a.id, b.id));
        return out;
    }

    // ================================================================
    // Loading
    // ================================================================

    private void load()
    {
        items = new java.util.HashMap<>();
        npcs = new java.util.HashMap<>();
        quests = new java.util.HashMap<>();
        skillsByClass = new java.util.HashMap<>();
        soldItemIds = new java.util.HashSet<>();
        classesById = new java.util.HashMap<>();
        classTreeByBase = new java.util.HashMap<>();
        chainsByBase = new java.util.HashMap<>();

        parseItems(JsonResource.autoObjectList("items.json"));
        parseNpcs(JsonResource.autoObjectList("npcs.json"));
        parseQuests(JsonResource.autoObjectList("quests.json"));
        parseSkills(JsonResource.autoObjectList("skills.json"));
        parseShops(JsonResource.autoObjectList("shops.json"));
        parseClasses(JsonResource.autoObjectList("classes.json"));
        parseChains(JsonResource.autoObjectList("chains.json"));

        java.util.logging.Logger.getLogger(KnowledgeBase.class.getName())
            .info(TAG + " loaded: " + npcs.size() + " npcs, " + quests.size()
                + " quests, " + items.size() + " items in " + (loadNanos / 1_000_000) + "ms");
    }

    private static java.util.List<java.util.Map<String, Object>> objectList(String fileName)
    {
        return JsonResource.autoObjectList(fileName);
    }

    private void parseItems(java.util.List<java.util.Map<String, Object>> rows)
    {
        for (java.util.Map<String, Object> r : rows)
        {
            Integer id = (Integer) r.get("id");
            if (id == null)
            {
                continue;
            }
            items.put(id, new Item(id, str(r, "name"), str(r, "grade"), str(r, "type"),
                str(r, "slot"), int0(r, "price"), int0(r, "crystal"), str(r, "weaponType")));
        }
    }

    private void parseNpcs(java.util.List<java.util.Map<String, Object>> rows)
    {
        for (java.util.Map<String, Object> r : rows)
        {
            Integer id = (Integer) r.get("id");
            if (id == null)
            {
                continue;
            }
            java.util.List<Drop> drops = new java.util.ArrayList<>();
            Object dropsRaw = r.get("drops");
            if (dropsRaw instanceof java.util.List)
            {
                for (Object d0 : (java.util.List<?>) dropsRaw)
                {
                    java.util.Map<String, Object> d = (java.util.Map<String, Object>) d0;
                    drops.add(new Drop(int0(d, "itemId"), dbl(d, "chance"),
                        intOrNull(d, "min"), intOrNull(d, "max")));
                }
            }
            java.util.List<Spawn> spawns = new java.util.ArrayList<>();
            Object spawnsRaw = r.get("spawns");
            if (spawnsRaw instanceof java.util.List)
            {
                for (Object s0 : (java.util.List<?>) spawnsRaw)
                {
                    java.util.Map<String, Object> s = (java.util.Map<String, Object>) s0;
                    spawns.add(new Spawn(int0(s, "x"), int0(s, "y"), intOrNull(s, "z"),
                        s.get("zoneHint") == null ? "" : s.get("zoneHint").toString()));
                }
            }
            npcs.put(id, new Npc(id, str(r, "name"), intOrNull(r, "level"), intOrNull(r, "hp"),
                int0(r, "aggroRange"), Boolean.TRUE.equals(r.get("isAggressive")),
                str(r, "type"), drops, spawns));
        }
    }

    private void parseQuests(java.util.List<java.util.Map<String, Object>> rows)
    {
        for (java.util.Map<String, Object> r : rows)
        {
            Integer id = (Integer) r.get("id");
            if (id == null)
            {
                continue;
            }
            quests.put(id, new Quest(id, str(r, "name"), intOrNull(r, "minLevel"),
                intOrNull(r, "maxLevel"), intOrNull(r, "startNpc"),
                intList(r, "talkNpcs"), intList(r, "rewards"),
                stringList(r, "races"), Boolean.TRUE.equals(r.get("needsReview"))));
        }
    }

    private void parseSkills(java.util.List<java.util.Map<String, Object>> rows)
    {
        for (java.util.Map<String, Object> r : rows)
        {
            Integer id = (Integer) r.get("id");
            Integer cls = (Integer) r.get("class");
            if (id == null || cls == null)
            {
                continue;
            }
            skillsByClass.computeIfAbsent(cls, k -> new java.util.ArrayList<>()).add(
                new Skill(id, cls, intOrNull(r, "parentClass"), intOrNull(r, "level"),
                    int0(r, "skillLevel"), int0(r, "cost")));
        }
    }

    private void parseShops(java.util.List<java.util.Map<String, Object>> rows)
    {
        // GK-8: item ids sold in ANY buylist (shop availability for the build recommender).
        for (java.util.Map<String, Object> r : rows)
        {
            Object itemsRaw = r.get("items");
            if (!(itemsRaw instanceof java.util.List))
            {
                continue;
            }
            for (Object it0 : (java.util.List<?>) itemsRaw)
            {
                if (it0 instanceof java.util.Map)
                {
                    Object iid = ((java.util.Map<?, ?>) it0).get("itemId");
                    if (iid instanceof Integer)
                    {
                        soldItemIds.add((Integer) iid);
                    }
                }
            }
        }
    }

    /** True when some buylist sells this item id (shop availability for the build guide). */
    public boolean isSoldInShop(int itemId)
    {
        return soldItemIds.contains(itemId);
    }

    private void parseClasses(java.util.List<java.util.Map<String, Object>> rows)
    {
        // GK-8: classes.json — per-base tier tree ({baseClassId, baseName, chain:[{classId,name,tier}]}).
        for (java.util.Map<String, Object> r : rows)
        {
            Integer base = (Integer) r.get("baseClassId");
            if (base == null)
            {
                continue;
            }
            String baseName = str(r, "baseName");
            java.util.List<ClassInfo> tree = new java.util.ArrayList<>();
            Object chainRaw = r.get("chain");
            if (chainRaw instanceof java.util.List)
            {
                for (Object c0 : (java.util.List<?>) chainRaw)
                {
                    if (!(c0 instanceof java.util.Map))
                    {
                        continue;
                    }
                    java.util.Map<?, ?> c = (java.util.Map<?, ?>) c0;
                    Integer cid = (Integer) c.get("classId");
                    if (cid == null)
                    {
                        continue;
                    }
                    ClassInfo info = new ClassInfo(cid, c.get("name") == null ? "" : c.get("name").toString(),
                        int0((java.util.Map<String, Object>) c, "tier"), base, baseName);
                    classesById.put(cid, info);
                    tree.add(info);
                }
            }
            classTreeByBase.put(base, java.util.Collections.unmodifiableList(tree));
        }
    }

    private void parseChains(java.util.List<java.util.Map<String, Object>> rows)
    {
        // GK-8: chains.json — race×base zero→hero quest steps ({baseClassId, steps:[{kind,level,...}]}).
        for (java.util.Map<String, Object> r : rows)
        {
            Integer base = (Integer) r.get("baseClassId");
            if (base == null)
            {
                continue;
            }
            java.util.List<ChainStep> steps = new java.util.ArrayList<>();
            Object stepsRaw = r.get("steps");
            if (stepsRaw instanceof java.util.List)
            {
                for (Object s0 : (java.util.List<?>) stepsRaw)
                {
                    if (!(s0 instanceof java.util.Map))
                    {
                        continue;
                    }
                    java.util.Map<String, Object> s = (java.util.Map<String, Object>) s0;
                    steps.add(new ChainStep(str(s, "kind"), int0(s, "level"), str(s, "name"),
                        int0(s, "npc"), int0(s, "questId")));
                }
            }
            chainsByBase.put(base, java.util.Collections.unmodifiableList(steps));
        }
    }

    private static String str(java.util.Map<String, Object> m, String key)
    {
        Object v = m.get(key);
        return v == null ? "" : v.toString();
    }

    private static int int0(java.util.Map<String, Object> m, String key)
    {
        Integer v = intOrNull(m, key);
        return v == null ? 0 : v;
    }

    private static Integer intOrNull(java.util.Map<String, Object> m, String key)
    {
        Object v = m.get(key);
        return v instanceof Integer ? (Integer) v : null;
    }

    private static Double dbl(java.util.Map<String, Object> m, String key)
    {
        Object v = m.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : null;
    }

    private static java.util.List<Integer> intList(java.util.Map<String, Object> m, String key)
    {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        Object v = m.get(key);
        if (v instanceof java.util.List)
        {
            for (Object o : (java.util.List<?>) v)
            {
                if (o instanceof Integer)
                {
                    out.add((Integer) o);
                }
            }
        }
        return out;
    }

    private static java.util.List<String> stringList(java.util.Map<String, Object> m, String key)
    {
        java.util.List<String> out = new java.util.ArrayList<>();
        Object v = m.get(key);
        if (v instanceof java.util.List)
        {
            for (Object o : (java.util.List<?>) v)
            {
                out.add(o.toString());
            }
        }
        return out;
    }
}
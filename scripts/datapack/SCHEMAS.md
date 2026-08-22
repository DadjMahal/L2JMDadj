# SCHEMAS.md — generated knowledge JSON contracts (GK-1)

Source of truth: `SourceCode/dist/game/data/` (read-only). Generated into
`AIPlayerEngine/src/main/resources/knowledge/`. Every file is a top-level JSON array of
objects; fields below are the FULL contract. GK-1 ships empty-but-valid skeletons; GK-2..GK-5
fill the entries. `validate.py` (this dir) enforces the invariants §last.

---

## npcs.json  (GK-2)
| field | type | meaning |
|---|---|---|
| id | int | NPC id (stats/npcs) |
| name | string | normalized display name |
| level | int | level |
| hp | int | max HP |
| aggroRange | int | aggro radius (0 = passive) |
| isAggressive | bool | attacks on sight |
| type | string | race/type enum value |
| drops | array<npc> | drop rows (see drop table) |
| dropRows `{itemId, chance, min, max}` | int/int/int/int | drop chance in (0,1], clamped at 1.0 |
| spawns | array<npc> | spawn points |
| spawnRow `{x, y, z, zoneHint}` | int×3+string | in-world centroid + zone attr |

> Notes (GK-2): `drops` merges BOTH `<drop>` (grouped) and `<spoil>` (direct) rows. Drop
> chance is the XML percent ÷ 100, clamped to 1.0 (chance>100 = multi-roll guarantee; a
> single-roll probability can't exceed 1). `zoneHint` = the `<spawn zone=...>` attribute.

## items.json  (GK-3)
| field | type | meaning |
|---|---|---|
| id | int | item id (stats/items) |
| name | string | normalized display name |
| grade | string | grade letter (0..S80) or "" |
| type | string | armor/weapon/etc. type |
| slot | string | body slot (for armor) |
| price | int | base sell price |
| weaponType | string | weapon class (one-hand etc.) |
| crystal | int | crystal count strike |
| crystalType | string | crystal grade needed |

## skills.json  (GK-3)
| field | type | meaning |
|---|---|---|
| id | int | skill id |
| class | string | class id this entry belongs to |
| level | int | learnable level |
| skillLevel | int | skill level granted |
| cost | int | SP cost |
| minLevel | int | min character level |

## spawns.json  (GK-2)
| field | type | meaning |
|---|---|---|
| npcId | int | NPC id spawned |
| x, y, z | int | world position (bounds enforced) |
| zone | string | zone label (from file path / region) |

## quests.json  (GK-4)
| field | type | meaning |
|---|---|---|
| id | int | quest id (scripts/quests folder) |
| name | string | class/quest name |
| startNpc | int | quest-giver NPC id |
| talkNpcs | array<int> | NPCs in the talk graph |
| items | array<int> | quest items |
| rewards | array<int> | reward item ids |
| minLevel | int | min level to accept |
| maxLevel | int | max level (Interlude no-max often -1) |
| races | array<string> | races REQUIRED (getRace()==Race.X) | 
| classExclusions | array<string> | races RESTRICTED (getRace()!=Race.X) |
| chain | `{prev, next}` int|null | quest chain |

> Notes (GK-4): regex over the formulaic Java sources. `minLevel` from `getLevel() < N`,
> `getLevel() >= N`, `getLevel() > N` (min N+1) or `MIN_LEVEL = N` const (strictest wins).
> `races` = REQUIRED via `getRace() == Race.X`; restricted (`!=`) -> `classExclusions`.
> `htmGraph` = dialog files grouped by leading `<npcId>` segment. Absent fields set
> `needsReview=true`; review report printed (≤ 50 entries).

## shops.json  (GK-5)
| field | type | meaning |
|---|---|---|
| id | int | buylist / multisell list id (file name) |
| kind | string | `"buylist"` or `"multisell"` |
| npcId | array<int> | multisell: vendor NPCs from `<npcs>`; buylist: null + needsReview (linkage not in the buylist file) |
| items | array<shop> | buylist rows |
| shopRow `{itemId, price, count}` | int×3 | item + price + stock count (count=0 for buylists) |
| offers | array<offer> | multisell recipes |
| offer `{id, count, ingredients:[{itemId,count}]}` | int×2+array | production item + its ingredients |
| needsReview | bool | true when a field couldn't be resolved (never guessed) |

> Notes (GK-5): buylists carry NO vendor NPC in the read-only file (that linkage lives in the
> server NPC-template layer), so buylist records are always `needsReview:true` with `npcId:null`
> — honest, never made up.

## classes.json  (GK-3)
| field | type | meaning |
|---|---|---|
| baseClassId | int | root class (no parent) |
| baseName | string | base class name |
| chain | array<class> | classes under this base, each with tier |
| chainClass `{classId, name, tier}` | int/string/int | tier 0=base, 1=1st prof, 2=2nd, 3=3rd |

## chains.json  (GK-7)
| field | type | meaning |
|---|---|---|
| race | string | race (HUMAN/ELF/DARK_ELF/ORC/DWARF) |
| baseClassId | int | base class id (from classes.json) |
| baseName | string | base class name |
| steps | array<step> | ordered roadmap |
| step `{kind, questId, name, level, npc}` | string/int/str/int/int | kind: newbie/leveling/endgame/firstClass/secondClass |

> Transfer selection is a naming heuristic (documented in RuntimeLog): race pre-filter +
> chain-token scoring + mystic-vs-fighter line filter. Not a perfect class attribution — that
> needs per-class quest data the extraction doesn't carry yet.

## map.json  (GK-9)
| field | type | meaning |
|---|---|---|
| kind | string | `"teleporter"` \| `"zone"` \| `"route"` \| `"spawnRegion"` |
| id | int/string | unique record id (see each kind) |
| — teleporter: npcId, category, type | int/str/str | teleport NPC + subfolder (town/dungeon/others/chamberlain/doorman/clanhall) + teleport type (NORMAL/OTHER/NOBLES_*) |
| destinations `{name,x,y,z,feeId,feeCount}` | array | reachable locations (feeId for nobles-* types) |
| — zone: name, type, shape, minZ, maxZ | str×3+int×2 | zone footprint (Town/Peace/NoLanding/pvp; NPoly/Cuboid/Cylinder) |
| nodes `{x,y}` + `{x,y,z}` centroid | array | polygon/box footprint (x/y only; z band = minZ..maxZ) |
| — route: name, repeat, repeatStyle, points `{x,y,z,delay,run}` | array | scripted NPC walk routes (Routes.xml) |
| — spawnRegion: name, spawnCount, `{x,y}` centroid, minLevel, maxLevel | str/int×2+int×2 | aggregated spawn density per world region (zoneHint prefix) for hunting nav |
| needsReview | bool | GK-5 pattern: boss/raid/event zones (Valakas lair, Seed of Annihilation…) lie outside the playable world box — flagged honestly, never faked |

> Notes (GK-9): `spawnRegion`s aggregate the 10,754 spawn rows (npcs.json) per zoneHint
> prefix — the natural "where do I hunt at level N" unit (centroid + level band). Zone
> records carry the full raw polygon (incl. event areas) so consumers can room-augment;
> `needsReview:true` zones are exempted from the world-bounds invariant (documented outlier,
> not a data error). Teleporter destinations and route waypoints are strictly in-bounds.

---

## Invariants enforced by validate.py
1. Every expected file exists and parses as JSON (`schema presence`).
2. No null/absent `id` on any record (npcs/items/skills/spawns/quests/shops).
3. Coordinates within world bounds: x∈[-204800,204800], y∈[-204800,204800], z∈[-16000,16000].
4. Drop chances in open `(0, 1]` (absent chance = no violation).
5. Empty files are VALID (GK-1 acceptance: empty-but-valid passes).
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
| races | array<int> | race mask |
| classes | array<int> | class ids |
| chain | `{prev, next}` int|null | quest chain |

## shops.json  (GK-5)
| field | type | meaning |
|---|---|---|
| npcId | int | vendor NPC id |
| items | array<shop> | buy-list rows |
| shopRow `{itemId, price, count}` | int×3 | item + price + stock count |
| multisell | array<int> | multisell list ids |

---

## Invariants enforced by validate.py
1. Every expected file exists and parses as JSON (`schema presence`).
2. No null/absent `id` on any record (npcs/items/skills/spawns/quests/shops).
3. Coordinates within world bounds: x∈[-204800,204800], y∈[-204800,204800], z∈[-16000,16000].
4. Drop chances in open `(0, 1]` (absent chance = no violation).
5. Empty files are VALID (GK-1 acceptance: empty-but-valid passes).
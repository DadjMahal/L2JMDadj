# RuntimeLog — 2026-08-22 GK-5 — shops.json (buylists + multisell)

## Task
GK-5 — Shop extractor (616 buylists + 93 multisell) -> `shops.json`.

## What was done
`scripts/datapack/extract_shops.py`:
- **buylists/** (616 files): `<item id price/>` per buylist = shop list id (file stem).
  -> {id, kind:"buylist", items:[{itemId, price}]}.
- **multisell/** (95 files): `<list><npcs><npc>` vendors + `<item><ingredient>` +
  `<production>` recipes. -> {id, kind:"multisell", npcId:[vendors], offers:[{id, count,
  ingredients:[{itemId, count}]}]}.

## HONEST boundary (documented in SCHEMAS.md)
Buylist files do NOT carry which merchant NPC opens them (that linkage lives in the server
NPC-template layer, not in the read-only datapack shop files). So buylist records set
`npcId=null` + `needsReview:true` — never guessed. 6/95 multisells also have no `<npcs>` block
(recipe-only lists) — flagged the same way.

## Counts (real)
buylists 616 (item rows 18,728) · multisells 95 (offers 8,609, vendor npcs 616) -> shops.json
3.0MB. Sample: multisell id=1 -> offers [{id:22, ing: adena 2100 + item 21}, ...].

## Evidence / gate
- `extract_all.py` **7/7** · `validate.py` files=7 issues=0.
- One commit set, pushed to master.
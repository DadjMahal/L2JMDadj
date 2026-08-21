#!/usr/bin/env python3
"""watch_fleet.py — fleet watcher (S9-T01/S9-T02 fix).

Polls a FleetPlay dashboard /json every INTERVAL seconds and appends one per-race line per poll
to NOTES. XP/min is computed from CONSECUTIVE polls (prev state is refreshed after each poll —
the original bug froze prev at startup, so xp/min always read 0).

Usage: python3 watch_fleet.py [dashUrl] [notesPath] [intervalSec] [durationMin]
"""
import sys, json, time, os, urllib.request
from collections import Counter, defaultdict

URL  = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8210/json"
# EP-6: dashboard token (query param) when configured via fleet_env.local.
if os.environ.get("DASH_TOKEN"):
    URL += ("&" if "?" in URL else "?") + "token=" + os.environ["DASH_TOKEN"]
NOTES= sys.argv[2] if len(sys.argv) > 2 else "/tmp/watch_fleet.log"
INTERVAL = int(sys.argv[3]) if len(sys.argv) > 3 else 120
DURATION = (int(sys.argv[4]) if len(sys.argv) > 4 else 120) * 60
JSON_MODE = len(sys.argv) > 5 and sys.argv[5].lower() == "json"   # S2-T09: emit JSON-lines
STATE = NOTES + ".state"
RN = {1:"ELF", 2:"DARK_ELF", 3:"ORC", 4:"DWARF", 0:"HUMAN"}
RACES = ["ELF","DARK_ELF","ORC","DWARF","HUMAN"]

def snap():
    try:
        with urllib.request.urlopen(URL, timeout=15) as r:
            return json.load(r).get("bots", [])
    except Exception:
        return None

def load_prev():
    try:
        return json.load(open(STATE))
    except Exception:
        return {}

def save_prev(p):
    try:
        json.dump(p, open(STATE, "w"))
    except Exception:
        pass

def line(s):
    with open(NOTES, "a") as f:
        f.write(s + "\n")

prev = load_prev()
start = time.time()
line(f"=== watcher start {time.strftime('%H:%M:%S')} url={URL} every={INTERVAL}s dur={DURATION//60}m ===")
while time.time() - start < DURATION:
    t0 = time.time()
    bots = snap()
    elapsed = int((time.time() - start) / 60)
    if bots is None:
        line(f"[{elapsed}min] ERROR: dashboard unreachable")
        time.sleep(INTERVAL); continue
    per_race = Counter(); lvl_sum = defaultdict(int); exp_sum = defaultdict(int)
    mobile = Counter(); stalled = Counter(); dead = Counter(); retreat = Counter()
    for b in bots:
        try:
            i = int(b.get("account","").replace("ai_rand_","") or 0)
        except ValueError:
            continue
        race = RN[i % 5]
        per_race[race] += 1
        lvl_sum[race] += b.get("level", 0); exp_sum[race] += b.get("exp", 0)
        st = b.get("state","")
        if st == "dead": dead[race] += 1
        if st == "retreat": retreat[race] += 1
        if b.get("movedLast60", 0) > 0: mobile[race] += 1
        else: stalled[race] += 1
    cur = {r: exp_sum[r] for r in RACES}
    pexp = prev.get("exp", {})
    bits = []
    races_json = {}
    for r in RACES:
        n = per_race[r]
        d = cur.get(r,0) - pexp.get(r,0)
        xpm = int(d / (INTERVAL / 60.0)) if d > 0 else 0
        bits.append(f"{r}:n{n}/avgLv{lvl_sum[r]//n if n else 0}/xpmin{xpm}/mob{mobile[r]}/stall{stalled[r]}/dead{dead[r]}/ret{retreat[r]}")
        races_json[r] = {"n": n, "avgLv": lvl_sum[r]//n if n else 0, "xpmin": xpm,
                         "mob": mobile[r], "stall": stalled[r], "dead": dead[r], "ret": retreat[r]}
    if JSON_MODE:
        import json as _json
        line(_json.dumps({"elapsed_min": elapsed, "ts": time.time(), "races": races_json}))  # S2-T09
    else:
        line(f"[{elapsed}min] " + " ".join(bits))
    prev = {"exp": cur}          # FIX (S9-T02): refresh prev so xp/min is a per-interval delta
    save_prev(prev)
    time.sleep(max(5, INTERVAL - (time.time() - t0)))
line("=== watcher end ===")

#!/usr/bin/env python3
"""
AIWebDashboard — real-time stats for L2JM AI players.

Zero-dependency (Python 3 stdlib only). Shows: online, level, exp, position,
HP%, current target, latest action for every ai_% character, refreshed live.

Data sources (no engine changes):
  1. MySQL `gameserver.characters`  -> online / level / exp / x,y,z / hp  (truth)
  2. latest `<run>/mpsession.out`   -> per-bot ENGAGE target + DIAG action

Start:  setsid nohup python3 dashboard.py [port] &
Reach:  http://<host>:8199/
"""
import json
import os
import re
import subprocess
import threading
import time

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------
PORT = int(os.environ.get("DASHBOARD_PORT", "8199"))
RUN_DIR_TMP = "/tmp/run_dir.txt"
DEFAULT_RUN = "/home/volodro/L2JM/AIStatusLogs"
REFRESH_S = 2          # background refresh cadence
LOG_TAIL_BYTES = 1_500_000  # read last 1.5MB of driver log per refresh

ENGAGE_RE = re.compile(r"\[MP\] (\S+) ENGAGE target=(\d+) actions=(\d+) hostileCount=(\d+)")
DIAG_RE = re.compile(r"\[MP\] (\S+) DIAG action=(\w+)(?: target=(-?\d+))?.*hostiles=(\d+)")
DEAD_RE = re.compile(r"\[MP\] (\S+) DEAD")


def find_run_dir():
    """Prefer explicit /tmp/run_dir.txt, else newest multiplayer_run_* dir."""
    try:
        with open(RUN_DIR_TMP) as f:
            p = f.read().strip()
            if p.startswith("/"):
                return p
            return os.path.join("/home/volodro/L2JM", p)
    except OSError:
        pass
    try:
        cands = [d for d in os.listdir(DEFAULT_RUN) if d.startswith("multiplayer_run_")]
        if cands:
            cands.sort(reverse=True)
            return os.path.join(DEFAULT_RUN, cands[0])
    except OSError:
        pass
    return DEFAULT_RUN


def query_rows(sql):
    """Run a read-only MySQL query; returns list of list-of-str."""
    try:
        out = subprocess.run(
            ["sudo", "mysql", "-u", "root", "--batch", "--skip-column-names",
             "-e", sql, "gameserver"],
            capture_output=True, text=True, timeout=15,
        )
        rows = []
        for line in out.stdout.splitlines():
            if line.strip():
                rows.append(line.split("\t"))
        return rows
    except Exception as e:  # noqa: BLE001
        return [["ERR", str(e)[:60]]]


def parse_log(run):
    """Tail the driver log for per-bot target + latest action."""
    targets, actions, hostiles, dead = {}, {}, {}, {}
    path = os.path.join(run, "mpsession.out")
    try:
        with open(path, "rb") as f:
            f.seek(0, os.SEEK_END)
            size = f.tell()
            f.seek(max(0, size - LOG_TAIL_BYTES))
            blob = f.read().decode("utf-8", "replace")
    except OSError:
        return targets, actions, hostiles, dead

    for m in DIAG_RE.finditer(blob):
        acc, act = m.group(1), m.group(2)
        try:
            hostiles[acc] = int(m.group(4))
        except (ValueError, IndexError):
            pass
        if acc not in actions:
            actions[acc] = act
    for m in ENGAGE_RE.finditer(blob):
        acc, tgt = m.group(1), m.group(2)
        targets[acc] = tgt
        actions[acc] = "ATTACK"
    for m in DEAD_RE.finditer(blob):
        dead[m.group(1)] = True
    return targets, actions, hostiles, dead


def build_stats():
    run = find_run_dir()
    rows = query_rows(
        "SELECT c.account_name, c.char_name, c.online, c.level, c.exp, "
        "c.x, c.y, c.z, c.curHp, c.maxHp, c.charId "
        "FROM characters c WHERE c.account_name LIKE 'ai\\_%' ORDER BY c.charId;"
    )
    targets, actions, hostiles, dead = parse_log(run)

    players = []
    online = 0
    for r in rows:
        if len(r) < 11:
            continue
        (acc, name, on, lvl, exp, x, y, z, hp, maxhp, _oid) = r
        is_on = str(on).strip() == "1"
        if is_on:
            online += 1
        hp_pct = "-"
        try:
            if float(maxhp) > 0:
                hp_pct = f"{round(100 * float(hp) / float(maxhp))}%"
        except (ValueError, ZeroDivisionError):
            pass
        players.append({
            "account": acc, "name": name, "online": is_on,
            "level": lvl, "exp": exp,
            "x": x, "y": y, "z": z, "hp": hp, "maxhp": maxhp, "hp_pct": hp_pct,
            "target": targets.get(acc, "-"),
            "action": "DEAD" if dead.get(acc) else actions.get(acc, "-"),
            "hostiles": hostiles.get(acc, "-"),
        })

    return {
        "ts": int(time.time()), "online": online, "total": len(players),
        "run": os.path.basename(run), "players": players,
    }


CACHE = {"stats": None, "lock": threading.Lock()}


def refresh_loop():
    while True:
        try:
            stats = build_stats()
            with CACHE["lock"]:
                CACHE["stats"] = stats
        except Exception:  # noqa: BLE001
            pass
        time.sleep(REFRESH_S)


# ---------------------------------------------------------------------------
# HTTP
# ---------------------------------------------------------------------------
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer  # noqa: E402

PAGE = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<title>L2JM AI Players Dashboard</title>
<style>
 body{font-family:Segoe UI,Roboto,sans-serif;background:#0d1117;color:#e6edf3;margin:0}
 .wrap{max-width:1200px;margin:0 auto;padding:20px}
 h1{font-size:20px;margin:0 0 4px} .sub{color:#8b949e;font-size:12px;margin-bottom:14px}
 .cards{display:flex;gap:16px;margin-bottom:14px}
 .card{background:#161b22;border:1px solid #30363d;border-radius:8px;padding:12px 18px;flex:1}
 .card .k{font-size:11px;color:#8b949e;text-transform:uppercase;letter-spacing:.5px}
 .card .v{font-size:26px;font-weight:600;margin-top:2px}
 .card .v.a{color:#f0883e}
 table{width:100%;border-collapse:collapse;background:#161b22;border-radius:8px;overflow:hidden;font-size:13px}
 th,td{padding:7px 10px;text-align:left;border-bottom:1px solid #21262d}
 th{background:#1c2128;color:#8b949e;font-size:11px;text-transform:uppercase}
 td.mono{font-family:ui-monospace,Consolas,monospace;font-size:12px}
 .on{border-radius:10px;width:9px;height:9px;display:inline-block}
 .on.y{background:#3fb950}.on.n{background:#f85149}
 .b{background:#238636}.r{background:#f85149}.w{background:#d29922}
 .acc{font-weight:600}.upd{color:#8b949e;font-size:12px;margin-top:8px}
</style></head><body><div class="wrap">
<h1>&#128421; L2JM AI Players &mdash; live</h1><div class="sub">run: <span id="run">&hellip;</span></div>
<div class="cards">
 <div class="card"><div class="k">Online</div><div class="v a" id="c-online">&hellip;</div></div>
 <div class="card"><div class="k">Total bots</div><div class="v" id="c-total">&hellip;</div></div>
 <div class="card"><div class="k">Updated</div><div class="v" id="c-ts" style="font-size:16px;padding-top:6px">&hellip;</div></div>
</div>
<table><thead><tr>
<th></th><th>Account</th><th>Char</th><th>Lvl</th><th>Exp</th><th>HP</th>
<th>X</th><th>Y</th><th>Z</th><th>Target</th><th>Action</th></tr></thead>
<tbody id="rows"></tbody></table>
<div class="upd" id="upd">connecting&hellip;</div>
<script>
const fmt=(p)=>{const e=document.createElement('tr');
e.innerHTML=`<td><span class="on ${p.online?'y':'n'}"></span></td>
<td class="acc">${p.account}</td><td>${p.name}</td>
<td>${p.level}</td><td class="mono">${p.exp}</td><td>${p.hp_pct}</td>
<td class="mono">${p.x}</td><td class="mono">${p.y}</td><td class="mono">${p.z}</td>
<td class="mono">${p.target}</td><td><span class="b">${p.action}</span></td>`;return e;};
async function tick(){try{
 const r=await fetch('/api/stats');const d=await r.json();
 document.getElementById('run').textContent=d.run;
 document.getElementById('c-online').textContent=d.online;
 document.getElementById('c-total').textContent=d.total;
 document.getElementById('c-ts').textContent=new Date(d.ts*1000).toLocaleTimeString();
 const tb=document.getElementById('rows');tb.replaceChildren();
 d.players.forEach(p=>tb.appendChild(fmt(p)));
 document.getElementById('upd').textContent='auto-refresh 2s &middot; '+d.players.length+' rows';
}catch(e){document.getElementById('upd').textContent='ERR '+e;}}
setInterval(tick,2000);tick();
</script></div></body></html>"""


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):  # noqa: N802
        if self.path == "/api/stats":
            with CACHE["lock"]:
                payload = CACHE["stats"]
            if payload is None:
                payload = {"ts": int(time.time()), "online": 0, "total": 0, "run": "?", "players": []}
            body = json.dumps(payload).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            body = PAGE.encode()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    def log_message(self, fmt, *args):  # noqa: A003
        pass


if __name__ == "__main__":
    print(f"AIWebDashboard: http://0.0.0.0:{PORT}/  refresh={REFRESH_S}s")
    threading.Thread(target=refresh_loop, daemon=True).start()
    srv = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    srv.serve_forever()
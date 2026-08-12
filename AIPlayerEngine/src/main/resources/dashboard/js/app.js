/* ============================================================================
 * WPT-31 — frontend modularization (dashboard/js/app.js)
 * ----------------------------------------------------------------------------
 * Core SPA logic for the AI Fleet Dashboard: v1 API polling, view switching,
 * grid, events, detail, theme + hotkeys. Map rendering is delegated to
 * js/map.js via window.MapRenderer (kept separate to reduce merge conflicts).
 *
 * Reads the FROZEN v1 contract: /api/v1/bots, /entities, /landmarks, /events.
 * Bundled + minified by scripts/build_dashboard.sh into index.html.
 * ==========================================================================*/
/* global MapRenderer */
'use strict';

var bots = [], ent = [], towns = [], events = [], view = 'map';
var gridSort = { key: 'account', dir: 1 }, detailIndex = 0;
var seenSeqs = new Set();
// WPT-11 — per-bot movement trails (TIM-001 evidence).
var TRAIL_N = 60;      // last-N distinct positions kept per bot (N = number of polls)
var trailsOn = true;   // hotkey T toggles trails
var trails = {};       // name -> [{x,y}, ...] oldest->newest
// WPT-12 — state playback / replay mode (reads real /api/v1/history).
var PB = {
  on: false, playing: false, loaded: false, loading: false, err: '',
  botNames: [], byBot: {}, times: [], frames: [], nTotal: 0,
  idx: 0, speed: 250, timer: null, spanStart: 0, spanEnd: 0,
  frameBots: [], trails: {}
};

function $(id) { return document.getElementById(id); }
function FMT(n) { return n == null ? '-' : Number(n).toLocaleString('en-US'); }
function pct(a, b) { return b > 0 ? Math.min(100, a / b * 100) : 0; }
function esc(s) {
  s = String(s == null ? '' : s).replace(/[<>&]/g, function (c) {
    return ({ '<': '&lt;', '>': '&gt;', '&': '&amp;' })[c];
  });
  return s.split('"').join('&quot;');
}

// ---- v1 polling (frozen contract) ----
async function poll() {
  try {
    var p, l, e, ev;
    var res = await Promise.all([
      fetch('/api/v1/bots').then(function (r) { return r.json(); }),
      fetch('/api/v1/landmarks').then(function (r) { return r.json(); }),
      fetch('/api/v1/entities').then(function (r) { return r.json(); }).catch(function () { return { entities: [] }; }),
      fetch('/api/v1/events').then(function (r) { return r.json(); }).catch(function () { return { events: [] }; })
    ]);
    p = res[0]; l = res[1]; e = res[2]; ev = res[3];
    bots = p.bots || [];
    towns = l.towns || [];
    ent = e.entities || [];
    mergeEvents(ev.events || []);
    updateTrails();
    $('meta').textContent = bots.length + ' bots · ' + events.length + ' evts · ' +
      new Date().toLocaleTimeString() + ' · up ' +
      Math.round((Date.now() - (window._t0 || Date.now())) / 1000) + 's · poll OK';
    render();
  } catch (err) {
    $('meta').textContent = 'poll error — fleet may be restarting (' + err + ')';
  }
}
function mergeEvents(list) {
  list.forEach(function (e) {
    if (e && e.seq != null && !seenSeqs.has(e.seq)) { seenSeqs.add(e.seq); events.push(e); }
  });
  events.sort(function (a, b) { return (b.seq || 0) - (a.seq || 0); });
  if (events.length > 200) events = events.slice(0, 200);
}
// WPT-11 — append each bot's current /api/v1 position to its trail (last-N distinct
// points, so static bots grow no polyline = movement is visible). Prune vanished bots.
function updateTrails() {
  var seen = {}, now = Date.now();
  bots.forEach(function (b) {
    if (b.x == null) return;
    var name = b.account || b.name;
    seen[name] = true;
    var arr = trails[name];
    if (!arr) arr = trails[name] = [];
    var last = arr[arr.length - 1];
    if (!last || last.x !== b.x || last.y !== b.y) arr.push({ x: b.x, y: b.y, t: now });
    if (arr.length > TRAIL_N) arr.splice(0, arr.length - TRAIL_N);
  });
  Object.keys(trails).forEach(function (name) { if (!seen[name]) delete trails[name]; });
}
function toggleTrails() { trailsOn = !trailsOn; render(); }
function render() {
  if (PB.on) {
    // WPT-12 — playback overrides the live map/events with the current recorded frame.
    if (view === 'map') { if (window.MapRenderer) window.MapRenderer.render(PB.frameBots, ent, towns, PB.trails, true); }
    else if (view === 'events') renderPlaybackEvents();
    else if (view === 'grid') renderGrid();
    else renderDetail();
    pbRenderUI();
    return;
  }
  if (view === 'map') { if (window.MapRenderer) window.MapRenderer.render(bots, ent, towns, trails, trailsOn); }
  else if (view === 'grid') renderGrid();
  else if (view === 'events') renderEvents();
  else renderDetail();
}

function setView(v) {
  view = v;
  $('btnMap').classList.toggle('on', v === 'map');
  $('btnGrid').classList.toggle('on', v === 'grid');
  $('btnEvents').classList.toggle('on', v === 'events');
  var panels = { map: 'mapPanel', grid: 'gridPanel', events: 'eventsPanel', detail: 'detailPanel' };
  Object.keys(panels).forEach(function (k) {
    $(panels[k]).style.display = (k === v) ? '' : 'none';
  });
  render();
}

// ---- WPT-13 Grid v2 ----
function itemList(it) {
  if (!Array.isArray(it)) return [];
  return it.filter(function (i) { return Array.isArray(i); })
    .map(function (i) { return { id: i[0], count: i[1] }; });
}
function itemMarkup(b) {
  var it = itemList(b.items);
  return it.length ? it.map(function (i) { return '<span class="th">' + i.id + '</span>×' + i.count; }).join(' ') : '-';
}
var COLS = [
  { k: 'account', t: 'Account', num: false, v: function (b) { return b.account || ''; }, h: function (b) { return esc(b.account || '-'); } },
  { k: 'name', t: 'Char', num: false, v: function (b) { return b.name || ''; }, h: function (b) { return '<b>' + esc(b.name || '-') + '</b>'; } },
  { k: 'level', t: 'Lvl', num: true, v: function (b) { return b.level || 0; }, h: function (b) { return b.level; } },
  { k: 'class', t: 'Class', num: false, v: function (b) { return b.charClass || b.cls || ''; }, h: function (b) { return esc(b.charClass || b.cls || '-'); } },
  { k: 'exp', t: 'EXP', num: true, v: function (b) { return b.exp == null ? -1 : b.exp; }, h: function (b) { return FMT(b.exp); } },
  { k: 'hp', t: 'HP', num: true, v: function (b) { return b.hp || 0; }, h: function (b) { var p = pct(b.hp, b.hpMax); return b.hp + '/' + b.hpMax + '<div class="bar"><div style="width:' + p + '%;background:var(--gr)"></div></div>'; } },
  { k: 'mp', t: 'MP', num: true, v: function (b) { return b.mp || 0; }, h: function (b) { var p = pct(b.mp, b.mpMax); return b.mp + '/' + b.mpMax + '<div class="bar"><div style="width:' + p + '%;background:var(--cy)"></div></div>'; } },
  { k: 'cp', t: 'CP', num: true, v: function (b) { return b.cp || 0; }, h: function (b) { return b.cp + '/' + b.cpMax; } },
  { k: 'pos', t: 'Pos (x,y,z)', num: false, v: function (b) { return b.x + ',' + b.y + ',' + b.z; }, h: function (b) { return b.x + ', ' + b.y + ', ' + b.z; } },
  { k: 'moved', t: 'Δ1m', num: true, v: function (b) { return b.movedLast60 != null ? b.movedLast60 : -1; }, h: function (b) { return b.movedLast60 != null ? b.movedLast60 + 'u' : '-'; } },
  { k: 'moves', t: 'Moves', num: true, v: function (b) { return b.movesSent != null ? b.movesSent : -1; }, h: function (b) { return b.movesSent != null ? b.movesSent : '-'; } },
  { k: 'load', t: 'Load', num: true, v: function (b) { return b.load || 0; }, h: function (b) { return b.load + '/' + b.maxLoad; } },
  { k: 'weapon', t: 'Weapon', num: true, v: function (b) { return b.weapon ? 1 : 0; }, h: function (b) { return b.weapon ? '🗡️' : '🦾'; } },
  { k: 'adena', t: 'Adena', num: true, v: function (b) { return b.adena || 0; }, h: function (b) { return FMT(b.adena); } },
  { k: 'sp', t: 'SP', num: true, v: function (b) { return b.sp || 0; }, h: function (b) { return FMT(b.sp); } },
  { k: 'mobs', t: 'Mobs', num: true, v: function (b) { return b.mobs || 0; }, h: function (b) { return b.mobs; } },
  { k: 'npcs', t: 'NPCs', num: true, v: function (b) { return b.npcs || 0; }, h: function (b) { return b.npcs; } },
  { k: 'inv', t: 'Inv', num: true, v: function (b) { return b.invPct || 0; }, h: function (b) { return b.invPct + '% (' + b.itemCount + ')'; } },
  { k: 'items', t: 'Items', num: false, v: function (b) { return itemList(b.items).length; }, h: function (b) { return itemMarkup(b); } },
  { k: 'target', t: 'Target', num: false, v: function (b) { return b.target ? b.target.label : ''; }, h: function (b) { return b.target ? esc(b.target.label) : '-'; } },
  { k: 'tgtd', t: 'Tgt dist', num: true, v: function (b) { return b.target && b.target.d ? b.target.d : -1; }, h: function (b) { return b.target && b.target.d ? Math.round(b.target.d) : '-'; } },
  { k: 'thought', t: 'Thought', num: false, v: function (b) { return b.thought || b.action || ''; }, h: function (b) { return '<span class="th">' + esc(b.thought || b.action || '-') + '</span>'; } },
  { k: 'state', t: 'State', num: false, v: function (b) { return b.state || ''; }, h: function (b) { return '<span class="st st-' + esc(b.state) + '">' + esc(b.state) + '</span>'; } },
  { k: 'online', t: 'Online', num: true, v: function (b) { return b.online ? 1 : 0; }, h: function (b) { return '<span class="' + (b.online ? 'online' : 'offline') + '">' + (b.online ? 'ONLINE' : 'OFF') + '</span>'; } },
  { k: 'up', t: 'Up', num: true, v: function (b) { return b.uptimeSec || 0; }, h: function (b) { return b.uptimeSec ? '~' + Math.floor(b.uptimeSec / 60) + 'm' : '-'; } },
  { k: 'age', t: 'PktAge', num: true, v: function (b) { return b.pktAgeMs != null ? b.pktAgeMs : -1; }, h: function (b) { return b.pktAgeMs != null ? (b.pktAgeMs / 1000).toFixed(1) + 's' : '-'; } }
];


function filteredBots() {
  var fo = $('fOnline').value, fs = $('fState').value;
  return bots.filter(function (b) {
    if (fo === '1' && !b.online) return false;
    if (fo === '0' && b.online) return false;
    if (fs && (b.state || '') !== fs) return false;
    return true;
  });
}
function cmpRows(a, b) {
  var c = COLS.filter(function (x) { return x.k === gridSort.key; })[0] || COLS[0];
  var av = c.v(a), bv = c.v(b), r;
  if (c.num) { av = av || 0; bv = bv || 0; r = av - bv; }
  else { av = String(av); bv = String(bv); r = av.localeCompare(bv); }
  return r * gridSort.dir;
}
function buildStateFilter() {
  var sel = $('fState'), cur = sel.value;
  var states = Array.from(new Set(bots.map(function (b) { return b.state; }).filter(Boolean))).sort();
  sel.innerHTML = '<option value="">State: all</option>' +
    states.map(function (s) { return '<option value="' + esc(s) + '">' + esc(s) + '</option>'; }).join('');
  sel.value = cur;
}
function sortGrid(k) {
  if (gridSort.key === k) gridSort.dir *= -1;
  else { gridSort.key = k; gridSort.dir = 1; }
  renderGrid();
}
function renderGrid() {
  buildStateFilter();
  var rows = filteredBots().slice().sort(cmpRows);
  var h = '<table><thead><tr>';
  COLS.forEach(function (c) {
    var act = c.k === gridSort.key;
    var arr = act ? (gridSort.dir === 1 ? '▲' : '▼') : '';
    h += '<th class="sortable" onclick="sortGrid(\'' + c.k + '\')" title="sort by ' + c.t + '">' + c.t + '<span class="arr">' + arr + '</span></th>';
  });
  h += '</tr></thead><tbody>';
  rows.forEach(function (b) {
    h += '<tr>' + COLS.map(function (c) { return '<td>' + c.h(b) + '</td>'; }).join('') + '</tr>';
  });
  h += '</tbody></table>';
  $('gridTbl').innerHTML = h;
  $('gridCount').textContent = rows.length + ' / ' + bots.length + ' bots';
}
function csvCell(v) {
  var s2 = String(v == null ? '' : v);
  return (s2.indexOf(',') >= 0 || s2.indexOf('"') >= 0 || s2.indexOf('\n') >= 0)
    ? '"' + s2.split('"').join('""') + '"' : s2;
}
function exportCsv() {
  var rows = filteredBots().slice().sort(cmpRows);
  var lines = [COLS.map(function (c) { return csvCell(c.t); }).join(',')];
  rows.forEach(function (b) { lines.push(COLS.map(function (c) { return csvCell(c.v(b)); }).join(',')); });
  var blob = new Blob(['\ufeff' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' });
  var a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'fleet_grid_' + new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-') + '.csv';
  document.body.appendChild(a); a.click(); a.remove();
  URL.revokeObjectURL(a.href);
}


// ---- WPT-14 live event feed ----
var EVCLASS = { kill: 'ev-kill', 'level-up': 'ev-level', 'skill-cast': 'ev-skill', damage: 'ev-dmg', chat: 'ev-chat', sysmsg: 'ev-sys', connect: 'ev-conn', disconnect: 'ev-disc', move: 'ev-move' };
var EVLABEL = { kill: 'KILL', 'level-up': 'LEVEL', 'skill-cast': 'SKILL', damage: 'DMG', chat: 'CHAT', sysmsg: 'SYS', connect: 'CONN', disconnect: 'DISC', move: 'MOVE' };
function fmtEventData(d) {
  if (!d || typeof d !== 'object') return '';
  var parts = [];
  for (var k in d) {
    var val = d[k];
    if (Array.isArray(val)) val = val.join('x');
    else if (val && typeof val === 'object') val = JSON.stringify(val);
    parts.push(k + ': ' + val);
  }
  return parts.join(' · ');
}
function renderEvents(list) {
  if (!list) {
    list = events;
    if ($('eventsNote')) $('eventsNote').textContent = 'Live event feed — newest first · capped at 200';
  }
  var h = '';
  if (!list.length) {
    h = '<div class="ev ev-other"><span class="tag">···</span><span class="body">No events yet — the feed fills when the fleet runs.</span></div>';
  }
  list.forEach(function (ev) {
    var type = ev.type || 'other';
    var cls = EVCLASS[type] || 'ev-other';
    var lab = EVLABEL[type] || String(type).toUpperCase();
    var t = new Date(ev.t).toLocaleTimeString();
    var body = fmtEventData(ev.data) || '-';
    h += '<div class="ev ' + cls + '"><span class="t">' + t + '</span><span class="tag">' + lab + '</span><span class="bot">' + esc(ev.bot || '-') + '</span><span class="body">' + esc(body) + '</span></div>';
  });
  $('eventsTbl').innerHTML = h;
}
function clearEvents() { events = []; seenSeqs.clear(); renderEvents(); }

// ---- WPT-20 detail view ----
function cycleDetail(d) { detailIndex = (detailIndex + d + bots.length) % bots.length; renderDetail(); }
function renderDetail() {
  if (!bots.length) { $('detailBody').innerHTML = '<div class="meta">No bots to inspect.</div>'; return; }
  var b = bots[detailIndex % bots.length];
  var hpP = pct(b.hp, b.hpMax), mpP = pct(b.mp, b.mpMax);
  var it = itemList(b.items).map(function (i) { return '<span class="th">' + i.id + '</span>×' + i.count; }).join(' ') || '-';
  function card(k, v) { return '<div class="dcard"><div class="k">' + k + '</div><div class="v">' + v + '</div></div>'; }
  var h = '<h2>' + esc(b.name || b.account) + ' <span class="meta">' + esc(b.account || '') + ' · L' + b.level + '</span></h2>';
  h += '<div class="dstats">';
  h += card('State', '<span class="st st-' + esc(b.state) + '">' + esc(b.state) + '</span> ' + (b.online ? '<span class="online">ONLINE</span>' : '<span class="offline">OFF</span>'));
  h += card('Position', b.x + ', ' + b.y + ', ' + b.z);
  h += card('HP', b.hp + '/' + b.hpMax + '<div class="bar"><div style="width:' + hpP + '%;background:var(--gr)"></div></div>');
  h += card('MP', b.mp + '/' + b.mpMax + '<div class="bar"><div style="width:' + mpP + '%;background:var(--cy)"></div></div>');
  h += card('CP', b.cp + '/' + b.cpMax);
  h += card('EXP', FMT(b.exp)); h += card('SP', FMT(b.sp));
  h += card('Load', b.load + '/' + b.maxLoad); h += card('Weapon', b.weapon ? '🗡️' : '🦾'); h += card('Adena', FMT(b.adena));
  h += card('Mobs / NPCs', b.mobs + ' / ' + b.npcs); h += card('Inventory', b.invPct + '% · ' + b.itemCount + ' items'); h += card('Items', it);
  h += card('Target', b.target ? esc(b.target.label) + ' (' + Math.round(b.target.d) + 'u)' : '-');
  h += card('Uptime', b.uptimeSec ? '~' + Math.floor(b.uptimeSec / 60) + 'm' : '-'); h += card('PktAge', b.pktAgeMs != null ? (b.pktAgeMs / 1000).toFixed(1) + 's' : '-');
  h += card('Thought', '<span class="th">' + esc(b.thought || b.action || '-') + '</span>');
  h += '</div>';
  h += '<div class="dnav"><button onclick="cycleDetail(-1)">◀ prev</button><button onclick="cycleDetail(1)">next ▶</button></div>';
  $('detailBody').innerHTML = h;
}

// ---- WPT-12 state playback / replay mode --------------------------------
// Reads REAL /api/v1/history (HistoryRing snapshots: {t,bot,x,y,z,level,exp,hp,hpMax}),
// merges every queried bot's trail into a global time-ordered frame timeline, and
// replays it (play/pause/scrub/step/speed) onto the map + event feed. No fake data.
function fmtPBT(ms) {
  var d = new Date(ms);
  function p(n) { return (n < 10 ? '0' : '') + n; }
  return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
}
function pbBuildFrames() {
  var timeSet = new Set(), bots = Object.keys(PB.byBot);
  bots.forEach(function (b) { PB.byBot[b].forEach(function (s) { timeSet.add(s.t); }); });
  PB.times = Array.from(timeSet).sort(function (a, b) { return a - b; });
  var ptr = {}, cur = {};
  PB.frames = [];
  PB.times.forEach(function (t) {
    bots.forEach(function (b) {
      var arr = PB.byBot[b], j = ptr[b] || 0;
      while (j < arr.length && arr[j].t <= t) { cur[b] = arr[j]; j++; }
      ptr[b] = j;
    });
    var fb = [];
    bots.forEach(function (b) { if (cur[b]) fb.push(cur[b]); });
    PB.frames.push({ t: t, bots: fb });
  });
  PB.spanStart = PB.times.length ? PB.times[0] : 0;
  PB.spanEnd = PB.times.length ? PB.times[PB.times.length - 1] : 0;
  PB.nTotal = bots.reduce(function (a, b) { return a + PB.byBot[b].length; }, 0);
}
async function pbLoad() {
  if (PB.loading) return;
  PB.loading = true; PB.loaded = false; PB.err = '';
  pbRenderUI();
  try {
    // Seed bot names from the live fleet; expand with any discovered in history.
    var names = [];
    try {
      var live = await fetch('/api/v1/bots').then(function (r) { return r.json(); });
      (live.bots || []).forEach(function (b) { var n = b.account || b.name; if (n && names.indexOf(n) < 0) names.push(n); });
    } catch (e) { /* live list unavailable — rely on discovery */ }
    var byBot = {}, toFetch = names.slice(), guard = 0;
    while (toFetch.length && guard < 24) {
      guard++;
      var batch = toFetch; toFetch = [];
      await Promise.all(batch.map(function (name) {
        return fetch('/api/v1/history?bot=' + encodeURIComponent(name))
          .then(function (r) { return r.json(); })
          .then(function (j) {
            var h = (j && j.history) || [];
            var arr = byBot[name] || (byBot[name] = []);
            for (var i = 0; i < h.length; i++) if (h[i] && h[i].x != null && h[i].t != null) arr.push(h[i]);
          }).catch(function () { /* skip bot with no/errored history */ });
      }));
      Object.keys(byBot).forEach(function (b) {
        byBot[b].forEach(function (s) {
          if (s.bot && s.bot !== b && !byBot[s.bot]) toFetch.push(s.bot);
        });
      });
    }
    Object.keys(byBot).forEach(function (b) {
      byBot[b].sort(function (a, c) { return a.t - c.t; });
    });
    PB.byBot = byBot; PB.botNames = Object.keys(byBot);
    pbBuildFrames();
    PB.loaded = true; PB.idx = 0; PB.playing = false;
  } catch (err) {
    PB.err = String(err);
  }
  PB.loading = false;
  pbRefresh();
}

function pbClamp(i) { return PB.frames.length ? Math.max(0, Math.min(PB.frames.length - 1, i)) : 0; }
function pbFrameBots() {
  var f = PB.frames[PB.idx]; if (!f) return [];
  return f.bots.map(function (s) {
    return { name: s.bot, account: s.bot, level: s.level, hp: s.hp, hpMax: s.hpMax,
      x: s.x, y: s.y, z: s.z, state: 'history', thought: fmtPBT(s.t) };
  });
}
function pbTrails() {
  var tr = {};
  for (var i = 0; i <= PB.idx; i++) {
    var f = PB.frames[i]; if (!f) continue;
    f.bots.forEach(function (s) {
      var a = tr[s.bot]; if (!a) a = tr[s.bot] = [];
      var last = a[a.length - 1];
      if (!last || last.x !== s.x || last.y !== s.y) a.push({ x: s.x, y: s.y });
    });
  }
  return tr;
}
function pbSeek(idx) {
  PB.idx = pbClamp(idx);
  PB.frameBots = pbFrameBots();
  PB.trails = pbTrails();
  render();
}
function pbRefresh() { pbSeek(PB.idx); pbRenderUI(); }
function pbEnter() {
  PB.on = true;
  if ($('btnPlayback')) $('btnPlayback').classList.add('on');
  if ($('playbar')) $('playbar').style.display = 'flex';
  setView('map');
  if (!PB.loaded && !PB.loading) pbLoad();
  pbRefresh();
}
function pbExit() {
  PB.on = false; PB.playing = false;
  if (PB.timer) { clearInterval(PB.timer); PB.timer = null; }
  if ($('btnPlayback')) $('btnPlayback').classList.remove('on');
  if ($('playbar')) $('playbar').style.display = 'none';
  render();
}
function pbToggle() { if (PB.on) pbExit(); else pbEnter(); }
function pbTogglePlay() {
  if (!PB.loaded || !PB.frames.length) return;
  PB.playing = !PB.playing;
  if (PB.playing) {
    if (PB.idx >= PB.frames.length - 1) PB.idx = 0;
    PB.timer = setInterval(pbTick, Math.max(50, PB.speed));
  } else if (PB.timer) { clearInterval(PB.timer); PB.timer = null; }
  pbRefresh();
}
function pbTick() {
  if (PB.idx < PB.frames.length - 1) { pbSeek(PB.idx + 1); }
  else { PB.playing = false; if (PB.timer) { clearInterval(PB.timer); PB.timer = null; } pbRenderUI(); }
}
function pbStep(d) {
  if (!PB.frames.length) return;
  if (PB.playing) { PB.playing = false; if (PB.timer) { clearInterval(PB.timer); PB.timer = null; } }
  pbSeek(PB.idx + (d || 0));
}
function pbScrub(v) { if (PB.frames.length) pbSeek(parseInt(v, 10) || 0); }
function pbSetSpeed(ms) {
  PB.speed = parseInt(ms, 10) || 250;
  if (PB.playing) { if (PB.timer) clearInterval(PB.timer); PB.timer = setInterval(pbTick, Math.max(50, PB.speed)); }
}
function renderPlaybackEvents() {
  var f = PB.frames[PB.idx];
  var lo = 0, hi = Number.MAX_VALUE;
  if (f) { lo = PB.idx > 0 ? PB.frames[PB.idx - 1].t + 1 : 0; hi = f.t; }
  var list = events.filter(function (ev) { return ev.t >= lo && ev.t <= hi; });
  var note = 'PLAYBACK: events in frame window ';
  note += (PB.loaded && PB.frames.length) ? (fmtPBT(lo === 0 ? PB.spanStart : lo) + ' → ' + fmtPBT(hi)) : '—';
  if ($('eventsNote')) $('eventsNote').textContent = note;
  renderEvents(list);
}
function pbRenderUI() {
  if (!$('playbar')) return;
  var f = PB.frames[PB.idx];
  $('pbPlay').textContent = PB.playing ? '⏸' : '▶';
  var sl = $('pbSlider');
  if (sl) { sl.max = PB.frames.length ? PB.frames.length - 1 : 0; sl.value = PB.idx; sl.disabled = !PB.frames.length; }
  var msg;
  if (PB.loading) msg = 'loading history…';
  else if (!PB.loaded) msg = PB.err ? 'history error: ' + PB.err : 'not loaded';
  else if (!PB.frames.length) msg = 'no recorded snapshots — /api/v1/history is empty';
  else msg = 'frame ' + (PB.idx + 1) + '/' + PB.frames.length + ' · ' + PB.nTotal + ' snapshots · ' + PB.botNames.length + ' bots · ' + fmtPBT(PB.spanStart) + '→' + fmtPBT(PB.spanEnd);
  $('pbStatus').textContent = msg;
  $('pbTime').textContent = f ? fmtPBT(f.t) + ' +' + Math.round((f.t - PB.spanStart) / 1000) + 's' : '-';
}

// ---- WPT-20 theme + hotkeys ----
function applyTheme(t) { document.body.setAttribute('data-theme', t); localStorage.setItem('fleetTheme', t); }
function initTheme() { applyTheme(localStorage.getItem('fleetTheme') || 'dark'); }
function toggleTheme() { applyTheme(document.body.getAttribute('data-theme') === 'light' ? 'dark' : 'light'); }
document.addEventListener('keydown', function (e) {
  var tag = (e.target && e.target.tagName) || '';
  if (tag === 'INPUT' || tag === 'SELECT' || tag === 'TEXTAREA') return;
  var k = e.key.toLowerCase();
  if (k === 'm') setView('map');
  else if (k === 'g') setView('grid');
  else if (k === 'e') setView('events');
  else if (k === 'd') { detailIndex = 0; setView('detail'); }
  else if (k === 't') toggleTrails();   // WPT-11: show/hide movement trails
  else if (k === 'p') pbToggle();       // WPT-12: toggle state playback mode
});

initTheme();
setInterval(poll, 2000);
window._t0 = Date.now();
poll();


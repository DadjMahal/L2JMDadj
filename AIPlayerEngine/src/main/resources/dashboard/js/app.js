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
function render() {
  if (view === 'map') { if (window.MapRenderer) window.MapRenderer.render(bots, ent, towns); }
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
function renderEvents() {
  var h = '';
  if (!events.length) {
    h = '<div class="ev ev-other"><span class="tag">···</span><span class="body">No events yet — the feed fills when the fleet runs.</span></div>';
  }
  events.forEach(function (ev) {
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
});

initTheme();
setInterval(poll, 2000);
window._t0 = Date.now();
poll();


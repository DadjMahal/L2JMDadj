/* ============================================================================
 * WPT-10 — Map renderer v2 (dashboard/js/map.js)
 * ----------------------------------------------------------------------------
 * Real-coordinate pan/zoom world map for the AI Fleet dashboard.
 *
 *  * Loads region polygons + landmark points from data/regions.json and
 *    data/landmarks.json (real L2 world coords, meters).
 *  * Draws a schematic terrain/base layer (ocean + land base + km grid),
 *    the town region polygons + labels, landmark pins, then live bot /
 *    entity / target-line overlays supplied by the app (frozen v1 API).
 *  * Supports DRAG to pan, WHEEL / PINCH to zoom, and programmatic
 *    zoomIn / zoomOut / fit controls with zoom-at-cursor anchoring.
 *
 * Data injection: when bundled by scripts/build_dashboard.sh the build
 * inlines the two JSON files as window.__MAP_DATA__ = { regions, landmarks }.
 * When the page is opened directly (dev src shell / index.src.html) and
 * __MAP_DATA__ is absent, the module falls back to fetch('data/...json').
 *
 * Exposes window.MapRenderer = { init, render, zoomIn, zoomOut, fit, zoomAt }.
 * This file is self-contained (defines its own esc/colour helpers) so it can
 * be concatenated and minified independently of app.js.
 * ==========================================================================*/
(function () {
  'use strict';

  function $e(id) { return document.getElementById(id); }
  function esc(s) {
    s = String(s == null ? '' : s).replace(/[<>&]/g,
      function (c) { return ({ '<': '&lt;', '>': '&gt;', '&': '&amp;' })[c]; });
    return s.split('"').join('&quot;');
  }
  function stateColor(s) {
    s = String(s || '').toUpperCase();
    if (['ATTACK', 'ENGAGE_TARGET', 'USE_SKILL', 'ENGAGE'].indexOf(s) >= 0) return 'var(--red)';
    if (['FLEE', 'RETREAT'].indexOf(s) >= 0) return 'var(--or)';
    if (s === 'WANDER') return 'var(--cy)';
    if (s === 'DEAD') return 'var(--dim)';
    return 'var(--lm)';
  }

  // ---- world/projection state -------------------------------------------
  var svg = null, wrap = null;
  var regions = [], landmarks = [];
  var minX = 0, maxX = 0, minY = 0, maxY = 0;   // world bounds (meters)
  var s = 1, tx = 0, ty = 0;                    // scale (px/m) + pan (px)
  var baseBuilt = false;
  var pointers = new Map();                     // pointerId -> {x,y}
  var pinchDist = 0;
  var lastDyn = null;                          // WPT-11: last dynamic overlay, kept across pan/zoom re-renders

  function dataFromInline() {
    if (window.__MAP_DATA__ && window.__MAP_DATA__.regions && window.__MAP_DATA__.landmarks) {
      return Promise.resolve(window.__MAP_DATA__);
    }
    return Promise.all([
      fetch('data/regions.json').then(function (r) { return r.json(); }),
      fetch('data/landmarks.json').then(function (r) { return r.json(); })
    ]).then(function (arr) {
      return { regions: arr[0].regions || [], landmarks: arr[1].landmarks || [] };
    });
  }

  function computeBounds() {
    var xs = [], ys = [];
    regions.forEach(function (r) {
      (r.polygon || []).forEach(function (p) { xs.push(p[0]); ys.push(p[1]); });
      if (r.center) { xs.push(r.center.x); ys.push(r.center.y); }
    });
    landmarks.forEach(function (l) { xs.push(l.x); ys.push(l.y); });
    if (xs.length === 0) { xs = [-100000, 160000]; ys = [-180000, 260000]; }
    minX = Math.min.apply(null, xs); maxX = Math.max.apply(null, xs);
    minY = Math.min.apply(null, ys); maxY = Math.max.apply(null, ys);
    // pad ~6%
    var dx = (maxX - minX) * 0.06, dy = (maxY - minY) * 0.06;
    minX -= dx; maxX += dx; minY -= dy; maxY += dy;
  }

  function viewW() { return wrap ? wrap.clientWidth : 800; }
  function viewH() { return wrap ? wrap.clientHeight : 600; }
  function nx(wx) { return wx - minX; }
  function ny(wy) { return maxY - wy; }

  function fit() {
    var w = viewW(), h = viewH();
    var bw = (maxX - minX) || 1, bh = (maxY - minY) || 1;
    s = Math.max(0.00001, Math.min(w / bw, h / bh));
    tx = (w - bw * s) / 2;
    ty = (h - bh * s) / 2;
  }

  // Anchor zoom: keep the world point under screen cursor (cx,cy) fixed.
  function zoomAt(cx, cy, factor) {
    var ns = s * factor;
    if (ns < 0.00002 || ns > 2000) return;
    var wxs = (cx - tx) / s, wys = (cy - ty) / s;
    tx = cx - wxs * ns;
    ty = cy - wys * ns;
    s = ns;
    render();
  }

  function zoomBy(factor) { zoomAt(viewW() / 2, viewH() / 2, factor); }

  // ---- static base / terrain layer ---------------------------------------
  function svgEl(tag, attrs, parent) {
    var el = document.createElementNS('http://www.w3.org/2000/svg', tag);
    for (var k in attrs) el.setAttribute(k, attrs[k]);
    if (parent) parent.appendChild(el);
    return el;
  }

  function buildDefs() {
    var defs = svgEl('defs', {}, svg);
    var grad = svgEl('linearGradient', { id: 'terrainGrad', x1: 0, y1: 0, x2: 1, y2: 1 }, defs);
    grad.appendChild(svgEl('stop', { offset: '0%', 'stop-color': '#1c3a2a' }));
    grad.appendChild(svgEl('stop', { offset: '55%', 'stop-color': '#27493a' }));
    grad.appendChild(svgEl('stop', { offset: '100%', 'stop-color': '#3a4f36' }));
    var mk = svgEl('marker', { id: 'arr', markerWidth: '8', markerHeight: '8', refX: '6', refY: '3', orient: 'auto' }, defs);
    mk.appendChild(svgEl('path', { d: 'M0,0 L6,3 L0,6 Z', fill: '#ff5d5d' }));
  }

  function buildBase() {
    if (!svg) return;
    while (svg.firstChild) svg.removeChild(svg.firstChild);
    buildDefs();

    var g = svgEl('g', { id: 'baseLayer', transform: 'translate(' + tx + ',' + ty + ') scale(' + s + ')' }, svg);
    var bw = (maxX - minX), bh = (maxY - minY);
    var M = Math.max(bw, bh) * 4;   // margin so ocean always fills viewport

    // Ocean base (fills well beyond the land).
    svgEl('rect', {
      x: -M, y: -M, width: bw + M * 2, height: bh + M * 2,
      fill: '#0a1e33', stroke: 'none'
    }, g);

    // Land base (the playable world) — schematic terrain shape with gradient.
    svgEl('rect', {
      x: 0, y: 0, width: bw, height: bh,
      rx: Math.min(bw, bh) * 0.02, ry: Math.min(bw, bh) * 0.02,
      fill: 'url(#terrainGrad)', stroke: 'var(--gridline)', 'stroke-width': 2 / s
    }, g);

    // Km grid every 30_000 m.
    var STEP = 30000;
    var gx;
    for (gx = Math.ceil(minX / STEP) * STEP; gx <= maxX; gx += STEP) {
      svgEl('line', { x1: nx(gx), y1: 0, x2: nx(gx), y2: bh, stroke: 'var(--gridline)', 'stroke-width': 1 / s }, g);
    }
    for (gx = Math.ceil(minY / STEP) * STEP; gx <= maxY; gx += STEP) {
      svgEl('line', { x1: 0, y1: ny(gx), x2: bw, y2: ny(gx), stroke: 'var(--gridline)', 'stroke-width': 1 / s }, g);
    }

    // Region polygons (real town zones) + labels.
    regions.forEach(function (r) {
      var poly = r.polygon || [];
      if (poly.length < 3) return;
      var pts = poly.map(function (p) { return nx(p[0]) + ',' + ny(p[1]); }).join(' ');
      var p = svgEl('polygon', {
        points: pts,
        fill: 'var(--pu)', 'fill-opacity': '0.14',
        stroke: 'var(--pu)', 'stroke-opacity': '0.6', 'stroke-width': 1.5 / s
      }, g);
      p.appendChild(svgEl('title', {}, p)).textContent = r.name + ' (' + poly.length + '-pt polygon)';
      var cx = r.center ? r.center.x : centroidX(poly);
      var cy = r.center ? r.center.y : centroidY(poly);
      svgEl('text', {
        x: nx(cx), y: ny(cy), 'text-anchor': 'middle', class: 'mlabel',
        'font-size': 11 / s, fill: 'var(--txthi)'
      }, g).textContent = r.name;
    });

    // Landmark pins.
    landmarks.forEach(function (l) {
      var cx = nx(l.x), cy = ny(l.y);
      var pin = svgEl('circle', { cx: cx, cy: cy, r: 9 / s, fill: 'var(--accent)', opacity: '0.9' }, g);
      pin.appendChild(svgEl('title', {}, pin)).textContent = l.name + ' (' + l.x + ', ' + l.y + ', ' + (l.z || 0) + ')';
      svgEl('text', {
        x: cx, y: cy + 26 / s, 'text-anchor': 'middle', class: 'mlabel',
        'font-size': 12 / s, fill: 'var(--accent)'
      }, g).textContent = l.name;
    });
  }

  function centroidX(poly) { return poly.reduce(function (a, p) { return a + p[0]; }, 0) / poly.length; }
  function centroidY(poly) { return poly.reduce(function (a, p) { return a + p[1]; }, 0) / poly.length; }


  // ---- dynamic overlay (bots / entities / targets / trails) ---------------
  // WPT-11 — draw each bot's last-N movement trail as a faded polyline.
  function drawTrails(g, trails, on) {
    if (!on || !trails) return;
    Object.keys(trails).forEach(function (name) {
      var pts = trails[name];
      if (!pts || pts.length < 2) return;
      var d = '';
      for (var i = 0; i < pts.length; i++) {
        var X = nx(pts[i].x), Y = ny(pts[i].y);
        d += (i === 0 ? 'M' : 'L') + X + ',' + Y + ' ';
      }
      svgEl('path', {
        d: d, fill: 'none', stroke: 'var(--accent)',
        'stroke-width': 2 / s, 'stroke-linecap': 'round', 'stroke-linejoin': 'round',
        opacity: '0.55', class: 'trail'
      }, g);
    });
  }

  function render(bots, ent, towns, trails, onTrails) {
    if (!svg) return;
    if (trails !== undefined) {
      // Fresh data from the app: remember it so pan/zoom re-renders (no args) keep trails + bots.
      lastDyn = { bots: bots || [], ent: ent || [], towns: towns || [], trails: trails, onTrails: !!onTrails };
    } else if (lastDyn) {
      bots = lastDyn.bots; ent = lastDyn.ent; towns = lastDyn.towns;
      trails = lastDyn.trails; onTrails = lastDyn.onTrails;
    }
    bots = bots || []; ent = ent || []; towns = towns || [];
    var old = $e('overlay');
    if (old) svg.removeChild(old);
    var g = svgEl('g', {
      id: 'overlay', transform: 'translate(' + tx + ',' + ty + ') scale(' + s + ')'
    }, svg);

    // Live towns from the v1 landmark feed (same coords as data/landmarks.json).
    towns.forEach(function (t) {
      if (t.x == null) return;
      svgEl('circle', { cx: nx(t.x), cy: ny(t.y), r: 9 / s, fill: 'var(--pu)', opacity: '0.85' }, g);
    });

    // WPT-11 — movement trails below entity/bot dots so they read as paths.
    drawTrails(g, trails, onTrails);

    // Entities (hostiles red, players cyan, npcs green).
    ent.forEach(function (e) {
      if (e.x == null) return;
      var col = e.kind === 1 ? 'var(--red)' : (e.kind === 2 ? 'var(--cy)' : 'var(--gr)');
      svgEl('circle', { cx: nx(e.x), cy: ny(e.y), r: (e.kind === 2 ? 5 : 3) / s, fill: col, opacity: '0.8' }, g);
    });

    // Target lines.
    bots.forEach(function (b) {
      if (b.x == null || !b.target || b.target.x == null) return;
      var x1 = nx(b.x), y1 = ny(b.y), x2 = nx(b.target.x), y2 = ny(b.target.y);
      var len = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)) || 1;
      svgEl('line', {
        x1: x1, y1: y1, x2: x2 - (x2 - x1) / len * 6 / s, y2: y2 - (y2 - y1) / len * 6 / s,
        stroke: 'var(--or)', 'stroke-width': 1.5 / s, 'marker-end': 'url(#arr)', opacity: '0.8'
      }, g);
    });

    // Bots.
    bots.forEach(function (b) {
      if (b.x == null) return;
      var col = stateColor(b.state), cx = nx(b.x), cy = ny(b.y);
      var tip = esc(b.name) + ' (' + esc(b.account || '') + ')\nL' + b.level + ' · ' +
        (b.exp != null ? Number(b.exp).toLocaleString('en-US') : '-') +
        'xp · HP ' + b.hp + '/' + b.hpMax +
        '\n(' + b.x + ',' + b.y + ',' + b.z + ') · ' + esc(b.state) + '\n' + esc(b.thought || '');
      var c = svgEl('circle', { cx: cx, cy: cy, r: 8 / s, fill: col, stroke: 'var(--stroke)', 'stroke-width': 1.5 / s }, g);
      c.appendChild(svgEl('title', {}, c)).textContent = tip;
      svgEl('circle', { cx: cx, cy: cy, r: 8 / s, fill: col, opacity: '0.3', class: 'plr2' }, g);
      svgEl('text', { x: cx, y: cy - 13 / s, 'text-anchor': 'middle', 'font-size': 11 / s, class: 'mlabel' }, g)
        .textContent = esc(b.name) + ' L' + b.level;
      svgEl('text', { x: cx, y: cy + 22 / s, 'text-anchor': 'middle', class: 'sublabel', 'font-size': 9 / s }, g)
        .textContent = esc((b.thought || b.action || '').slice(0, 26));
    });
  }


  // ---- pointer interaction -----------------------------------------------
  function evXY(e) {
    var r = wrap.getBoundingClientRect();
    return { x: e.clientX - r.left, y: e.clientY - r.top };
  }
  function pointerDist() {
    var a = Array.from(pointers.values());
    if (a.length < 2) return 0;
    return Math.sqrt((a[0].x - a[1].x) * (a[0].x - a[1].x) + (a[0].y - a[1].y) * (a[0].y - a[1].y));
  }
  function pointerMid() {
    var a = Array.from(pointers.values());
    return { x: (a[0].x + a[1].x) / 2, y: (a[0].y + a[1].y) / 2 };
  }
  function onDown(e) {
    var p = evXY(e);
    pointers.set(e.pointerId, p);
    if (wrap.setPointerCapture) wrap.setPointerCapture(e.pointerId);
    pinchDist = pointers.size === 2 ? pointerDist() : 0;
  }
  function onMove(e) {
    if (!pointers.has(e.pointerId)) return;
    var p = evXY(e);
    var prev = pointers.get(e.pointerId);
    if (pointers.size === 1) {
      tx += p.x - prev.x;
      ty += p.y - prev.y;
      pointers.set(e.pointerId, p);
      render();
    } else if (pointers.size === 2) {
      pointers.set(e.pointerId, p);
      var d = pointerDist();
      if (pinchDist > 0) {
        var mid = pointerMid();
        zoomAt(mid.x, mid.y, d / pinchDist);
      }
      pinchDist = d;
    }
  }
  function onUp(e) {
    pointers.delete(e.pointerId);
    if (pointers.size < 2) pinchDist = 0;
  }
  function onWheel(e) {
    e.preventDefault();
    var p = evXY(e);
    zoomAt(p.x, p.y, Math.exp(-e.deltaY * 0.0015));
  }

  // ---- public API --------------------------------------------------------
  function init() {
    svg = $e('map');
    wrap = $e('mapWrap');
    if (!svg || !wrap) return;
    // No viewBox: 1 user unit = 1 CSS px; clip overflow.
    svg.removeAttribute('viewBox');
    svg.removeAttribute('preserveAspectRatio');
    svg.style.overflow = 'hidden';

    svg.addEventListener('pointerdown', onDown);
    svg.addEventListener('pointermove', onMove);
    svg.addEventListener('pointerup', onUp);
    svg.addEventListener('pointercancel', onUp);
    svg.addEventListener('wheel', onWheel, { passive: false });
    svg.addEventListener('dblclick', function () { fit(); render(); });

    dataFromInline().then(function (data) {
      regions = data.regions || [];
      landmarks = data.landmarks || [];
      computeBounds();
      fit();
      buildBase();
      baseBuilt = true;
      render();
      if (window.__onMapReady) window.__onMapReady(regions, landmarks);
    }).catch(function () {
      // Degrade gracefully: still let bots render with default bounds.
      computeBounds(); fit(); buildBase(); baseBuilt = true; render();
    });

    window.addEventListener('resize', function () { render(); });
  }

  window.MapRenderer = {
    init: init,
    render: function (bots, ent, towns, trails, onTrails) { render(bots, ent, towns, trails, onTrails); },
    zoomIn: function () { zoomBy(1.3); },
    zoomOut: function () { zoomBy(1 / 1.3); },
    fit: function () { fit(); render(); },
    zoomAt: zoomAt,
    getRegions: function () { return regions; },
    getLandmarks: function () { return landmarks; }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();


#!/usr/bin/env node
/* ============================================================================
 * WPT-31 — Dashboard bundler (scripts/_build.js)
 * ----------------------------------------------------------------------------
 * Inline helper used by scripts/build_dashboard.sh. Reads the modular
 * dashboard source and produces a single self-contained, minified
 * index.html (the file actually served by FleetPlay's single-resource "/"
 * handler). Steps:
 *
 *   1. compute a version tag (git short hash + timestamp, or "dev").
 *   2. read index.src.html, css/style.css, js/map.js, js/app.js and the two
 *      data JSON files.
 *   3. minify CSS + JS with a conservative, syntax-safe minifier.
 *   4. embed regions/landmarks as window.__MAP_DATA__ (so the served bundle
 *      needs NO network fetch for map data) and inject inline <style>/<script>.
 *   5. drop the DEV-only <link>/<script> external references.
 *   6. validate minified JS via new Function(...) and JSON via JSON.parse.
 *   7. write dashboard/index.html + print stats.
 * ==========================================================================*/
'use strict';

var fs = require('fs');
var path = require('path');
var cp = require('child_process');

var ROOT = path.resolve(__dirname, '..');
var DASH = path.join(ROOT, 'AIPlayerEngine', 'src', 'main', 'resources', 'dashboard');
var SRC_HTML = path.join(DASH, 'index.src.html');
var OUT_HTML = path.join(DASH, 'index.html');
var CSS = path.join(DASH, 'css', 'style.css');
var MAP_JS = path.join(DASH, 'js', 'map.js');
var APP_JS = path.join(DASH, 'js', 'app.js');
var REGIONS = path.join(DASH, 'data', 'regions.json');
var LANDMARKS = path.join(DASH, 'data', 'landmarks.json');

function read(p) { return fs.readFileSync(p, 'utf8'); }
function readJson(p) {
  var raw = read(p);
  try { return JSON.parse(raw); }
  catch (e) { throw new Error('Invalid JSON in ' + p + ': ' + e.message); }
}

function versionTag() {
  var hash = 'dev';
  try {
    hash = cp.execSync('git rev-parse --short HEAD', { cwd: ROOT }).toString().trim() || 'dev';
  } catch (e) { hash = 'dev'; }
  var d = new Date();
  var pad = function (n) { return (n < 10 ? '0' : '') + n; };
  var ts = '' + d.getUTCFullYear() + pad(d.getUTCMonth() + 1) + pad(d.getUTCDate()) +
    pad(d.getUTCHours()) + pad(d.getUTCMinutes()) + pad(d.getUTCSeconds());
  return hash + '-' + ts;
}

/* --- conservative, syntax-safe comment + whitespace stripping ------------ */
function stripJsComments(src) {
  var res = '', i = 0, n = src.length, sq = false, dq = false, tmpl = false, block = false;
  while (i < n) {
    var c = src[i], nx = src[i + 1];
    if (block) { if (c === '*' && nx === '/') { i += 2; block = false; } else i++; continue; }
    if (sq) { if (c === '\\') { res += c + (src[i + 1] || ''); i += 2; continue; } res += c; if (c === "'") sq = false; i++; continue; }
    if (dq) { if (c === '\\') { res += c + (src[i + 1] || ''); i += 2; continue; } res += c; if (c === '"') dq = false; i++; continue; }
    if (tmpl) { if (c === '\\') { res += c + (src[i + 1] || ''); i += 2; continue; } res += c; if (c === '`') tmpl = false; i++; continue; }
    if (c === '/' && nx === '/') { while (i < n && src[i] !== '\n') i++; continue; }
    if (c === '/' && nx === '*') { i += 2; block = true; continue; }
    if (c === "'") { sq = true; res += c; i++; continue; }
    if (c === '"') { dq = true; res += c; i++; continue; }
    if (c === '`') { tmpl = true; res += c; i++; continue; }
    res += c; i++;
  }
  return res;
}
function minifyJs(src) {
  var stripped = stripJsComments(src);
  var lines = stripped.split('\n').map(function (l) { return l.trim(); });
  return lines.filter(function (l) { return l.length > 0; }).join('\n');
}
function minifyCss(src) {
  var s = src.replace(/\/\*[\s\S]*?\*\//g, '');
  s = s.replace(/[ \t]+/g, ' ').replace(/\s*\n\s*/g, '\n').replace(/\n{2,}/g, '\n').trim();
  return s;
}

/* --- main ----------------------------------------------------------------- */
function main() {
  var version = versionTag();
  var css = minifyCss(read(CSS));
  var mapJs = read(MAP_JS), appJs = read(APP_JS);
  var combined = mapJs + '\n' + appJs;
  var minced = minifyJs(combined);

  // Syntax validation of the minified JS before embedding.
  try { new Function(minced); }
  catch (e) { throw new Error('Minified JS failed syntax check: ' + e.message); }

  var regions = readJson(REGIONS);
  var landmarks = readJson(LANDMARKS);
  var dataJs = 'window.__MAP_DATA__={regions:' + JSON.stringify(regions) +
    ',landmarks:' + JSON.stringify(landmarks) + '};';

  var html = read(SRC_HTML);
  // Strip DEV-only external references (the bundle inlines everything).
  html = html.replace(/<!--DEV-CSS-->[\s\S]*?<!--\/DEV-CSS-->/, '');
  html = html.replace(/<!--DEV-JS-->[\s\S]*?<!--\/DEV-JS-->/, '');
  // Inject inline payloads.
  html = html.replace('<!--__CSSTAG__-->', '<style>' + css + '</style>');
  html = html.replace('<!--__JSTAG__-->',
    '<script>/* build:' + version + ' */window.__BUILD__="' + version + '";' + dataJs + minced + '<\/script>');

  var banner = '<!-- AI Fleet Dashboard GENERATED bundle — version ' + version + '.\n' +
    '     Source: index.src.html + js/map.js + js/app.js + css/style.css + data/*.json.\n' +
    '     Regenerate with: scripts/build_dashboard.sh  (do not hand-edit) -->\n';
  html = html.replace(/<!doctype html>\n?/, '<!doctype html>\n' + banner);

  fs.writeFileSync(OUT_HTML, html, 'utf8');

  console.log('build_dashboard.sh: generated ' + path.relative(ROOT, OUT_HTML));
  console.log('  version      = ' + version);
  console.log('  regions      = ' + regions.regions.length + ', landmarks = ' + landmarks.landmarks.length);
  console.log('  css bytes    = ' + css.length + '  (minified)');
  console.log('  js  bytes    = ' + minced.length + '  (minified, was ' + combined.length + ')');
  console.log('  index.html   = ' + html.length + ' bytes');
  return 0;
}

try { process.exit(main()); }
catch (e) { console.error('build error:', e.message); process.exit(1); }


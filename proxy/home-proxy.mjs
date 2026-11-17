// Heim-Proxy für den IPTV Player.
// Läuft auf deinem PC zu Hause. Der Anbieter sieht deine normale Heim-IP
// (so wie bei IPTV Smarters, das sich direkt vom Gerät aus verbindet).
// Die App wird gleich mit ausgeliefert: am PC http://localhost:8787 öffnen,
// am Handy oder Tablet im selben WLAN die Adresse, die beim Start angezeigt wird.
//
// Nur Geräte im eigenen Netz haben Zugriff. Es wird nichts ins Internet freigegeben,
// kein fremder Dienst ist beteiligt und es entstehen keine Kosten.
//
// Start:  node home-proxy.mjs        (Node.js 18 oder neuer)

import http from 'node:http';
import crypto from 'node:crypto';
import os from 'node:os';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Readable } from 'node:stream';
import { spawn } from 'node:child_process';

const UPSTREAM = process.env.UPSTREAM || 'http://ip.sltv.be:8080';
const PORT = Number(process.env.PORT || 8787);
const USER_AGENT = process.env.USER_AGENT || 'IPTVSmartersPro';
const MAX_REDIRECTS = 5;

const here = path.dirname(fileURLToPath(import.meta.url));
const APP_FILE = [path.join(here, '..', 'index.html'), path.join(here, 'index.html')].find(f => fs.existsSync(f));

// Schlüssel für signierte Adressen (Segmente auf anderen Servern nach einer Weiterleitung)
const KEY = crypto.randomBytes(32);
const sign = u => crypto.createHmac('sha256', KEY).update(u).digest('base64url').slice(0, 22);
const validSig = (u, s) => {
  if (!s) return false;
  const a = Buffer.from(sign(u)), b = Buffer.from(s);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
};

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'Range',
  'Access-Control-Expose-Headers': 'Content-Length, Content-Range, Accept-Ranges',
};

const mask = u => String(u)
  .replace(/\/(live|movie|series|timeshift)\/[^/]+\/[^/]+\//, '/$1/***/***/')
  .replace(/(username|password)=[^&]*/g, '$1=***');

// Nur Anfragen aus dem eigenen Netz (PC selbst, WLAN, VPN-Bereiche)
function isLocal(addr = '') {
  const a = addr.replace(/^::ffff:/, '');
  return a === '::1' || /^127\./.test(a) || /^10\./.test(a) || /^192\.168\./.test(a) ||
    /^172\.(1[6-9]|2\d|3[01])\./.test(a) || /^169\.254\./.test(a) || /^(fe80|fc|fd)/i.test(a) ||
    /^100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\./.test(a);
}

function sendText(res, status, body) {
  res.writeHead(status, { ...CORS, 'Content-Type': 'text/plain; charset=utf-8' });
  res.end(body);
}

async function fetchChain(target, headers, signal) {
  const hops = [];
  let url = target;
  for (let i = 0; i <= MAX_REDIRECTS; i++) {
    let r;
    try {
      r = await fetch(url, { headers, redirect: 'manual', signal });
    } catch (e) {
      const msg = e.cause?.code || e.cause?.message || e.message;
      hops.push({ url, error: msg });
      return { hops, error: msg, url };
    }
    const loc = r.headers.get('location');
    hops.push({ url, status: r.status, type: r.headers.get('content-type') || '', location: loc || '' });
    if (r.status >= 300 && r.status < 400 && loc) {
      r.body?.cancel().catch(() => {});
      url = new URL(loc, url).toString();
      continue;
    }
    return { hops, res: r, url };
  }
  return { hops, error: 'Zu viele Weiterleitungen', url };
}

const describe = hops => hops.map((h, i) => h.error
  ? `${i + 1}. ${mask(h.url)} -> FEHLER: ${h.error}`
  : `${i + 1}. ${mask(h.url)} -> HTTP ${h.status}` + (h.type ? ` [${h.type}]` : '') +
    (h.location ? ` -> ${mask(h.location)}` : '')).join('\n');

async function proxy(req, res, reqUrl) {
  const target = reqUrl.searchParams.get('u');
  if (!target || !(target.startsWith(UPSTREAM) || validSig(target, reqUrl.searchParams.get('sig')))) {
    return sendText(res, 403, 'Forbidden');
  }

  // Bricht der Player ab (Senderwechsel), wird auch die Verbindung zum Anbieter beendet.
  // Wichtig, weil Anbieter meist nur 1 gleichzeitige Verbindung erlauben.
  const ac = new AbortController();
  res.on('close', () => ac.abort());

  const headers = { 'User-Agent': USER_AGENT, 'Accept': '*/*' };
  if (req.headers.range) headers['Range'] = req.headers.range;

  const { hops, res: up, error, url: finalUrl } = await fetchChain(target, headers, ac.signal);

  if (reqUrl.searchParams.has('debug')) {
    let sample = '';
    if (up?.body) {
      const reader = up.body.getReader();
      const { value } = await reader.read().catch(() => ({}));
      reader.cancel().catch(() => {});
      sample = !value ? '\nKeine Daten empfangen' : `\nErste Daten: ${value.length} Bytes` +
        (value[0] === 0x47 ? ' (MPEG-TS, sieht gut aus)'
          : ': ' + Buffer.from(value.slice(0, 200)).toString('utf8').replace(/\s+/g, ' '));
    }
    return sendText(res, 200, 'Heim-Proxy-Diagnose\n' + describe(hops) + (error ? '\nAbbruch: ' + error : '') + sample);
  }

  if (error) {
    if (ac.signal.aborted) return;
    return sendText(res, 502, 'Upstream nicht erreichbar: ' + error + ' (' + mask(finalUrl) + ')');
  }
  if (!up.ok && up.status !== 206) {
    up.body?.cancel().catch(() => {});
    const via = hops.length > 1 ? ' nach Weiterleitung zu ' + new URL(finalUrl).host : '';
    return sendText(res, up.status, 'Upstream HTTP ' + up.status + via);
  }

  const type = up.headers.get('content-type') || '';
  const out = { ...CORS };
  for (const k of ['content-type', 'content-length', 'content-range', 'accept-ranges']) {
    const v = up.headers.get(k);
    if (v) out[k] = v;
  }

  if (type.includes('mpegurl') || /\.m3u8$/.test(new URL(finalUrl).pathname)) {
    // Playlist: alle Adressen wieder über diesen Proxy, signiert
    const self = `http://${req.headers.host}`;
    const base = new URL(finalUrl);
    const wrap = u => {
      const abs = new URL(u, base).toString();
      return `${self}/?u=${encodeURIComponent(abs)}&sig=${sign(abs)}`;
    };
    const body = await up.text();
    const rewritten = body.split('\n').map(line => {
      const t = line.trim();
      if (!t) return line;
      if (t.startsWith('#')) return line.replace(/URI="([^"]+)"/g, (_, u) => `URI="${wrap(u)}"`);
      return wrap(t);
    }).join('\n');
    out['content-type'] = 'application/vnd.apple.mpegurl';
    delete out['content-length'];
    res.writeHead(up.status, out);
    return res.end(rewritten);
  }

  res.writeHead(up.status, out);
  if (req.method === 'HEAD' || !up.body) return res.end();
  Readable.fromWeb(up.body).on('error', () => res.destroy()).pipe(res);
}

function serveApp(res) {
  if (!APP_FILE) return sendText(res, 404, 'index.html nicht gefunden. Ordnerstruktur behalten: index.html und proxy/ nebeneinander.');
  const cfg = `<script>window.HOME_PROXY=${JSON.stringify({ upstream: UPSTREAM })}</script>`;
  const html = fs.readFileSync(APP_FILE, 'utf8').replace('<!--HOME_PROXY-->', cfg);
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-cache' });
  res.end(html);
}

function lanAddresses() {
  return Object.values(os.networkInterfaces()).flat()
    .filter(i => i && i.family === 'IPv4' && !i.internal && isLocal(i.address))
    .map(i => `http://${i.address}:${PORT}`);
}

function setupPage(res) {
  const urls = lanAddresses();
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(`<!doctype html><html lang="de"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Heim-Proxy</title>
<style>body{font-family:sans-serif;background:#141414;color:#fff;text-align:center;padding:30px}
.qr{background:#fff;display:inline-block;padding:16px;border-radius:12px;margin:12px}
a{color:#8cf}code{background:#333;padding:2px 6px;border-radius:4px}</style></head><body>
<h1>Heim-Proxy läuft</h1>
<p>Am PC: <a href="/">http://localhost:${PORT}</a></p>
${urls.length ? `<p>Am Handy oder Tablet (gleiches WLAN): Code mit der Kamera scannen</p>
${urls.map((u, i) => `<div><div class="qr" id="qr${i}"></div><p><code>${u}</code></p></div>`).join('')}
<script src="https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js"></script>
<script>${JSON.stringify(urls)}.forEach((u, i) => new QRCode(document.getElementById('qr' + i), { text: u, width: 220, height: 220 }));</script>`
  : '<p>Keine WLAN-Adresse gefunden. Ist der PC mit dem Netzwerk verbunden?</p>'}
</body></html>`);
}

const server = http.createServer(async (req, res) => {
  if (!isLocal(req.socket.remoteAddress)) return sendText(res, 403, 'Nur im eigenen Netz erlaubt');
  const reqUrl = new URL(req.url, 'http://localhost');
  try {
    if (req.method === 'OPTIONS') { res.writeHead(204, CORS); return res.end(); }
    if (reqUrl.pathname === '/' && reqUrl.searchParams.has('u')) return await proxy(req, res, reqUrl);
    if (reqUrl.pathname === '/' || reqUrl.pathname === '/index.html') return serveApp(res);
    if (reqUrl.pathname === '/setup') return setupPage(res);
    sendText(res, 404, 'Not found');
  } catch (e) {
    if (!res.headersSent) sendText(res, 500, 'Proxy-Fehler: ' + e.message);
    else res.destroy();
  }
});

server.on('error', e => {
  console.log(e.code === 'EADDRINUSE' ? `Port ${PORT} ist belegt. Läuft der Proxy schon in einem anderen Fenster?` : e.message);
  process.exit(1);
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`\nHeim-Proxy läuft (Anbieter: ${UPSTREAM})`);
  console.log(`  Am PC:                 http://localhost:${PORT}`);
  for (const u of lanAddresses()) console.log(`  Handy/Tablet im WLAN:  ${u}`);
  console.log(`  QR-Code fürs Handy:    http://localhost:${PORT}/setup`);
  console.log('\nDieses Fenster offen lassen, solange du schaust. Beenden mit Strg+C.\n');
  if (!process.env.NO_BROWSER) openBrowser(`http://localhost:${PORT}/`);
});

function openBrowser(url) {
  const cmd = process.platform === 'win32' ? ['cmd', ['/c', 'start', '', url]]
    : process.platform === 'darwin' ? ['open', [url]] : ['xdg-open', [url]];
  try { spawn(cmd[0], cmd[1], { stdio: 'ignore', detached: true }).on('error', () => {}).unref(); } catch {}
}

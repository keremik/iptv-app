// Heim-Proxy für den IPTV Player.
// Läuft auf einem Rechner bei dir zu Hause, damit der Anbieter eine normale Heim-IP sieht
// (so wie IPTV Smarters, das sich direkt vom Gerät aus verbindet).
// Ein Cloudflare-Tunnel macht ihn per HTTPS für das iPhone erreichbar, ohne Router-Einstellungen.
//
// Start:  node home-proxy.mjs        (Node.js 18 oder neuer)
// Danach am PC http://localhost:8787/setup öffnen und den QR-Code mit dem iPhone scannen.

import http from 'node:http';
import crypto from 'node:crypto';
import { Readable } from 'node:stream';
import { spawn } from 'node:child_process';

const UPSTREAM = process.env.UPSTREAM || 'http://ip.sltv.be:8080';
const PORT = Number(process.env.PORT || 8787);
const USER_AGENT = process.env.USER_AGENT || 'IPTVSmartersPro';
const APP_URL = process.env.APP_URL || 'https://keremik.github.io/iptv-app/';
const MAX_REDIRECTS = 5;

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

let tunnelUrl = '';

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
    const self = `${req.headers['x-forwarded-proto'] || (req.headers['cf-connecting-ip'] ? 'https' : 'http')}://${req.headers.host}`;
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

function setupPage(res) {
  const link = tunnelUrl ? `${APP_URL}?proxy=${encodeURIComponent(tunnelUrl)}` : '';
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(`<!doctype html><html lang="de"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Heim-Proxy</title>
${tunnelUrl ? '' : '<meta http-equiv="refresh" content="3">'}
<style>body{font-family:sans-serif;background:#070a2e;color:#fff;text-align:center;padding:30px}
#qr{background:#fff;display:inline-block;padding:16px;border-radius:12px;margin:20px}
a{color:#8cf;word-break:break-all}code{background:#1a1f4a;padding:2px 6px;border-radius:4px}</style></head><body>
<h1>IPTV Heim-Proxy läuft</h1>
${link ? `<p>Scanne den Code mit der iPhone-Kamera. Die App öffnet sich in Safari und übernimmt die Proxy-Adresse.</p>
<div id="qr"></div><p><a href="${link}">${link}</a></p>
<p>Proxy-Adresse: <code>${tunnelUrl}</code></p>
<p>Die Adresse ändert sich bei jedem Neustart. Dann einfach den neuen Code scannen.</p>
<script src="https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js"></script>
<script>new QRCode(document.getElementById('qr'), { text: ${JSON.stringify(link)}, width: 280, height: 280 });</script>`
  : `<p>Warte auf den Cloudflare-Tunnel ... (Seite lädt sich selbst neu)</p>
<p>Falls nichts passiert: Ist <code>cloudflared</code> installiert? Siehe Fenster mit dem Proxy.</p>`}
</body></html>`);
}

const server = http.createServer(async (req, res) => {
  const reqUrl = new URL(req.url, 'http://localhost');
  try {
    if (req.method === 'OPTIONS') { res.writeHead(204, CORS); return res.end(); }
    if (reqUrl.pathname === '/setup') {
      // Einrichtungsseite nur am PC selbst, nicht über den Tunnel
      if (req.headers['cf-connecting-ip']) return sendText(res, 404, 'Not found');
      return setupPage(res);
    }
    if (reqUrl.pathname === '/' && reqUrl.searchParams.has('u')) return await proxy(req, res, reqUrl);
    if (reqUrl.pathname === '/') return sendText(res, 200, 'IPTV Heim-Proxy ok');
    sendText(res, 404, 'Not found');
  } catch (e) {
    if (!res.headersSent) sendText(res, 500, 'Proxy-Fehler: ' + e.message);
    else res.destroy();
  }
});

server.listen(PORT, () => {
  console.log(`Heim-Proxy läuft auf http://localhost:${PORT}  (Anbieter: ${UPSTREAM})`);
  if (process.env.NO_TUNNEL) return;
  startTunnel();
});

function startTunnel() {
  const bin = process.env.CLOUDFLARED || 'cloudflared';
  let cf;
  try {
    cf = spawn(bin, ['tunnel', '--no-autoupdate', '--url', `http://localhost:${PORT}`], { stdio: ['ignore', 'pipe', 'pipe'] });
  } catch (e) {
    return noTunnel(e);
  }
  cf.on('error', noTunnel);
  const onData = d => {
    const m = String(d).match(/https:\/\/[a-z0-9-]+\.trycloudflare\.com/);
    if (m && !tunnelUrl) {
      tunnelUrl = m[0];
      console.log('\nTunnel bereit: ' + tunnelUrl);
      console.log(`Jetzt am PC öffnen:  http://localhost:${PORT}/setup  und den QR-Code mit dem iPhone scannen.\n`);
      openBrowser(`http://localhost:${PORT}/setup`);
    }
  };
  cf.stdout.on('data', onData);
  cf.stderr.on('data', onData);
  cf.on('exit', code => {
    console.log('cloudflared beendet (Code ' + code + '). Neustart in 5 s ...');
    tunnelUrl = '';
    setTimeout(startTunnel, 5000);
  });
  const stop = () => { cf.kill(); process.exit(0); };
  process.once('SIGINT', stop);
  process.once('SIGTERM', stop);
}

function noTunnel(e) {
  console.log('\ncloudflared wurde nicht gefunden (' + (e.code || e.message) + ').');
  console.log('Installieren:  Windows: winget install --id Cloudflare.cloudflared');
  console.log('               Mac:     brew install cloudflared');
  console.log('Danach dieses Programm neu starten.\n');
}

function openBrowser(url) {
  const cmd = process.platform === 'win32' ? ['cmd', ['/c', 'start', '', url]]
    : process.platform === 'darwin' ? ['open', [url]] : ['xdg-open', [url]];
  try { spawn(cmd[0], cmd[1], { stdio: 'ignore', detached: true }).on('error', () => {}).unref(); } catch {}
}

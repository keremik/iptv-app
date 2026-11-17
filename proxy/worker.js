// Cloudflare Worker: leitet Anfragen von der Web-App an den IPTV-Server weiter,
// damit die App auch ohne PC (z. B. am iPhone über GitHub Pages) funktioniert.
//
// Durchgelassen wird nur:
//  - der Server UPSTREAM,
//  - Adressen, die der Worker selbst signiert hat (Segmente in Playlists nach einer Weiterleitung),
//  - Bilder (?img=...), nur wenn die Antwort wirklich ein Bild ist.
// Der Schlüssel SIGN_KEY ist als geheime Variable im Worker hinterlegt, nicht im Code.
//
// Diagnose: ?u=...&debug=1 verfolgt Weiterleitungen einzeln und zeigt Status und Ziel.

const UPSTREAM = 'http://ip.sltv.be:8080';
const MAX_REDIRECTS = 5;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'Range',
  'Access-Control-Expose-Headers': 'Content-Length, Content-Range, Accept-Ranges',
};

const mask = u => String(u)
  .replace(/\/(live|movie|series|timeshift)\/[^/]+\/[^/]+\//, '/$1/***/***/')
  .replace(/(username|password)=[^&]*/g, '$1=***');

const text = (body, status = 200) =>
  new Response(body, { status, headers: { ...CORS, 'Content-Type': 'text/plain; charset=utf-8' } });

// Cloudflare Workers dürfen keine reinen IP-Adressen abrufen (Antwort: "error code: 1003").
// Viele Anbieter leiten aber auf eine IP weiter. nip.io und sslip.io sind öffentliche
// DNS-Namen, die genau auf die enthaltene IP zeigen (1.2.3.4.nip.io -> 1.2.3.4).
// Die Anfrage selbst geht weiterhin direkt an den Server des Anbieters.
const IP_DNS = ['nip.io', 'sslip.io'];
const isIp = h => /^\d{1,3}(\.\d{1,3}){3}$/.test(h);

async function fetchUrl(url, init) {
  const u = new URL(url);
  if (!isIp(u.hostname)) return fetch(url, init);
  let lastErr;
  for (let i = 0; i < IP_DNS.length; i++) {
    const v = new URL(url);
    v.hostname = `${u.hostname}.${IP_DNS[i]}`;
    try {
      const r = await fetch(v.toString(), init);
      // 530 = Name nicht auflösbar: nächsten DNS-Dienst versuchen
      if (r.status !== 530 || i === IP_DNS.length - 1) return r;
      r.body?.cancel();
    } catch (e) {
      lastErr = e;
    }
  }
  throw lastErr || new Error('IP-Adresse nicht erreichbar');
}

let keyPromise = null;
function hmacKey(secret) {
  return keyPromise ??= crypto.subtle.importKey('raw', new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
}
async function sign(env, u) {
  if (!env.SIGN_KEY) return '';
  const mac = await crypto.subtle.sign('HMAC', await hmacKey(env.SIGN_KEY), new TextEncoder().encode(u));
  return btoa(String.fromCharCode(...new Uint8Array(mac))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '').slice(0, 22);
}
async function validSig(env, u, s) {
  if (!s || !env.SIGN_KEY) return false;
  const expected = await sign(env, u);
  if (expected.length !== s.length) return false;
  let diff = 0;
  for (let i = 0; i < s.length; i++) diff |= expected.charCodeAt(i) ^ s.charCodeAt(i);
  return diff === 0;
}

// Weiterleitungen selbst verfolgen, damit Fehler den betroffenen Schritt nennen
async function fetchChain(target, headers) {
  const hops = [];
  let url = target;
  for (let i = 0; i <= MAX_REDIRECTS; i++) {
    let res;
    try {
      res = await fetchUrl(url, { headers, redirect: 'manual' });
    } catch (e) {
      hops.push({ url, error: e.message });
      return { hops, error: e.message, url };
    }
    const loc = res.headers.get('Location');
    hops.push({ url, status: res.status, type: res.headers.get('Content-Type') || '', location: loc || '' });
    if (res.status >= 300 && res.status < 400 && loc) {
      res.body?.cancel();
      url = new URL(loc, url).toString();
      continue;
    }
    return { hops, res, url };
  }
  return { hops, error: 'Zu viele Weiterleitungen', url };
}

function describe(hops) {
  return hops.map((h, i) => {
    const where = mask(h.url);
    if (h.error) return `${i + 1}. ${where} -> FEHLER: ${h.error}`;
    return `${i + 1}. ${where} -> HTTP ${h.status}` +
      (h.type ? ` [${h.type}]` : '') +
      (h.location ? ` -> ${mask(h.location)}` : '');
  }).join('\n');
}

// Poster und Senderlogos: http-Bilder kann eine https-Seite nicht anzeigen.
// Manche Server liefern Bilder ohne passenden Typ aus, daher zusätzlich Erkennung am Dateianfang.
function sniffImage(b) {
  if (!b || b.length < 12) return '';
  if (b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4e && b[3] === 0x47) return 'image/png';
  if (b[0] === 0xff && b[1] === 0xd8 && b[2] === 0xff) return 'image/jpeg';
  if (b[0] === 0x47 && b[1] === 0x49 && b[2] === 0x46) return 'image/gif';
  if (b[0] === 0x52 && b[1] === 0x49 && b[2] === 0x46 && b[3] === 0x46 && b[8] === 0x57 && b[9] === 0x45 && b[10] === 0x42 && b[11] === 0x50) return 'image/webp';
  return '';
}

// SSRF-Schutz für den Bild-Proxy: private, lokale und Metadaten-Adressen sind tabu,
// damit der Proxy nicht zum Abtasten fremder Netze missbraucht werden kann.
function isPrivateIp(ip) {
  const m = ip.split('.').map(Number);
  if (m.length !== 4 || m.some(x => isNaN(x) || x < 0 || x > 255)) return false;
  const [a, b] = m;
  return a === 0 || a === 10 || a === 127 || a >= 224 ||
    (a === 169 && b === 254) || (a === 172 && b >= 16 && b <= 31) ||
    (a === 192 && b === 168) || (a === 100 && b >= 64 && b <= 127);
}
function unsafeImageHost(host) {
  const h = host.toLowerCase().replace(/\.$/, '');
  if (h === 'localhost' || h.endsWith('.localhost') || h.endsWith('.local') || h.endsWith('.internal')) return true;
  if (/^\[?::1\]?$/.test(h) || /^\[?(fc|fd|fe80|fe9|fea|feb)/i.test(h)) return true;
  // auch IP-in-DNS-Formen wie 10.0.0.1.nip.io erkennen
  const ipm = h.match(/(\d{1,3}(?:\.\d{1,3}){3})(?:\.(?:nip|sslip)\.io)?$/);
  return ipm ? isPrivateIp(ipm[1]) : false;
}
const OK_PORTS = ['', '80', '443', '8080', '8443', '8880'];

async function image(src) {
  if (!/^https?:\/\//i.test(src)) return text('Forbidden', 403);
  let url = src, res;
  for (let i = 0; i < 3; i++) {                       // Weiterleitungen einzeln prüfen
    let u;
    try { u = new URL(url); } catch (e) { return text('Forbidden', 403); }
    if (unsafeImageHost(u.hostname) || OK_PORTS.indexOf(u.port) < 0) return text('Forbidden', 403);
    try {
      res = await fetchUrl(u.toString(), { headers: { 'User-Agent': 'Mozilla/5.0', 'Accept': 'image/*,*/*' }, redirect: 'manual', cf: { cacheTtl: 86400, cacheEverything: true } });
    } catch (e) {
      return text('Bild nicht erreichbar', 502);
    }
    const loc = res.headers.get('Location');
    if (res.status >= 300 && res.status < 400 && loc) { res.body?.cancel(); url = new URL(loc, u).toString(); continue; }
    break;
  }
  const len = Number(res.headers.get('Content-Length') || 0);
  if (!res.ok || !res.body || len > MAX_IMAGE_BYTES) {
    res.body?.cancel();
    return text('Kein Bild', 415);
  }
  let type = res.headers.get('Content-Type') || '';
  let body = res.body;
  if (!type.startsWith('image/')) {
    const reader = res.body.getReader();
    const first = await reader.read().catch(() => ({}));
    type = sniffImage(first.value);
    if (!type) {
      reader.cancel().catch(() => {});
      return text('Kein Bild', 415);
    }
    body = new ReadableStream({
      start(c) { c.enqueue(first.value); },
      async pull(c) {
        const { done, value } = await reader.read();
        if (done) c.close(); else c.enqueue(value);
      },
      cancel() { reader.cancel().catch(() => {}); },
    });
  }
  return new Response(body, {
    headers: {
      ...CORS, 'Content-Type': type, 'Cache-Control': 'public, max-age=86400',
      'X-Content-Type-Options': 'nosniff', 'Content-Security-Policy': "default-src 'none'; style-src 'unsafe-inline'",
    },
  });
}

export default {
  async fetch(request, env) {
    const reqUrl = new URL(request.url);
    const self = reqUrl.origin;

    if (request.method === 'OPTIONS') {
      return new Response(null, { headers: CORS });
    }

    const img = reqUrl.searchParams.get('img');
    if (img) return image(img);

    const target = reqUrl.searchParams.get('u');
    if (!target || !(target.startsWith(UPSTREAM) || await validSig(env, target, reqUrl.searchParams.get('sig')))) {
      return text('Forbidden', 403);
    }

    const headers = { 'User-Agent': 'IPTVSmartersPro', 'Accept': '*/*' };
    const range = request.headers.get('Range');
    if (range) headers['Range'] = range;

    const { hops, res: upstream, error, url: finalUrl } = await fetchChain(target, headers);

    if (reqUrl.searchParams.has('debug')) {
      let sample = '';
      if (upstream) {
        // Nur die ersten Bytes lesen, damit ein Live-Stream nicht endlos läuft
        const reader = upstream.body?.getReader();
        if (reader) {
          const { value } = await reader.read().catch(() => ({}));
          reader.cancel().catch(() => {});
          if (value) {
            const n = value.length;
            const isTs = value[0] === 0x47;
            const isVideo = /^(video|audio)\/|octet-stream|matroska/i.test(upstream.headers.get('Content-Type') || '');
            sample = `\nErste Daten: ${n} Bytes` + (isTs ? ' (MPEG-TS, sieht gut aus)' : isVideo ? ' (Videodaten, sieht gut aus)' :
              ': ' + new TextDecoder().decode(value.slice(0, 200)).replace(/\s+/g, ' '));
          } else {
            sample = '\nKeine Daten empfangen';
          }
        }
      }
      return text('Proxy-Diagnose (Cloudflare)\n' + describe(hops) + (error ? '\nAbbruch: ' + error : '') + sample);
    }

    if (error) {
      return text('Upstream nicht erreichbar: ' + error + ' (' + mask(finalUrl) + ')', 502);
    }

    if (!upstream.ok && upstream.status !== 206) {
      upstream.body?.cancel();
      const via = hops.length > 1 ? ' nach Weiterleitung zu ' + new URL(finalUrl).host : '';
      return text('Upstream HTTP ' + upstream.status + via, upstream.status);
    }

    const type = upstream.headers.get('Content-Type') || '';
    const out = new Headers(CORS);
    for (const k of ['Content-Type', 'Content-Length', 'Content-Range', 'Accept-Ranges']) {
      const v = upstream.headers.get(k);
      if (v) out.set(k, v);
    }

    if (type.includes('mpegurl') || /\.m3u8$/.test(new URL(finalUrl).pathname)) {
      // Alle Adressen in der Playlist laufen wieder über diesen Proxy, signiert
      const base = new URL(finalUrl);
      const body = await upstream.text();
      const lines = await Promise.all(body.split('\n').map(async line => {
        const t = line.trim();
        if (!t) return line;
        const wrap = async u => {
          const abs = new URL(u, base).toString();
          return `${self}/?u=${encodeURIComponent(abs)}&sig=${await sign(env, abs)}`;
        };
        if (t.startsWith('#')) {
          const m = line.match(/URI="([^"]+)"/);
          return m ? line.replace(m[0], `URI="${await wrap(m[1])}"`) : line;
        }
        return wrap(t);
      }));
      out.set('Content-Type', 'application/vnd.apple.mpegurl');
      out.delete('Content-Length');
      return new Response(lines.join('\n'), { status: upstream.status, headers: out });
    }

    return new Response(upstream.body, { status: upstream.status, headers: out });
  },
};

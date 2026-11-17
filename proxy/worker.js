// Cloudflare Worker: leitet Anfragen von der Web-App an den IPTV-Server weiter.
// Nur der Server UPSTREAM wird durchgelassen, damit der Proxy nicht missbraucht werden kann.
//
// Diagnose: ?u=...&debug=1 ruft die Adresse ab, verfolgt Weiterleitungen einzeln
// und gibt für jeden Schritt Status, Typ und Ziel als Text zurück (ohne Stream).

const UPSTREAM = 'http://ip.sltv.be:8080';
const MAX_REDIRECTS = 5;

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'Range',
  'Access-Control-Expose-Headers': 'Content-Length, Content-Range, Accept-Ranges',
};

// Zugangsdaten in Adressen für Meldungen unkenntlich machen
const mask = u => String(u)
  .replace(/\/(live|movie|series|timeshift)\/[^/]+\/[^/]+\//, '/$1/***/***/')
  .replace(/(username|password)=[^&]*/g, '$1=***');

const text = (body, status = 200) =>
  new Response(body, { status, headers: { ...CORS, 'Content-Type': 'text/plain; charset=utf-8' } });

// Weiterleitungen selbst verfolgen, damit Fehler den betroffenen Schritt nennen
async function fetchChain(target, headers) {
  const hops = [];
  let url = target;
  for (let i = 0; i <= MAX_REDIRECTS; i++) {
    let res;
    try {
      res = await fetch(url, { headers, redirect: 'manual' });
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

export default {
  async fetch(request) {
    const reqUrl = new URL(request.url);
    const self = reqUrl.origin;

    if (request.method === 'OPTIONS') {
      return new Response(null, { headers: CORS });
    }

    const target = reqUrl.searchParams.get('u');
    if (!target || !target.startsWith(UPSTREAM)) {
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
            sample = `\nErste Daten: ${n} Bytes` + (isTs ? ' (MPEG-TS, sieht gut aus)' :
              ': ' + new TextDecoder().decode(value.slice(0, 200)).replace(/\s+/g, ' '));
          } else {
            sample = '\nKeine Daten empfangen';
          }
        }
      }
      return text('Proxy-Diagnose\n' + describe(hops) + (error ? '\nAbbruch: ' + error : '') + sample);
    }

    if (error) {
      return text('Upstream nicht erreichbar: ' + error + ' (' + mask(finalUrl) + ')', 502);
    }

    if (!upstream.ok && upstream.status !== 206) {
      const via = hops.length > 1 ? ' nach Weiterleitung zu ' + new URL(finalUrl).host : '';
      return text('Upstream HTTP ' + upstream.status + via, upstream.status);
    }

    const type = upstream.headers.get('Content-Type') || '';
    const isPlaylist = target.includes('.m3u8') || type.includes('mpegurl');

    const out = new Headers(CORS);
    for (const k of ['Content-Type', 'Content-Length', 'Content-Range', 'Accept-Ranges']) {
      const v = upstream.headers.get(k);
      if (v) out.set(k, v);
    }

    if (isPlaylist) {
      // Alle Adressen in der Playlist laufen wieder über diesen Proxy
      const base = new URL(finalUrl);
      const wrap = u => `${self}/?u=${encodeURIComponent(new URL(u, base).toString())}`;
      const body = await upstream.text();
      const rewritten = body.split('\n').map(line => {
        const t = line.trim();
        if (!t) return line;
        if (t.startsWith('#')) return line.replace(/URI="([^"]+)"/g, (_, u) => `URI="${wrap(u)}"`);
        return wrap(t);
      }).join('\n');
      out.set('Content-Type', 'application/vnd.apple.mpegurl');
      out.delete('Content-Length');
      return new Response(rewritten, { status: upstream.status, headers: out });
    }

    return new Response(upstream.body, { status: upstream.status, headers: out });
  },
};

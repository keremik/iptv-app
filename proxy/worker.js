// Cloudflare Worker: leitet Anfragen von der Web-App an den IPTV-Server weiter.
// Nur der Server UPSTREAM wird durchgelassen, damit der Proxy nicht missbraucht werden kann.

const UPSTREAM = 'http://ip.sltv.be:8080';

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'Range',
  'Access-Control-Expose-Headers': 'Content-Length, Content-Range, Accept-Ranges',
};

export default {
  async fetch(request) {
    const reqUrl = new URL(request.url);
    const self = reqUrl.origin;

    if (request.method === 'OPTIONS') {
      return new Response(null, { headers: CORS });
    }

    const target = reqUrl.searchParams.get('u');
    if (!target || !target.startsWith(UPSTREAM)) {
      return new Response('Forbidden', { status: 403, headers: CORS });
    }

    const headers = {};
    const range = request.headers.get('Range');
    if (range) headers['Range'] = range;

    const upstream = await fetch(target, { headers, redirect: 'follow' });
    const type = upstream.headers.get('Content-Type') || '';
    const isPlaylist = target.includes('.m3u8') || type.includes('mpegurl');

    const out = new Headers(CORS);
    for (const k of ['Content-Type', 'Content-Length', 'Content-Range', 'Accept-Ranges']) {
      const v = upstream.headers.get(k);
      if (v) out.set(k, v);
    }

    if (isPlaylist) {
      // Alle Adressen in der Playlist laufen wieder über diesen Proxy
      const base = new URL(target);
      const wrap = u => `${self}/?u=${encodeURIComponent(new URL(u, base).toString())}`;
      const text = await upstream.text();
      const rewritten = text.split('\n').map(line => {
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

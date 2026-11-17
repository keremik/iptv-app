# IPTV Player

Web-App für Xtream-Codes-Anbieter: https://keremik.github.io/iptv-app/

## So funktioniert es

IPTV Smarters ist eine native App und verbindet sich direkt vom Gerät mit dem Anbieter.
Eine Web-App im Browser kann das nicht. Der Browser sperrt `http://`-Streams auf einer `https://`-Seite,
und viele Anbieter sperren Rechenzentren wie Cloudflare.

Deshalb läuft ein kleiner **Heim-Proxy** auf deinem PC oder Mac zu Hause:

```
iPhone (Safari) --https--> Cloudflare-Tunnel --> Heim-Proxy (dein PC, Heim-IP) --http--> Anbieter
```

Der Anbieter sieht damit deine normale Heim-IP, wie bei Smarters.
Der Tunnel ist kostenlos, braucht kein Konto und keine Router-Einstellungen.

## Einrichtung (einmalig)

1. Diesen Ordner `proxy/` auf den PC laden
   (auf GitHub: **Code → Download ZIP**, entpacken).
2. Starten:
   - **Windows:** `proxy/start-proxy-windows.bat` doppelklicken.
     Beim ersten Mal werden Node.js und cloudflared installiert. Danach Fenster schließen und erneut doppelklicken.
   - **Mac:** `proxy/start-proxy-mac.command` doppelklicken (braucht [Homebrew](https://brew.sh)).
     Wenn macOS warnt: Rechtsklick → Öffnen.
3. Im Browser des PCs öffnet sich `http://localhost:8787/setup` mit einem QR-Code.
4. QR-Code mit der iPhone-Kamera scannen. Die App öffnet sich in Safari und übernimmt die Proxy-Adresse.
5. In der App Server, Benutzername und Passwort eintragen und anmelden.

Das Fenster mit dem Proxy muss offen bleiben, solange du schaust. Der PC darf nicht schlafen.
Die Tunnel-Adresse ändert sich bei jedem Neustart. Dann einfach den neuen QR-Code scannen,
die Zugangsdaten bleiben gespeichert.

## Wiedergabe

- **Live-Sender:** iPhone/iPad/Safari spielen `.m3u8` (HLS) mit dem eingebauten Player.
  Desktop-Browser nutzen `.ts` mit mpegts.js.
- **Filme/Serien:** direkt als Datei. Safari kann nur `mp4`/`m3u8`, keine `mkv`.
- Bei einem Fehler fragt die App den Proxy und zeigt den genauen Grund in der Statuszeile
  (z. B. `HTTP 403` = Anbieter sperrt, `ECONNREFUSED` = Server nicht erreichbar).
- Beim Zurückgehen wird der Stream beendet, damit das Verbindungslimit des Anbieters frei wird.

## Dateien

- `index.html`: die ganze Web-App.
- `proxy/home-proxy.mjs`: Heim-Proxy (Node.js 18+, keine weiteren Pakete).
  Einstellungen per Umgebungsvariable: `UPSTREAM`, `PORT`, `USER_AGENT`, `NO_TUNNEL=1`.
- `proxy/worker.js`: älterer Cloudflare-Worker-Proxy (`iptv-proxy.keremirmak02.workers.dev`).
  Funktioniert nur, wenn der Anbieter Cloudflare nicht sperrt.

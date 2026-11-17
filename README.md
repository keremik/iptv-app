# IPTV Player

Web-App für Xtream-Codes-Anbieter, gestaltet wie ein Streamingdienst:
Titelbild, Reihen pro Kategorie, Details mit Staffeln und Folgen, Weiterschauen, Meine Liste, Suche.

## Warum ein Heim-Proxy?

IPTV Smarters ist eine native App und verbindet sich direkt vom Gerät mit dem Anbieter.
Ein Browser kann das nicht direkt: Er sperrt Anfragen an fremde Server (CORS),
und `http://`-Streams auf `https://`-Seiten.

Deshalb läuft ein kleiner **Heim-Proxy** auf deinem PC oder Mac. Er liefert die App aus
und holt die Daten vom Anbieter, mit deiner normalen Heim-IP:

```
Browser (PC, Handy, Tablet im WLAN) --> Heim-Proxy auf deinem PC --> Anbieter
```

- Kostenlos, kein Konto, kein fremder Dienst dazwischen.
- Nur Geräte in deinem eigenen Netz haben Zugriff. Nichts wird ins Internet freigegeben.
- Die Zugangsdaten bleiben in deinem Browser. Sie stehen nicht in diesen Dateien.

## Einrichtung (einmalig)

1. **Node.js** installieren: https://nodejs.org, die Version „LTS“.
2. Dieses Repo herunterladen (auf GitHub: **Code → Download ZIP**) und entpacken.
   `index.html` und der Ordner `proxy/` müssen nebeneinander bleiben.
3. Starten:
   - **Windows:** `proxy/start-proxy-windows.bat` doppelklicken.
     Fragt die Windows-Firewall nach: nur „Private Netzwerke“ erlauben.
   - **Mac:** `proxy/start-proxy-mac.command` doppelklicken. Wenn macOS warnt: Rechtsklick → Öffnen.
4. Der Browser öffnet `http://localhost:8787`. Benutzername und Passwort eintragen, fertig.

**Handy oder Tablet im selben WLAN:** Im Proxy-Fenster steht eine Adresse wie
`http://192.168.178.20:8787`. Diese im Browser öffnen, oder am PC `http://localhost:8787/setup`
aufrufen und den QR-Code scannen.

Das Proxy-Fenster muss offen bleiben, solange du schaust.

## Bedienung

- Maus, Touch oder Tastatur/Fernbedienung (Pfeiltasten, Enter, Zurück/Escape).
- **Live TV:** Sender antippen. Oben stehen die aktuelle und die nächste Sendung.
  Senderwechsel mit ⏮ ⏭, Bild-auf/Bild-ab oder Pfeil hoch/runter.
- **Filme/Serien:** Antippen öffnet die Details. „+“ legt sie in „Meine Liste“.
  Die Position wird gespeichert und erscheint unter „Weiterschauen“. Die nächste Folge startet automatisch.
- Bei einem Wiedergabe-Fehler steht der genaue Grund unten im Hinweis
  (z. B. `HTTP 403` = Anbieter sperrt, `ECONNREFUSED` = Server nicht erreichbar).

Safari (iPhone/iPad) spielt nur `mp4` und `m3u8`. Filme im Format `mkv` laufen dort nicht,
in Chrome oder Edge am PC meistens schon.

## Dateien

- `index.html`: die ganze Web-App.
- `proxy/home-proxy.mjs`: Heim-Proxy (Node.js 18+, keine weiteren Pakete).
  Einstellungen per Umgebungsvariable: `UPSTREAM` (Anbieter-Server), `PORT`, `USER_AGENT`.
- `proxy/worker.js`: älterer Cloudflare-Worker-Proxy, wird nicht mehr gebraucht.

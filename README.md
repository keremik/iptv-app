# IPFLIX

Web-App für Xtream-Codes-Anbieter, gestaltet wie ein Streamingdienst:
Titelbild, Reihen pro Kategorie, Details mit Staffeln und Folgen, Weiterschauen, Meine Liste, Suche.

## Ohne PC (iPhone, unterwegs)

https://keremik.github.io/iptv-app/ in Safari öffnen und anmelden.
Die Proxy-Adresse `https://iptv-proxy.keremirmak02.workers.dev` ist schon eingetragen.

Dabei läuft alles über den Cloudflare-Worker (kostenloser Tarif, es entstehen keine Kosten;
beim Tageslimit von 100.000 Anfragen hört er einfach auf).
Sperrt der Anbieter Cloudflare, zeigt die App das als `HTTP 403` an. Dann hilft nur der Heim-Proxy.

Tipp: In Safari auf „Teilen → Zum Home-Bildschirm“ tippen, dann startet die App wie eine normale App.

## Am Fernseher (Hisense mit VIDAA)

1. Am TV die App „Browser“ öffnen und `keremik.github.io/iptv-app` eintippen, dann anmelden.
2. Als App-Kachel auf dem Startbildschirm (klappt nicht bei jeder Software-Version):
   im TV-Browser `hisense://debug` eintippen, als Name `IPFLIX` und als Adresse
   `https://keremik.github.io/iptv-app/` eintragen, bei Symbol-Feldern
   `https://keremik.github.io/iptv-app/icon.png`, dann „Install“.

Die App vermeidet neuere Web-Technik, damit sie auch im älteren Browser des Fernsehers (Chrome 77) läuft,
und lässt sich mit den Pfeiltasten der Fernbedienung bedienen.

## Sprachen

Angezeigt werden nur deutsche und türkische Inhalte (auch Österreich). Erkannt wird das am Namen
der Kategorie (`DE | Kino`, `GERMANY`, `TR | Ulusal`, `Türkiye` ...) oder am Kürzel vor dem Titel (`(DE-) Film`).
Umschalten: Profil-Symbol oben rechts → „Alle Sprachen anzeigen“.

## Filme im Format mkv am iPhone

Kein iPhone-Browser kann `mkv` abspielen. Im Fehlerhinweis erscheint dann der Knopf „In VLC öffnen“.
Die kostenlose App VLC spielt den Film direkt ab.

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
- `proxy/worker.js`: Cloudflare-Worker für die Nutzung ohne PC. Braucht die geheime Variable `SIGN_KEY`.

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

## Empfehlungen und Lieblingsverein

- Die App merkt sich in diesem Browser, welche Sender du wie lange schaust und aus welchen Kategorien du Filme
  und Serien startest. Daraus entstehen die Reihen „Deine Sender“, „Empfohlen für dich“ und „Weil du … gesehen hast“.
  Zurücksetzen: Profil-Symbol → „Empfehlungen zurücksetzen“.
- Für den Lieblingsverein (Voreinstellung Galatasaray, änderbar im Profil-Menü) zeigt die Startseite das nächste Spiel
  und passende Sender. Der Spielplan kommt von TheSportsDB. Ab 36 Stunden vor dem Anpfiff sucht die App im Programm
  des Anbieters nach dem Spiel.
- Welcher Sender ein Spiel wirklich zeigt, steht in `broadcast.json`. Die Datei wird vor den Spielen per Recherche
  aktualisiert (türkische Sportmedien, Rechteinhaber). Die App zeigt den Sender mit allen Varianten des Anbieters
  (4K, Full HD, HD, HEVC, VIP, Ersatz), die beste Auflösung zuerst.
- Senderlogos kommen zuerst aus der Sammlung [tv-logo/tv-logos](https://github.com/tv-logo/tv-logos), sonst vom Anbieter.

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

## Sicherheit und Daten

- Die App speichert Zugangsdaten, Verlauf und Empfehlungen nur im Browser (localStorage). Sie sendet nichts davon an Dritte.
- Der Proxy leitet nur an den einen Anbieter weiter und an Adressen, die er selbst signiert hat. Der Bild-Proxy
  liefert nur echte Bilder aus und blockiert private, lokale und Metadaten-Adressen (Schutz gegen Missbrauch, SSRF).
- Die Seite setzt eine Content-Security-Policy: Skripte nur aus dem eigenen Ordner und von jsdelivr, keine eingebetteten
  Frames, keine fremden Formularziele. Das begrenzt den Schaden, falls ein Anbieter manipulierte Texte liefert.
- Alle Anzeigetexte des Anbieters werden als Text gesetzt, nie als HTML ausgeführt.

## Allgemeine App für alle (ab Version 1.1)

IPFLIX ist eine allgemeine IPTV-App wie andere Xtream-Player: Jeder meldet sich mit den Zugangsdaten
seines eigenen Anbieters an. Die App läuft komplett auf dem Gerät und braucht keinen fremden Server.

- **Erster Start:** Anmeldung mit Server, Benutzername und Passwort, oder einfach den kompletten
  M3U-Link (`.../get.php?username=...&password=...`) ins erste Feld einfügen.
- **Danach Einrichtung:** Lieblingsverein (für das nächste Spiel und die passenden Sender), Sprachen
  (Deutsch, Türkisch, Englisch, Französisch, Arabisch, Kurdisch, Spanisch, Italienisch, Polnisch,
  Niederländisch, Portugiesisch, Russisch, Balkan, Albanisch oder alle), maximale Auflösung und Sparmodus.
- **Später ändern:** Profil-Symbol oben rechts → „Einstellungen“. „Konto wechseln“ für andere Zugangsdaten.
- Zugangsdaten, Verlauf und Einstellungen bleiben nur auf dem jeweiligen Gerät.
- Die App enthält keine Zugangsdaten und keine persönlichen Adressen. Sie kann auf beliebig vielen
  Sticks installiert werden.

## Als App auf dem Fernseher (Android-Stick)

Im Ordner `android/` liegt eine native Android-TV-App (Paket `com.ipflix.app`). Sie zeigt dieselbe
Oberfläche in einer WebView, aber die Wiedergabe übernimmt ein eingebauter Player (ExoPlayer), der
`.mkv`, Live-TS und HLS flüssig abspielt. Streams, Datenabfragen und Logos gehen direkt vom Stick
an den Anbieter, ohne fremden Server dazwischen. Die App läuft auch mit älteren Android-WebViews (ab Chrome 66).

- **Bauen:** `cd android && ANDROID_HOME=<sdk> ./gradlew assembleRelease`
  Ergebnis: `android/app/build/outputs/apk/release/app-release.apk`. Der Bau kopiert `index.html`,
  `broadcast.json`, `icon.png` und `lib/` automatisch in die App.
- **Signatur:** fest hinterlegter Schlüssel (`android/app/ipflix-release.jks`), damit spätere Updates
  sich ohne Neuinstallation über die alte Version legen. Es ist ein reiner Selbstsignatur-Schlüssel
  für diese persönliche App, kein sensibles Passwort.
- **Installieren (Stick):** Datei per „Downloader“-App (über einen Link), USB oder „Send Files to TV“
  auf den Stick bringen und öffnen. In den Android-Einstellungen vorher „Apps aus unbekannten Quellen“
  für die genutzte App erlauben.
- **Fernbedienung:** Pfeiltasten, OK, Zurück; im Player Sender hoch/runter, Kanal-Tasten; Zurück auf
  der Startseite legt die App in den Hintergrund.
- **Abgestimmt auf den Xiaomi TV Stick 4K (Android TV 11, 2 GB):** volle Oberfläche, Hardware-Decoder für
  4K/HEVC, Puffer für ruckelfreie Wiedergabe (schneller Start nach 1,5 s, bis 50 s Vorlauf), beste
  Qualitätsstufe von Anfang an und die beste Fassung eines Films (z. B. 4K statt HD in derselben Sprache).
- **Bildwiederholrate:** Der Fernseher wird auf die Bildrate der Sendung umgestellt (50 Hz für Fußball und
  deutsches TV, 24 Hz für Filme). Abschaltbar unter Einstellungen → Bildwiederholrate.
- **Ausfallsicherheit:** Bricht ein Stream ab, verbindet der Player sofort neu (mehrere schnelle Versuche,
  erst HLS, dann TS). Klappt das nicht, wechselt er automatisch auf eine andere Variante desselben Senders
  (z. B. FHD → HD → Ersatz), beim Spiel des Lieblingsvereins auch auf einen anderen Sender mit dem Spiel.
  Fällt das Internet aus, geht die Wiedergabe von selbst weiter, sobald es zurück ist. Filme setzen an der
  gleichen Stelle fort.
- **Player-Bedienung (Filme/Serien):** rechts/links = 10 Sekunden vor/zurück (gedrückt halten spult schneller),
  OK = Pause/Weiter, hoch = Leiste zeigen, runter = in die Knöpfe; dort mit allen Pfeiltasten zwischen Knöpfen,
  Zeitbalken (links/rechts = 10 s schieben) und Zahnrad (Tonspur/Untertitel) wechseln,
  Zurück = Knöpfe verlassen bzw. Player schließen. Live: hoch/runter wechselt den Sender, OK zeigt die Leiste.

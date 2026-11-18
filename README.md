# IPFLIX

IPTV-App für Android TV (Xtream Codes), gestaltet wie ein Streamingdienst. Abgestimmt auf den
Xiaomi TV Stick 4K (Android TV 11), läuft aber auf jedem Android-TV-Gerät ab Android 5.
Jeder meldet sich mit den Zugangsdaten seines eigenen Anbieters an. Die App läuft komplett auf dem
Gerät und braucht keinen fremden Server.

**Download:** in der App „Downloader“ auf dem Stick `keremik.github.io/iptv-app/tv.apk` eintippen.

## Funktionen

- **Startseite wie bei Netflix:** Titelbild, Weiterschauen (Filme ab 20 Minuten), Zuletzt gesehene Sender,
  Deine Sender, Empfehlungen nach Sehgewohnheiten, Meine Liste, Suche (auch per Sprache über das Mikrofon-Symbol).
- **Live-TV:** Senderlogos, Varianten (4K, Full HD, HD, HEVC …) beste zuerst, Programm (EPG) im Player.
  - **OK** öffnet die Senderliste, oben steht der vorherige Sender. **Hoch/runter** schaltet um.
  - Zifferntasten wählen die Sendernummer, die Taste „Vorheriger Sender“ springt zurück.
  - **Verpasst? Zurückschauen:** Bei Sendern mit Archiv beim Anbieter verpasste Sendungen der letzten Tage
    von Anfang an schauen.
- **Filme und Serien:** links/rechts 10 Sekunden (halten = schneller), OK Pause, runter in die Knöpfe
  (Zeitbalken, CC = Untertitel, Zahnrad = Tonspur). Untertitel sind anfangs aus (nur erzwungene erscheinen)
  und lassen sich ein- und ausschalten. Am Ende einer Folge: „Nächste Folge in … s“, OK startet sofort.
- **Lieblingsverein:** nächstes Spiel mit den Sendern, die es zeigen (TheSportsDB, Programm des Anbieters,
  `app-ui/broadcast.json`).
- **Flüssig und ausfallsicher:**
  - Live startet mit 2,5 s Polster, kurze Aussetzer des Anbieters bleiben unsichtbar.
  - Steht die Leitung 3 s still, wird sofort neu verbunden.
  - Fällt ein Sender aus, wechselt die App automatisch auf eine gleichwertige Variante.
  - Ohne Internet wartet sie und spielt weiter, sobald es zurück ist.
- **Schnellstart:** Die Listen des Anbieters werden auf dem Gerät gespeichert. Die App startet sofort mit dem
  letzten Stand und aktualisiert im Hintergrund (beim Start, beim Zurückkehren, alle 6 Stunden).
- **Bild:** bis 4K, Hardware-Decoder, Bildwiederholrate passend zur Sendung (50 Hz / 24 Hz, abschaltbar).

## Aufbau

- `app-ui/index.html`: die Oberfläche (läuft in der WebView der App), dazu `broadcast.json`, `icon.png`, `lib/`.
- `android/`: die Android-TV-App (Paket `com.ipflix.app`, Kotlin, media3/ExoPlayer). Der Bau kopiert `app-ui/` in die App.
- `index.html` (Wurzel): Download-Seite für GitHub Pages. Die frühere Web-Version und der Cloudflare-Worker
  sind abgeschaltet.

## Bauen

```
cd android
IPFLIX_KEY_PASS=<privates Passwort> ./build-release.sh
```

Der Signatur-Schlüssel liegt nur verschlüsselt im Repo (`android/app/ipflix-release.jks.enc`). Das Passwort
ist privat und steht nirgends im Repo. Ohne Passwort lässt sich keine Update-fähige APK bauen.

## Sicherheit und Daten

- Zugangsdaten, Verlauf und Einstellungen bleiben auf dem Gerät. Die App sendet nichts davon an Dritte und
  schließt sich von der Android-Cloud-Sicherung aus.
- Alle Anzeigetexte des Anbieters werden als Text gesetzt, nie als HTML ausgeführt; die Oberfläche hat eine
  Content-Security-Policy.

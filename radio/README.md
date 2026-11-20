# Radio DE·TR

Radio-App für Android (ab Android 8, abgestimmt auf Samsung One UI 9 / Android 17) mit deutschen und türkischen Sendern.

**Download:** `radio.apk` im Hauptordner des Repos.

## Funktionen

- **Zwei Länderlisten:** Deutschland 🇩🇪 und Türkei 🇹🇷, jeweils die beliebtesten Sender aus dem freien Verzeichnis
  radio-browser.info, mit Suche. Die Listen werden auf dem Gerät gespeichert (Start ohne Wartezeit), Ziehen nach unten
  aktualisiert. Ohne Netz beim allerersten Start gibt es eine kleine eingebaute Liste.
- **Favoriten:** Stern antippen – im Player, in der Liste, in der Benachrichtigung und im Auto.
- **Musikerkennung (Registerkarte „Erkennen“):**
  - Läuft ein Sender, wird der Song direkt aus dem Ton des Senders erkannt (kein Mikrofon nötig, klappt auch im Auto).
    Sonst über das Mikrofon (10 Sekunden).
  - Erkennung über AudD (audd.io). Ohne Schlüssel sind einige Erkennungen pro Tag frei; ein eigener Schlüssel kann
    in den Einstellungen (Zahnrad) eingetragen werden.
  - Der Treffer öffnet sofort das passende **YouTube-Video** (abschaltbar). Verlauf der erkannten Songs.
  - Schickt der Sender den Titel mit, steht „Läuft gerade laut Sender“ ganz oben – ein Tipp öffnet YouTube.
- **Auto / Bluetooth:** Wiedergabe über eine MediaSession (media3). Play/Pause, Weiter/Zurück (= nächster/vorheriger
  Sender der gewählten Liste) über Lenkradtasten und Autoradio, Sender- und Songname im Autodisplay. Drückt man im Auto
  „Play“, geht es mit dem zuletzt gehörten Sender weiter, auch wenn die App nicht offen ist.
  **Android Auto** zeigt Favoriten, Deutschland und Türkei zum Durchblättern, mit Suche und Favoriten-Knopf.
- **Oberfläche:** große einklappende Überschriften und abgerundete Kartengruppen wie in One UI, Farben aus dem
  Hintergrundbild (Material You), Hell/Dunkel automatisch, randlos, Vorhersage-Zurückgeste, Deutsch und Türkisch.

## Bauen

```
cd radio
RADIO_KEY_PASS=<privates Passwort> ./build-release.sh     # erzeugt ../radio.apk
```

Der Signatur-Schlüssel liegt nur verschlüsselt im Repo (`app/radio-release.jks.enc`). Ohne das Passwort lässt sich
keine Update-fähige APK bauen. Gebaut wird mit Android-SDK 37, AGP 9.5, Kotlin 2.4, Jetpack Compose und media3.

## Daten

Favoriten, Verlauf und Einstellungen bleiben auf dem Gerät. Nur für eine angestoßene Erkennung wird ein kurzer
Tonausschnitt an AudD geschickt; für das YouTube-Video wird die YouTube-Suche mit Interpret und Titel aufgerufen.

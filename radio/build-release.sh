#!/bin/bash
# Baut die signierte Radio-APK. Braucht das private Passwort:  RADIO_KEY_PASS=... ./build-release.sh
set -e
cd "$(dirname "$0")"
if [ -z "$RADIO_KEY_PASS" ]; then echo "RADIO_KEY_PASS fehlt (privates Passwort des Signatur-Schlüssels)."; exit 1; fi
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -in app/radio-release.jks.enc -out app/radio-private.jks -pass env:RADIO_KEY_PASS
trap 'rm -f app/radio-private.jks' EXIT
./gradlew :app:assembleRelease --no-daemon -q
cp app/build/outputs/apk/release/app-release.apk ../radio.apk
echo "Fertig: radio.apk"

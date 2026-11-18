#!/bin/bash
# Baut die signierte IPFLIX-APK. Braucht das private Passwort:  IPFLIX_KEY_PASS=... ./build-release.sh
set -e
cd "$(dirname "$0")"
if [ -z "$IPFLIX_KEY_PASS" ]; then echo "IPFLIX_KEY_PASS fehlt (privates Passwort des Signatur-Schlüssels)."; exit 1; fi
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -in app/ipflix-release.jks.enc -out app/ipflix-private.jks -pass env:IPFLIX_KEY_PASS
trap 'rm -f app/ipflix-private.jks' EXIT
./gradlew :app:assembleRelease --no-daemon -q
echo "Fertig: app/build/outputs/apk/release/app-release.apk"

#!/bin/bash
# Doppelklick im Finder startet den Heim-Proxy. Fenster offen lassen, solange du schaust.
cd "$(dirname "$0")"
if ! command -v node >/dev/null 2>&1; then
  echo "Node.js fehlt. Bitte einmalig von https://nodejs.org (LTS) installieren und diese Datei erneut öffnen."
  read -r -p "Enter zum Beenden"
  exit 1
fi
node home-proxy.mjs

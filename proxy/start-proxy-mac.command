#!/bin/bash
# Doppelklick im Finder startet den Heim-Proxy. Fenster offen lassen, solange du schaust.
cd "$(dirname "$0")"

if ! command -v brew >/dev/null 2>&1 && { ! command -v node >/dev/null 2>&1 || ! command -v cloudflared >/dev/null 2>&1; }; then
  echo "Homebrew fehlt. Installiere es von https://brew.sh und starte diese Datei erneut."
  read -r -p "Enter zum Beenden"
  exit 1
fi
command -v node >/dev/null 2>&1 || brew install node
command -v cloudflared >/dev/null 2>&1 || brew install cloudflared

echo "Starte Heim-Proxy. Dieses Fenster offen lassen, solange du schaust."
node home-proxy.mjs

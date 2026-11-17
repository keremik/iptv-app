@echo off
chcp 65001 >nul
title IPTV Heim-Proxy
cd /d "%~dp0"

where node >nul 2>nul
if errorlevel 1 (
  echo Node.js fehlt. Wird installiert ...
  winget install --id OpenJS.NodeJS.LTS -e --accept-source-agreements --accept-package-agreements
  echo.
  echo Bitte dieses Fenster schliessen und die Datei erneut doppelklicken.
  pause
  exit /b
)

where cloudflared >nul 2>nul
if errorlevel 1 (
  echo cloudflared fehlt. Wird installiert ...
  winget install --id Cloudflare.cloudflared -e --accept-source-agreements --accept-package-agreements
  echo.
  echo Bitte dieses Fenster schliessen und die Datei erneut doppelklicken.
  pause
  exit /b
)

echo Starte Heim-Proxy. Dieses Fenster offen lassen, solange du schaust.
node home-proxy.mjs
pause

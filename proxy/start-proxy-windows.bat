@echo off
chcp 65001 >nul
title IPTV Heim-Proxy
cd /d "%~dp0"

where node >nul 2>nul
if errorlevel 1 (
  echo Node.js fehlt. Bitte einmalig installieren:
  echo   https://nodejs.org  -  "LTS" herunterladen und installieren.
  echo Danach diese Datei erneut doppelklicken.
  pause
  exit /b
)

node home-proxy.mjs
pause

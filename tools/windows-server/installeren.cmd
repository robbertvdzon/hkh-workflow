@echo off
rem Start install.ps1 uit dezelfde map. Rechtsklik op dit bestand en kies "Als administrator uitvoeren".
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" %*
echo.
pause

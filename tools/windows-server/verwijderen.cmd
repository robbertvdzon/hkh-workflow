@echo off
rem Start uninstall.ps1 uit dezelfde map. Rechtsklik op dit bestand en kies "Als administrator uitvoeren".
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0uninstall.ps1" -Firewall %*
echo.
pause

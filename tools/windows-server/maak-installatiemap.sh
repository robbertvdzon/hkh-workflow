#!/usr/bin/env bash
# Stelt de installatiemap samen: alles wat nodig is om de app op een Windows-server te installeren, om mee te nemen (zie LEESMIJ.txt).
# De jar komt van de nieuwste Release op GitHub (met controle van de sha256), zodat de app daarna zichzelf kan bijwerken.
#
#   tools/windows-server/maak-installatiemap.sh              # maakt target/zcbs-workflow-install
#   tools/windows-server/maak-installatiemap.sh --laptop     # zet hem daarna ook als C:\zcbs-workflow-install op de testlaptop
#   tools/windows-server/maak-installatiemap.sh --lokale-jar # gebruikt target/hkh-workflow.jar in plaats van de Release
#
# WinSW moet in tools/windows-server/winsw/WinSW.exe staan (zie README.md). Voor --laptop: LAPTOP, LAPTOP_USER en LAPTOP_KEY zoals bij deploy-testlaptop.sh.

set -euo pipefail

hier="$(cd "$(dirname "$0")" && pwd)"
projectmap="$(cd "$hier/../.." && pwd)"
doelmap="$projectmap/target/zcbs-workflow-install"
repo="robbertvdzon/hkh-workflow"

laptop=0; lokaal=0
for a in "$@"; do
  case "$a" in
    --laptop) laptop=1 ;;
    --lokale-jar) lokaal=1 ;;
    *) echo "Onbekende optie: $a" >&2; exit 1 ;;
  esac
done

winsw="$hier/winsw/WinSW.exe"
[ -f "$winsw" ] || { echo "WinSW ontbreekt: zet het als $winsw (zie tools/windows-server/README.md)." >&2; exit 1; }

rm -rf "$doelmap"
mkdir -p "$doelmap"
for f in install.ps1 uninstall.ps1 run.ps1 hkh-workflow.xml installeren.cmd verwijderen.cmd; do cp "$hier/$f" "$doelmap/$f"; done
cp "$winsw" "$doelmap/WinSW.exe"
# Kladblok en cmd willen Windows-regeleinden.
for f in installeren.cmd verwijderen.cmd; do perl -pi -e 's/\r?\n/\r\n/' "$doelmap/$f"; done
perl -pe 's/\r?\n/\r\n/' "$hier/LEESMIJ.txt" > "$doelmap/LEESMIJ.txt"

if [ "$lokaal" = 1 ]; then
  [ -f "$projectmap/target/hkh-workflow.jar" ] || { echo "target/hkh-workflow.jar ontbreekt; bouw eerst (mvn package)." >&2; exit 1; }
  cp "$projectmap/target/hkh-workflow.jar" "$doelmap/hkh-workflow.jar"
  versie="lokale build van $(date '+%Y-%m-%d %H:%M')"
else
  basis="https://github.com/$repo/releases/latest/download"
  echo "== Jar ophalen van de nieuwste Release"
  curl -fsSL -o "$doelmap/hkh-workflow.jar" "$basis/hkh-workflow.jar"
  verwacht="$(curl -fsSL "$basis/hkh-workflow.jar.sha256" | cut -d' ' -f1)"
  echt="$(shasum -a 256 "$doelmap/hkh-workflow.jar" | cut -d' ' -f1)"
  [ "$verwacht" = "$echt" ] || { echo "De sha256 van de jar klopt niet." >&2; exit 1; }
  tag="$(curl -fsSLI -o /dev/null -w '%{url_effective}' "https://github.com/$repo/releases/latest" | sed 's#.*/##')"
  versie="$tag van GitHub, sha256 $echt"
fi
printf 'hkh-workflow.jar: %s\r\nMap samengesteld op %s\r\n' "$versie" "$(date '+%Y-%m-%d %H:%M')" > "$doelmap/VERSIE.txt"

# Windows PowerShell 5.1 leest scripts zonder BOM als ANSI: er mag alleen ASCII in staan.
if LC_ALL=C grep -n '[^[:print:][:space:]]' "$doelmap"/*.ps1 "$doelmap"/*.cmd "$doelmap/LEESMIJ.txt"; then
  echo "Er staan tekens buiten ASCII in de scripts (zie hierboven)." >&2; exit 1
fi

echo "== Klaar: $doelmap ($versie)"
ls -l "$doelmap"

if [ "$laptop" = 1 ]; then
  LAPTOP="${LAPTOP:-192.168.178.175}"; LAPTOP_USER="${LAPTOP_USER:-ik}"; LAPTOP_KEY="${LAPTOP_KEY:-$HOME/.ssh/testlaptop}"
  ssh_opts=(-i "$LAPTOP_KEY" -o ConnectTimeout=10 -o BatchMode=yes)
  echo "== Naar de testlaptop: C:\\zcbs-workflow-install"
  ssh "${ssh_opts[@]}" "$LAPTOP_USER@$LAPTOP" 'powershell -NoProfile -Command Remove-Item -Recurse -Force -ErrorAction SilentlyContinue C:\zcbs-workflow-install; exit 0'
  scp "${ssh_opts[@]}" -q -r "$doelmap" "$LAPTOP_USER@$LAPTOP:C:/zcbs-workflow-install"
  ssh "${ssh_opts[@]}" "$LAPTOP_USER@$LAPTOP" 'powershell -NoProfile -Command Get-ChildItem C:\zcbs-workflow-install'
fi

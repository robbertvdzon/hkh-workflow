#!/usr/bin/env bash
# Zet de app als Windows-service op de testlaptop (zie tools/windows-testlaptop) en start hem. Bouwt de jar, kopieert alles naar
# C:\zcbs-workflow\install\ en draait daar install.ps1 (de SSH-gebruiker is beheerder).
#
#   tools/windows-server/deploy-testlaptop.sh                    # bouwen (zonder tests), kopieren, (her)installeren en starten
#   tools/windows-server/deploy-testlaptop.sh --zonder-bouwen    # de bestaande target/hkh-workflow.jar gebruiken
#   tools/windows-server/deploy-testlaptop.sh --update           # alleen een nieuwe jar in updates\ zetten (zoals de updater straks doet)
#   tools/windows-server/deploy-testlaptop.sh --status           # service, versie en laatste regels van update.log
#   tools/windows-server/deploy-testlaptop.sh --verwijderen      # de service weghalen
#
# WinSW moet in tools/windows-server/winsw/WinSW.exe staan (zie README.md). Omgevingsvariabelen zoals bij deploy-app.sh:
# LAPTOP (192.168.178.175), LAPTOP_USER (ik), LAPTOP_KEY (~/.ssh/testlaptop), LAPTOP_MAP (C:\zcbs-workflow), LAPTOP_POORT (9000).

set -euo pipefail

LAPTOP="${LAPTOP:-192.168.178.175}"
LAPTOP_USER="${LAPTOP_USER:-ik}"
LAPTOP_KEY="${LAPTOP_KEY:-$HOME/.ssh/testlaptop}"
MAP="${LAPTOP_MAP:-C:\\zcbs-workflow}"
POORT="${LAPTOP_POORT:-9000}"

hier="$(cd "$(dirname "$0")" && pwd)"
projectmap="$hier/../.."
ssh_opts=(-i "$LAPTOP_KEY" -o ConnectTimeout=10 -o BatchMode=yes)
doel="$LAPTOP_USER@$LAPTOP"
installmap="$MAP\\install"

laptop() { ssh "${ssh_opts[@]}" "$doel" "$@"; }
scp_pad() { echo "$1" | sed 's#\\#/#g'; }
ps() { laptop "powershell -NoProfile -ExecutionPolicy Bypass -File $1"; }

# Een .ps1 op de laptop zetten en uitvoeren (dubbele aanhalingstekens gaan via SSH verloren, dus nooit inline).
laptop_script() {
  local tmp; tmp="$(mktemp -t hkhsvc).ps1"; cat > "$tmp"
  scp "${ssh_opts[@]}" -q "$tmp" "$doel:$(scp_pad "$installmap\\tmp-opdracht.ps1")"
  rm -f "$tmp"
  ps "$installmap\\tmp-opdracht.ps1"
}

laptop 'exit 0' || { echo "Geen SSH-verbinding met $doel" >&2; exit 1; }
laptop "powershell -NoProfile -Command \"New-Item -ItemType Directory -Force '$installmap' | Out-Null\""

case "${1:-}" in
  --status)
    laptop_script <<EOF
\$ProgressPreference = 'SilentlyContinue'
\$s = Get-Service hkh-workflow -ErrorAction SilentlyContinue
if (\$s) { 'Service: ' + \$s.Status + ' (' + \$s.StartType + ')' } else { 'Service: niet geinstalleerd' }
function Inhoud(\$pad) { \$c = (Invoke-WebRequest -Uri ('http://localhost:$POORT' + \$pad) -UseBasicParsing -TimeoutSec 3).Content; if (\$c -is [byte[]]) { [Text.Encoding]::UTF8.GetString(\$c) } else { \$c } }
try { 'Health: ' + (Inhoud '/actuator/health') } catch { 'Health: geen antwoord' }
try { 'Info: ' + (Inhoud '/actuator/info') } catch { }
'--- update.log'
Get-Content '$MAP\\logs\\update.log' -Tail 25 -ErrorAction SilentlyContinue
'--- app-fout.log'
Get-Content '$MAP\\logs\\app-fout.log' -Tail 8 -ErrorAction SilentlyContinue
EOF
    exit 0 ;;
  --verwijderen)
    scp "${ssh_opts[@]}" -q "$hier/uninstall.ps1" "$doel:$(scp_pad "$installmap\\uninstall.ps1")"
    ps "$installmap\\uninstall.ps1 -Map $MAP -Poort $POORT"
    exit 0 ;;
esac

bouwen=1; update=0
while [ "${1:-}" = "--zonder-bouwen" ] || [ "${1:-}" = "--update" ]; do
  [ "$1" = "--zonder-bouwen" ] && bouwen=0
  [ "$1" = "--update" ] && update=1
  shift
done
jar="${1:-$projectmap/target/hkh-workflow.jar}"

if [ "$bouwen" = 1 ] && [ -z "${1:-}" ]; then
  echo "== Bouwen (mvn -q package -DskipTests)"
  (cd "$projectmap" && mvn -q package -DskipTests) || { echo "Bouwen mislukt." >&2; exit 1; }
fi
[ -f "$jar" ] || { echo "Jar niet gevonden: $jar" >&2; exit 1; }

if [ "$update" = 1 ]; then
  # Eerst onder een tijdelijke naam kopieren en dan hernoemen, zodat run.ps1 nooit een half bestand ziet (zo doet de updater het ook).
  sha="$(shasum -a 256 "$jar" | cut -d' ' -f1)"
  echo "== Update: $(basename "$jar") ($(du -h "$jar" | cut -f1)) naar updates\\"
  scp "${ssh_opts[@]}" -q "$jar" "$doel:$(scp_pad "$MAP\\updates\\hkh-workflow.jar.part")"
  laptop_script <<EOF
Set-Content -Path '$MAP\\updates\\hkh-workflow.jar.sha256' -Value '$sha  hkh-workflow.jar' -Encoding ASCII
Move-Item -Force '$MAP\\updates\\hkh-workflow.jar.part' '$MAP\\updates\\hkh-workflow.jar'
'Klaargezet. De service pakt hem op bij de volgende start van java (stop java of herstart de service).'
EOF
  exit 0
fi

winsw="$hier/winsw/WinSW.exe"
[ -f "$winsw" ] || { echo "WinSW ontbreekt: zet het als $winsw (zie tools/windows-server/README.md)." >&2; exit 1; }

echo "== Kopieren naar $installmap"
for f in run.ps1 hkh-workflow.xml install.ps1 uninstall.ps1; do
  scp "${ssh_opts[@]}" -q "$hier/$f" "$doel:$(scp_pad "$installmap\$f")"
done
scp "${ssh_opts[@]}" -q "$winsw" "$doel:$(scp_pad "$installmap\\WinSW.exe")"
echo "   jar: $(basename "$jar") ($(du -h "$jar" | cut -f1))"
scp "${ssh_opts[@]}" -q "$jar" "$doel:$(scp_pad "$installmap\\hkh-workflow.jar")"

echo "== Installeren en starten"
ps "$installmap\\install.ps1 -WinSw $installmap\\WinSW.exe -Jar $installmap\\hkh-workflow.jar -Map $MAP -Poort $POORT"
echo
echo "Klaar: http://$LAPTOP:$POORT/   (status: $0 --status)"

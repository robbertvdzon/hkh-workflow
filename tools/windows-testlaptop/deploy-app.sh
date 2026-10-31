#!/usr/bin/env bash
# Bouwt de hkh-workflow.jar (mvn clean package) en zet hem op de Windows-testlaptop. De app wordt daar NIET gestart; een
# draaiende versie wordt wel gestopt, want Windows houdt de jar vergrendeld zolang hij draait.
#
#   tools/windows-testlaptop/deploy-app.sh                    # mvn clean package, de jar uploaden, niet starten
#   tools/windows-testlaptop/deploy-app.sh --starten          # ook de app op de laptop starten
#   tools/windows-testlaptop/deploy-app.sh --zonder-tests     # mvn clean package -DskipTests (sneller)
#   tools/windows-testlaptop/deploy-app.sh --zonder-bouwen    # de bestaande target/hkh-workflow.jar uploaden
#   tools/windows-testlaptop/deploy-app.sh pad/naar/app.jar   # een andere jar uploaden (wordt niet gebouwd)
#   tools/windows-testlaptop/deploy-app.sh --stop             # alleen de app op de laptop stoppen
#   tools/windows-testlaptop/deploy-app.sh --log              # laatste regels van de app-log tonen
#
# De opties --starten, --zonder-tests en --zonder-bouwen kunnen worden gecombineerd.
# Omgevingsvariabelen: LAPTOP (standaard 192.168.178.175), LAPTOP_USER (ik), LAPTOP_KEY (~/.ssh/testlaptop),
# LAPTOP_MAP (C:\zcbs-workflow), LAPTOP_POORT (9000), MAC_IP (het IP van deze Mac, standaard van en0).
# Het bouwen gebeurt in de projectmap, dus met alles wat er op dit moment in de werkmap staat.

set -euo pipefail

LAPTOP="${LAPTOP:-192.168.178.175}"
LAPTOP_USER="${LAPTOP_USER:-ik}"
LAPTOP_KEY="${LAPTOP_KEY:-$HOME/.ssh/testlaptop}"
MAP="${LAPTOP_MAP:-C:\\zcbs-workflow}"
POORT="${LAPTOP_POORT:-9000}"
MAC_IP="${MAC_IP:-$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || true)}"

hier="$(cd "$(dirname "$0")" && pwd)"
ssh_opts=(-i "$LAPTOP_KEY" -o ConnectTimeout=10 -o BatchMode=yes)
doel="$LAPTOP_USER@$LAPTOP"
ps_script="$MAP\\app-start.ps1"

laptop() { ssh "${ssh_opts[@]}" "$doel" "$@"; }
# Windows-pad met backslashes naar het formaat dat scp verwacht
scp_pad() { echo "$1" | sed 's#\\#/#g'; }

case "${1:-}" in
  --log)
    laptop "powershell -NoProfile -Command \"Get-Content '$MAP\\app.log' -Tail 40; Get-Content '$MAP\\app.err' -Tail 20\""
    exit 0 ;;
  --stop)
    scp "${ssh_opts[@]}" -q "$hier/app-start.ps1" "$doel:$(scp_pad "$ps_script")"
    laptop "powershell -NoProfile -ExecutionPolicy Bypass -File $ps_script -Stop -Map $MAP"
    exit 0 ;;
esac

projectmap="$hier/../.."
bouwen=1
tests=1
starten=0
while [ "${1:-}" = "--zonder-bouwen" ] || [ "${1:-}" = "--zonder-tests" ] || [ "${1:-}" = "--starten" ]; do
  [ "$1" = "--zonder-bouwen" ] && bouwen=0
  [ "$1" = "--zonder-tests" ] && tests=0
  [ "$1" = "--starten" ] && starten=1
  shift
done
if [ -n "${1:-}" ]; then jar="$1"; bouwen=0; else jar="$projectmap/target/hkh-workflow.jar"; fi

laptop 'exit 0' || { echo "Geen SSH-verbinding met $doel" >&2; exit 1; }

if [ "$bouwen" = 1 ]; then
  mvn_opties=(-q clean package)
  [ "$tests" = 0 ] && mvn_opties+=(-DskipTests)
  echo "== Bouwen (mvn ${mvn_opties[*]})"
  (cd "$projectmap" && mvn "${mvn_opties[@]}") || { echo "Bouwen mislukt, niets gekopieerd." >&2; exit 1; }
fi
[ -f "$jar" ] || { echo "Jar niet gevonden: $jar" >&2; exit 1; }

echo "== Stoppen (een draaiende app houdt de jar vergrendeld)"
scp "${ssh_opts[@]}" -q "$hier/app-start.ps1" "$doel:$(scp_pad "$ps_script")"
laptop "powershell -NoProfile -ExecutionPolicy Bypass -File $ps_script -Stop -Map $MAP"

echo "== Kopiëren: $(basename "$jar") ($(du -h "$jar" | cut -f1))"
scp "${ssh_opts[@]}" -q "$jar" "$doel:$(scp_pad "$MAP\\hkh-workflow.jar")"

# De app gebruikt als "test-ZCBS" standaard localhost. Op de laptop staat die op de Mac: een eigen configbestand naast de jar
# (config/application.properties; een eventuele application.properties van jou blijft ongemoeid).
if [ -n "$MAC_IP" ]; then
  echo "== Test-ZCBS van de app: $MAC_IP"
  cfg="$(mktemp)"
  printf 'hkh.workflow.test-zcbs-url=http://%s:8081\nhkh.workflow.test-ftp-host=%s\nhkh.workflow.test-ftp-port=21\n' "$MAC_IP" "$MAC_IP" > "$cfg"
  laptop "powershell -NoProfile -Command \"New-Item -ItemType Directory -Force '$MAP\\config' | Out-Null\""
  scp "${ssh_opts[@]}" -q "$cfg" "$doel:$(scp_pad "$MAP\\config\\application.properties")"
  rm -f "$cfg"
else
  echo "== Let op: het IP van de Mac is onbekend (zet MAC_IP); de app gebruikt dan localhost als test-ZCBS" >&2
fi

if [ "$starten" = 0 ]; then
  echo
  echo "Klaar: jar en config staan op de laptop in $MAP. De app draait niet; start hem zelf, of draai dit script met --starten."
  exit 0
fi

echo "== Starten"
laptop "powershell -NoProfile -ExecutionPolicy Bypass -File $ps_script -Poort $POORT -Map $MAP"

echo
echo "Klaar: http://$LAPTOP:$POORT/"

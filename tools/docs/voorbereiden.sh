#!/bin/bash
# Zet de testomgeving voor de screenshots klaar en start de app daarin op poort 9010 (APP_PORT). Alleen lokaal; er gaat niets naar het echte archief.
#
#   tools/docs/voorbereiden.sh [werkmap] [jar]     # standaard: /private/tmp/hkh-docs en de nieuwste release-jar van GitHub (gh release download)
#   tools/docs/voorbereiden.sh --stop [werkmap]    # de app stoppen
#
# Vereist: de test-ZCBS in Docker (tools/docker/zcbs, poort 8081 en ftp 21), Perl, `gh` (alleen voor het downloaden) en Java 21.
# De mapnamen e2e2 en e2e3 liggen vast: schermen.cjs vervangt ze op het scherm door K:\ en \\server\werkmap.
set -euo pipefail
cd "$(dirname "$0")/../.."
REPO="$PWD"
PORT="${APP_PORT:-9010}"

if [ "${1:-}" = "--stop" ]; then
  W="${2:-/private/tmp/hkh-docs}"
  [ -f "$W/app.pid" ] && kill "$(cat "$W/app.pid")" 2>/dev/null || true
  rm -f "$W/app.pid"; echo "gestopt"; exit 0
fi

W="${1:-/private/tmp/hkh-docs}"
JAR="${2:-}"
curl -s -m 5 -o /dev/null http://localhost:8081/ || { echo "De test-ZCBS op localhost:8081 is niet bereikbaar (tools/docker/zcbs)." >&2; exit 1; }
[ -f "$W/app.pid" ] && kill "$(cat "$W/app.pid")" 2>/dev/null || true
rm -rf "$W/e2e2" "$W/e2e3" "$W/logs" "$W/settings.json" "$W/verwerkt.tsv" "$W/hkh-workflow-bewerkt.tsv" "$W/hkh-workflow-inloggen.key"
mkdir -p "$W/e2e2/k/backup/100pct" "$W/e2e2/k/backup/pdf" "$W/e2e2/k/resten" "$W/e2e2/invoer" "$W/e2e3/scans/Kerkplein" "$W/e2e3/scans/Schoolfoto's"

# De nep-programma's, met echte kopieen van MockImage.pm (de app kopieert de programmamap).
cp -RL tools/mock/pdf2zcbs "$W/e2e2/pdf2zcbs"
cp -RL tools/mock/images-werk "$W/e2e2/images-werk"
cp tools/mock/images-werk/images-mock.ini.example "$W/e2e2/images-werk/images-mock.ini"
rm -rf "$W/e2e2/images-werk/images/"* 2>/dev/null || true

# Scans: de kleine testscans van tools/localfileserver onder andere namen.
S="tools/localfileserver/scans_jelly"
SF="$S/Schoolfoto's 1950-1960"
FIRST=$(ls "$SF"/*.pdf | head -1); SECOND=$(ls "$SF"/*.pdf | sed -n 2p); THIRD=$(ls "$SF"/*.pdf | sed -n 3p)
cp "$FIRST" "$W/e2e3/scans/Kerkplein/kerkplein_1950.pdf"; cp "$SECOND" "$W/e2e3/scans/Kerkplein/kerkplein_1962.pdf"
cp "$THIRD" "$W/e2e3/scans/Kerkplein/raadhuis_voorgevel.pdf"; cp "$S/Dorpsfeest 1975/dorpsfeest_foto_001.jpg" "$W/e2e3/scans/Kerkplein/markt_1935.jpg"
cp "$FIRST" "$W/e2e3/scans/Schoolfoto's/school_001.pdf"; cp "$SECOND" "$W/e2e3/scans/Schoolfoto's/school_002.pdf"; cp "$S/Dorpsfeest 1975/dorpsfeest_foto_003.jpg" "$W/e2e3/scans/Schoolfoto's/klas_foto.jpg"

cat > "$W/settings.json" <<JSON
{
  "testFolder": "$W/e2e2/invoer",
  "imagesExe": "$W/e2e2/images-werk/images.pl",
  "pdf2zcbsExe": "$W/e2e2/pdf2zcbs/pdf2zcbs.pl",
  "perlioUnbuffered": true,
  "collections": { "archief": { "imagesResolution": "6", "backup100pctFolder": "$W/e2e2/k/backup/100pct", "backupPdfFolder": "$W/e2e2/k/backup/pdf" } },
  "leftoversFolder": "$W/e2e2/k/resten",
  "useTestZcbs": true,
  "sourceFolders": "Jelly=$W/e2e3/scans",
  "ftpPassword": "hkhtest"
}
JSON

if [ -z "$JAR" ]; then
  mkdir -p "$W/jar"
  TAG="$(gh release list --repo robbertvdzon/hkh-workflow --limit 1 --json tagName --jq '.[0].tagName')"
  gh release download "$TAG" --repo robbertvdzon/hkh-workflow --pattern hkh-workflow.jar --dir "$W/jar" --clobber
  JAR="$W/jar/hkh-workflow.jar"; echo "release $TAG"
fi
cd "$W"
HKH_WORKFLOW_PORT="$PORT" HKH_WORKFLOW_ADMIN_PASSWORD=abc HKH_WORKFLOW_TEST_ADMIN_LOG_URL=http://127.0.0.1:9 HKH_WORKFLOW_SETTINGS_FILE="$W/settings.json" \
  HKH_WORKFLOW_PROCESSED_LOG="$W/verwerkt.tsv" HKH_WORKFLOW_LOG_DIR="$W/logs" nohup java -jar "$JAR" > "$W/app.log" 2>&1 &
echo $! > "$W/app.pid"
for i in $(seq 1 60); do curl -s -m 2 -o /dev/null -w '%{http_code}' "http://localhost:$PORT/actuator/health" | grep -q 200 && { echo "app draait op poort $PORT"; exit 0; }; sleep 2; done
echo "de app startte niet; zie $W/app.log" >&2; exit 1

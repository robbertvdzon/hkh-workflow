#!/bin/bash
# Maakt van de handleidingen in manual/ pdf's in manual/pdf/. Vereist: Java 21, Google Chrome en de commonmark-jars in ~/.m2
# (die staan er door de build van hkh). Gebruik: tools/docs/bouw-pdf.sh [bestand.md ...]  (zonder argumenten: alle handleidingen)
set -euo pipefail
cd "$(dirname "$0")/../.."
M2="${M2_REPO:-$HOME/.m2/repository}"
CP="$M2/org/commonmark/commonmark/0.24.0/commonmark-0.24.0.jar:$M2/org/commonmark/commonmark-ext-gfm-tables/0.24.0/commonmark-ext-gfm-tables-0.24.0.jar"
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
[ -f "${CP%%:*}" ] || { echo "commonmark ontbreekt in $M2; draai eerst een mvn-build" >&2; exit 1; }
[ -x "$CHROME" ] || { echo "Chrome niet gevonden: zet CHROME op het programma" >&2; exit 1; }
OUT=manual/pdf
mkdir -p "$OUT"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
FILES=("$@")
[ ${#FILES[@]} -gt 0 ] || FILES=(manual/quickstart-invoeren.md manual/quickstart-records-aanpassen.md manual/invoeren.md manual/records-aanpassen.md manual/instellingen.md )
for md in "${FILES[@]}"; do
  name="$(basename "$md" .md)"
  # De html moet naast de plaatjes staan (manual/afbeeldingen) zodat de verwijzingen kloppen.
  html="manual/.${name}.pdf-bouw.html"
  java -cp "$CP" tools/docs/Md.java "$md" "$html" tools/docs/pdf.css
  "$CHROME" --headless=new --disable-gpu --no-pdf-header-footer --print-to-pdf="$OUT/$name.pdf" "file://$PWD/$html" >/dev/null 2>&1
  rm -f "$html"
  echo "pdf: $OUT/$name.pdf ($(du -h "$OUT/$name.pdf" | cut -f1))"
done

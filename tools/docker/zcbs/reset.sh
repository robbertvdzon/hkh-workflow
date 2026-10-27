#!/bin/bash
# Zet de map files/ terug naar de beginstand (een kopie van initial-files/) en start de containers opnieuw.
set -euo pipefail
cd "$(dirname "$0")"
[ -d initial-files ] || { echo "De map initial-files ontbreekt"; exit 1; }
docker compose down
rm -rf files
cp -a initial-files files
chmod -R a+rwX files
docker compose up -d
echo "Klaar: files/ staat weer in de beginstand."

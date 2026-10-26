#!/bin/bash
# Zet de map files/ terug naar de beginstand (files.initial.tgz) en start de containers opnieuw.
set -euo pipefail
cd "$(dirname "$0")"
[ -f files.initial.tgz ] || { echo "files.initial.tgz ontbreekt"; exit 1; }
docker compose down
rm -rf files
tar -xzf files.initial.tgz
chmod -R a+rwX files
docker compose up -d
echo "Klaar: files/ staat weer in de beginstand."

#!/bin/bash
# Zet files/ terug naar de beginstand (initial-files/ + de aanpassingen uit adapt-files.sh) en start de container opnieuw.
set -euo pipefail
cd "$(dirname "$0")"
docker compose down
./adapt-files.sh
docker compose up -d
echo "Klaar: files/ staat weer in de beginstand."

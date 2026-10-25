#!/bin/bash
# Eenmalig: zet de benodigde serverbestanden uit docs/server in server-files/ (gitignored, los van docs/server).
# Daarna leest prepare.sh alleen nog uit server-files/. Je kunt server-files/ ook zelf vullen met een verse download.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
src="$here/../../docs/server"
dst="$here/server-files"
[ -f "$src/cgi-bin/objecten.pl" ] || { echo "Geen $src/cgi-bin/objecten.pl"; exit 1; }
mkdir -p "$dst/cgi-bin/misc" "$dst/cgi-bin/archief/misc" "$dst/cgi-bin/archief/100pct" "$dst/archief"
cp "$src/cgi-bin/archief.pl" "$src/cgi-bin/archief2.pl" "$src/cgi-bin/objecten.pl" "$dst/cgi-bin/"
for f in config.pm materialen.pm techniek.pm httpbl.pm replace.txt; do cp "$src/cgi-bin/misc/$f" "$dst/cgi-bin/misc/"; done
cp "$src/zcbs_frontend/cgi-lib.pl" "$dst/cgi-bin/misc/cgi-lib.pl"
for f in malware.lst whitelist.txt; do
  if [ -f "$src/cgi-bin/$f" ]; then cp "$src/cgi-bin/$f" "$dst/cgi-bin/misc/"; elif [ -f "$src/cgi-bin/misc/$f" ]; then cp "$src/cgi-bin/misc/$f" "$dst/cgi-bin/misc/"; fi
done
cp "$src/cgi-bin/archief/archief.txt" "$src/cgi-bin/archief/updates.txt" "$dst/cgi-bin/archief/"
cp "$src/cgi-bin/archief/misc/cfg-cbs.pl" "$src/cgi-bin/archief/misc/layout.html" "$src/cgi-bin/archief/misc/info-beheer.html" "$dst/cgi-bin/archief/misc/"
cp "$src/cgi-bin/archief/100pct/"* "$dst/cgi-bin/archief/100pct/" 2>/dev/null || true
for d in large small ocr pdf; do mkdir -p "$dst/archief/$d"; cp "$src/archief/$d/"* "$dst/archief/$d/" 2>/dev/null || true; done
echo "server-files/ staat klaar ($(du -sh "$dst" | cut -f1))."

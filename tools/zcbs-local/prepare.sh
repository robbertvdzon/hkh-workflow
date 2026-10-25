#!/bin/bash
# Bouwt de werkmap run/ uit de gedownloade serverbestanden (docs/server) en onze eigen testinstellingen.
# De originelen in docs/server worden niet aangepast. Draai dit opnieuw om weer met schone data te beginnen.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
src="$here/../../docs/server"
run="$here/run"

[ -f "$src/cgi-bin/objecten.pl" ] || { echo "Geen $src/cgi-bin/objecten.pl: zie docs/server/README.md"; exit 1; }
docker compose -f "$here/docker-compose.yml" down >/dev/null 2>&1 || true
rm -rf "$run"
mkdir -p "$run/cgi-bin/misc" "$run/cgi-bin/archief/misc" "$run/archief"

# Code
cp "$src/cgi-bin/archief.pl" "$src/cgi-bin/archief2.pl" "$src/cgi-bin/objecten.pl" "$run/cgi-bin/"
for f in config.pm materialen.pm techniek.pm httpbl.pm replace.txt; do cp "$src/cgi-bin/misc/$f" "$run/cgi-bin/misc/"; done
cp "$src/zcbs_frontend/cgi-lib.pl" "$run/cgi-bin/misc/cgi-lib.pl"
cp "$src/cgi-bin/malware.lst" "$src/cgi-bin/whitelist.txt" "$run/cgi-bin/misc/" 2>/dev/null || true

# Onze eigen testbestanden
cp "$here/overrides/sendmail.pm" "$run/cgi-bin/misc/sendmail.pm"
cp "$here/overrides/permit.lst" "$run/cgi-bin/misc/permit.lst"
: > "$run/cgi-bin/archief/guest.lst"

# Data en instellingen van archief; geen e-mailadressen (dan verstuurt ZCBS niets)
cp "$src/cgi-bin/archief/archief.txt" "$src/cgi-bin/archief/updates.txt" "$run/cgi-bin/archief/"
cp "$src/cgi-bin/archief/misc/layout.html" "$src/cgi-bin/archief/misc/info-beheer.html" "$run/cgi-bin/archief/misc/"
sed -E "s/^(\\\$PERMIT *= *)'[^']*'/\\1'testpermit'/; s/^(\\\$TEL_BB *= *)'[^']*'/\\1''/" \
  "$src/cgi-bin/archief/misc/cfg-cbs.pl" > "$run/cgi-bin/archief/misc/cfg-cbs.pl"
for d in addenda backup1 databases forms uitgeleend 100pct 100pct-open 100pct-closed; do mkdir -p "$run/cgi-bin/archief/$d"; done
cp "$src/cgi-bin/archief/100pct/"* "$run/cgi-bin/archief/100pct/" 2>/dev/null || true

# Publieke bestanden: een paar voorbeelden, en een lege logmap (geen echte logs)
for d in large small ocr pdf; do mkdir -p "$run/archief/$d"; cp "$src/archief/$d/"* "$run/archief/$d/" 2>/dev/null || true; done
mkdir -p "$run/archief/logging/extern" "$run/archief/download" "$run/archief/misc"

# Alle e-mailadressen in code en config worden xxx@x.nl. De nep-sendmail verstuurt toch niets; de mails komen in run/mailbox.txt.
find "$run/cgi-bin" -type f \( -name '*.pl' -o -name '*.pm' \) ! -name 'sendmail.pm' -print0 | \
  xargs -0 perl -pi -e 's/[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}/xxx\@x.nl/g'

chmod -R a+rwX "$run"
chmod a+x "$run"/cgi-bin/*.pl
echo "run/ staat klaar. Start met: docker compose up --build -d"

#!/bin/bash
# Maakt files/ (de werkmap) uit initial-files/ (de originelen) en past hem aan zoals in het README staat onder "Aanpassen".
set -euo pipefail
cd "$(dirname "$0")"
[ -d initial-files/cgi-bin ] || { echo "De map initial-files ontbreekt of is leeg (zie README)"; exit 1; }
rm -rf files
cp -a initial-files files
rm -f files/SHA256SUMS files/HERKOMST.txt

# 1. de nep-bestanden uit stubs/
cp stubs/permit.lst stubs/sendmail.pm files/cgi-bin/misc/

# 2. de configuratie van archief: geen echt wachtwoord of telefoonnummer
perl -pi -e "s/^(\\\$TEL_BB\\s*=\\s*)'[^']*'/\$1''/; s/^(\\\$PERMIT\\s*=\\s*)'[^']*'/\$1'testpermit'/" files/cgi-bin/archief/misc/cfg-cbs.pl

# 3. rechten: de container draait als een andere gebruiker dan jij
chmod -R a+rwX files
chmod a+x files/cgi-bin/*.pl
echo "files/ staat klaar."

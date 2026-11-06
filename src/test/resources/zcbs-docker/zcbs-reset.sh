#!/bin/sh
# Zet de ZCBS terug in de beginstand: alles in public_html wordt vervangen door de kopie uit /baseline

set -e
PH=/home/hkhadmin/domains/historischekringheemskerk.nl/public_html
mkdir -p "$PH"
find "$PH" -mindepth 1 -delete
cp -a /baseline/. "$PH"/
chmod -R a+rwX "$PH"
chmod a+x "$PH"/cgi-bin/*.pl

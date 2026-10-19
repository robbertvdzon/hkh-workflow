#!/bin/sh
# Stelt de vragen van images.exe en schrijft de gegeven antwoorden in answers.txt, om te controleren wat de app antwoordt.
ask() { printf '%s\n' "$1"; read a; echo "$2=$a" >> answers.txt; }
rm -f answers.txt
ask '> Type ENTER/RETURN indien OK -- of kies A, B of C' type
ask 'Kies 1,2,3 (objecten,boeken,etc), of kies 6,7,8 (foto s,etc)' resolutie
ask '> UPLOADEN aangemaakte images in small/large/100pct naar webserver? [J/N]' upload
if [ "$(grep -c '^upload=j' answers.txt)" = "1" ]; then
  ask '> Nummer, naam of plaats instelling?' instelling
  ask '> FTP wachtwoord van uw webserver?' wachtwoord
  ask '> Kies de ZCBS-toepassing (= tevens naam van de uploadmap op de webserver):' collectie
  ask '> Oude images (met dezelfde naam) in small/large/100pct op webserver OVERSCHRIJVEN? [J/N]' overschrijven
fi
ask '> AANMAKEN en UPLOADEN van de bijbehorende 2 nieuwe lege DATABASE RECORDS? [J/N]' records
ask '> VERWIJDEREN (tijdelijk) aangemaakte mappen [small], [large] en [100pct]? [J/N]' verwijdermappen
ask '> VERWIJDEREN inhoud van de map [images] met bron images? [J/N]' verwijderbron
mkdir -p small large 100pct
for f in images/*.jpg; do
  [ -e "$f" ] || continue
  n="$(basename "$f")"
  cp "$f" "small/$n"; cp "$f" "large/$n"; cp "$f" "100pct/$n"
done

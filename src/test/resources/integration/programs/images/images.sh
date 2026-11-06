#!/bin/sh
# Neemt in de integratietests de plaats in van images.exe: stelt dezelfde vragen, maakt small, large en 100pct, uploadt die met ftp naar de ZCBS in
# de container en zet de nieuwe records in updates.txt (ophalen, regels erbij, terugzetten), zoals het echte programma. De server staat vast: @FTP_HOST@.
ask() { printf '%s ' "$1"; read -r answer; echo "$2=$answer" >> antwoorden.txt; eval "$2=\$answer"; }
SERVER="ftp://@FTP_HOST@:@FTP_PORT@"
USER_NAME='ZCBS@historischekringheemskerk.nl'
rm -f antwoorden.txt
ask '> Type ENTER/RETURN indien OK -- of kies A, B of C' type
ask 'Kies 1,2,3 (objecten,boeken,etc), of kies 6,7,8 (foto s,etc)' resolutie
mkdir -p small large 100pct
aantal=0
for f in images/*.jpg; do
  [ -e "$f" ] || continue
  n="$(basename "$f")"
  cp "$f" "small/$n"; cp "$f" "large/$n"; cp "$f" "100pct/$n"
  aantal=$((aantal + 1))
done
ask '> UPLOADEN aangemaakte images in small/large/100pct naar webserver? [J/N]' upload
if [ "$upload" = "j" ]; then
  ask '> Nummer, naam of plaats instelling?' instelling
  ask '> FTP wachtwoord van uw webserver?' wachtwoord
  ask '> Kies de ZCBS-toepassing (= tevens naam van de uploadmap op de webserver):' collectie
  ask '> Oude images (met dezelfde naam) in small/large/100pct op webserver OVERSCHRIJVEN? [J/N]' overschrijven
  put() { curl -sS --disable-epsv -u "$USER_NAME:$wachtwoord" -T "$1" "$SERVER/$2" || { echo "FTP mislukt: $2"; exit 1; }; }
  for f in small/*.jpg; do
    [ -e "$f" ] || continue
    n="$(basename "$f")"
    put "small/$n" "public_html/$collectie/small/$n"
    put "large/$n" "public_html/$collectie/large/$n"
    put "100pct/$n" "public_html/cgi-bin/$collectie/100pct/$n"
  done
fi
ask "> AANMAKEN en UPLOADEN van de bijbehorende $aantal nieuwe lege DATABASE RECORDS? [J/N]" records
if [ "$records" = "j" ]; then
  curl -sS --disable-epsv -u "$USER_NAME:$wachtwoord" -o updates.txt "$SERVER/public_html/cgi-bin/$collectie/updates.txt" || { echo "FTP mislukt: updates.txt"; exit 1; }
  dag="$(date +%y%m%d)"; tijd="$(date +%H:%M)"
  for f in small/*.jpg; do
    [ -e "$f" ] || continue
    n="$(basename "$f" .jpg)"
    printf 'HKH\t997\t%s\t\t\tEen nog niet beschreven record (%s)\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t0\t\t\t\t\t\t\t\t\t\t\t%s-%s|system\t\t%s-%s|system\n' "$n" "$dag" "$dag" "$tijd" "$dag" "$tijd" >> updates.txt
  done
  curl -sS --disable-epsv -u "$USER_NAME:$wachtwoord" -T updates.txt "$SERVER/public_html/cgi-bin/$collectie/updates.txt" || { echo "FTP mislukt: updates.txt"; exit 1; }
fi
ask '> VERWIJDEREN (tijdelijk) aangemaakte mappen [small], [large] en [100pct]? [J/N]' verwijdermappen
ask '> VERWIJDEREN inhoud van de map [images] met bron images? [J/N]' verwijderbron
ask '> VERWIJDEREN (tijdelijk) PC-bestand met nieuwe database records? [J/N]' verwijderdb
rm -f updates.txt
ask 'Geef een Return/Enter om dit DOS-window te verlaten' einde

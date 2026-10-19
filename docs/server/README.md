# Bestanden van de ZCBS-server (lokaal, niet in git)

Alles in deze map behalve dit README staat in `.gitignore`. Zet hier de bestanden die je met FTP van de
ZCBS-server en van de Windows-pc bij HKH ophaalt, zodat een agent ze kan bekijken zonder dat ze ooit
gepusht worden.

## Ophalen

| Bestand op de server / pc | Hier als | Waarom |
|---|---|---|
| `/cgi-bin/archief.pl`, `/cgi-bin/archief2.pl` | `archief.pl`, `archief2.pl` | Startscripts: type (`$ZCBS=…`) en hoe de naam wordt doorgegeven |
| `/cgi-bin/archief/misc/cfg-cbs.pl`, `layout.html` | `archief/cfg-cbs.pl`, `archief/layout.html` | Config: `$PDF_VIEWER`, `$OCR_MAP`, `$NODB`, `$INTERNET`, `$EMAIL_TO`, veldnamen |
| `/cgi-bin/beeldbank.pl`, `/cgi-bin/beeldbank/misc/cfg-cbs.pl` | `beeldbank.pl`, `beeldbank/cfg-cbs.pl` | Zelfde voor het fototype (optioneel) |
| Eerste 3 regels van `/cgi-bin/archief/archief.txt` en `updates.txt` | `archief/archief-head.txt`, `archief/updates-head.txt` | Kopregel met veldnamen; hoe een door images.exe gemaakt record eruitziet |
| Maplijst van `/cgi-bin`, `/cgi-bin/archief/`, `/public_html/archief/` | `mappen.txt` | Hoe de server er nu uitziet (de documentatie is van 2017) |
| Windows-pc: `images.ini`, `pdf2zcbs.ini`, laatste `log-datum-tijd.txt` van images.exe | `pc/…` | Vaste antwoorden, FTP-dialoog met hostnaam |

## Niet hierheen kopiëren

- `objecten.pl`: programmacode van de auteur, niet nodig.
- `/cgi-bin/misc/permit.lst`: gebruikersnamen en wachtwoorden. Noteer hooguit één regel geanonimiseerd.
- Wachtwoorden in `images.ini` en in het images.exe-log: regel weghalen vóór het kopiëren. Deze map wordt niet
  gepusht, maar liever niet eens op een laptop.

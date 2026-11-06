# Nagebouwde ZCBS voor de integratietests

Een kleine ZCBS in Docker: Apache met een eigen Perl-script (`public_html/cgi-bin/archief.pl`) en ftp (vsftpd). Het script is eigen code en bevat niets van de echte ZCBS; het doet alleen na wat de app van buitenaf ziet.

- `public_html/` is de beginstand: een leeg archief (`archief.txt`, alleen de kopregel), **2 testitems** (99003 en 99067, met jpg's, tekst en een pdf) in `updates.txt`, en de testgebruikers in `cgi-bin/misc/permit.lst`. De bestanden hebben dezelfde indeling en plek als bij de echte ZCBS.
- Het image bevat die beginstand in `/baseline`. `zcbs-reset` (ook bij het opstarten) zet `public_html` daaruit terug: `docker exec <container> zcbs-reset`.
- Alle namen, wachtwoorden en records zijn verzonnen.

Hoe de tests dit gebruiken staat in [docs/integratietests.md](../../../../docs/integratietests.md).

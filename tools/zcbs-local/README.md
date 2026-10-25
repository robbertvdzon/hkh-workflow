# Lokale ZCBS (om tegen te testen)

De echte ZCBS-code (Gerard van Nes, niet in git) draait in Docker op `http://localhost:8081/cgi-bin/archief.pl`, met een kopie van de archiefdata en eigen testgebruikers.
Niets gaat naar de echte server en er worden geen e-mails verstuurd: alle adressen in de lokale kopie zijn `xxx@x.nl`, en een nep-`/usr/sbin/sendmail` zet elke mail in `run/mailbox.txt`.

## Wat er nodig is

- De gedownloade serverbestanden in `docs/server/` (zie `docs/server/README.md`), onder andere `cgi-bin/objecten.pl`, `archief.pl`, `cgi-bin/archief/…`, `zcbs_frontend/cgi-lib.pl`.
- Docker.

## Starten

```bash
tools/zcbs-local/prepare.sh                    # bouwt de werkmap run/ (gitignored) uit docs/server
cd tools/zcbs-local && docker compose up --build -d
docker compose down                            # stoppen
```

Draai `prepare.sh` opnieuw om met schone data te beginnen (opslagen zijn dan weg). Mails (naar `xxx@x.nl`) komen in `run/mailbox.txt`; opslagen in `run/cgi-bin/archief/updates.txt`, logs in `run/archief/logging/`.

## Testgebruikers (verzonnen)

| Naam | Wachtwoord | Recht |
|---|---|---|
| Test Beheerder | Beheer1 | 1+ |
| Test Bewerker | Bewerk1 | 1 |
| Test Tweede | Tweede1 | 1 |
| Test Lezer | Lezer1 | 3 |
| `<ROUTER> Test Kring` (IP `198.51.100.7`, via de header `X-Forwarded-For`) | n.v.t. | router |

## Let op

- De hostnaam in ZCBS is `zcbs.test` (`UseCanonicalName`), niet `localhost`: op `localhost` ziet ZCBS iedereen als beheerder zonder inlog.
- Stuur een eigen `User-Agent` (niet `curl`); zie `docs/zcbs-lokaal-testresultaten.md`.
- Verzoeken zonder cookie worden per IP begrensd (7 per 10 seconden).
- De code en data in `run/` zijn niet bedoeld om te delen. `run/` staat in `.gitignore`.

## Wat er in de container zit

Alleen de collectie `archief`. `prepare.sh` kopieert uit `docs/server/`: de code (`archief.pl`, `archief2.pl`, `objecten.pl`, de modules in `misc/`, `cgi-lib.pl`, `malware.lst`, `whitelist.txt`), de config en data van archief
(`cfg-cbs.pl`, `layout.html`, `info-beheer.html`, `archief.txt`, `updates.txt`), een paar voorbeeldbestanden (`large`, `small`, `ocr`, `pdf`, `100pct`) en lege mappen die ZCBS verwacht. De map `run/` is als
`public_html` in de container gemonteerd, dus wat je daar neerzet of wat ZCBS schrijft, zie je meteen aan beide kanten. De andere collecties (beeldbank, bidprent, library, ...), de logs van de echte server en de
`.htaccess`-bestanden worden niet gebruikt.

## Sftp (om de upload te testen)

Dezelfde mapindeling als de echte server, en dezelfde map als ZCBS gebruikt: wat je uploadt naar `archief/ocr`, `archief/pdf`, `archief/small` of `archief/large` staat meteen in de lokale ZCBS.

| Instelling | Waarde |
|---|---|
| Host, poort | `localhost`, `2222` |
| Gebruiker, wachtwoord | `hkhadmin`, `hkhtest` |
| Basismap | `/domains/historischekringheemskerk.nl/public_html` |
| Vingerafdruk | `docker exec zcbs-sftp sh -c 'ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub'` (verandert als de container opnieuw wordt gemaakt) |

Gewone FTP (poort 21) is er niet. images.exe heeft zijn eigen hostlijst en kan hier niet naartoe wijzen; de nep-images.exe uploadt niets.

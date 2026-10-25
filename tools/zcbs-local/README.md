# Lokale ZCBS (om tegen te testen)

De echte ZCBS-code (Gerard van Nes, niet in git) draait in Docker op `http://localhost:8081/cgi-bin/archief.pl`, met een kopie van de archiefdata en eigen testgebruikers.
Niets gaat naar de echte server en er worden geen e-mails verstuurd.

## Wat er nodig is

- De gedownloade serverbestanden in `docs/server/` (zie `docs/server/README.md`), onder andere `cgi-bin/objecten.pl`, `archief.pl`, `cgi-bin/archief/…`, `zcbs_frontend/cgi-lib.pl`.
- Docker.

## Starten

```bash
tools/zcbs-local/prepare.sh                    # bouwt de werkmap run/ (gitignored) uit docs/server
cd tools/zcbs-local && docker compose up --build -d
docker compose down                            # stoppen
```

Draai `prepare.sh` opnieuw om met schone data te beginnen (opslagen zijn dan weg). Mails komen in `run/mailbox.txt`; opslagen in `run/cgi-bin/archief/updates.txt`, logs in `run/archief/logging/`.

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

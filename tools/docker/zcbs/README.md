# Lokale ZCBS in Docker

De echte ZCBS-code (van Gerard van Nes) draait lokaal in Docker, met de archiefdata van HKH, verzonnen testgebruikers en zonder echte e-mail. Alles zit in **één container** (`zcbs`), waarin supervisord drie diensten draait:

| Dienst | Wat | Adres |
|---|---|---|
| Apache + Perl | ZCBS | http://localhost:8081/cgi-bin/archief.pl |
| sshd | sftp, zelfde mapindeling als de echte server | `127.0.0.1`, poort `2222` |
| vsftpd | gewone ftp, zoals images.exe die gebruikt | `127.0.0.1`, poort `2121` (passief, `30000-30009`) |

## De mappen

| Map | Wat | In git? |
|---|---|---|
| `initial-files/` | De **originele** bestanden van de server, ongewijzigd. Hier pas je nooit iets aan. | nee |
| `stubs/` | Onze eigen nepbestanden: `permit.lst` (testgebruikers) en `sendmail.pm` (verstuurt niets). | ja |
| `files/` | De werkmap: een kopie van `initial-files/` plus de aanpassingen hieronder. Dit is de `public_html` die in de container wordt gemount. ZCBS, sftp en ftp werken er alle drie in. | nee |

`initial-files/` en `files/` staan in `.gitignore`: de ZCBS-code en de data zijn van Gerard van Nes en HKH en horen niet op een openbare plek.

## 1. Bestanden van de server halen (samen met de systeembeheerder)

We halen **niet meer dan nodig** op. Ik heb uitgeprobeerd welke bestanden echt nodig zijn: zonder een van de acht onderstaande bestanden werkt ZCBS niet, en alle andere kun je weglaten.

| Op de server | Naar `initial-files/` | Waarom |
|---|---|---|
| `/cgi-bin/archief.pl` | `cgi-bin/archief.pl` | Startscript van archief |
| `/cgi-bin/objecten.pl` | `cgi-bin/objecten.pl` | De ZCBS-applicatie zelf |
| `/cgi-bin/misc/cgi-lib.pl` | `cgi-bin/misc/cgi-lib.pl` | Leest formulieren en cookies. **Vraag de beheerder waar dit bestand staat**: `objecten.pl` zoekt het in `cgi-bin/archief/misc/` en dan in `cgi-bin/misc/`. |
| `/cgi-bin/misc/httpbl.pm` | `cgi-bin/misc/httpbl.pm` | Controle op spam-IP-adressen; ZCBS laadt het bij elke bezoeker zonder cookie |
| `/cgi-bin/archief/misc/cfg-cbs.pl` | `cgi-bin/archief/misc/cfg-cbs.pl` | De instellingen van archief: veldnamen, keuzelijsten, e-mailadressen |
| `/cgi-bin/archief/misc/layout.html` | `cgi-bin/archief/misc/layout.html` | De paginasjabloon |
| `/cgi-bin/archief/archief.txt` | `cgi-bin/archief/archief.txt` | Het hoofdbestand met de records |
| `/cgi-bin/archief/updates.txt` | `cgi-bin/archief/updates.txt` | De wijzigingen sinds de laatste merge. Zonder dit bestand mist je de recente stand. |

Vraag de beheerder ook:

- Welke versie `objecten.pl` is (de regel `$VERSION = "…"` bovenin) en of dat de nieuwste is.
- Of `archief.pl` en `objecten.pl` aan de kant van de server nog zijn aangepast of de standaard van Gerard zijn.
- Of er meer dan één kopie van `cfg-cbs.pl` of `layout.html` is (bijvoorbeeld een testversie `…2.pl`).

**Niet ophalen** (niet nodig, en vaak gevoelig):

- `permit.lst`: bevat de gebruikersnamen en wachtwoorden. Wij gebruiken een eigen testbestand uit `stubs/`.
- `guest.lst` (gegevens van bezoekers), alle logs (`archief/logging/`), `images.log`, de `.htaccess`-bestanden en de back-ups.
- De andere collecties (beeldbank, bidprent, library, …), `archief2.pl` en de modules `config.pm`, `materialen.pm`, `techniek.pm`, `replace.txt`, `malware.lst`, `whitelist.txt` en `info-beheer.html`: ZCBS draait zonder deze bestanden (getest).
- Afbeeldingen, pdf's en tekstbestanden (`archief/large`, `small`, `pdf`, `ocr`, `cgi-bin/archief/100pct`): niet nodig om de records te bewerken.

Daarna:

1. Zet de bestanden in `initial-files/`, in dezelfde mapindeling als in de tabel. Pas ze **niet** aan.
2. Vul `initial-files/HERKOMST.txt` in (wanneer, met wie, welke versie).
3. Maak de controlesommen opnieuw, zodat je later kunt zien dat de originelen niet zijn veranderd:

   ```bash
   cd initial-files
   find . -type f ! -name SHA256SUMS ! -name HERKOMST.txt | sort | xargs shasum -a 256 > SHA256SUMS
   ```

   Controleren kan altijd met `cd initial-files && shasum -a 256 -c SHA256SUMS`.

In `initial-files/` staat nu als voorlopige stand de download van 21 en 22 oktober 2026.

## 2. Aanpassen: de werkmap `files/` maken

`files/` is een kopie van `initial-files/` waarin je het volgende aanpast. Je hoeft dit maar één keer te doen (en opnieuw na een reset).

1. **Kopieer de originelen:**

   ```bash
   cd tools/docker/zcbs
   rm -rf files && cp -a initial-files files
   rm -f files/SHA256SUMS files/HERKOMST.txt
   ```

2. **Zet de nepbestanden uit `stubs/` erbij:**

   ```bash
   cp stubs/permit.lst stubs/sendmail.pm files/cgi-bin/misc/
   ```

   `permit.lst` bevat de verzonnen testgebruikers (zie hieronder). `sendmail.pm` is een nep-versie die niets verstuurt. Beide bestonden niet in `initial-files/`, omdat we het echte permit-bestand niet ophalen en de echte `sendmail.pm` niet willen gebruiken.

3. **Pas `files/cgi-bin/archief/misc/cfg-cbs.pl` aan.** Open het bestand en verander deze vijf regels (de rest laat je staan):

   | Regel (begint met) | Nieuwe waarde |
   |---|---|
   | `$EMAIL_TO` | `'xxx@x.nl'` |
   | `$EMAIL_FROM` | `'xxx@x.nl'` |
   | `$EMAIL_BB` | `'xxx@x.nl'` |
   | `$TEL_BB` | `''` (leeg, dit is een telefoonnummer) |
   | `$PERMIT` | `'testpermit'` (het wachtwoord voor de permit-pagina) |

   Houd de aanhalingstekens en het puntkomma zoals ze staan. Het bestand heeft Windows-regeleinden; bewerk het met een editor die dat laat staan (VS Code, `nano`).

4. **Zet de rechten goed.** De container draait als een andere gebruiker dan jij:

   ```bash
   chmod -R a+rwX files
   chmod a+x files/cgi-bin/*.pl
   ```

5. **Alleen als je wilt:** de e-mailadressen in `objecten.pl` (het CC `…@zcbs.nl` voor database-alarmen en `error@zcbs.nl`) hoef je niet aan te passen. Er kan toch niets uit de container: `sendmail.pm` is een nep-versie, en `/usr/sbin/sendmail` in de container schrijft alles in `files/mailbox.txt`.

Wil je dit niet met de hand doen, dan doet `./adapt-files.sh` precies de stappen 1 tot en met 4. `./reset.sh` gebruikt dat script.

Niets hoeft aangepast in `archief.txt`, `updates.txt`, `layout.html`, `cgi-lib.pl` of `archief.pl`. De map `files/` krijgt de lege mappen die ZCBS en ftp nodig hebben (`addenda`, `backup1`, `archief/small`, `archief/logging`, …) automatisch: de container maakt ze bij het starten zelf aan.

## 3. Starten

Vereist Docker (Docker Desktop of vergelijkbaar).

```bash
cd tools/docker/zcbs
docker compose up --build -d
```

Open daarna http://localhost:8081/cgi-bin/archief.pl?user=reset en log in. Stoppen: `docker compose down`. Logs van alle drie de diensten: `docker logs -f zcbs`.

Poorten 8081, 2222 en 2121 mogen niet bezet zijn. Draait de oudere opzet met drie containers (`zcbs-local`) nog, stop die dan eerst.

## 4. Inloggen (verzonnen testgebruikers)

| Naam | Wachtwoord | Recht |
|---|---|---|
| Test Beheerder | `Beheer1` | 1+ (systeembeheer) |
| Test Bewerker | `Bewerk1` | 1 (bewerken) |
| Test Tweede | `Tweede1` | 1 (bewerken) |
| Test Lezer | `Lezer1` | 3 (intern lezen) |
| `<ROUTER> Test Kring` | n.v.t. | routerweg; IP `198.51.100.7` via de header `X-Forwarded-For` |

De gebruikers staan in `files/cgi-bin/misc/permit.lst` (een kopie van `stubs/permit.lst`). Gebruik in de browser `localhost:8081` (niet het IP-adres): ZCBS ziet de naam `zcbs.test`, anders wordt iedereen als beheerder gezien zonder inlog.

## 5. sftp en ftp

| | sftp | ftp |
|---|---|---|
| Host | `127.0.0.1` (niet `localhost`; dat kan IPv6 worden) | `127.0.0.1` |
| Poort | `2222` | `2121` |
| Gebruiker | `hkhadmin` | `hkhadmin` |
| Wachtwoord | `hkhtest` | `hkhtest` |
| Basismap | `/domains/historischekringheemskerk.nl/public_html` | idem |

Alle drie de diensten gebruiken `files/`: wat je uploadt, staat meteen in de lokale ZCBS, en wat ZCBS opslaat, zie je via sftp en ftp. Apache draait als gebruiker `hkhadmin`, dezelfde als sftp en ftp.

Vingerafdruk van de sftp-server: `docker exec zcbs ssh-keygen -lf /etc/ssh/keys/ssh_host_ed25519_key.pub`. De sleutels staan in een Docker-volume (`sshkeys`) en veranderen niet als je de container opnieuw maakt.

Met Midnight Commander (het wachtwoord moet in het adres; alleen verantwoord voor deze testserver):

```bash
mc . sftp://hkhadmin:hkhtest@127.0.0.1:2222/domains/historischekringheemskerk.nl/public_html
```

Of met `sftp -P 2222 hkhadmin@127.0.0.1`.

## 6. Mail, logs en wat ZCBS schrijft

- E-mails (naar `xxx@x.nl`) worden niet verstuurd maar komen in `files/mailbox.txt`.
- Een opslag van een record: een regel in `files/cgi-bin/archief/updates.txt` en in `files/cgi-bin/archief/backup1/upd-cumm.txt`.
- Logs en vergrendelingen: `files/archief/logging/` (per gebruiker, `lock_object.txt`).

## 7. Terug naar de beginstand

```bash
./reset.sh
```

Dat maakt `files/` opnieuw uit `initial-files/` (met de aanpassingen uit stap 2, via `adapt-files.sh`) en start de container opnieuw. Alles wat je hebt opgeslagen of geüpload is dan weg. Als je zelf extra dingen in `files/` hebt aangepast, doe je die daarna opnieuw, of je zet ze in `adapt-files.sh`.

## Handig om te weten

- Een eigen `User-Agent` sturen als je ZCBS met een programma aanroept (`curl` wordt geweigerd), en cookies meesturen: zonder cookie geldt een snelheidsbegrenzing (zie `docs/zcbs-lokaal-testresultaten.md`).
- De nep-images.exe (`tools/mock/images-werk/images.pl`) kan hier naartoe uploaden en records aanmaken; zie `tools/mock/README.md` (`images-mock.ini`, poort 2121).

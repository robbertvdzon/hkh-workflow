# Lokale ZCBS in Docker

| Dienst | Wat | Adres |
|---|---|---|
| Apache + Perl | ZCBS | http://localhost:8081/cgi-bin/archief.pl |
| sshd | sftp, zelfde mapindeling als de echte server | `127.0.0.1`, poort `2222` |
| vsftpd | gewone ftp, zoals images.exe die gebruikt | `127.0.0.1`, poort `2121` (passief, `30000-30009`) |


## De mappen

| Map | Wat | 
|---|---|
| `initial-files/` | De **originele** bestanden van de server, ongewijzigd. Hier pas je nooit iets aan. | 
| `stubs/` | Onze eigen nepbestanden: `permit.lst` (testgebruikers) en `sendmail.pm` (een lege versie; ZCBS start niet zonder dit bestand). | 
| `files/` | De werkmap: een kopie van `initial-files/` plus de aanpassingen hieronder. Dit is de `public_html` die in de container wordt gemount. ZCBS, sftp en ftp werken er alle drie in. | 

## 1. Bestanden van de server halen

| Files | Waarom |
|---|---|
| `/cgi-bin/archief.pl` | Startscript van archief |
| `/cgi-bin/objecten.pl` | De ZCBS-applicatie zelf |
| `/cgi-bin/misc/cgi-lib.pl` | Leest formulieren en cookies. **Vraag de beheerder waar dit bestand staat**: `objecten.pl` zoekt het in `cgi-bin/archief/misc/` en dan in `cgi-bin/misc/`. |
| `/cgi-bin/misc/httpbl.pm` | Controle op spam-IP-adressen; ZCBS laadt het bij elke bezoeker zonder cookie |
| `/cgi-bin/archief/misc/cfg-cbs.pl` | De instellingen van archief: veldnamen en keuzelijsten |
| `/cgi-bin/archief/misc/layout.html` | De paginasjabloon |
| `/cgi-bin/archief/archief.txt` | Het hoofdbestand met de records |
| `/cgi-bin/archief/updates.txt` | De wijzigingen sinds de laatste merge. Zonder dit bestand mist je de recente stand. |


## 2. Aanpassen: de werkmap `files/` maken

`files/` is een kopie van `initial-files/` waarin je het volgende aanpast. Je hoeft dit maar één keer te doen (en opnieuw na een reset).

1. **Kopieer de originelen:**

   ```bash
   cd tools/docker/zcbs
   rm -rf files && cp -a initial-files files
   ```

2. **Zet de nepbestanden uit `stubs/` erbij:**

   ```bash
   cp stubs/permit.lst stubs/sendmail.pm files/cgi-bin/misc/
   ```

   `permit.lst` bevat de verzonnen testgebruikers (zie hieronder). `sendmail.pm` heeft ZCBS nodig om te starten; dit is een lege versie.

3. **Pas `files/cgi-bin/archief/misc/cfg-cbs.pl` aan.** Open het bestand en verander deze twee regels (de rest laat je staan):

   | Regel (begint met) | Nieuwe waarde |
   |---|---|
   | `$TEL_BB` | `''` (leeg, dit is een telefoonnummer) |
   | `$PERMIT` | `'testpermit'` (het wachtwoord voor de permit-pagina) |

   Houd de aanhalingstekens en het puntkomma zoals ze staan. Het bestand heeft Windows-regeleinden; bewerk het met een editor die dat laat staan (VS Code, `nano`).

4. **Zet de rechten goed.** De container draait als een andere gebruiker dan jij:

   ```bash
   chmod -R a+rwX files
   chmod a+x files/cgi-bin/*.pl
   ```

## 3. Starten

Vereist Docker (Docker Desktop of vergelijkbaar).

```bash
cd tools/docker/zcbs
docker compose up --build -d
```

Open daarna http://localhost:8081/cgi-bin/archief.pl?user=reset en log in. Stoppen: `docker compose down`. Logs van alle drie de diensten: `docker logs -f zcbs`.

Poorten 8081, 2222 en 2121 mogen niet bezet zijn. Draait de oudere opzet met drie containers (`zcbs-local`) nog, stop die dan eerst.

## 4. Inloggen (verzonnen testgebruikers)

| Naam                  | Wachtwoord | Recht |
|-----------------------|------------|---|
| user1                 | `abc`      | 1+ (systeembeheer) |
| user2                 | `abc`      | 1 (bewerken) |

## 5. sftp en ftp

| | sftp | ftp |
|---|---|---|
| Host | `127.0.0.1` (niet `localhost`; dat kan IPv6 worden) | `127.0.0.1` |
| Poort | `2222` | `2121` |
| Gebruiker | `hkhadmin` | `hkhadmin` |
| Wachtwoord | `hkhtest` | `hkhtest` |
| Basismap | `/domains/historischekringheemskerk.nl/public_html` | idem |


```bash
mc . sftp://hkhadmin:hkhtest@127.0.0.1:2222/domains/historischekringheemskerk.nl/public_html
```

Of met `sftp -P 2222 hkhadmin@127.0.0.1`.

## 6. Terug naar de beginstand

```bash
./reset.sh
```

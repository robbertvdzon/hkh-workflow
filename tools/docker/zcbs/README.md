# Lokale ZCBS in Docker

De echte ZCBS-code (van Gerard van Nes) draait lokaal in Docker, met een kopie van de archiefdata, verzonnen testgebruikers en zonder echte e-mail. Alles zit in **één container** (`zcbs`),
waarin supervisord drie diensten draait:

| Dienst | Wat | Adres |
|---|---|---|
| Apache + Perl | ZCBS | http://localhost:8081/cgi-bin/archief.pl |
| sshd | sftp, zelfde mapindeling als de echte server | `127.0.0.1`, poort `2222` |
| vsftpd | gewone ftp, zoals images.exe die gebruikt | `127.0.0.1`, poort `2121` (passief, `30000-30009`) |

Alle drie gebruiken dezelfde map: **`files/`**. Dat is de `public_html` van de server (in de container: `/home/hkhadmin/domains/historischekringheemskerk.nl/public_html`). Wat je uploadt of wat ZCBS
opslaat, staat meteen in `files/` en dus voor alle drie zichtbaar. Apache draait als gebruiker `hkhadmin`, dezelfde als sftp en ftp, zodat bestanden altijd door elkaar te lezen en te schrijven zijn.

## Starten

Vereist Docker (Docker Desktop of vergelijkbaar).

```bash
cd tools/docker/zcbs
docker compose up --build -d
```

Open daarna http://localhost:8081/cgi-bin/archief.pl?user=reset en log in (zie hieronder). Stoppen:

```bash
docker compose down
```

Poorten 8081, 2222 en 2121 mogen niet bezet zijn. Draait de oudere opzet met drie containers (`zcbs-local`) nog, stop die dan eerst: de poorten zijn dezelfde.
Logs van alle drie de diensten: `docker logs -f zcbs`.

## Inloggen (verzonnen testgebruikers)

| Naam | Wachtwoord | Recht |
|---|---|---|
| Test Beheerder | `Beheer1` | 1+ (systeembeheer) |
| Test Bewerker | `Bewerk1` | 1 (bewerken) |
| Test Tweede | `Tweede1` | 1 (bewerken) |
| Test Lezer | `Lezer1` | 3 (intern lezen) |
| `<ROUTER> Test Kring` | n.v.t. | routerweg; IP `198.51.100.7` via de header `X-Forwarded-For` |

De gebruikers staan in `files/cgi-bin/misc/permit.lst`. Gebruik in de browser `localhost:8081` (niet het IP-adres): ZCBS ziet de naam `zcbs.test`, anders wordt iedereen als beheerder gezien zonder inlog.

## sftp en ftp

| | sftp | ftp |
|---|---|---|
| Host | `127.0.0.1` (niet `localhost`; dat kan IPv6 worden) | `127.0.0.1` |
| Poort | `2222` | `2121` |
| Gebruiker | `hkhadmin` | `hkhadmin` |
| Wachtwoord | `hkhtest` | `hkhtest` |
| Basismap | `/domains/historischekringheemskerk.nl/public_html` | idem |

Vingerafdruk van de sftp-server: `docker exec zcbs ssh-keygen -lf /etc/ssh/keys/ssh_host_ed25519_key.pub`. De sleutels staan in een Docker-volume (`sshkeys`) en veranderen niet als je de container opnieuw maakt.

Met Midnight Commander (het wachtwoord moet in het adres):

```bash
mc . sftp://hkhadmin:hkhtest@127.0.0.1:2222/domains/historischekringheemskerk.nl/public_html
```

Of met `sftp -P 2222 hkhadmin@127.0.0.1`. Het wachtwoord in een adres is alleen verantwoord voor deze testserver.

## Wat er in files/ staat

```
files/
  cgi-bin/archief.pl, archief2.pl, objecten.pl        de ZCBS-code
  cgi-bin/misc/                                       cgi-lib.pl, config.pm, materialen.pm, techniek.pm, httpbl.pm, replace.txt,
                                                      malware.lst, whitelist.txt, sendmail.pm (nep), permit.lst (testgebruikers)
  cgi-bin/archief/                                    archief.txt, updates.txt, guest.lst (leeg), misc/{cfg-cbs.pl, layout.html, info-beheer.html},
                                                      100pct/ en lege mappen (addenda, backup1, databases, forms, uitgeleend, ...)
  archief/                                            large/, small/, ocr/, pdf/ (voorbeelden), logging/ (leeg)
```

De lege mappen die ZCBS, ftp en sftp nodig hebben (`addenda`, `backup1`, `archief/small`, `archief/logging`, ...) maakt de container bij het starten zelf aan als ze ontbreken. De bestanden zijn al klaar voor gebruik: alle e-mailadressen in code en config zijn `xxx@x.nl`, het permit-wachtwoord en het telefoonnummer zijn vervangen, `permit.lst` bevat alleen de testgebruikers, en `sendmail` is een nep-versie.
Je hoeft dus niets te importeren.

## Mail, logs en wat ZCBS schrijft

- E-mails (naar `xxx@x.nl`) worden niet verstuurd maar komen in `files/mailbox.txt`.
- Een opslag van een record: een regel in `files/cgi-bin/archief/updates.txt` en in `files/cgi-bin/archief/backup1/upd-cumm.txt`.
- Logs en vergrendelingen: `files/archief/logging/` (per gebruiker, `lock_object.txt`).

## Terug naar de beginstand

```bash
./reset.sh
```

Dat vervangt `files/` door een kopie van de map `initial-files/` (de beginstand, die je zelf nooit aanpast) en start de container opnieuw. Alles wat je hebt opgeslagen of geüpload is dan weg.

## Niet in git

`files/` en `initial-files/` staan in `.gitignore`: de ZCBS-code en de data zijn van Gerard van Nes en HKH en horen niet op een openbare plek. Op een andere computer moet je `files/` zelf neerzetten
(dezelfde indeling als hierboven, met de serverbestanden), en een kopie ervan als `initial-files/`: `cp -a files initial-files`, als je `reset.sh` wilt gebruiken.

## Handig om te weten

- Een eigen `User-Agent` sturen als je ZCBS met een programma aanroept (`curl` wordt geweigerd), en cookies meesturen: zonder cookie geldt een snelheidsbegrenzing (zie `docs/zcbs-lokaal-testresultaten.md`).
- De nep-images.exe (`tools/mock/images-werk/images.pl`) kan hier naartoe uploaden en records aanmaken; zie `tools/mock/README.md` (`images-mock.ini`, poort 2121).

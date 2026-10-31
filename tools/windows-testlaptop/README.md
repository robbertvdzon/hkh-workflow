# Windows-testlaptop voor het echte images.exe

Doel: het **echte** `images.exe` (en daarmee de echte Windows-omgeving van HKH) laten draaien tegen onze lokale ZCBS in
Docker (`tools/docker/zcbs`), zonder dat er iets naar de echte HKH-server gaat. Daarmee is gemeten welke server, poort en
gebruiker het programma gebruikt, en wat het precies vraagt en doet. De resultaten staan in
[../../docs/zcbs-achtergrond.md](../../docs/zcbs-achtergrond.md) (sectie images.exe).

Eerste volledige run: 30 oktober 2026, images.exe 3.27 op Windows 10 (4 GB geheugen), vanaf de Mac aangestuurd via SSH.

## Hoe het in elkaar zit

```
laptop (Windows, C:\images-test\images.exe)
   hosts: ftp.historischekringheemskerk.nl en www.zcbs.nl  ->  IP van de Mac
   ftp  (poort 21, passief 30000-30009)  -> Docker -> container zcbs (vsftpd, gebruiker ZCBS@historischekringheemskerk.nl)
   http (poort 80, alleen de versiecontrole)  -> luisteraar op de Mac of niets (geeft 404)
   firewall: images.exe en i_view32.exe mogen alleen naar het IP van de Mac
Mac: SSH naar de laptop met ~/.ssh/testlaptop, bestanden met scp, script drive-images.ps1 beantwoordt de vragen
```

Wat het programma buiten dit doet: niets dat we gezien hebben. Alle DNS-vragen tijdens de runs waren
`ftp.historischekringheemskerk.nl`, `www.zcbs.nl` en Windows' eigen telemetrie.

## Opzetten (eenmalig)

1. **Mac:** in `tools/docker/zcbs/.env` staan `BIND_ADDRESS=0.0.0.0` en `PASV_ADDRESS=<IP van de Mac>`. `docker compose up -d --build`
   (de FTP-gebruiker `ZCBS@historischekringheemskerk.nl` / `hkhtest` en poort 21 zijn onderdeel van de container).
2. **Laptop, als administrator** (PowerShell): `Set-ExecutionPolicy -Scope Process Bypass`, dan `.\setup-ssh.ps1`. Het installeert de
   SSH-server (alleen sleutel, alleen lokaal netwerk) en toont IP-adres en gebruikersnaam. De gebruiker moet beheerder zijn.
   De sleutel op de Mac: `~/.ssh/testlaptop` (publieke sleutel staat in het script).
3. Zet in `C:\images-test`: `images.exe`, `images.ini`, `i_view32.exe`, een map `images` met bronbestanden, en deze scripts.
4. **Laptop, als administrator** (of via SSH, de SSH-gebruiker is beheerder):
   - `.\hosts-instellen.ps1 -Mac <IP van de Mac>` stuurt beide namen naar de Mac;
   - `.\firewall-blokkade.ps1 -Mac <IP van de Mac>` blokkeert images.exe en i_view32.exe naar al het andere (IPv4 en IPv6).
   De firewall werkt per programmapad: draai `images.exe` dus alleen vanuit `C:\images-test`.
5. Controle: de naam moet naar de Mac wijzen (`[Net.Dns]::GetHostAddresses('ftp.historischekringheemskerk.nl')`).

Een eigen DNS-server is niet nodig. Er is wel een optie voor als je de hostnaam van een onbekend programma moet ontdekken:
`docker compose --profile dns up -d` laat elke naam naar de Mac wijzen en logt elke vraag (`docker logs zcbs-dns`); zet dan de
DNS van de laptop op het IP van de Mac. Zo is de naam hierboven gevonden. Zonder internet voor de laptop zolang je dat doet.

## images.exe laten draaien

```
powershell -NoProfile -ExecutionPolicy Bypass -File C:\images-test\drive-images.ps1 -MaxSeconden 150
```

`drive-images.ps1` start `images.exe` met pijpen (stdin/stdout), wacht tot de vraag op het scherm staat en stuurt dan het
antwoord met CRLF, net als `ExeRunner` in de app. Eigenschappen die we hebben gemeten:

- **Alle antwoorden in één keer uit een bestand (`< invoer.txt`) werkt niet:** het programma blijft bij dezelfde vraag hangen.
  De invoer moet na de vraag komen.
- **Er is geen `PERLIO=:unix` nodig:** de vragen verschijnen met pijpen zonder aanpassing.
- De uitvoer is code page 850 (IBM850); `Echo` in `images.ini` was leeg, dus verborgen wachtwoordinvoer (`Echo : 1`) is niet getest.
- De volgorde van de vragen (versie 3.27, collectie `archief`, één bron-jpg): zie de tabel in
  [../../docs/zcbs-achtergrond.md](../../docs/zcbs-achtergrond.md). Het script herkent ze op tekst.

Gebruik een **nieuw recordnummer** (bijvoorbeeld `99002.jpg`): bij "AANMAKEN en UPLOADEN van de bijbehorende records" voegt het
programma lege records toe aan `updates.txt`, en dat geeft "last line wins" voor een bestaand nummer. Laat bij een tweede run
de mappen `small`, `large`, `100pct` en het bestand `updates-add.txt` weghalen, anders komen er extra vragen
("Toch de afgeleide images ... opnieuw aanmaken?").

## Controleren op de Mac

- `tools/docker/zcbs/files/archief/small|large/<nr>.jpg` en `.../cgi-bin/archief/100pct/<nr>.jpg`;
- `tools/docker/zcbs/files/cgi-bin/archief/updates.txt`: één regel erbij (`HKH<TAB>997<TAB><nr>...Een nog niet beschreven record (jjmmdd)`);
- `.../cgi-bin/misc/images.log`: een regel met versie, collectie en de Windows-gebruiker;
- FTP-commando's: `docker exec zcbs grep 'FTP command' /var/log/vsftpd.log` (de container-tijd is UTC, dus een uur terug).

## De hkh-workflow-app op de laptop draaien

Voor een test met het echte `images.exe` en de app samen op de laptop (map `C:\zcbs-workflow`, Java staat erop):

```
tools/windows-testlaptop/deploy-app.sh               # mvn clean package, stopt de app op de laptop, uploadt de jar; start NIET
tools/windows-testlaptop/deploy-app.sh --starten     # doet het ook nog starten
tools/windows-testlaptop/deploy-app.sh --zonder-tests    # sneller bouwen (-DskipTests)
tools/windows-testlaptop/deploy-app.sh --zonder-bouwen   # gebruikt de bestaande target/hkh-workflow.jar
tools/windows-testlaptop/deploy-app.sh --stop        # alleen stoppen
tools/windows-testlaptop/deploy-app.sh --log         # laatste regels van app.log en app.err
```

Na `--starten` (of als je hem zelf start) draait de app op `http://<IP van de laptop>:9000/` (aanpasbaar met `LAPTOP_POORT`; `LAPTOP`, `LAPTOP_USER`, `LAPTOP_KEY` en
`LAPTOP_MAP` kunnen ook). Het bouwt uit de werkmap, dus met alles wat daar op dat moment staat (ook niet-gecommitte wijzigingen), en een gelijktijdige
`mvn clean` van iets anders in dezelfde map kan het bouwen laten mislukken. `deploy-app.sh` kopieert ook `app-start.ps1`, dat op de laptop het stoppen en starten doet. Het
instellingenbestand `hkh-workflow-settings.json` naast de jar blijft bij een nieuwe jar staan.

- Een draaiende app houdt de jar vergrendeld, daarom wordt hij eerst gestopt.
- De app wordt via WMI gestart. Met `Start-Process` verdween hij zodra de SSH-sessie sloot (gemeten).
- Het script maakt eenmalig een firewallregel `hkh-workflow (9000)` aan, alleen voor het lokale netwerk. `terugzetten.ps1` haalt die
  niet weg: `Remove-NetFirewallRule -DisplayName 'hkh-workflow (9000)'`.
- De app gebruikt als test-ZCBS `localhost`. Op de laptop is dat de Mac: `deploy-app.sh` zet daarom `config\application.properties` naast de jar met het IP van de Mac (`MAC_IP`, standaard het IP van en0) voor het ZCBS-adres en de ftp-server. Op de Instellingenpagina staat dan "Gebruik de test-ZCBS op localhost" aan; de FTP-gebruiker is `ZCBS@historischekringheemskerk.nl` met wachtwoord `hkhtest`.
- De beheerderslogin gebruikt het wachtwoord uit de jar (`application.properties`) tenzij `HKH_WORKFLOW_ADMIN_PASSWORD` gezet is.

## Terugzetten en opruimen

`.\terugzetten.ps1` (als administrator) haalt de `hosts`-regels en firewallregels weg en zet de DNS weer op automatisch.
`.\setup-ssh.ps1 -Verwijder` zet SSH uit en verwijdert de sleutel. Verwijder daarna `C:\images-test`.
Op de Mac: testrecords uit het archief halen (back-up van `updates.txt` maken vóór een run), de bestanden
`99xxx.jpg` uit `archief/small|large` en `cgi-bin/archief/100pct` en `docker compose --profile dns down` (alleen de DNS-container).

## Lessen over de omgeving

- PowerShell via SSH: dubbele aanhalingstekens in een commando gaan verloren; zet het in een `.ps1` en kopieer die. Voeg bij
  commando's met voortgangsbalk `$ProgressPreference = 'SilentlyContinue'` toe (anders "Toegang geweigerd" door de console).
- Een firewallregel voor `::/0` accepteert Windows niet; gebruik `::/1` en `8000::/1`.
- Wine in Docker op Apple Silicon is geprobeerd en lukt niet: Rosetta draait geen 32-bit x86 (`invalid gdt selector index`),
  QEMU laat Wine crashen. Op x86 (bijvoorbeeld de OpenShift-node) zou het wel moeten gaan; niet gedaan.

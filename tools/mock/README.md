# Nep-versies van images.exe en pdf2zcbs.exe

Om de testpagina op een Mac (of Linux) te proberen zonder Windows. Het zijn Perl-scripts die dezelfde
vragen stellen als de echte programma's (volgens `docs/042.pdf` en `docs/050.pdf`), maar niets uploaden.

Instellingen in de app (pas het pad aan naar jouw checkout):

| Instelling | Waarde |
|---|---|
| pdf2zcbs.exe | `…/hkh-workflow/tools/mock/pdf2zcbs/pdf2zcbs.pl` |
| images.exe | `…/hkh-workflow/tools/mock/images-werk/images.pl` |

Net als een gewoon Perl-programma bufferen ze hun uitvoer als ze niet in een terminal draaien. Zo zie
je thuis al hetzelfde probleem als met de echte exe's te verwachten is. Zet bij Instellingen
**PERLIO=:unix** aan of uit om het verschil te zien.

De scripts schrijven in hun eigen map (`output/`, `images/`, `small/` …); die mappen staan in `.gitignore`.

## Echte plaatjes en uitvoer

- `pdf2zcbs.pl` doet net als de echte pdf2zcbs.exe bij HKH **niets op het scherm** zolang `pdf2zcbs.ini` naast het programma staat (dat is hier zo):
  de vragen zijn dan al beantwoord, het programma toont niets en wacht nergens op. Haal `pdf2zcbs.ini` weg (of hernoem het) om de vragen en de
  voortgang van de handleiding te zien; de automatische antwoorden bij Instellingen zijn alleen dan nodig.
- `pdf2zcbs.pl` maakt per pdf `output/pdf`, `output/ocr` (met `pdftotext`; een gescande pdf zonder tekstlaag krijgt een vaste mock-tekst, want de echte exe doet OCR), `output/small` en `output/large` (met `pdftoppm`,
  de eerste pagina) en wist `output/` bij elke start, zoals het echte programma. Zonder `pdftoppm` komen er kleine nep-jpg's.
- `images.pl` maakt van de jpg's in `images/` een small (100 of 250 volgens de gekozen resolutie), large (700) en 100pct
  (met `sips` op macOS; het schaalt niet op) en ruimt alleen op als je op de verwijdervragen J antwoordt.

## Een sftp-server om de upload te proberen

De upload van tekst en pdf's kun je thuis proberen tegen een sftp-server in het geheugen. Bouw eerst en haal het
classpath op, en start dan de server:

```bash
mvn -q test-compile dependency:build-classpath -Dmdep.outputFile=/tmp/hkh-cp.txt -Dmdep.includeScope=test
mkdir -p /tmp/hkh-sftp/public_html/archief/ocr /tmp/hkh-sftp/public_html/archief/pdf
java -cp "target/test-classes:target/classes:$(cat /tmp/hkh-cp.txt)" nl.vdzon.hkhworkflow.sftp.TestSftpServerMainKt /tmp/hkh-sftp /tmp/hkh-hostkey.ser
```

Het programma toont de poort, de vingerafdruk en het wachtwoord (gebruiker `robbert`, wachtwoord `geheim`). Geef als derde
argument een poortnummer mee als je na een herstart dezelfde poort wilt houden; met hetzelfde hostsleutelbestand blijft ook
de vingerafdruk gelijk. Zet bij Instellingen
host `localhost`, die poort, basismap `/public_html` en klik Test verbinding; zet de vingerafdruk vast.


## images.pl met echte FTP-upload en records (tegen de lokale ZCBS)

Zonder verdere instelling uploadt `images.pl` niets. Wil je dat hij net als de echte images.exe via FTP uploadt en de lege records in `updates.txt` zet, start dan de lokale ZCBS
(`tools/zcbs-local/README.md`, met gewone FTP op poort 2121) en kopieer het voorbeeldbestand:

```bash
cp tools/mock/images-werk/images-mock.ini.example tools/mock/images-werk/images-mock.ini    # staat in .gitignore
```

Het programma doet dan, na J op de vragen:

- vraagt het FTP-wachtwoord (`hkhtest`) en controleert dat door te verbinden; bij een fout wachtwoord vraagt hij opnieuw;
- toont de ZCBS-toepassingen op de server en laat je er een kiezen (`archief`);
- vraagt, als er al bestanden met dezelfde naam staan, "Oude images ... OVERSCHRIJVEN? [J/N]" en slaat die bij N over;
- zet small en large in `<toepassing>/small` en `/large` en de 100pct in `cgi-bin/<toepassing>/100pct`;
- bij "AANMAKEN en UPLOADEN van de bijbehorende records" J: haalt `cgi-bin/<toepassing>/updates.txt` van de server, voegt de lege records toe (titel "Een nog niet beschreven record (jjmmdd)",
  stempel `system`, 35 velden), zet het terug en meldt "Oude/Nieuwe update database ... bevat ... records". Het controleert niet of de records al bestaan, net als de echte (zie handleiding, stap D);
- zet een regel in `cgi-bin/misc/images.log` op de server.

Zet in de app bij Instellingen het automatische antwoord op "FTP wachtwoord" op `hkhtest`. De sftp-instellingen voor tekst en pdf's (poort 2222) staan in `tools/zcbs-local/README.md`.

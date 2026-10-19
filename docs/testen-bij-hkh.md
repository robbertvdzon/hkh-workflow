# Eerste echte test bij HKH

Dit is de eerste run met de echte `pdf2zcbs.exe` en `images.exe` via de app, **in het echte archief** omdat er geen
testdatabase is. Ga daarom stap voor stap en kijk na elke stap of het klopt. Gaat een stap mis, maak dan het werk af op de
oude manier (zie onderaan); tot en met het opruimen is er niets weg.

## Voor je begint

- Java 21 of nieuwer: `java -version`. Zie `README.md` als `java` niet gevonden wordt.
- Neem `target/hkh-workflow.jar` mee en start hem vanuit een eigen map: `java -jar hkh-workflow.jar` (poort 9000).
- Neem een **back-up van `/cgi-bin/archief/` en `/cgi-bin/misc/permit.lst`** met FTP voordat je iets uploadt.
- Zet in de testmap (`c:\zcbs\invoer`) **alleen de originele bestanden** met hun oude naam. De app geeft ze zelf het
  nummer. Staan er al op nummer hernoemde bestanden, dan krijgen die een tweede nummer.
- Kijk wat het volgende vrije recordnummer in archief is (zoekopdracht `LAST`) en hoeveel cijfers de nummers hebben.
  Gebruik dat als beginnummer. Voor een eerste proef kun je een nummer ver buiten de echte reeks nemen, zoals bij de
  testsessie `90000000`, maar dan komen er wel echte records met dat nummer in archief.

## Instellingen

| Instelling | Waarde |
|---|---|
| Testmap | `c:\zcbs\invoer` |
| Archived-map | `c:\zcbs\backup_invoer_org` (vervangt haar back-up van de invoer) |
| pdf2zcbs.exe | `c:\zcbs\pdf2zcbs\pdf2zcbs.exe` |
| images.exe | `c:\zcbs\images\images.exe` |
| Collectie, bestandstype, resolutie, instelling | `archief`, `a`, `6`, `4` |
| Back-upmap voor 100pct | `c:\zcbs\backup_100pct` |
| Sftp | host, poort, gebruiker en wachtwoord zoals in Total Commander |
| Basismap | `/domains/historischekringheemskerk.nl/public_html` |
| Wachtwoord bij de regel "FTP wachtwoord" | alleen nodig als je met automatisch uitvoeren wilt uploaden |

Klik **Test verbinding**. Controleer dat beide mappen (`archief/ocr` en `archief/pdf`) bestaan, vergelijk de vingerafdruk met die
van Total Commander of de hostingprovider, en zet hem dan pas vast. Zonder vastgezette vingerafdruk wordt er niet geüpload.

## De stappen

De app toont bij elke stap wat hij gaat doen, en waarom hij niet kan als er iets niet klopt. Na elke stap staan er vinkjes
per bestand. Ga pas verder als ze groen zijn.

1. **Hernoemen.** Controleer in `invoer\werk` dat de kopieën met het juiste nummer staan. De originelen blijven staan.
2. **pdf2zcbs.** Start met Automatisch uitvoeren, of Handmatig als je het wilt zien. Controleer: vier groene vinkjes per
   bestand, en kijk in `pdf2zcbs\output\ocr` of de tekst klopt. Staat er een rood vinkje of een melding over een vorige run,
   verwijder dan `output` zelf, nadat je hebt gekeken of er niets in zit dat je nodig hebt.
3. **images.exe.** De vinkjes voor upload en records staan standaard uit. Laat ze uit voor een eerste droge run, en zet ze
   pas aan als de rest klopt. Kijk in het runlog welke antwoorden de app gaf. Vraagt images.exe iets wat de app niet herkent
   (bijvoorbeeld in welke submap de afbeeldingen moeten), dan stopt de app en antwoord je zelf in het veld onder de console.
4. **Upload (sftp).** Controleer in het plan de twee doelmappen. De app weigert als een bestand al bestaat en overschrijft
   nooit. Controleer daarna met Total Commander of de bestanden er staan.
5. **Opruimen.** Maakt een back-up van de 100pct en ruimt dan de mappen van de programma's op. Sla deze stap over als je
   het liever zelf doet, bijvoorbeeld omdat de upload niet is gelukt.
6. **Archiveren.** De originelen gaan naar de archived-map.

## Als een stap mislukt: op de oude manier afmaken

Alles staat zoals bij de handmatige werkwijze, dus je kunt vanaf elke stap verder met `docs/docs-19-oct-testsessie/Stappenplan
archief beeldbank.docx`. De genummerde bestanden staan in `invoer\werk`.

- Na stap 2: de tekst en pdf's staan in `pdf2zcbs\output`. Doe de sftp-overzet met Total Commander en ga verder bij images.exe.
- Na stap 3: kijk met `dir` in `images\small`, `large` en `100pct`. Heb je upload en records aan laten maken, maak ze dan
  **niet** nog een keer aan.
- Na stap 4: de bestanden op de server blijven staan. Opnieuw proberen slaat wat er al staat over.
- Zet een stap die je zelf hebt afgemaakt op "Toch klaar" of "Overslaan", zodat de volgende stappen aan de beurt komen.

## Meenemen terug

De map `logs\` (een log per run) en eventuele meldingen uit de app, zodat de echte programma's in detail bekeken kunnen worden.

# Eerste echte test bij HKH

Dit is de eerste run met de echte `pdf2zcbs.exe` en `images.exe` via de app, **in het echte archief** omdat er geen
testdatabase is. Ga daarom stap voor stap en kijk na elke stap of het klopt. Gaat een stap mis, maak dan het werk af op de
oude manier (zie onderaan); tot het opruimen is er niets weg, en bij het opruimen is er eerst een back-up gemaakt.

## Voor je begint

- Java 21 of nieuwer: `java -version`. Zie `README.md` als `java` niet gevonden wordt.
- Neem `target/hkh-workflow.jar` mee en start hem vanuit een eigen map: `java -jar hkh-workflow.jar` (poort 9000).
- Neem een **back-up van `/cgi-bin/archief/` en `/cgi-bin/misc/permit.lst`** met FTP voordat je iets uploadt.
- Zet in de testmap (`c:\zcbs\invoer`) **alleen de originele bestanden** met hun oude naam. De app geeft ze zelf het
  nummer. Staan er al op nummer hernoemde bestanden, dan krijgen die een tweede nummer.
- **Recordnummers in archief hebben precies vijf cijfers** (`$MAX_OBJECT` is 99999; ZCBS weigert andere lengtes). Gebruik dus geen
  nummers zoals `90000000`. In de serverbestanden van 21 oktober was het hoogste nummer 11933, dus het volgende vrije is
  dan 11934. Controleer dat met `LAST` in ZCBS.
- **De records bestaan al voordat images.exe draait.** Sinds eind februari 2026 maakt Carla ze eerst aan met `NEWRECORD <begin>
  <eind>` (beheerderspagina, optie Q) en laat ze bij images.exe het aanmaken van records uit. Doe dat dus eerst, voor het bereik
  dat je gaat gebruiken. Records 11842 tot en met 11933 zijn op 14 oktober al aangemaakt en hadden toen nog geen
  images.exe-sessie: gebruik die nummers niet per ongeluk voor andere bestanden, en kijk met `IMAGENO` welke records nog geen
  afbeelding hebben.

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

**Stap 0, met de hand in ZCBS:** maak de lege records aan voor het bereik dat je gaat verwerken (`NEWRECORD <begin> <eind>`, hoogstens
1000 tegelijk, vijf cijfers). De app doet dit niet en controleert het nog niet; laat bij images.exe de vinkjes voor upload en
records daarom uit, tenzij je uploaden bewust wilt proberen.

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

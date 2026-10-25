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
- **Wie maakt de records aan?** Volgens Robbert maakt Carla geen records met de hand aan: het stappenplan (`Stappenplan archief
  beeldbank.docx`) noemt geen `NEWRECORD`, en images.exe maakt ze zelf aan in stap D (vraag "AANMAKEN en UPLOADEN van de bijbehorende
  ... nieuwe lege DATABASE RECORDS? [J/N]", antwoord J). images.exe zet ze via FTP in de update-database op de server. Een eerdere
  afleiding uit de aanmaaktijden (dat Carla sinds februari 2026 `NEWRECORD` gebruikt) is daarmee niet meer het uitgangspunt.
  Records die al bestaan mag images.exe niet nog eens aanmaken: controleer vooraf (met `LAST` of de bewerkpagina) dat de nummers
  van deze batch nog niet bestaan. Records 11842 tot en met 11933 bestaan al sinds 14 oktober.

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

**Stap 0, vooraf controleren:** de nummers van deze batch mogen nog niet als record bestaan, want images.exe maakt ze zelf aan.
De app controleert dat nog niet; kijk met `LAST` in ZCBS wat het hoogste nummer is. Laat bij images.exe de vinkjes voor upload en
records uit voor een droge run, en zet ze pas aan als je het echt wilt (dat schrijft in het echte archief).

De app toont bij elke stap wat hij gaat doen, en waarom hij niet kan als er iets niet klopt. Na elke stap staan er vinkjes
per bestand. Ga pas verder als ze groen zijn.

0. **Bestanden ophalen** (alleen als de bronmap is ingesteld). Vink op de testpagina aan welke pdf's je wilt verwerken (Ria geeft dat aan); ze staan op de server in `K:\scabslelly\<persoon>\<map>`. De app kopieert ze naar de testmap en onthoudt wie het gescand heeft. De originelen op de server blijven staan.
1. **Hernoemen.** Controleer in `invoer\werk` dat de kopieën met het juiste nummer staan. De originelen blijven staan.
2. **pdf2zcbs.** Start met Automatisch uitvoeren, of Handmatig als je het wilt zien. Controleer: vier groene vinkjes per
   bestand, en kijk in `pdf2zcbs\output\ocr` of de tekst klopt. Staat er een rood vinkje of een melding over een vorige run,
   verwijder dan `output` zelf, nadat je hebt gekeken of er niets in zit dat je nodig hebt.
3. **images.exe.** De vinkjes voor upload en records staan standaard uit. Laat ze uit voor een eerste droge run, en zet ze
   pas aan als de rest klopt. Kijk in het runlog welke antwoorden de app gaf. Vraagt images.exe iets wat de app niet herkent
   (bijvoorbeeld in welke submap de afbeeldingen moeten), dan stopt de app en antwoord je zelf in het veld onder de console.
4. **Upload (sftp).** Controleer in het plan de twee doelmappen. De app weigert als een bestand al bestaat en overschrijft
   nooit. Controleer daarna met Total Commander of de bestanden er staan.
5. **Records bewerken.** Log in met je eigen ZCBS-naam en wachtwoord (dat bewaart de app alleen in het geheugen) en klik op Uitvoeren. De app controleert dat de records bestaan, daarna open je ze één voor één op de pagina Records: het openen zet de vergrendeling van ZCBS, je ziet een voorbeeld van de wijziging, na het opslaan leest de app het record terug. Een record dat je overslaat zet je op "later". **In het echte archief schrijft elke opslag in ZCBS en gaat er een e-mail naar de beheerder**; begin met één record dat net door images.exe is aangemaakt en controleer het in ZCBS zelf. Gaat er iets mis, dan is er altijd de link "Open in ZCBS". Stel het adres van ZCBS in bij Instellingen (voor oefenen: `http://localhost:8081`).
6. **Opruimen.** Maakt een back-up van de 100pct en ruimt dan de mappen van de programma's op. Sla deze stap over als je
   het liever zelf doet, bijvoorbeeld omdat de upload niet is gelukt.
7. **Archiveren.** De originelen gaan naar de archived-map.

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

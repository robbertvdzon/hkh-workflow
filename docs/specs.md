# ZcbsServer (hkh-workflow) — specificaties

> **Voor een nieuwe agent of ontwikkelaar:** lees dit document helemaal. Het bevat de context, alle gemaakte
> keuzes, wat er al gebouwd is en wat de volgende stappen zijn. Achtergrond over ZCBS zelf (hoe het werkt,
> wat images.exe en pdf2zcbs.exe precies doen) staat in [zcbs-achtergrond.md](zcbs-achtergrond.md).
> De bronnen: de ZCBS-handleidingen in deze map en het UX-ontwerp [ZcbsServer UX.pdf](ZcbsServer%20UX.pdf).

Laatst bijgewerkt: 30 oktober 2026.

> **30 oktober 2026 — de eerste versie is simpeler gemaakt** na overleg met Jan (HKH): de flow volgt zijn stappenplan, zonder het invullen van gegevens,
> en het bewerken van records is een aparte knop. De app begint nu met inloggen bij ZCBS en het kiezen van een collectie, en er is een beheerderslogin.
> De app uploadt tekst en pdf's sinds 30 oktober met **ftp** (zoals images.exe), niet meer met sftp; waar hieronder "sftp" staat bij het huidige proces of de eerste testketen, is dat de handmatige werkwijze of de oude stand.
> Alles daarover staat in [specs-nieuwe-bestanden.md](specs-nieuwe-bestanden.md). Waar dit document daar van afwijkt (de acht fasen met bewerken, de
> archived-map, de demoschermen, het woord "batch"), geldt het nieuwe document. §5.3 t/m §5.5 zijn hieronder bijgewerkt.

---

## 1. Context

- **Organisatie:** Historische Kring Heemskerk (HKH), website historischekringheemskerk.nl.
- **Eigenaar/ontwikkelaar:** Robbert (systeembeheerder bij HKH, Kotlin/Spring Boot-ontwikkelaar). Communiceert in het
  Nederlands; documentatie, UI-teksten en commitberichten zijn Nederlands.
- **Betrokkenen in het huidige proces:** Ria (scant), Carla (beschrijft en voert in), Jan (mede-ontwikkelaar; moet de
  code kunnen lezen).
- **ZCBS** (Zijper Collectie Beheer Systeem) is het collectiebeheersysteem van HKH: Perl/CGI met platte tekstdatabases,
  zeven collecties. Zie [zcbs-achtergrond.md](zcbs-achtergrond.md).
- **Verwante repo:** `~/git/hkh` (GitHub `robbertvdzon/hkh`): de eigen HKH-app (Kotlin/Spring Modulith-backend + Flutter),
  o.a. met een module die de ZCBS-collecties scrapet naar Postgres. Die repo is de **stijlreferentie** voor deze repo
  (pom-opbouw, Modulith-conventies, tests).

### Terminologie (gekozen)

| Term | Betekenis |
|---|---|
| **Ingest** | Technische naam voor het hele proces (OAIS-term): van scan tot record in ZCBS. |
| **Digitaliseren** | Het scannen. |
| **Tekstherkenning (OCR)** | Tekst uit een gescande afbeelding/pdf halen. Voor handschrift: HTR (bijv. Transkribus). |
| **Beschrijven** | Metadata toevoegen (titel, datum, trefwoorden …). |
| **Invoeren** | Het record in ZCBS zetten. |

Proces voor gebruikers: *digitaliseren → tekstherkenning → beschrijven → invoeren in ZCBS*.

---

## 2. Huidig proces (handmatig, zoals Robbert het beschreef)

**Scannen**
1. Ria zet de pdf's op het netwerk in `K:\scabslelly`, bijvoorbeeld `K:\scabslelly\map55\` met `xx01.pdf` t/m `xx56.pdf`.
2. Ze meldt aan Carla dat de bestanden verwerkt kunnen worden.

**Beschrijven en invoeren**
1. Carla zoekt in ZCBS het laatste recordnummer op (kan met zoekcommando `LAST`).
2. Ze kopieert de bestanden naar `c:\zcbs\invoer\[nummer].pdf`, oplopend vanaf laatste recordnummer + 1.
3. Ze start **pdf2zcbs.exe** (bestanden gaan naar `c:\zcbs\pdf2zcbs`; er komt een tekstbestand met de tekst uit de pdf).
4. Ze start **images.exe**: maakt small/large/100pct en zet "het bestand in de database".
5. Ze controleert of de 3 afbeeldingen en het tekstbestand er zijn.
6. Total Commander: bestanden naar `c:\zcbs\images`; **sftp** naar de ZCBS-server; kopie naar een back-upplek.
7. In ZCBS: record opzoeken en metadata invoeren.

**Open vragen over dit proces (nog aan Carla stellen):**
- Maakt de scanner/scansoftware al doorzoekbare pdf's (met tekstlaag)? pdf2zcbs doet zelf **geen** OCR.
- Antwoordt Carla bij images.exe "N" op uploaden? images.exe gebruikt **FTP**, Carla uploadt met **sftp**; misschien
  accepteert de server geen gewone FTP meer.
- Als images.exe niet uploadt: hoe komen de records dan in `updates.txt` (handmatig uploaden van updates.txt? `NEWRECORD`?)?
- Waar zet ze de pdf en het tekstbestand op de server (pdf-map en `$OCR_MAP`)?
- Wat verplaatst ze precies naar `c:\zcbs\images` (images.exe verwerkt wat vóór het starten in `\images` staat)?
- De oorspronkelijke bestandsnaam (`xx01.pdf`) gaat bij het hernoemen verloren; bij type-A-bestanden vult images.exe
  veld 27 niet.

### Het echte stappenplan (Carla's Word-document, 19 oktober 2026)

Uit de testsessie van 19 oktober bij HKH (bestanden: `docs/docs-19-oct-testsessie/`, nog niet in git). Dit is de werkwijze die
de app moet nabootsen. Windows-mappen bij HKH: `c:\zcbs\invoer`, `c:\zcbs\pdf2zcbs`, `c:\zcbs\images`.

1. `copy c:\zcbs\invoer\*.* c:\zcbs\pdf2zcbs` en `pdf2zcbs.exe` draaien. Per pdf komt in `output\`: `ocr\<nr>.txt`,
   `pdf\<nr>.pdf` (de pdf wordt **verplaatst**), `small\<nr>.jpg` (250 breed, in de test 250×193), `large\<nr>.jpg` (hoogte 500,
   in de test 647×500) en `log\`. De ocr-tekst is latin-1.
2. **Niet de pdf's maar de large-jpg's gaan naar images.exe**: `copy c:\zcbs\pdf2zcbs\output\large\*.jpg c:\zcbs\images\images`.
   images.exe maakt daar small, large en 100pct van (type **A**, het bestandstype wordt met `a` geforceerd; resolutie **6**).
   Omdat de large-jpg al klein is, is de 100pct ongeveer even klein; de pdf is het volwaardige document.
3. Controle met `dir` op `small`, `large` en `100pct` in `c:\zcbs\images`.
4. Met Total Commander (**sftp**) naar de server: `output\ocr` naar `/domains/historischekringheemskerk.nl/public_html/archief/ocr` en
   `output\pdf` naar `.../archief/pdf`. De afbeeldingen zet images.exe zelf op de server (FTP; in `images.log`
   staat de upload in vrijwel elke sessie). De records worden sinds eind februari 2026 **vooraf** met `NEWRECORD` aangemaakt, zie hieronder.
5. Back-up: `100pct` naar `c:\zcbs\backup_100pct`, invoer naar `c:\zcbs\backup_invoer_org`.
6. Opruimen: `rmdir /S /Q output` (pdf2zcbs), `rmdir /S /Q 100pct small large` (images), `del c:\zcbs\images\*.txt` (het
   images.exe-log), `del c:\zcbs\images\images\*.*`, `del c:\zcbs\invoer\*.*`.

Antwoorden uit de testlog van images.exe (zonder upload, want er is geen testdatabase): type `a`, resolutie `6`, upload `n`,
records `n`, verwijderen `n`, `n`. Versie bij HKH: **images.exe 3.27** (de interne lijst met beeldbanken is van 21-01-2022) en
**pdf2zcbs 1.16**; de handleidingen beschrijven 3.30 en 1.16. Bij pdf2zcbs staat een `pdf2zcbs.ini` (het log meldt "ini aanwezig
en verwerkt"), dus de vragen komen daar waarschijnlijk niet; onze automatische regels doen dan niets.

Antwoorden op eerdere open vragen: de sftp-mappen zijn hierboven bekend. FTP voor images.exe lijkt dus nog te werken.

### Wat de serverbestanden laten zien (download van 21 oktober 2026, in `docs/server/`, niet in git)

- **Recordnummers zijn vijf cijfers.** `$MAX_OBJECT = 99999` in de config; het hoofdprogramma weigert andere lengtes. Archief heeft
  records 10000 tot en met 11933 (1908 stuks, 26 gaten). Nummers van acht cijfers, zoals `90000000` uit de testsessie, kunnen niet.
- **Paden:** `$OCR_MAP = archief/ocr` en `$PDF_VIEWER = archief/pdf`, relatief aan de webroot. ZCBS zoekt de tekst door die map te
  doorzoeken en leidt het recordnummer af uit de bestandsnaam; de pdf wordt gevonden als `archief/pdf/<nummer>.pdf`. `small` en
  `large` staan in `public_html/archief`, de `100pct` in het beveiligde `cgi-bin/archief/100pct`. Archief is type `$ZCBS = 2` (publicaties).
- **Nieuwe records** bestaan uit alleen id's, het nummer en een aanmaakstempel `jjmmdd-uu:mm|system`; de titel is leeg en veld 27
  (originele bestandsnaam) is nergens gevuld. "Nog te beschrijven" is dus: lege titel, of een lege beschrijfdatum (veld 33).
  `updates.txt` krijgt bij elke opslag een regel erbij; de laatste regel per nummer wint.
- **Hoe de records ontstaan (uit `images.log`, 103 archief-sessies sinds februari 2022):** tot en met 25 februari 2026 maakte
  images.exe de records aan (stappen `up1-up2`, in 49 sessies). Sinds eind februari 2026 maakt Carla ze ongeveer tien minuten
  vóór images.exe aan met `NEWRECORD` (hetzelfde `system`-stempel, aantal gelijk aan de batch) en draait images.exe zonder
  records-stap. `NEWRECORD` kent een maximum van 1000, weigert bestaande nummers en vereist vijf cijfers.
- **images.exe uploadt in vrijwel elke sessie** (stap `ftp2-C`), dus FTP werkt bij HKH. Carla gebruikt versie 3.27, Marcel 3.34 en Jan
  Kamps gebruikte op 13 oktober 2026 3.62 voor de beeldbank. Op de ZCBS-site staat 3.62 (29-10-2025) als huidige; pdf2zcbs 1.17
  (HKH heeft 1.16).
- **Opnieuw draaien is gewoon:** op 19 van 76 dagen was er meer dan één sessie, bijna altijd met hetzelfde aantal afbeeldingen. In
  9 sessies moest opnieuw worden ingelogd. Overschrijven moet in de app dus een bewuste keuze per run kunnen zijn.
- **Batchgrootte:** mediaan 11 afbeeldingen, maximaal 108. De records van 14 oktober 2026 (11842 tot en met 11933, 92 stuks) hadden
  nog geen images.exe-sessie; voor de 104 records van 15 oktober 2025 is er ook geen sessie met dat aantal.
- **Tekenset:** de master is latin-1, maar 40 records bevatten UTF-8-resten (`Ã©` voor é). Nooit UTF-8 schrijven in de database.
- **Back-ups:** het laatste `backup.exe`-run in `backup.log` is van 1 januari 2024 (daarvoor rond twee per jaar).
- **Voorbeeld-ocr:** `ocr/10000.txt` bevat alleen de kop van pdf2zcbs; de pdf is een scan zonder tekstlaag. pdf2zcbs doet geen OCR.
- **Images 2.0** (`zcbs_frontend`, versie 1.1.6.14) is een webversie van images.exe voor afbeeldingen, met een PHP-backend. Niet
  ingesteld bij HKH (`FIRST_START`). Geen pdf of OCR. De backend zelf is niet gedownload.
- Beveiligingspunten voor Jan of de ZCBS-groep: een wachtwoord en privénummer staan leesbaar in `cfg-cbs.pl`; de `.js.map` van Images
  2.0 bevat een vaste API-sleutel en een debug-sleutel en de app stuurt na elke upload een logregel met sleutel onversleuteld naar
  een externe host; `phpinfo.php` en `printenv.pl` staan op de server.

---

## 3. Doelproces: "ZcbsServer" (voorstel van Robbert)

- Een nieuwe applicatie op een server binnen HKH, code op GitHub, leesbaar voor Jan en Robbert, grotendeels door
  Claude Code geschreven.
- **Scannen verandert niet**: bestanden blijven naar `K:\scabslelly` gaan. Meerdere mensen scannen, **ieder in een eigen map**.
- De server scant elke minuut `K:\scabslelly`, houdt bij welke bestanden er zijn en of ze al in ZCBS staan
  (eigen database; zoeken in ZCBS op bestandsnaam werkt niet omdat bestanden hernoemd worden).
- Webinterface (alleen binnen HKH): Carla ziet wat nog verwerkt moet worden, selecteert pdf's, kiest de **collectie**
  en drukt op verwerken. Er start een **batch** met datum/tijd als ID; de UI toont dat hij bezig is.
- De server doet pdf2zcbs.exe, images.exe en de upload. Daarna krijgt Carla **links naar de records in ZCBS** en
  beschrijft ze die één voor één met de hand.
- Overzicht van **alle eerdere batches**.

**Aanbevelingen uit de review (nog niet gebouwd, wel meenemen):**
1. Externe exe's aansturen is het kwetsbaarste deel (zie §6 en §9). Alternatief op termijn: de stappen zelf doen
   (pdftotext, thumbnails met ImageMagick/pdftoppm, upload via SFTP, records toevoegen aan `updates.txt`). Mag
   licentietechnisch zolang er geen ZCBS-code wordt overgenomen.
2. **Echte OCR** toevoegen (bijv. OCRmyPDF met `-l nld`) zodat er altijd een tekstlaag is.
3. **Recordnummers** centraal bepalen en vastleggen; nooit twee batches tegelijk; rekening houden met nummers die
   iemand met de hand in ZCBS aanmaakt.
4. **Eigen tabel** origineel pad → collectie + recordnummer (en eventueel origineel in veld 27). Bij ingebruikname de
   al verwerkte bestanden eenmalig als "gedaan" markeren.
5. **Gelijktijdig schrijven in `updates.txt`** (ZCBS-bewerkingen terwijl een batch records toevoegt) risico klein houden.
6. **Instellingen per collectie** (mappen, voorgevulde velden, thumbnailformaat 100×100 documenten / 250×250 foto's).
7. **Back-upstap** van de originelen behouden; originelen in `K:\scabslelly` laten staan tot gearchiveerd.
8. **Foutafhandeling/herstarten** per bestand; geen dubbele records.

---

## 4. UX-ontwerp

Ontwerp: [ZcbsServer UX.pdf](ZcbsServer%20UX.pdf) (export van een Claude Design-canvas "ZcbsServer UX").
Zes schermen, desktop 1280 breed, Nederlandse teksten, rustige "archief"-stijl:

| # | Scherm | Inhoud |
|---|---|---|
| 1 | Inloggen | Gebruikersnaam/wachtwoord; "alleen bereikbaar binnen het HKH-netwerk". (Nog niet gebouwd: v1 heeft geen login.) |
| 2 | Te verwerken | Bestanden gegroepeerd per scanmap/persoon (bijv. `K:\scabslelly\Ria\map57`), filter per map, vinkjes (ook per groep), kolommen pagina's / gescand / tekstlaag ("Tekst gevonden" of "Server doet OCR"), melding bij eerder mislukte bestanden. Onderbalk: aantal geselecteerd, **collectie kiezen (verplicht)**, knop Verwerken. |
| 3 | Batch controleren | Origineel → nieuw recordnummer, collectie, nummerreeks, afbeeldingsformaten, optionele opmerking, "wat de server doet" (5 stappen), knoppen Annuleren / Batch starten. |
| 4 | Batch bezig | Batch-ID (`B-20260925-1441`), voortgangsbalk met 5 stappen (tekstherkenning, afbeeldingen, uploaden, records in ZCBS, back-up), status per bestand, "je kunt deze pagina sluiten". |
| 5 | Batch klaar | Tellers (in ZCBS gezet / mislukt / beschreven), knop "eerste onbeschreven record", per bestand link "Openen in ZCBS", status "Beschreven / Nog te beschrijven" (server controleert of het record een titel heeft), mislukte bestanden met "Opnieuw proberen". |
| 6 | Alle batches | Lijst met datum, door, collectie, aantal, resultaat, beschreven-voortgang, filter op collectie en "alleen met onbeschreven records". |

Collecties in de keuzelijst: artikelen, archief, beeldbank (foto's), library (bibliotheek), bidprent, objecten, transcripties.
Aanname (te controleren): ZCBS-recordlink = `https://www.historischekringheemskerk.nl/cgi-bin/<collectie>.pl?ident=<nummer>`.

---

## 5. Gebouwd: versie 1 (testversie)

Doel van v1: **testen of images.exe en pdf2zcbs.exe vanuit een Java-applicatie te gebruiken zijn**, met goede logging.
De UX-schermen staan er met demo-gegevens; de echte batchverwerking komt later.

### 5.1 Techniek (gekozen)

| Onderwerp | Keuze |
|---|---|
| Vorm | **Eén runnable jar** (`target/hkh-workflow.jar`) |
| Taal/framework | **Kotlin 2.2.21, Spring Boot 4.0.6, Spring Modulith 2.0.0, Java 21**, Maven — zelfde opzet als `~/git/hkh/backend` |
| Frontend | **Server-side HTML: Thymeleaf + htmx** (htmx 2.0.11 meegeleverd in `static/js`, geen CDN) |
| Opslag instellingen | **JSON-bestand naast de jar** (`hkh-workflow-settings.json`); nog geen database |
| Login | **Geen** in v1 |
| Netwerk | Luistert op **0.0.0.0** (bewust: ook vanaf andere systemen te benaderen tijdens het testen) |
| Poort | **9000** |
| Package | `nl.vdzon.hkhworkflow` |
| Jackson | Spring Boot 4 = **Jackson 3** (`tools.jackson.databind.ObjectMapper`). Let op: `JsonNode.map {}` in Kotlin pakt de Jackson-methode, niet die van Kotlin — gebruik een lus. |

Configuratie (`application.properties`, alles via omgevingsvariabelen te overschrijven):

| Property | Env | Standaard |
|---|---|---|
| `server.address` | `HKH_WORKFLOW_ADDRESS` | `0.0.0.0` |
| `server.port` | `HKH_WORKFLOW_PORT` | `9000` |
| `hkh.workflow.settings-file` | `HKH_WORKFLOW_SETTINGS_FILE` | `hkh-workflow-settings.json` (relatief aan werkmap) |
| `hkh.workflow.console-charset` | `HKH_WORKFLOW_CONSOLE_CHARSET` | `IBM850` (Nederlandse DOS-box) |
| `hkh.workflow.log-dir` | `HKH_WORKFLOW_LOG_DIR` | `logs` |

### 5.2 Modules (Spring Modulith)

Elke module heeft een `package-info.java` met `@ApplicationModule(allowedDependencies = {...})`, geen wildcards
(afgedwongen door `ModulithArchitectureTest`, zoals in hkh). Controllers staan in een `api`-subpackage.

| Module | Mag gebruiken | Inhoud |
|---|---|---|
| `settings` | – | `WorkflowSettings`, `AnswerRule`, `SourceRoot`, `SettingsService` (JSON lezen/schrijven, controles), `WorkflowProperties` (o.a. het beheerderswachtwoord); `api/SettingsController` |
| `exerunner` | – | `ExeRunner`: start één extern programma tegelijk, leest uitvoer, stuurt invoer, automatische antwoorden, logbestand per run |
| `ftp` | `settings` | `FtpService`: ftp-upload (Apache Commons Net) zoals images.exe, nooit overschrijven of verwijderen; `api/FtpController` (Test verbinding) |
| `zcbs` | `settings` | De client voor ZCBS: `ZcbsClient` (inloggen, `LAST`, de vrije nummers, bestaat het record, formulier ophalen, opslaan; cookies in het geheugen), `FreeNumbers` (vrije reeksen en de controle of een reeks bestanden aaneengesloten past), `FormParser` (Jsoup), `ItemForm` (alle velden die een browser post plus de zichtbare velden), `RecordRebuilder` (de controle tegen `a0`), `ZcbsText` (tekens uit Word en woorden die ZCBS weigert), `ZcbsPages` (welke pagina is dit) |
| `items` | `zcbs`, `settings` | `ItemEditService`: inloggen bij ZCBS (in het geheugen), de vrije nummers, en de records die Carla kiest bewerken (openen, voorbeeld, opslaan met opnieuw inloggen, conflictcontrole en teruglezen; later); `api/ItemsController` en de pagina's Records |
| `testrun` | `settings`, `exerunner`, `ftp`, `items`, `zcbs` | `TestRunService` (de stappenketen met controles en de fasen van Carla), `SourceFilesService` (bladeren door de mappen met scans en kopiëren), `ProcessedLog` (wat al verwerkt is), model/views, `sequenceNumbers`; `api/NewFilesController` (pagina Nieuwe bestanden en Verwerkt) en `api/TestController` (technisch) |
| `ux` | `settings`, `items` | Het begin van de app: `api/ShellController` (inloggen, collectie, acties, beheerderslogin), `AdminAccess`, `WebConfig` (wie waar mag komen) |

### 5.3 Routes

| Methode | Pad | Wat |
|---|---|---|
| GET | `/`, `/collectie`, `/collectie/{naam}` | Inloggen bij ZCBS, de collectie kiezen, en dan "Nieuwe bestanden in ZCBS zetten" of "ZCBS records aanpassen". Pagina's van Carla sturen naar `/` zolang je niet bent ingelogd |
| GET | `/nieuw` (`bron`, `pad`) | **Het hoofdscherm**: de flow in zeven fasen (controleren, bestanden kiezen, nummers, pdf2zcbs, images.exe, upload, back-up), met een lijst van wat er gemaakt is en een controle na pdf2zcbs en images.exe |
| POST | `/nieuw/start`, `/nieuw/resten` (`actie` = verplaats, verwijder of accepteer), `/nieuw/kopieer` (`bron`, `pad`, `files`), `/nieuw/bestanden/verwijder`, `/bevestig`, `/andere`, `/nieuw/voorbereiden` (`start`), `/nieuw/gecontroleerd` (`stap`), `/nieuw/opnieuw` | Bestanden uit de map naar de invoermap kopiëren, een kopie weghalen, de lijst bevestigen of opnieuw kiezen, nummers geven (vrije reeks controleren, `werk`-kopieën maken), de uitvoer als gecontroleerd markeren, opnieuw beginnen |
| GET | `/nieuw/resultaat/{soort}/{nummer}` | Een bestand uit de uitvoer (afbeelding, pdf of tekst) om te controleren; alleen voor nummers van de lopende doorloop |
| GET | `/verwerkt` | Wat al verwerkt is, per doorloop |
| GET/POST | `/beheer`, `/beheer/inloggen`, `/beheer/uitloggen` | Inloggen als beheerder (wachtwoord uit het configuratiebestand); nodig voor `/instellingen` en `/test` |
| GET | `/records`, `/records/{nr}` | De gekozen records en het bewerkformulier van één record (het openen zet de vergrendeling van ZCBS) |
| POST | `/records/kies` (`nummers`) | Een recordnummer, een reeks (`11934-11945`) of meer daarvan kiezen om te bewerken |
| POST | `/records/{nr}/voorbeeld`, `/opslaan`, `/terug`, `/conflict`, `/later` | Voorbeeld van de wijziging, opslaan (opnieuw inloggen, formulier opnieuw ophalen, conflictcontrole, posten, teruglezen), terug naar het formulier, conflict oplossen, record op later zetten |
| POST | `/zcbs/login`, `/zcbs/logout` | Inloggen bij ZCBS (naam en wachtwoord, alleen in het geheugen) |
| GET/POST | `/instellingen` | Instellingen
| GET | `/test` | Testpagina |
| POST | `/test/start` (`startNumber`), `/test/reset` | Testrun starten / nieuwe test |
| POST | `/instellingen/ftp-test` | Verbinding testen (logt in en controleert de mappen, schrijft niets) |
| POST | `/test/steps/{id}/run` (`mode=IN_APP\|WINDOW`, `auto=true\|false`) | Stap uitvoeren; automatisch geeft de vaste antwoorden |
| POST | `/test/steps/{id}/skip`, `/done`, `/retry` | Overslaan / toch klaar / opnieuw |
| GET | `/test/console` | htmx-fragment, elke seconde zolang er iets draait; stuurt `HX-Refresh` als het klaar is |
| POST | `/test/console/input` (`line`), `/test/console/stop` | Invoer sturen / programma stoppen |
| GET | `/test/log` | Logbestand van de laatste run (text/plain) |
| GET | `/test/runs/log?file=..` | Logbestand van een eerdere run als htmx-fragment (onder de runlijst) |
| POST | `/test/runs/delete` (`file`), `/test/runs/delete-all` | Eén logbestand / alle logbestanden verwijderen (niet dat van een lopend programma) |

### 5.4 Instellingen

Opgeslagen in JSON; velden:

- `testFolder` — de invoermap (`c:\zcbs\invoer`): hier komen de gekozen pdf's te staan voordat ze verwerkt worden.
- `pdf2zcbsExe`, `imagesExe` — volledige paden. Mag elk uitvoerbaar bestand zijn (op Mac/Linux een script met
  `chmod +x`); de pagina waarschuwt als een pad niet bestaat of niet uitvoerbaar is.
- `sourceFolders` — de mappen met scans, één per regel `naam=pad` (`Jelly=K:\scans jelly`, `Ria=K:\scans ria`); Carla bladert erdoor en de naam laat zien wie het scande.
- `useTestZcbs` (standaard **aan**; een checkbox in de UI) — de enige keuze voor de omgeving: aan = de test-ZCBS in Docker (`tools/docker/zcbs`), uit = het echte archief. Hieruit volgen `zcbsBaseUrl` (`http://localhost:8081` of `https://www.historischekringheemskerk.nl`; het script is `/cgi-bin/<collectie>.pl`) en `ftpHost`/`ftpPort` (`localhost:21` of `ftp.historischekringheemskerk.nl:21`); ze worden bij het laden ingevuld en niet apart ingesteld. Waar de test-ZCBS draait staat in `hkh.workflow.test-zcbs-url`, `test-ftp-host` en `test-ftp-port` (standaard localhost, op de testlaptop het IP van de Mac, zie `tools/windows-testlaptop`). Een oud bestand zonder deze keuze telt als test, tenzij het adres het echte archief is. Wisselt de omgeving, dan vervalt de inlog bij ZCBS.
- `perlioUnbuffered` (standaard **aan**) — start de programma's met `PERLIO=:unix` (zie §6).
- De collectie is altijd `archief` (niet instelbaar): het antwoord op "Kies de ZCBS-toepassing" en de basis voor de ftp-mappen. De startpagina toont alle zeven collecties van HKH; alleen het archief is te kiezen.
- `backup100pctFolder` en `backupPdfFolder` — back-up van de 100pct en van de pdf's met recordnummer vóór het opruimen (op K:).
- `ftpPassword` (verborgen in de UI, zoals andere wachtwoorden) — één wachtwoord voor de hele server (alle collecties), voor images.exe en de ftp-upload van de app. De gebruiker is altijd `ZCBS@historischekringheemskerk.nl` en de basismap altijd `/public_html`; de mappen voor tekst en pdf zijn altijd `<collectie>/ocr` en `<collectie>/pdf`. Staat het wachtwoord er niet, dan neemt de app het over als iemand het bij Handmatig uitvoeren van images.exe typt. Gewone ftp is niet versleuteld.
- `collections` — per collectie (alle zeven, ook die de app nog niet gebruikt) `imagesResolution` (het antwoord op "Kies 1,2,3 of 6,7,8"; standaard `6`, voor het archief ook `6`) en de back-upmappen `backup100pctFolder` en `backupPdfFolder`. Een oud bestand zonder `collections` geeft zijn back-upmappen en zijn antwoord op "Kies 1,2,3" aan het archief.
- **Geen antwoordtabellen meer.** De vragen en antwoorden van pdf2zcbs.exe en images.exe staan vast in de code (`ExeAnswers`); alleen de resolutie (per collectie) en het ftp-wachtwoord komen uit de instellingen. pdf2zcbs.exe vraagt bij HKH niets (`pdf2zcbs.ini`). Een onbekende vraag beantwoordt de app niet: hij stopt en toont de vraag.

### 5.5 Testworkflow (pagina Test)

> **Bijgewerkt 30 oktober 2026.** De flow van Carla staat in [specs-nieuwe-bestanden.md](specs-nieuwe-bestanden.md) en draait op dezelfde stappen en controles.
> De stappen "records bewerken" en "archiveren" zijn eruit (bewerken is een aparte knop; de originelen blijven staan), en het opruimen maakt nu ook een
> back-up van de pdf's en schrijft het verwerkt-log. Hieronder staat de technische pagina `/test` (alleen beheerder), met de stappen per bestand.
Bedoeld voor de eerste echte run bij HKH, **in het echte archief** omdat er geen testdatabase is. Daarom volgt de flow Carla's
stappenplan (§2), draait elke stap pas na jouw goedkeuring, en blijft alles op schijf zo staan dat een stap die misgaat op de
oude manier kan worden afgemaakt. Er wordt pas iets verwijderd of gearchiveerd als de stappen daarvoor kloppen.

0. **Bestanden kiezen** (vóór de run, op de pagina Nieuwe bestanden): bladeren door de mappen met scans (`sourceFolders`) en de aangevinkte pdf's naar de invoermap kopiëren (nooit overschrijven, de originelen blijven staan). De app onthoudt per bestand uit welke bronmap het kwam (Jelly of Ria, bestand `herkomst.tsv` in de invoermap). Op de technische pagina zet je de pdf's zelf in de invoermap.
1. Vraagt een **beginnummer** (alleen cijfers; voorloopnullen blijven: `01650` → `01651`).
2. Neemt alle `*.pdf` in de testmap (niet recursief, gesorteerd op naam). Stappen, in volgorde: per bestand **hernoemen**,
   één keer **pdf2zcbs**, één keer **images**, één keer **upload**, één keer **back-up en opruimen** (n + 4 stappen). Beide programma's draaien één keer voor alle bestanden samen, want ze verwerken alles wat in hun map staat en
   pdf2zcbs leegt zijn `output/` bij elke start.
3. **Elke stap start de gebruiker zelf.** De kaart toont de eerstvolgende stap met **wat hij gaat doen** (paden, aantallen,
   antwoorden) en **waarom hij nu niet kan** (rood, knoppen uit). Daarna kijk je in de tabel of het klopt.
4. **Controle per bestand** onder elke rij in de tabel: vinkjes voor tekst, pdf, small en large (pdf2zcbs), small, large en
   100pct (images), tekst en pdf op de server, en de back-up. Groen = het bestand bestaat en is niet leeg, rood = ontbreekt,
   grijs = stap nog niet aan de beurt. Na het opruimen verdwijnen de lokale groepen.
5. Een exe-stap is pas **klaar** als de verwachte bestanden er zijn; exitcode 0 alleen is niet genoeg (Perl-programma's geven
   die bijna altijd). Ontbreekt er iets, dan is de stap mislukt met de lijst van ontbrekende bestanden.

| Stap | Wat er gebeurt | Weigert als |
|---|---|---|
| Hernoemen | **Kopie** van het origineel naar `<testmap>\werk\<nummer>.pdf`. Origineel blijft staan. | doel bestaat al, origineel weg |
| pdf2zcbs.exe | Kopieert alle `werk\<nummer>.pdf` naar de map van pdf2zcbs en start het programma één keer in die map, zonder argumenten. Verwacht per bestand tekst, pdf, small en large in `output\`. | er andere pdf's in de map staan; `output\` resultaten van een andere run bevat (het programma wist die map bij de start) |
| images.exe | Kopieert de **large-jpg's** uit `pdf2zcbs\output\large` naar `<map van images.exe>\images` en start het programma één keer. Verwacht per bestand small, large en 100pct. Wist en overschrijft nooit. | large-jpg's ontbreken; er andere bestanden in `images\` staan; `small`, `large` of `100pct` oude resultaten bevatten (die zouden opnieuw worden geüpload) |
| Upload (ftp) | Zet tekst en pdf's op de server in `<collectie>/ocr` en `<collectie>/pdf`. **Eerst een controle** van alles (mappen bestaan, geen bestand bestaat al), pas dan uploaden onder een tijdelijke naam, hernoemen, rechten 644 en de grootte controleren. Nooit overschrijven, nooit verwijderen, nooit mappen aanmaken. Bij opnieuw proberen worden al geüploade bestanden overgeslagen. | ftp niet volledig ingesteld, lokale bestanden ontbreken, doelmap ontbreekt, een doelbestand bestaat al |
| Back-up en opruimen | **Eerst** de 100pct en de pdf's (met recordnummer) kopiëren naar de back-upmappen en de grootte controleren, **dan** verwijderen: `pdf2zcbs\output`, `images\small`, `large`, `100pct`, de eigen bestanden in `images\images`, `log-*.txt` in de images-map, de pdf's in de map van pdf2zcbs en de kopieën in `werk` en in de invoermap. Alleen wat bij deze doorloop hoort. Daarna komen de bestanden in het verwerkt-log. De originelen op K: blijven staan. | back-upmappen ontbreken of bestaan niet, een 100pct of pdf ontbreekt. Waarschuwt als de upload is overgeslagen |

**images.exe en de keuzes die in productie schrijven.** Bij Automatisch uitvoeren staan er twee vinkjes, standaard **uit**:
*Afbeeldingen uploaden (FTP)* en *Lege records aanmaken*. Records aanmaken kan alleen samen met uploaden, en uploaden vraagt
dat het FTP-wachtwoord in de regels bij Instellingen staat. Vraagt images.exe iets wat geen regel herkent (bijvoorbeeld in
welke submap de afbeeldingen moeten), dan stopt de app daar en toont de vraag, zodat je zelf antwoordt.

Knoppen bij een exe-stap: **Handmatig uitvoeren** (jij antwoordt in de browser), **Automatisch uitvoeren** (antwoorden
uit Instellingen), **In cmd-venster op de server** (Windows: `cmd /c start "<titel>" /wait <exe>` — opent op de
computer waar de jar draait, niet bij de browser). Verder: Overslaan; bij mislukken Opnieuw / Toch klaar.
Exitcode 0 = klaar, anders mislukt. Testrun staat alleen in het geheugen (weg na herstart).

**Nieuwe test** (knop rechtsboven of opnieuw starten): een nog lopend programma wordt gestopt (`stopAndWait`, max 5 s)
en de console wordt leeggemaakt (`ExeRunner.clear`), zodat de uitvoer van de vorige test niet meer te zien is.

**Runs** (onderaan de testpagina): alle logbestanden uit de logmap, nieuwste boven, met starttijd (uit de bestandsnaam),
programma + bestand (uit de eerste logregel), resultaat (exitcode, "Gestopt door gebruiker", of "Onbekend" als er geen
EXIT-regel is), looptijd (uit de EXIT-regel) en knoppen **Toon** (log inline) en **Wis**. De console bovenaan de pagina
staat er alleen zolang een programma draait; daarna staat het resultaat bij de stap en het log onder Runs.
**Alle logs wissen** ruimt de hele logmap op; het log van een lopend programma blijft altijd staan. Komt uit de
bestanden zelf, dus blijft staan na een herstart van de app.

### 5.6 Programma's starten en logging (`ExeRunner`)

- `ProcessBuilder`, stderr samengevoegd met stdout, werkmap = map van de exe.
- Uitvoer wordt **teken voor teken** (blokken tot 4096) gelezen, omdat vragen niet op een nieuwe regel eindigen;
  gedecodeerd met `IBM850`. In de UI worden `\r\n` → `\n`.
- Invoer eindigt op CRLF op Windows, op **LF** als PERLIO gezet is of op Mac/Linux (anders komt er een `\r` in het antwoord).
- Eén programma tegelijk. Stoppen = proces en kinderen `destroyForcibly`.
- **Logbestand per run** in `logs/run-<datum-tijd>-<id>-<label>.log` met tijd sinds start per regel:
  commando, werkmap, modus, charset/regeleinde, extra env, argumenten (geen), pid, automatische regels, voorbereide
  kopie, inhoud van de werkmap; daarna `OUT <n> tekens: "<exacte tekst met \r\n zichtbaar>"`, `IN "<antwoord>"`,
  `IN (auto) …`, `AUTO vraag herkend: "…"`, `STIL al Ns geen uitvoer` (elke 5 s), `AUTO geen regel herkent de laatste
  vraag …`, `EOF`, `EXIT code=…, tekens, brokken, invoerregels`.
- Wachtwoorden: in console `********`, in log `(wachtwoord, n tekens)`.
- UI toont live: ontvangen tekens/brokken, seconden sinds laatste uitvoer, aantal invoerregels, aantal automatische
  antwoorden, link "Volledig log". **Buffer-waarschuwing** alleen bij verdachte stilte: nog nooit uitvoer, of niets
  meer na de laatste invoer (≥ 5 s). Rode melding als automatisch een vraag niet herkent.
- **Automatisch antwoorden:** bij elk nieuw stuk uitvoer zoekt `autoRespond` in de nog niet beantwoorde tekst de
  eerst voorkomende `pattern` (hoofdletterongevoelig), stuurt het antwoord en gaat verder na die plek. Maximaal 100
  automatische antwoorden per run (beveiliging tegen kringetjes).

### 5.7 Testen zonder Windows: nep-exe's

`tools/mock/pdf2zcbs/pdf2zcbs.pl` en `tools/mock/images-werk/images.pl`: Perl-scripts die de vragen uit de
handleidingen nadoen, maar niets uploaden. pdf2zcbs.pl maakt met `pdftoppm` echte small- (hoogte 193) en large-jpg's
(hoogte 500) in `output/small` en `output/large`; images.pl maakt met `sips` small (100 of 250), large (700) en 100pct,
en schaalt niet op. Ze bufferen zoals een gewoon Perl-programma (`MOCK_UNBUFFERED=1` zet dat
uit). Net als de echte programma's **leegt pdf2zcbs.pl de map `output/` bij elke start** (handleiding 050, §11.14) en
**ruimt images.pl bij "J" echt op** (small/large/100pct en de inhoud van `images/`). Zie
[../tools/mock/README.md](../tools/mock/README.md).

### 5.8 Tests

- `ModulithArchitectureTest` (modulegrenzen + fail-closed `allowedDependencies`).
- `SettingsServiceTest`, `NumberingTest`, `TestRunServiceTest` (hernoemen/archiveren met tijdelijke mappen, volgorde).
- `ExeRunnerTest` (alleen Mac/Linux): shellscript met vragen, controleert automatische antwoorden en dat het
  wachtwoord niet in uitvoer of log komt; geschiedenis, stoppen, wissen van logs.
- `FtpServiceTest`: tegen een in-process ftp-server (Apache FtpServer, testscope): uploaden, nooit overschrijven,
  geen mappen aanmaken, een tijdelijk bestand van een eerdere poging, verkeerd wachtwoord, een server die uit staat.
- `TestRunServiceTest` (alleen Mac/Linux): de volledige keten met nep-programma's uit `src/test/resources/fake-exes` en
  ftp-server; exitcode 0 zonder bestanden is mislukt; weigeringen bij oude resultaten, vreemde pdf's, ontbrekende back-upmap
  en bestaande serverbestanden; de antwoorden die images.exe krijgt.
- `TestFtpServerMain` (testbron): start een ftp-server om de app met de hand tegen te proberen (zie `tools/mock/README.md`).

---

## 6. Bevindingen uit het testen (thuis, Mac, nep-exe's)

- **Buffering bevestigd:** zonder PERLIO kwam alleen de kop door (Perl leegt zijn buffer toevallig bij een `pwd`-aanroep);
  de vragen bleven hangen tot er (blind) invoer kwam. Het programma "hangt" dan niet, het wacht op een onzichtbare vraag.
- **Met `PERLIO=:unix` werkt het**: vragen verschijnen direct, handmatig en automatisch antwoorden werkt.
- Restanten van eerdere pogingen in de map van pdf2zcbs worden opnieuw meegenomen (pdf2zcbs verwerkt **alle** pdf's in
  zijn map; bij meer dan één pdf vervalt de splitsvraag).
- **pdf2zcbs.exe leegt `output/` bij elke start** (handleiding §11.14). De testflow draait pdf2zcbs nu **per bestand**,
  dus de tekst en thumbnail van bestand 1 zijn weg zodra bestand 2 start. Dat is geen probleem van de app maar van de
  opzet: beide programma's verwerken alles wat in hun map staat en horen **één keer per batch** te draaien
  (images.exe adviseert zelfs 5–1000 bestanden per keer; één run = één FTP-sessie en één keer `updates.txt`
  ophalen/terugzetten). Zie §9.
- **19 oktober, eerste sessie bij HKH:** handmatig gedraaid, de app er nog niet mee getest. Het stappenplan en de logs
  staan in §2. Belangrijkste les: images.exe krijgt de large-jpg's van pdf2zcbs, niet de pdf's, en de tekst en pdf's gaan met
  sftp naar `.../archief/ocr` en `.../archief/pdf` (de handleiding noemt voorbeeldpaden).
- **30 oktober 2026: echte images.exe 3.27 op een Windows-testlaptop, aangestuurd vanaf de Mac met pijpen** (niet via de app zelf, maar
  met een PowerShell-script dat doet wat `ExeRunner` doet), tegen de lokale ZCBS in Docker. Zie `tools/windows-testlaptop/` en
  [zcbs-achtergrond.md](zcbs-achtergrond.md). Wat het voor de app betekent:
  - De hele keten werkt: afgeleide images, upload (FTP), lege records in `updates.txt`, regel in `images.log`. Twee runs gelukt.
  - **Geen `PERLIO` nodig** met de echte exe: de vragen verschijnen direct in de pijp. Antwoorden in één keer uit een bestand
    werken niet; ze moeten na de vraag komen (zoals `ExeRunner` doet). Invoer eindigt op `CRLF`, uitvoer is code page 850.
  - **FTP-server en -gebruiker zitten ingebouwd** per instellingsnummer: `ftp.historischekringheemskerk.nl`, gebruiker
    `ZCBS@historischekringheemskerk.nl`, poort 21, gewone FTP. De app hoeft die dus niet mee te geven, alleen het wachtwoord.
  - **Vragen die de app nog niet automatisch beantwoordt:** "Toch de afgeleide images ... opnieuw aanmaken?" (als de mappen al
    bestanden bevatten), de submapvraag ("Geef een ENTER/RETURN of ... de naam van de submap"), "VERWIJDEREN (tijdelijk)
    PC-bestand met nieuwe database records?" en "Geef een Return/Enter om dit DOS-window te verlaten". Niet gebouwd; wacht op het sein van Robbert.
  - Bij het starten haalt images.exe `http://www.zcbs.nl/cgi-bin/misc/downloads.txt` op (versiecontrole); het programma gaat ook door als
    dat niet antwoordt.
  - Niet getest: verborgen wachtwoordinvoer (`Echo : 1`), meer dan één toepassing op de server, "OVERSCHRIJVEN", tekens als ë in
    bestandsnamen, en alles tegen de echte HKH-server.
- **Nog niet getest met de echte exe's via de app** (perl2exe-programma's op Windows bij HKH). Onbekend: respecteert perl2exe
  `PERLIO`? Werkt verborgen wachtwoordinvoer van images.exe (`Echo : 1` in images.ini) zonder echte console? Komen
  tekens als ë goed door met IBM850?

---

## 7. Beslissingen (logboek)

| Onderwerp | Besluit | Reden |
|---|---|---|
| Naam | ZcbsServer, repo `hkh-workflow` | — |
| Stack | Kotlin/Spring Boot/Modulith, runnable jar | Zelfde als andere services van Robbert |
| Frontend | Thymeleaf + htmx | Eenvoudig, alles in Kotlin, geen aparte build |
| Exe's | Via stdin/stdout in de app; cmd-venster als reserve | Gekozen door Robbert |
| Interactiviteit | "Ga ervan uit dat de exe's niet-interactief zijn of dat er een oplossing komt" → opgelost met PERLIO + automatische antwoorden | Robbert |
| Archiveren | Origineel (oorspronkelijke naam) ná images.exe; hernoemen = kopie in `werk` | Gekozen door Robbert |
| Instellingen | JSON naast de jar | Gekozen door Robbert |
| Login | Nog niet; app open op het netwerk tijdens testen | Robbert: "ik ga nu alleen nog testen" |
| Poort | 9000 | Robbert |
| PERLIO | Vaste instelling, standaard aan | Robbert wilde het niet elke keer aanvinken |
| Automatisch uitvoeren | Regels "uitvoer bevat → antwoord" per programma in Instellingen; wachtwoorden verborgen | Robbert |
| Knoppen | "Handmatig uitvoeren" en "Automatisch uitvoeren" | Robbert |
| Commits | Lokaal op `main` als Robbert; Robbert pusht zelf (sessie heeft geen push-rechten/SSH) | — |
| Nieuwe test | Stopt een lopend programma en maakt de console leeg | Robbert: uitvoer van de vorige test hoort niet meer zichtbaar te zijn |
| Runlogboek | Lijst van alle runs (uit de logbestanden) op de testpagina, nieuwste boven | Robbert |
| Nep-exe's | Gedragen zich als de echte: output wissen, opruimen bij J | Robbert |
| Exe-stappen | Eén run per batch (pdf2zcbs en images.exe), met controle op vreemde pdf's in de exe-map | Robbert; de programma's verwerken hun hele map |
| Logs wissen | Per run en alles in één keer, nooit het log van een lopend programma | Robbert: het worden er veel |
| Eerste echte run | In het echte archief (geen testdatabase), elke stap apart goedkeuren en controleren; bij een fout op de oude manier afmaken | Robbert, 19 oktober |
| Route van de test | Carla's stappenplan: large-jpg's naar images.exe, tekst en pdf's met sftp, back-up en opruimen | Het stappenplan van 19 oktober |
| Succes van een stap | Aan de verwachte bestanden, niet aan de exitcode | Perl-programma's geven bijna altijd 0 |
| Schrijven in productie | Upload en records standaard uit en per run te kiezen; ftp overschrijft en verwijdert nooit, controleert eerst alles | Voorzichtigheid bij de eerste echte run |
| Opruimen | Pas na de upload en met back-up van de 100pct; originelen pas in de laatste stap archiveren | Zo blijft de oude werkwijze altijd mogelijk |
| Test van het echte images.exe | Op een aparte Windows-testlaptop (SSH vanaf de Mac), `hosts`-regel naar de lokale ZCBS, firewallregel per programmapad; nooit naar de echte server | Robbert, 30 oktober: niets naar productie; ook niet via Carla's pc |
| Workflow-logboek | Elke inlogpoging en elke doorloop (nieuwe bestanden, records aanpassen) wordt met alle instellingen (zonder wachtwoorden), bestanden, stappen en logs van de exe's gemeld aan het beheerscherm van hkh (`/workflow-logboek`), met een sessie-uuid; elke stap stuurt de volledige stand opnieuw (`adminlog`-module, `hkh.workflow.admin-log-url`, leeg = uit). Het meldpunt vraagt geen inlog maar een vaste sleutel, met limieten tegen misbruik (zie `docs/workflow-logboek.md` in de repo hkh) | Robbert wil zien wie wanneer wat deed, ook op afstand (31 oktober); de gegevens zijn niet gevoelig |
| Jpg's en png's als bron | Naast pdf's staan in de bronmappen ook jpg's en png's; ze zijn te kiezen en te bekijken. Een png wordt bij het kiezen een jpg. Een afbeelding krijgt een recordnummer uit dezelfde reeks, slaat pdf2zcbs.exe en de upload over (geen tekst of pdf) en gaat als `images\<nummer>.jpg` rechtstreeks naar images.exe; de 100pct-back-up is er wel, een pdf-back-up niet. Een run met alleen afbeeldingen slaat pdf2zcbs en de upload over | Robbert (31 oktober): Jelly en Ria leveren ook jpg's |
| Meerdere gebruikers op de server (gepland) | De app draait straks op één server (nu macOS, met de scans en de werkmap via de localfileserver benaderbaar vanaf Windows); gebruikers openen hem in de browser. **Eén sessie per collectie** voor de flow Nieuwe bestanden, twee gebruikers op twee collecties tegelijk moet kunnen. Records aanpassen mag door meerdere mensen tegelijk (ZCBS vergrendelt per record). Werkmap per gebruiker en collectie: `<werkmap>\<gebruiker>\<collectie>\{invoer, werk, pdf2zcbs, images, resten}`, met een kopie van de programma's in de mappen `pdf2zcbs` en `images`. Een slot per collectie (bestand in de werkmap, met eigenaar en laatste activiteit) vervangt claims op nummers; het slot vervalt na 2 uur zonder activiteit of door de beheerder. ZCBS-login, doorloop en `ExeRunner` per browsersessie/collectie; de stand van een doorloop wordt in de eigen map bewaard zodat hij een herstart overleeft. Paden als UNC | Robbert (31 oktober): meerdere gebruikers, per collectie één sessie |

---

## 8. Status (19 oktober 2026)

- Code staat lokaal in `~/git/hkh-workflow` op `main`; **Robbert pusht zelf** (`git push origin main`).
- `mvn clean package` is groen (40 tests). De hele keten is op de Mac doorlopen in de browser met de nep-exe's en een lokale
  sftp-server: hernoemen, pdf2zcbs, images, upload, opruimen, archiveren, met de vinkjes per bestand.
- **Nog niet gedaan:** de keten met de echte exe's op Windows bij HKH. Zie [testen-bij-hkh.md](testen-bij-hkh.md) voor de checklist.
- De demoschermen (Te verwerken, Batch, Batches) zijn nog niet gekoppeld; de testpagina is de motor van de latere batch.
- **25 oktober 2026, daarna:** de pagina **Batch** (`/batch`) laat de echte flow zien zoals de demo (acht fasen met een balk bovenaan, per fase een overzicht en een goedkeuring); in het menu staat hij bovenaan, de demo's staan onder "Demo's" en de oude testpagina heet nu "Test (technisch)". Doorgelopen in de browser tegen de lokale ZCBS.
- **25 oktober 2026, later:** de echte bewerkfunctie is gebouwd en op de Mac doorgelopen tegen de lokale ZCBS in Docker: bestanden ophalen van de server, de testketen met de nep-exe's, de lege records via de nep-images.exe, en daarna in de browser een record bewerkt, opgeslagen en teruggelezen (met de echte ZCBS-code). Nieuwe modules `zcbs` en `items`. 86 tests, waarvan 4 contracttests tegen de lokale ZCBS (`mvn test -Dzcbs.live=true`). Nog niet gedaan: alles tegen de echte HKH-server, en het eigen inlogscherm van de app.
- **30 oktober 2026, vereenvoudigd:** na het overleg met Jan is de flow herbouwd tot de pagina **Nieuwe bestanden** (zes fasen zonder bewerken, bladeren door K:, vrije nummers uit ZCBS,
  controle na pdf2zcbs en images.exe, back-up van 100pct en pdf's, verwerkt-log, opruimen van resten vóór de start, een invoermap per gebruiker, een eerste fase die de omgeving controleert, en een menu met alleen Start en Beheer), met een start (inloggen, collectie, twee knoppen), een beheerderslogin en onthouden antwoorden voor
  images.exe. De demoschermen en de modules ervoor zijn verwijderd. Doorgelopen in de browser tegen de lokale ZCBS, de lokale sftp en de nep-programma's; de tests slagen
  (`mvn test`, en `-Dzcbs.live=true` tegen de lokale ZCBS). Zie [specs-nieuwe-bestanden.md](specs-nieuwe-bestanden.md).
- **30 oktober 2026, instellingen vereenvoudigd:** de omgeving is één checkbox ("Gebruik de test-ZCBS op localhost"), de ftp-gebruiker, -basismap en -mappen staan vast, het ftp-wachtwoord is het antwoord op "FTP wachtwoord" bij images.exe (het blok Ftp-server is weg), de collectie is geen instelling meer (alleen het archief) en de startpagina toont alle zeven collecties met alleen het archief aanzetbaar. Het menu is opgeruimd: Uitloggen is een knop onder Start, "Beheer" in plaats van "Beheerder inloggen" en de knop Test (technisch) is weg.
- **30 oktober 2026, geen antwoordtabellen meer:** de vragen van images.exe 3.27 zijn bekend en bijna alle antwoorden vast (bestandstype `a`, uploaden `j`, instelling `4`, records `j`, nooit overschrijven of verwijderen), dus ze staan in de code in plaats van in de instellingen. Per collectie instelbaar zijn de resolutie en de back-upmappen; het ftp-wachtwoord is één instelling voor de hele server. Bij Handmatig uitvoeren stelt de console de vaste antwoorden voor en leert de app niets, op het ftp-wachtwoord na als dat nog niet is ingesteld. Automatisch uitvoeren uploadt voortaan altijd en maakt records aan (voorheen pas na één handmatige run); het vraagt wel het ftp-wachtwoord.
- **30 oktober 2026, inloggen per collectie:** rechten gelden per collectie (`permit.lst` begint elke regel met de collectie), dus de login op de startpagina is niet aan het archief gebonden. De app probeert dezelfde naam en hetzelfde wachtwoord bij het script van alle zeven collecties (`/cgi-bin/<collectie>.pl`); je bent ingelogd als het bij minstens één lukt. Links in het menu staan "Rechten in ZCBS", en op de keuzepagina staat een collectie alleen aan als je er rechten hebt én de app er al mee werkt (nu alleen het archief; `WorkflowSettings.IMPLEMENTED_COLLECTIONS`). Een fout wachtwoord en een ontbrekend recht zijn voor ZCBS hetzelfde antwoord, dus de melding noemt beide. Iemand met alleen rechten bij een andere collectie kan inloggen, maar niet met het archief werken. Getest tegen de lokale test-ZCBS: `user1` heeft rechten bij het archief en bij de andere scripts niet; nog niet getest tegen een echte server met meerdere collecties.
- **30 oktober 2026:** het echte `images.exe` 3.27 is op een Windows-testlaptop volledig doorlopen tegen de lokale ZCBS (upload, records, log); de ingebouwde FTP-server en -gebruiker zijn gevonden, en welke vragen de app nog niet automatisch beantwoordt. Zie §6 en `tools/windows-testlaptop/`. De app zelf is niet gewijzigd.
- **25 oktober 2026:** er is een klikbare demo van de hele flow zoals Carla hem gaat doorlopen (menu *Hele flow*, `/demo-flow`): inloggen, nieuwe bestanden, voorbereiden, pdf2zcbs, images.exe (met een optionele mislukking), upload, records één voor één bewerken (voorbeeld, "later", vergrendeld, intussen gewijzigd, niet veilig) en afsluiten. Alleen nepdata in het geheugen (`ux`-module, `FlowDemo.kt`); nog geen koppeling met ZCBS.

## 9. Volgende stappen

1. **Bij HKH testen met de echte exe's, in het echte archief, stap voor stap** (checklist in [testen-bij-hkh.md](testen-bij-hkh.md)).
   Kijk in elk runlog of PERLIO werkt en of de vragen direct verschijnen.
2. Als PERLIO niet werkt bij perl2exe: **pseudo-console** via JetBrains **pty4j** (Windows ConPTY), zodat het programma
   een echte console ziet (lost ook verborgen wachtwoordinvoer op).
3. Uitzoeken: bij uploaden vraagt images.exe in welke submap de afbeeldingen moeten (kies 0-1, of een nieuwe submap). Als de
   uitkomst vast is, een instelling of antwoordregel toevoegen; nu stopt de app bij die vraag en antwoord je zelf.
4. **Records aanmaken.** Volgens het stappenplan doet images.exe dat zelf (stap D). De app controleert alleen vooraf dat de nummers van de
   batch nog niet bestaan en erna dat ze bestaan; zie [specs-item-bekijken-bewerken.md](specs-item-bekijken-bewerken.md).
4a. **Item bekijken en bewerken** met het ZCBS-formulier, voor de eerste run met Carla: [specs-item-bekijken-bewerken.md](specs-item-bekijken-bewerken.md).
4b. **Overschrijven per run** (derde vinkje, standaard uit) voor het opnieuw uploaden van een batch.
5. (vervallen: de upload gaat met ftp, dus er is geen vingerafdruk meer om vast te zetten.)
6. Testcollectie: Gerard van Nes antwoordde dat je die zelf moet aanmaken (aparte map aan beide kanten van `/cgi-bin`, dan
   herkent images.exe hem als aparte beeldbank). Nog niet gedaan; tot dan is de eerste run in het echte archief.
7. Echte "Te verwerken": `K:\scabslelly` (submap per scanner) elke minuut scannen + database (status per bestand).
8. Echte batches (ID `B-yyyyMMdd-HHmm`), voortgang, resultaat met ZCBS-links, "beschreven"-controle, historie, gebouwd op de
   stappen van de testflow. Nummering uit ZCBS, collectie per batch.
9. Login (het UX-ontwerp heeft een inlogscherm) voordat de app breder gebruikt wordt.
10. Aanbevelingen uit §3 (OCR, recordnummers, mappingtabel, per-collectie-config, back-up).
11. De testrun staat alleen in het geheugen. Na een herstart van de app midden in een run begint de test opnieuw; de controles
    vooraf (oude resultaten in `output\` en de map van images.exe) weigeren dan te starten, zodat er niets verloren gaat.
12. **Meerdere gebruikers op de server** (besluit hierboven), in deze volgorde:
    1. Fundament: de ZCBS-login per browsersessie in plaats van één in het geheugen (`ItemEditService`); een doorloop (`TestRunService`) en
       een `ExeRunner` per collectie; de collectie in de adressen (`/collectie/{c}/nieuw` enz.) en per collectie de ftp-submappen, back-upmappen
       en resolutie (de instellingen per collectie bestaan al); het slot per collectie met de naam van wie er werkt op de startpagina.
    2. De werkmap per gebruiker en collectie met de kopie van de programma's (exe, ini, i_view32, plugins) en de controle op resten per map.
       Eerst bevestigen of de exe's hun mappen in de huidige map of naast de exe zoeken; een kopie werkt in beide gevallen.
    3. De stand van een doorloop bewaren in de eigen map (`sessie.json`), zodat hij een herstart overleeft.
    4. De meldingen aan het workflow-logboek volgen dan meerdere doorlopen tegelijk (nu staat er één per type).

## 10. Toekomstideeën (niet gepland)

- **Zonder images.exe en pdf2zcbs.exe werken** (bijvoorbeeld in Docker op een Linux-server of in de cloud, waar de Windows-exe's niet draaien):
  het record aanmaken met `NEWRECORD <begin> <eind> x` of het formulier voor een nieuw record (`upd_form=new`), en zelf small (100×100 of 250×250),
  large (700×500) en 100pct (`cgi-bin/archief/100pct/`) maken met 40% compressie en per sftp uploaden, naast pdf en ocr. De pdf- en ocr-aanmaak van
  pdf2zcbs zou dan ook vervangen moeten worden. Eerst naast images.exe uitproberen en de resultaten vergelijken. Details en afwegingen:
  [zcbs-analyse-en-wrapper.md](zcbs-analyse-en-wrapper.md) en de uitleg bij `NEWRECORD` in `objecten.pl` (regel 1433). Voor een aparte, beperkte
  sftp-account (alleen ocr, pdf en afbeeldingen) is een aparte FTP-account nodig; sftp gebeurt nu met `hkhadmin`, en images.exe gebruikt een eigen FTP-account `ZCBS@historischekringheemskerk.nl` (ingebouwd; gemeten). Of het FTP-wachtwoord gelijk is aan het sftp-wachtwoord is niet bekend.

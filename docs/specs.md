# ZcbsServer (hkh-workflow) — specificaties

> **Voor een nieuwe agent of ontwikkelaar:** lees dit document helemaal. Het bevat de context, alle gemaakte
> keuzes, wat er al gebouwd is en wat de volgende stappen zijn. Achtergrond over ZCBS zelf (hoe het werkt,
> wat images.exe en pdf2zcbs.exe precies doen) staat in [zcbs-achtergrond.md](zcbs-achtergrond.md).
> De bronnen: de ZCBS-handleidingen in deze map en het UX-ontwerp [ZcbsServer UX.pdf](ZcbsServer%20UX.pdf).

Laatst bijgewerkt: 9 oktober 2026 (middag).

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
| `settings` | – | `WorkflowSettings`, `AnswerRule`, `SettingsService` (JSON lezen/schrijven, controles), `WorkflowProperties`; `api/SettingsController` |
| `exerunner` | – | `ExeRunner`: start één extern programma tegelijk, leest uitvoer, stuurt invoer, automatische antwoorden, logbestand per run |
| `testrun` | `settings`, `exerunner` | `TestRunService` (stappen per bestand), model/views, `sequenceNumbers`; `api/TestController` |
| `ux` | – | `DemoData` + `api/UxController`: de UX-schermen met voorbeeldgegevens |

### 5.3 Routes

| Methode | Pad | Wat |
|---|---|---|
| GET | `/` | Te verwerken (demo) |
| GET | `/batch/controleren?files=..&collectie=..` | Batch controleren (demo; nummers vanaf 1711) |
| GET | `/batches`, `/batches/demo-bezig`, `/batches/demo-klaar` | Batches (demo) |
| GET/POST | `/instellingen` | Instellingen |
| GET | `/test` | Testpagina |
| POST | `/test/start` (`startNumber`), `/test/reset` | Testrun starten / nieuwe test |
| POST | `/test/steps/{id}/run` (`mode=IN_APP\|WINDOW`, `auto=true\|false`) | Stap uitvoeren |
| POST | `/test/steps/{id}/skip`, `/done`, `/retry` | Overslaan / toch klaar / opnieuw |
| GET | `/test/console` | htmx-fragment, elke seconde zolang er iets draait; stuurt `HX-Refresh` als het klaar is |
| POST | `/test/console/input` (`line`), `/test/console/stop` | Invoer sturen / programma stoppen |
| GET | `/test/log` | Logbestand van de laatste run (text/plain) |
| GET | `/test/runs/log?file=..` | Logbestand van een eerdere run als htmx-fragment (onder de runlijst); `/test/runs/raw?file=..` als text/plain |

### 5.4 Instellingen

Opgeslagen in JSON; velden:

- `testFolder` — map met de te testen pdf's.
- `archivedFolder` — waar de originelen na verwerking heen gaan.
- `pdf2zcbsExe`, `imagesExe` — volledige paden. Mag elk uitvoerbaar bestand zijn (op Mac/Linux een script met
  `chmod +x`); de pagina waarschuwt als een pad niet bestaat of niet uitvoerbaar is.
- `perlioUnbuffered` (standaard **aan**) — start de programma's met `PERLIO=:unix` (zie §6).
- `pdf2zcbsAnswers`, `imagesAnswers` — lijsten `{pattern, answer, secret}` voor automatisch uitvoeren.
  Standaardregels zijn afgeleid uit de handleidingen (`WorkflowSettings.DEFAULT_*`), met veilige keuzes:
  uploaden = `n`, records aanmaken = `n`, instelling = `4`, collectie = `test`, FTP-wachtwoord leeg.
- **Wachtwoorden** (`secret = true`): in de UI een `*`-veld; de waarde gaat nooit terug naar de browser
  (placeholder "•••••••• (opgeslagen)"); leeg laten = behouden, vinkje "wissen" = leegmaken. Staan wél leesbaar in het
  JSON-bestand.
- Formulier: rijen heten `<prefix>Pattern-<i>`, `<prefix>Answer-<i>`, `<prefix>Secret-<i>`, `<prefix>Clear-<i>`,
  plus `<prefix>Count`; steeds twee lege rijen extra; lege tekst = regel verwijderen.

### 5.5 Testworkflow (pagina Test)

1. Vraagt een **beginnummer** (alleen cijfers; voorloopnullen blijven: `01650` → `01651`).
2. Neemt alle `*.pdf` in de testmap (niet recursief, gesorteerd op naam) en maakt per bestand vier stappen.
   Volgorde: eerst alle hernoem-stappen, dan alle pdf2zcbs, dan alle images, dan alle archiveer-stappen.
3. **Elke stap start de gebruiker zelf.** De UI toont de eerstvolgende stap als vraag ("Run pdf2zcbs.exe voor 33.pdf?")
   met knoppen; alleen die stap mag uitgevoerd worden. Overzichtstabel met status per bestand × stap.

| Stap | Wat er gebeurt |
|---|---|
| Hernoemen | **Kopie** van het origineel naar `<testmap>\werk\<nummer>.pdf`. Origineel blijft staan (keuze: archiveren gebeurt met de originele naam). Faalt als het doel al bestaat. |
| pdf2zcbs.exe | Kopieert `werk\<nummer>.pdf` naar **de map van pdf2zcbs.exe** (overschrijft bij opnieuw proberen) en start het programma met die map als werkmap, **zonder argumenten**. |
| images.exe | Kopieert naar **`<map van images.exe>\images\`** en start images.exe in zijn eigen map, zonder argumenten. |
| Archiveren | **Verplaatst het origineel** (oorspronkelijke naam) naar de archived-map; bestaat de naam al, dan `naam-1.pdf` enz. |

Knoppen bij een exe-stap: **Handmatig uitvoeren** (jij antwoordt in de browser), **Automatisch uitvoeren** (antwoorden
uit Instellingen), **In cmd-venster op de server** (Windows: `cmd /c start "<titel>" /wait <exe>` — opent op de
computer waar de jar draait, niet bij de browser). Verder: Overslaan; bij mislukken Opnieuw / Toch klaar.
Exitcode 0 = klaar, anders mislukt. Testrun staat alleen in het geheugen (weg na herstart).

**Nieuwe test** (knop rechtsboven of opnieuw starten): een nog lopend programma wordt gestopt (`stopAndWait`, max 5 s)
en de console wordt leeggemaakt (`ExeRunner.clear`), zodat de uitvoer van de vorige test niet meer te zien is.

**Runs** (onderaan de testpagina): alle logbestanden uit de logmap, nieuwste boven, met starttijd (uit de bestandsnaam),
programma + bestand (uit de eerste logregel), resultaat (exitcode, "Gestopt door gebruiker", of "Onbekend" als er geen
EXIT-regel is), looptijd (uit de EXIT-regel) en knoppen **Toon** (log inline) en **Openen** (nieuw tabblad). Komt uit
de bestanden zelf, dus blijft staan na een herstart van de app.

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
handleidingen nadoen, maar niets uploaden. Ze bufferen zoals een gewoon Perl-programma (`MOCK_UNBUFFERED=1` zet dat
uit). Net als de echte programma's **leegt pdf2zcbs.pl de map `output/` bij elke start** (handleiding 050, §11.14) en
**ruimt images.pl bij "J" echt op** (small/large/100pct en de inhoud van `images/`). Zie
[../tools/mock/README.md](../tools/mock/README.md).

### 5.8 Tests

- `ModulithArchitectureTest` (modulegrenzen + fail-closed `allowedDependencies`).
- `SettingsServiceTest`, `NumberingTest`, `TestRunServiceTest` (hernoemen/archiveren met tijdelijke mappen, volgorde).
- `ExeRunnerTest` (alleen Mac/Linux): shellscript met vragen, controleert automatische antwoorden en dat het
  wachtwoord niet in uitvoer of log komt.

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
- **Nog niet getest met de echte exe's** (perl2exe-programma's op Windows bij HKH). Onbekend: respecteert perl2exe
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

---

## 8. Status (9 oktober 2026)

- Code staat lokaal in `~/git/hkh-workflow` op `main`; **Robbert pusht zelf** (`git push origin main`).
- De agent die dit schreef kon **niet compileren** (geen toegang tot Maven Central). Robbert bouwt met
  `mvn clean package` op zijn Mac. Laatst gemelde compileerfout (JsonNode.map in `SettingsService`) is opgelost maar
  nog niet opnieuw gebouwd. Reken op mogelijke compile/testfixes.
- Thuis getest met nep-exe's: testflow werkt, PERLIO lost buffering op. Build en tests zijn groen (`mvn clean package`
  op de Mac, 9 oktober).

## 9. Volgende stappen

1. Bouwen en tests groen krijgen.
2. Bij HKH testen met de **echte** images.exe en pdf2zcbs.exe (Windows), eerst met een **testcollectie** en upload = N.
   Kijk in het runlog of PERLIO werkt.
3. Als PERLIO niet werkt bij perl2exe: **pseudo-console** via JetBrains **pty4j** (Windows ConPTY), zodat het programma
   een echte console ziet (lost ook verborgen wachtwoordinvoer op).
4. **Testflow ombouwen naar één run per batch** (zie §6): hernoemen voor alle bestanden → alle kopieën in de map van
   pdf2zcbs → pdf2zcbs één keer → `output\` wegzetten → alle kopieën in `images\` → images.exe één keer → archiveren.
   De tabel wordt dan: per bestand hernoemen/archiveren, per batch de twee exe-stappen. Vóór het starten
   waarschuwen/opruimen als er al andere pdf's in de map van pdf2zcbs of in `images\` staan.
5. De open vragen uit §2 aan Carla stellen (FTP vs SFTP is cruciaal: images.exe kan alleen FTP).
6. Echte "Te verwerken": `K:\scabslelly` (submap per scanner) elke minuut scannen + database (status per bestand).
7. Echte batches (ID `B-yyyyMMdd-HHmm`), voortgang, resultaat met ZCBS-links, "beschreven"-controle, historie.
8. Login (het UX-ontwerp heeft een inlogscherm) voordat de app breder gebruikt wordt.
9. Aanbevelingen uit §3 (OCR, recordnummers, mappingtabel, per-collectie-config, back-up).
10. Testcollectie in ZCBS laten aanmaken door de auteur van ZCBS (zie achtergrond §licentie).

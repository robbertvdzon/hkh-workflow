# hkh-workflow

Webinterface ("ZcbsServer") om gescande pdf's van de Historische Kring Heemskerk te verwerken naar ZCBS.
Kotlin + Spring Boot 4 + Spring Modulith, server-side HTML met Thymeleaf en htmx. Levert één runnable jar.

**Documentatie:** [docs/specs.md](docs/specs.md) (specificaties, keuzes, status, volgende stappen) en
[docs/zcbs-achtergrond.md](docs/zcbs-achtergrond.md) (ZCBS, images.exe, pdf2zcbs.exe).

## Bouwen en starten

Vereist Java 21 en Maven.

```bash
mvn clean package          # bouwt en test; jar komt in target/hkh-workflow.jar
java -jar target/hkh-workflow.jar
```

Open daarna http://localhost:9000 (of `http://<computernaam>:9000` vanaf een ander systeem). De app luistert
op alle netwerkadressen en heeft nog **geen login**: iedereen die de computer kan bereiken kan de exe's
starten. Zet `HKH_WORKFLOW_ADDRESS=127.0.0.1` om dat te beperken. Instelbaar met omgevingsvariabelen:

| Variabele | Standaard | Betekenis |
|---|---|---|
| `HKH_WORKFLOW_PORT` | `9000` | Poort |
| `HKH_WORKFLOW_ADDRESS` | `0.0.0.0` | Adres waarop de app luistert |
| `HKH_WORKFLOW_SETTINGS_FILE` | `hkh-workflow-settings.json` | Instellingenbestand (relatief = t.o.v. de map waaruit je de jar start) |
| `HKH_WORKFLOW_CONSOLE_CHARSET` | `IBM850` | Codepagina van de DOS-uitvoer van de exe's |

## Schermen

- **Te verwerken, Batch controleren, Batch bezig/klaar, Batches** — de schermen uit `docs/ZcbsServer UX.pdf`,
  nog met **demo-gegevens**.
- **Batch** — het hoofdscherm: de batch zoals Carla hem doorloopt, in acht fasen, precies zoals in de demo hieronder maar echt:
  inloggen bij ZCBS, bestanden ophalen van de server (kiezen en naar de invoermap kopiëren; de map toont wie het gescand heeft),
  voorbereiden (het volgende nummer uit ZCBS met `LAST`, controle dat de nummers nog niet bestaan, hernoemen), pdf2zcbs, images.exe, upload
  van tekst en pdf, de records bewerken, en afsluiten (opruimen en archiveren). Elke fase begint met een overzicht van wat hij gaat doen en wat
  de app al controleerde, en start pas na jouw knop. De pagina **Test (technisch)** toont dezelfde stappen per bestand, met de logs.
- **Hele flow (demo)** — klikbare demo van wat Carla gaat doen: inloggen bij ZCBS, nieuwe bestanden, voorbereiden, pdf2zcbs, images.exe,
  upload, de records één voor één bewerken en afsluiten. Alleen nepdata; er wordt niets naar ZCBS, de exe's of de sftp-server gestuurd.
  Open `/demo-flow`; **Opnieuw beginnen** zet alles terug.
- **Instellingen** — mappen, paden naar `images.exe` en `pdf2zcbs.exe`, collectie, back-upmap en de sftp-server (met
  Test verbinding). Opgeslagen als JSON, die niet in git komt.
- **Records bewerken** — de records van de batch één voor één invullen via het ZCBS-formulier: inloggen met je eigen ZCBS-naam
  (alleen in het geheugen), voorbeeld van de wijziging, opslaan en teruglezen, "later" voor een record dat je overslaat. Het adres van
  ZCBS stel je in bij Instellingen: lokale testkopie `http://localhost:8081` (zie `tools/zcbs-local/README.md`) of de echte site. **Bij
  de echte site schrijft elke opslag in het echte archief en gaat er een e-mail naar de beheerder.**
- **Test** — haalt eerst (als de bronmap is ingesteld) de bestanden die Ria heeft aangegeven op van de server (`K:\scabslelly\<persoon>\<map>`,
  de map toont wie het gescand heeft) en verwerkt alle pdf's uit de testmap volgens het stappenplan van Carla. Elke stap start je zelf, na een
  overzicht van wat hij gaat doen, en per bestand zie je met vinkjes of de bestanden er echt zijn:
  1. **Hernoemen** (per bestand) — vraagt eerst een beginnummer; elk bestand krijgt een volgnummer (voorloopnullen
     blijven). Het origineel blijft staan; er komt een kopie `<nummer>.pdf` in `<testmap>\werk`.
  2. **pdf2zcbs.exe** (één keer voor de hele batch) — zet alle `<nummer>.pdf` in de map van pdf2zcbs.exe en start het
     programma daar. Verwacht per bestand tekst, pdf, small en large in `output\`.
  3. **images.exe** (één keer voor de hele batch) — krijgt de **large-jpg's** van pdf2zcbs in `<map van images.exe>\images`
     en maakt small, large en 100pct. Upload en records aanmaken staan standaard uit en kies je per run met een vinkje.
  4. **Upload (sftp)** — zet tekst en pdf's in `<collectie>/ocr` en `<collectie>/pdf` op de server. Overschrijft en
     verwijdert nooit, controleert eerst alles en gebruikt een vastgezette vingerafdruk van de server.
  5. **Records bewerken** — logt in bij ZCBS en opent de pagina Records; klaar als elk record is opgeslagen of op "later" staat.
  6. **Opruimen** — back-up van de 100pct, daarna de mappen van de programma's leegmaken.
  7. **Archiveren** (per bestand) — verplaatst het origineel naar de archived-map.

  De programma's draaien één keer per batch omdat ze alles verwerken wat in hun map staan en pdf2zcbs zijn `output\` bij
  elke start leegt. Een stap start niet als er oude resultaten of andere bestanden in die mappen staan. Een stap is pas
  klaar als de verwachte bestanden er zijn, niet op de exitcode alleen. Voor de eerste echte run bij HKH: zie
  [docs/testen-bij-hkh.md](docs/testen-bij-hkh.md).

  De exe's draaien **in de app**: de uitvoer verschijnt in de browser. Kies **Handmatig uitvoeren** om zelf te
  antwoorden (knoppen voor Enter/J/N) of **Automatisch uitvoeren**: de app herkent de vragen aan de teksten bij
  Instellingen en geeft het bijbehorende antwoord. Wachtwoorden worden nergens getoond of gelogd (wel leesbaar
  opgeslagen in het instellingenbestand). Automatisch werkt alleen als de vragen zichtbaar zijn, dus met
  **PERLIO=:unix** aan (staat standaard aan bij Instellingen).

  Lukt het in de app niet (bijvoorbeeld omdat een programma zijn vragen pas toont als je
  antwoordt), kies dan **In cmd-venster op de server**: het programma opent in een eigen cmd-venster op de computer waar de jar draait (alleen Windows).

### Logging

Elke run van een programma krijgt een eigen logbestand in `logs/` (instelbaar met `HKH_WORKFLOW_LOG_DIR`),
met tijdstempels van elk binnengekomen stuk uitvoer, elke verstuurde invoer, stille periodes en de exitcode.
Op de testpagina staat een link **Volledig log** en een teller van ontvangen tekens. Zo is te zien of een
programma zijn vragen pas laat toont (buffering). Zet dan **PERLIO=:unix** aan bij Instellingen.

Onderaan de testpagina staat **Runs**: alle logbestanden, nieuwste boven, met starttijd, programma, resultaat en
looptijd. Per run: **Toon** (log inline) en **Wis**. **Alle logs wissen** ruimt de hele map op; het log van een lopend
programma blijft staan. De console bovenaan is alleen zichtbaar zolang een programma draait. **Nieuwe test** stopt
een nog lopend programma.

### Testen op een Mac

De app start elk uitvoerbaar bestand, niet alleen `.exe`. In `tools/mock/` staan nep-versies van
images.exe en pdf2zcbs.exe als Perl-scripts; zie `tools/mock/README.md`.

> Let op: images.exe uploadt naar de echte webserver als je bij "UPLOADEN …? [J/N]" J antwoordt.
> Antwoord N of kies een testcollectie zolang je test.

## Modules (Spring Modulith)

| Module | Inhoud |
|---|---|
| `settings` | Instellingen lezen/schrijven + pagina |
| `exerunner` | Externe programma's starten, uitvoer lezen, invoer doorsturen |
| `sftp` | Uploaden naar de server met sftp, met vastgezette vingerafdruk |
| `zcbs` | Client voor ZCBS: inloggen, formulier ophalen en posten, controles op wat ZCBS wel en niet accepteert |
| `items` | Records van een batch bewerken via het ZCBS-formulier (voorbeeld, opslaan, conflictcontrole, teruglezen) + pagina's |
| `testrun` | De testworkflow (de stappenketen met controles, bestanden ophalen van de server) + pagina |
| `ux` | De UX-schermen met demo-gegevens |

## Documentatie

`docs/zcbs-analyse-en-wrapper.md` bundelt alle bevindingen uit de serverbestanden, hoe HKH nu werkt en het voorstel voor een wrapper
om ZCBS heen. `docs/` bevat verder de ZCBS-handleidingen (technische documentatie v4.0, images.exe = 042, pdf2zcbs = 050) en het
UX-ontwerp.

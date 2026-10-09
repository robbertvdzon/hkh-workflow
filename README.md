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
- **Instellingen** — testmap, archived-map, pad naar `images.exe` en `pdf2zcbs.exe`. Opgeslagen als JSON.
- **Test** — verwerkt alle pdf's uit de testmap, elke stap met de hand gestart:
  1. **Hernoemen** (per bestand) — vraagt eerst een beginnummer; elk bestand krijgt een volgnummer (voorloopnullen
     blijven). Het origineel blijft staan; er komt een kopie `<nummer>.pdf` in `<testmap>\werk`.
  2. **pdf2zcbs.exe** (één keer voor de hele batch) — zet alle `<nummer>.pdf` in de map van pdf2zcbs.exe en start
     het programma daar. Het weigert als er nog andere pdf's in die map staan.
  3. **images.exe** (één keer voor de hele batch) — zet alle `<nummer>.pdf` in `<map van images.exe>\images` en
     start het programma. Ook hier: geen andere pdf's in die map.
  4. **Archiveren** (per bestand) — verplaatst het origineel (met oorspronkelijke naam) naar de archived-map.

  De programma's draaien één keer per batch omdat ze alles verwerken wat in hun map staat, en pdf2zcbs zijn
  `output\` bij elke start leegt.

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
looptijd. Per run: **Toon** (inline), **Openen** (nieuw tabblad), **Wis**. **Alle logs wissen** ruimt de hele map op;
het log van een lopend programma blijft staan. **Nieuwe test** stopt een nog lopend programma en maakt de console leeg.

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
| `testrun` | De testworkflow (stappen per bestand) + pagina |
| `ux` | De UX-schermen met demo-gegevens |

## Documentatie

`docs/` bevat de ZCBS-handleidingen (technische documentatie v4.0, images.exe = 042, pdf2zcbs = 050) en het
UX-ontwerp.

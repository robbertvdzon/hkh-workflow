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
op alle netwerkadressen en start .exe-bestanden: zet hem alleen op een server waar niet iedereen bij kan, of zet `HKH_WORKFLOW_ADDRESS=127.0.0.1`.
Je logt in met je eigen ZCBS-naam en -wachtwoord; de instellingen en de technische pagina's vragen daarnaast het **beheerderswachtwoord**, dat in het
configuratiebestand staat (`hkh.workflow.admin-password` in `application.properties` naast de jar). Zonder dat wachtwoord blijven ze dicht.
Na het inloggen blijf je ingelogd, ook na een herstart van de app: de naam en het wachtwoord staan versleuteld in een cookie van 30 dagen (alleen op
de computer van de gebruiker), met de sleutel in `hkh-workflow-inloggen.key` naast het instellingenbestand (niet in git zetten). Uitloggen haalt het cookie weg.
Instelbaar met omgevingsvariabelen:

| Variabele | Standaard | Betekenis |
|---|---|---|
| `HKH_WORKFLOW_PORT` | `9000` | Poort |
| `HKH_WORKFLOW_ADDRESS` | `0.0.0.0` | Adres waarop de app luistert |
| `HKH_WORKFLOW_ADMIN_PASSWORD` | (leeg) | Beheerderswachtwoord; leeg = niemand kan als beheerder inloggen |
| `HKH_WORKFLOW_SETTINGS_FILE` | `hkh-workflow-settings.json` | Instellingenbestand (relatief = t.o.v. de map waaruit je de jar start) |
| `HKH_WORKFLOW_PROCESSED_LOG` | `hkh-workflow-verwerkt.tsv` | Het log van wat er verwerkt is (zonder pad: naast het instellingenbestand) |
| `HKH_WORKFLOW_CONSOLE_CHARSET` | `IBM850` | Codepagina van de DOS-uitvoer van de exe's |

## Schermen

De app begint met **inloggen bij ZCBS**, daarna kies je de **collectie** (nu alleen `archief`) en dan een van twee dingen. Alles staat in
[docs/specs-nieuwe-bestanden.md](docs/specs-nieuwe-bestanden.md).

- **Nieuwe bestanden in ZCBS zetten** — de flow van Carla, volgens het stappenplan van Jan, in zeven fasen met een knop per stap:
  1. **Controleren**: een lijstje met vinkjes (ingelogd, vrije nummers uit ZCBS, K: bereikbaar, de programma's, de back-upmappen, de upload, en geen werk van een eerdere keer).
     Staat er nog werk van een eerdere keer, dan verplaats of verwijder je dat hier. Staat alles groen, dan is er een knop voor de volgende fase.
  2. **Bestanden kiezen**: bladeren door de mappen met scans op K: (de eerste uit de instellingen staat meteen open), aanvinken (Shift-klik kiest "X t/m Y") en op Verder klikken. Elke gebruiker werkt in een eigen submap van de invoermap.
  3. **Nummers**: de vrije nummers uit ZCBS (zoals bij een nieuw record) en het laatste record; een beginnummer kiezen. De bestanden moeten aaneengesloten in een vrije reeks passen.
  4. **pdf2zcbs.exe**: twee knoppen (handmatig of automatisch); daarna een lijst van wat het maakte, met grootte, afmeting en een link om het te openen, en jouw controle.
  5. **images.exe**: idem. Handmatig typen: de app onthoudt de antwoorden en stelt ze de volgende keer voor; automatisch gebruikt hij de onthouden antwoorden.
  6. **Upload** van tekst en pdf met ftp, net als images.exe (overschrijft en verwijdert nooit; gewone ftp is niet versleuteld).
  7. **Back-up** van de 100pct en de pdf's naar K:, opruimen en in het verwerkt-log zetten. De originelen op K: blijven staan.
- **ZCBS records aanpassen** — kies een recordnummer of een reeks en vul de gegevens in via het formulier van ZCBS: voorbeeld van de wijziging, opslaan en teruglezen,
  "later" voor een record dat je overslaat. **Bij de echte site schrijft elke opslag in het echte archief en gaat er een e-mail naar de beheerder.**
- **Verwerkt** — wat al op het archief is gezet: per doorloop de bestanden, hun recordnummer, wie het deed en uit welke map ze kwamen.
- **Instellingen** en **Test (technisch)** (alleen beheerder) — mappen, paden naar `images.exe` en `pdf2zcbs.exe`, collectie, back-upmappen, ftp-server (met Test verbinding) en de
  onthouden antwoorden; en dezelfde stappen per bestand met alle knoppen en logs. Instellingen worden als JSON opgeslagen, die niet in git komt.

De stappen werken zo: elke stap start je zelf, na een overzicht van wat hij gaat doen, en per bestand zie je met vinkjes of de bestanden er echt zijn. De programma's
draaien één keer voor alle bestanden, omdat ze alles verwerken wat in hun map staat en pdf2zcbs zijn `output\` bij elke start leegt. Een stap start niet als er oude resultaten of andere
bestanden in die mappen staan en is pas klaar als de verwachte bestanden er zijn, niet op de exitcode alleen. Voor de eerste echte run bij HKH: zie
[docs/testen-bij-hkh.md](docs/testen-bij-hkh.md).

De exe's draaien **in de app**: de uitvoer verschijnt in de browser. Kies **Handmatig uitvoeren** om zelf te antwoorden (de app stelt het onthouden antwoord voor; knoppen voor Enter/J/N)
of **Automatisch uitvoeren**. Wachtwoorden worden nergens getoond of gelogd (wel leesbaar opgeslagen in het instellingenbestand). Automatisch werkt alleen als de vragen zichtbaar zijn,
dus met **PERLIO=:unix** aan (standaard aan). Lukt het in de app niet, dan kan de beheerder op de technische pagina **In cmd-venster op de server** kiezen (alleen Windows).

### Logging

Elke run van een programma krijgt een eigen logbestand in `logs/` (instelbaar met `HKH_WORKFLOW_LOG_DIR`),
met tijdstempels van elk binnengekomen stuk uitvoer, elke verstuurde invoer, stille periodes en de exitcode.
In de console staat een link **Volledig log** en een teller van ontvangen tekens. Zo is te zien of een
programma zijn vragen pas laat toont (buffering). Zet dan **PERLIO=:unix** aan bij Instellingen.

Onderaan de testpagina staat **Runs**: alle logbestanden, nieuwste boven, met starttijd, programma, resultaat en
looptijd. Per run: **Toon** (log inline) en **Wis**. **Alle logs wissen** ruimt de hele map op; het log van een lopend
programma blijft staan. De console bovenaan is alleen zichtbaar zolang een programma draait. **Nieuwe test** stopt
een nog lopend programma.

### Testen op een Mac

De app start elk uitvoerbaar bestand, niet alleen `.exe`. In `tools/mock/` staan nep-versies van
images.exe en pdf2zcbs.exe als Perl-scripts; zie `tools/mock/README.md`.

> Let op: images.exe uploadt naar de echte webserver als je bij "UPLOADEN …? [J/N]" J antwoordt, en de app onthoudt dat antwoord voor automatisch uitvoeren.
> Test tegen de lokale ZCBS (`tools/docker/zcbs`, `http://localhost:8081`, gebruiker `user1`, wachtwoord `abc`).

## Modules (Spring Modulith)

| Module | Inhoud |
|---|---|
| `settings` | Instellingen lezen/schrijven + pagina; het beheerderswachtwoord uit de configuratie |
| `exerunner` | Externe programma's starten, uitvoer lezen, invoer doorsturen |
| `ftp` | Uploaden naar de server met ftp, zonder iets te overschrijven of te verwijderen |
| `zcbs` | Client voor ZCBS: inloggen, de vrije nummers, formulier ophalen en posten, controles op wat ZCBS wel en niet accepteert |
| `items` | Inloggen bij ZCBS en records bewerken via het ZCBS-formulier (voorbeeld, opslaan, conflictcontrole, teruglezen) + pagina's |
| `testrun` | De flow Nieuwe bestanden (de stappenketen met controles, bladeren door K:, het verwerkt-log) + pagina's |
| `ux` | Het begin van de app: inloggen, collectie en acties kiezen, de beheerderslogin en wie waar mag komen |

## Documentatie

`docs/zcbs-analyse-en-wrapper.md` bundelt alle bevindingen uit de serverbestanden, hoe HKH nu werkt en het voorstel voor een wrapper
om ZCBS heen. `docs/` bevat verder de ZCBS-handleidingen (technische documentatie v4.0, images.exe = 042, pdf2zcbs = 050) en het
UX-ontwerp.

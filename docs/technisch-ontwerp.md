# Technisch ontwerp — ZcbsServer (hkh-workflow)

Stand: 1 november 2026. Wat er functioneel gebeurt staat in [functioneel-ontwerp.md](functioneel-ontwerp.md); besluiten en geschiedenis in
[specs.md](specs.md); hoe ZCBS en de programma's werken in [zcbs-achtergrond.md](zcbs-achtergrond.md).

## 1. Techniek

| Onderwerp | Keuze |
|---|---|
| Vorm | Eén runnable jar (`target/hkh-workflow.jar`), poort 9000, luistert op alle adressen |
| Taal/framework | Kotlin 2.2.21, Spring Boot 4.0.6, Spring Modulith 2.0.0, Java 21, Maven. Jackson 3 (`tools.jackson`) |
| Schermen | Server-side HTML: Thymeleaf + htmx (meegeleverd, geen CDN), eigen CSS en kleine scripts (`static/js/viewer.js`) |
| Opslag | Bestanden, geen database (zie §4) |
| Pdf's lezen | Apache PDFBox (aantal pagina's, afmeting) |
| Ftp | Apache Commons Net |
| Package | `nl.vdzon.hkhworkflow` |

## 2. Modules (Spring Modulith)

Elke module heeft een `package-info.java` met `@ApplicationModule(allowedDependencies = {...})` zonder wildcards; `ModulithArchitectureTest` dwingt dat af.
Controllers staan in een `api`-subpackage.

| Module | Mag gebruiken | Inhoud |
|---|---|---|
| `settings` | – | `WorkflowSettings`, `CollectionSettings`, `SettingsService` (JSON lezen/schrijven, opschonen), `WorkflowProperties` (properties), `SettingsChanged`; `api/SettingsController` |
| `exerunner` | – | `ExeRunner`: start één extern programma tegelijk, leest uitvoer, stuurt invoer, vaste antwoorden, logbestand per run |
| `ftp` | settings | `FtpService`: ftp-upload zoals images.exe, nooit overschrijven; `api/FtpController` (verbinding testen) |
| `zcbs` | settings | `ZcbsClient` (HTTP naar `/cgi-bin/<collectie>.pl`, cookies in het geheugen), `ZcbsPages` (antwoorden herkennen), `FormParser`/`ItemForm`, `RecordRebuilder` (controle tegen veld `a0`), `FreeNumbers` |
| `adminlog` | settings | `AdminLogClient` (meldingen aan hkh-admin), `SnapshotSanitizer`, `SettingsChangeReporter`, `AppLogAppender`/`AppLogSpool`/`AppLogShipper` (app-log), `AppVersion` |
| `items` | adminlog, zcbs, settings | `ItemEditService` (facade), `EditSession` (de ZCBS-sessie en het bewerken van één gebruiker), `Current` (wie is aan het woord), `RememberLogin`, `EditLogReporter`; `api/ItemsController` |
| `testrun` | adminlog, settings, exerunner, ftp, items, zcbs | `TestRunService` (registry van doorlopen), `FlowSession` (één doorloop: fasen, controles, stappen), `WorkFolders`, `ProgramFiles`, `SourceFilesService`, `ProcessedLog`, `FlowState` (bewaarde stand), `ExeAnswers`, `FlowLogReporter`; `api/NewFilesController`, `api/TestController` |
| `ux` | adminlog, settings, items, testrun | `CurrentContextFilter`, `WebConfig` (wie waar mag komen), `AdminAccess`; `api/ShellController`, `api/SessionsController` |

## 3. Wie is aan het woord: meerdere gebruikers

- **`Current`** is een `ThreadLocal` met de gebruiker en de collectie van het lopende verzoek. `CurrentContextFilter` vult hem uit de HTTP-sessie (`zcbsUser`, `collectie`).
  Achtergronddraden zetten hem zelf met `Current.runAs(user, collection) { … }`.
- **`ItemEditService`** is een facade over een register van `EditSession`s (sleutel: naam in kleine letters). Elke aanroep gaat naar de sessie van de gebruiker die aan het woord is.
  Een `EditSession` bewaart de inlog per collectie (`ZcbsClient` per collectie waar de gebruiker rechten heeft), de gekozen records en hun status.
- **`TestRunService`** is een register van `FlowSession`s met sleutel `<gebruiker in kleine letters>/<collectie>`. **Er is geen slot per collectie** (besluit 1 november 2026);
  twee gebruikers in dezelfde collectie hebben dus twee doorlopen met eigen `ExeRunner`, eigen mappen en eigen stand. `active()` voedt de pagina Sessies, `stop()` stopt een doorloop.
- **Nummerconflicten** zijn het enige dat gebruikers delen. `FlowSession.numbersTaken` controleert vlak voor de eerste poging van images.exe via `FreeNumbers.check`
  dat de nummers nog vrij zijn (alleen bij de eerste poging: bij een herhaling bestaan de eigen records al).
- Werkmappen: `<werkmap>/<gebruiker>/<collectie>/{invoer, werk, pdf2zcbs, images, resten}`. `ProgramFiles.sync` kopieert het programma (exe, ini, `i_view32.exe`, plugins…)
  uit de hoofdmap naar `pdf2zcbs/` en `images/` en laat gegevensmappen en uitvoerbestanden daar weg. Elke exe draait met zijn eigen map als werkmap.

## 4. Wat waar wordt bewaard

| Wat | Waar | Opmerking |
|---|---|---|
| Instellingen | `hkh-workflow-settings.json` (`hkh.workflow.settings-file`) | Atomair geschreven; bevat het ftp-wachtwoord |
| "Ingelogd blijven" | cookie (AES-GCM) + sleutel `hkh-workflow-inloggen.key` naast het instellingenbestand | Zonder sleutelbestand is het cookie waardeloos |
| Wat al verwerkt is | `hkh-workflow-verwerkt.tsv` (`hkh.workflow.processed-log`) | Een regel per bestand |
| Stand van een doorloop | `<werkmap>/<gebruiker>/<collectie>/sessie.json` | Elke 5 s bewaard, bij het opstarten teruggezet; een lopende stap wordt *mislukt* |
| Logs van de programma's | `logs/<collectie>/<gebruiker>/run-….log` (`hkh.workflow.log-dir`) | Per run, met tijdstempels |
| Wachtrij voor hkh-admin | `logs/admin-spool/` | App-log; verdwijnt na een geslaagde verzending |
| ZCBS-wachtwoorden | alleen geheugen (en het versleutelde cookie) | |
| Beheerderswachtwoord | `hkh.workflow.admin-password` (properties/omgeving) | Leeg = niemand kan als beheerder inloggen |

## 5. Verbindingen naar buiten

| Waarheen | Hoe | Waarvoor |
|---|---|---|
| ZCBS (test in Docker of echt archief) | HTTP(S), latin-1, cookies per `ZcbsClient`; gewone weergave `ident=NNNNN` (leest, zet geen vergrendeling), formulier `ident=…&upd_form=edt` (vergrendelt 10 min), `search=LAST`, `ident=0&upd_form=new` (vrije nummers) | Inloggen, titels, bewerken, controles |
| ftp-server van HKH | Commons Net, passieve modus; gebruiker en basismap vast | Upload van tekst en pdf's, lijst wat er staat |
| `pdf2zcbs.exe`, `images.exe` | `ExeRunner` (processen, `PERLIO=:unix` optioneel, vaste antwoorden `ExeAnswers`) | Tekst/afbeeldingen maken; images uploadt zelf en maakt records |
| hkh-admin | `POST /api/workflow-log/…` met een vaste sleutel; één draad, samengevoegd, met backoff | Logboek (nooit blokkerend) |

## 6. Schermen en routes (kort)

| Pad | Wat |
|---|---|
| `/`, `/collectie`, `/collectie/{naam}` | Inloggen, collectie, twee keuzes |
| `/nieuw`, `/nieuw/…` | De doorloop (kiezen, voorbereiden, starten, controleren, resten, opnieuw), `/nieuw/bron/…` (bestand en details van een bron), `/nieuw/resultaat/{soort}/{nummer}` (uitvoer bekijken) |
| `/verwerkt` | Wat al verwerkt is |
| `/records`, `/records/kies`, `/records/{nr}` en `/opslaan`, `/conflict`, `/later` | Records aanpassen. `/voorbeeld` en `/terug` (voorbeeldscherm) bestaan nog, maar `record.html` verwijst er niet meer naar |
| `/zcbs/login`, `/zcbs/logout` | ZCBS-inlog |
| `/beheer`, `/beheer/inloggen`, `/beheer/uitloggen` | Beheerderslogin |
| `/instellingen`, `/instellingen/ftp-test`, `/instellingen/sessies`, `/instellingen/sessies/stoppen` | Beheer |
| `/test`, `/test/…` | Technische testpagina (stappenketen, console, logs) |

`WebConfig` bepaalt wie waar mag komen: `/collectie`, `/nieuw`, `/records`, `/verwerkt` vragen een ZCBS-inlog; `/instellingen` en delen van `/test` de beheerder.
Na een herstart logt de interceptor automatisch opnieuw in met het cookie.

## 7. Fouten en veerkracht

- Elke stap controleert vooraf (`problems()`) en achteraf (verwachte bestanden bestaan en zijn niet leeg; een programma dat 0 teruggeeft zonder bestanden is mislukt).
- Een onbekende vraag van een exe wordt niet beantwoord: de run stopt en toont de vraag.
- De meldingen aan hkh-admin vangen alle fouten af, hebben een begrensde wachtrij en lopen op een eigen draad.
- Per `ExeRunner` loopt hooguit één programma; een doorloop is `@Synchronized` waar stand wordt gewijzigd.

## 8. Bouwen, draaien en testen

```bash
mvn clean package            # Java 21; draait alle tests en bouwt target/hkh-workflow.jar
java -jar target/hkh-workflow.jar
```

- **Tests** (ruim 200): modulegrenzen (`ModulithArchitectureTest`), de ZCBS-client en pagina-herkenning met opgeslagen pagina's (`src/test/resources/zcbs`) en een nep-ZCBS (`FakeZcbs`),
  de stappenketen met nep-exe's, meerdere gebruikers, herstart, logboek-clients, instellingen. `-Dzcbs.live=true` draait de contracttests tegen de lokale Docker-ZCBS.
- **Zonder Windows:** `tools/mock/` bevat nep-versies van pdf2zcbs en images (Perl); `tools/docker/` een lokale ZCBS (poort 8081, ftp 2121/21);
  `tools/localfileserver/` testscans; `tools/windows-testlaptop/` scripts voor de echte exe's op een testlaptop.
- **Handleidingen:** de screenshots en pdf's in `manual/` worden gemaakt met `tools/docs/` (Playwright en Chrome tegen de lokale testomgeving); zie `tools/docs/README.md`.
- **Omgevingsvariabelen** (`application.properties`): `HKH_WORKFLOW_ADDRESS`, `…_PORT`, `…_SETTINGS_FILE`, `…_CONSOLE_CHARSET`, `…_ADMIN_PASSWORD`, `…_PROCESSED_LOG`, `…_ADMIN_LOG_URL` (leeg = geen meldingen), `…_LOG_DIR`.
- **Draaien op de server:** nu macOS (Windows benadert mappen via de localfileserver); later een Windows-service met automatisch bijwerken (voorstel in [auto-update.md](auto-update.md)).
  Bij een Windows-service UNC-paden (`\\server\…`) gebruiken voor werkmap en scans.

## 9. Bekende beperkingen en open punten

- De echte `images.exe`/`pdf2zcbs.exe` zijn nog niet bevestigd vanuit een map van een gebruiker op Windows (de mocks wel); zie [testen-bij-hkh.md](testen-bij-hkh.md).
- Alleen het archief is ingeschakeld (`IMPLEMENTED_COLLECTIONS`); de instellingen per collectie bestaan al.
- De collectie is per browsersessie; geen collectie in het adres.
- Zonder slot kunnen twee gebruikers tegelijk dezelfde nummers kiezen; alleen de controle vlak voor images vangt dat af.
- Het ftp-wachtwoord staat leesbaar in het instellingenbestand en (optioneel) in het logboek bij hkh-admin.

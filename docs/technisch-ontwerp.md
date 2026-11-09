# Technisch ontwerp — ZcbsServer (hkh-workflow)

Stand: 1 november 2026. Wat er functioneel gebeurt staat in [functioneel-ontwerp.md](functioneel-ontwerp.md); besluiten en geschiedenis in
[specs.md](specs.md); hoe ZCBS en de programma's werken in [zcbs-achtergrond.md](zcbs-achtergrond.md).

## 1. Techniek

| Onderwerp | Keuze |
|---|---|
| Vorm | Eén runnable jar (`target/hkh-workflow.jar`), poort 9000, luistert op alle adressen |
| Taal/framework | Kotlin 2.2.21, Spring Boot 4.0.6, Spring Modulith 2.0.0, Java 21, Maven. Jackson 3 (`tools.jackson`) |
| Schermen | React-frontend in `frontend/` op een REST-API; gaat mee in de jar. Zie [frontend.md](frontend.md) en [api.md](api.md) |
| Opslag | Bestanden, geen database (zie §4) |
| Pdf's lezen | Apache PDFBox (aantal pagina's, afmeting) |
| Ftp | Apache Commons Net |
| Package | `nl.vdzon.hkhworkflow` |

## 2. Modules (Spring Modulith)

Elke module heeft een `package-info.java` met `@ApplicationModule(allowedDependencies = {...})` zonder wildcards. De root van een module bevat alleen een kleine interface en domeinklassen; de implementatie staat in `internal` en de controllers in `api`. De afspraken staan in [modules.md](modules.md); `ModulithArchitectureTest` en `ModuleApiConventionTest` dwingen ze af.

| Module | Mag gebruiken | Inhoud |
|---|---|---|
| `settings` | – | De instellingen: `SettingsService`, `WorkflowSettings`, `WorkflowProperties`, `SettingsChanged`. `internal`: `DefaultSettingsService`, `SettingsFile` (het bestand op schijf). `api`: `SettingsApiController` |
| `exerunner` | – | `ExeRunner`: start één extern programma tegelijk, leest uitvoer, stuurt invoer, vaste antwoorden, logbestand per run. `internal`: `ProcessExeRunner` |
| `ftp` | settings | `FtpService`: ftp-upload zoals images.exe, nooit overschrijven. `internal`: `DefaultFtpService`. `api`: `FtpApiController` |
| `zcbs` | settings | `ZcbsClient`, `ItemForm`, `Page`, `FreeNumbers`, `ZcbsText`. `internal`: `HttpZcbsClient` (HTTP naar `/cgi-bin/<collectie>.pl`, cookies in het geheugen), `ZcbsPages` (antwoorden herkennen), `FormParser`, `RecordRebuilder` (controle tegen veld `a0`) |
| `adminlog` | settings | `AdminLog`, `SessionReport`, `SnapshotSanitizer`, `AppVersion`. `internal`: `AdminLogClient`, `SettingsChangeReporter`, `AppLogAppender`/`AppLogSpool`/`AppLogShipper` (app-log) |
| `items` | adminlog, zcbs, settings | `ZcbsSession` (de inlog, voor andere modules), `LoginCookie`, `Current` (wie is aan het woord), `NewRecordsSource`. `internal`: `ItemEditService`, `EditSession` (de sessie en het bewerken van één gebruiker), `RecentRecords`, `RememberLogin`, `EditLogReporter`. `api`: `RecordsApiController` |
| `flow` | adminlog, settings, exerunner, ftp, items, zcbs | `RunningFlows`. `internal`: `FlowService` (register van doorlopen), `FlowSession` (één doorloop), `StepChecks` (wat houdt een stap tegen), `FlowViews` (wat de gebruiker ziet), `LeftoverFolders`, `FlowLayout`, `WorkFolders`, `ProgramFiles`, `SourceFilesService`, `ProcessedLog`, `FlowLogReporter`. `api`: `FlowApiController`, `AdminApiController` |
| `restapi` | – | `ApiProblem`: de foutvorm van de API. `internal`: `ApiExceptionHandler`, `OpenApiConfig`, `KotlinNullability` |
| `update` | adminlog, settings, flow | Geen openbare kant. `internal`: `UpdateService` (kijkt op GitHub of er een nieuwere build is, zet de jar klaar, herstart met exitcode 10), `GitHubReleases`. `api`: `VersionApiController` |
| `ux` | adminlog, settings, items | Geen openbare kant. `internal`: `CurrentContextFilter`, `FrontendConfig` (serveert de React-app), `WebConfig` (wie waar mag komen), `AdminAccess`. `api`: `SessionApiController` |

## 3. Wie is aan het woord: meerdere gebruikers

- **`Current`** is een `ThreadLocal` met de gebruiker en de collectie van het lopende verzoek. `CurrentContextFilter` vult hem uit de HTTP-sessie (`zcbsUser`, `collectie`).
  Achtergronddraden zetten hem zelf met `Current.runAs(user, collection) { … }`.
- **`ItemEditService`** (voor andere modules: `ZcbsSession`) is een facade over een register van `EditSession`s (sleutel: naam in kleine letters). Elke aanroep gaat naar de sessie van de gebruiker die aan het woord is.
  Een `EditSession` bewaart de inlog per collectie (`ZcbsClient` per collectie waar de gebruiker rechten heeft), de gekozen records en hun status.
- **`FlowService`** is een register van `FlowSession`s met sleutel `<gebruiker in kleine letters>/<collectie>`. **Er is geen slot per collectie** (besluit 1 november 2026);
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

| Scherm | Wat |
|---|---|
| `/`, `/collectie/{naam}` | Inloggen, collectie kiezen, twee keuzes |
| `/collectie/{naam}/nieuw` | De doorloop van nieuwe bestanden; de fase komt van de server |
| `/collectie/{naam}/records`, `.../records/{nr}` | Records aanpassen |
| `/beheer` | Beheerderslogin |
| `/instellingen`, `/instellingen/sessies`, `/instellingen/versie`, `/instellingen/logs` | Beheer |

Deze adressen zijn schermen van de frontend; `FrontendConfig` geeft daarvoor `index.html`. De gegevens komen uit de REST-API, zie [api.md](api.md). `WebConfig` bepaalt wie wat mag in de API: de gegevens van een collectie vragen een ZCBS-inlog, het beheer vraagt de beheerder.
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
  de stappenketen met nep-exe's, meerdere gebruikers, herstart, logboek-clients, instellingen. De integratietests en `ZcbsContractTest` draaien tegen een eigen nabouw van ZCBS in Docker, en met `-Dzcbs.real=true` tegen de echte scripts; zie [integratietests.md](integratietests.md).
- **Zonder Windows:** `tools/mock/` bevat nep-versies van pdf2zcbs en images (Perl); `tools/docker/` een lokale ZCBS (poort 8081, ftp 2121/21);
  `tools/localfileserver/` testscans; `tools/windows-testlaptop/` scripts voor de echte exe's op een testlaptop.
- **Handleidingen:** de screenshots en pdf's in `manual/` worden gemaakt met `tools/docs/` (Playwright en Chrome tegen de lokale testomgeving); zie `tools/docs/README.md`.
- **Omgevingsvariabelen** (`application.properties`): `HKH_WORKFLOW_ADDRESS`, `…_PORT`, `…_SETTINGS_FILE`, `…_CONSOLE_CHARSET`, `…_ADMIN_PASSWORD`, `…_PROCESSED_LOG`, `…_LOG_DIR`.
- **Draaien op de server:** nu macOS (Windows benadert mappen via de localfileserver); op Windows als service (`tools/windows-server`, zie §8a en [auto-update.md](auto-update.md)).
  Bij een Windows-service UNC-paden (`\\server\…`) gebruiken voor werkmap en scans.

## 8a. Build, release en bijwerken

- **Buildnummer.** `pom.xml` zet via `build-info` het buildnummer (`-Dbuild.number`, het `run_number` van de GitHub Action), de commit (`-Dbuild.commit`) en de bouwtijd in
  `META-INF/build-info.properties`. Lokaal blijven nummer en commit `0` en `-`. `AppVersion.info` leest ze; onderin het menu staat klein `build 57 · 2 nov 2026 20:34`
  (tijd in Nederlandse tijd), en het workflow-logboek krijgt `build 57 (0.0.1-SNAPSHOT+20261102.1934)`.
- **Release per build.** `.github/workflows/build.yml` draait bij elke push naar `main` `mvn clean package` (met alle tests) en zet bij een groene build een GitHub Release
  `build-<nummer>` met `hkh-workflow.jar` en `hkh-workflow.jar.sha256` klaar; de laatste 20 blijven staan. De repository is publiek, dus de server downloadt zonder token.
- **Bijwerken.** `UpdateService` vraagt `/repos/<repo>/releases` op en neemt de release met het **hoogste buildnummer** (niet de nieuwste aanmaaktijd). Is dat hoger dan het draaiende
  nummer, dan downloadt hij de jar naar `updates/hkh-workflow.jar.part`, controleert de sha256 (die van de Release) en of het buildnummer in de jar klopt, schrijft het `.sha256`-bestand
  en hernoemt de jar pas dan naar `hkh-workflow.jar`. Daarna sluit de app na twee seconden af met **exitcode 10**; `tools/windows-server/run.ps1` plaatst de jar en start hem
  (en draait terug als hij niet gezond wordt). Dit gebeurt alleen als de app als service draait (`HKH_SERVICE=1`, door `hkh-workflow.xml` gezet) en alleen als er geen programma of
  stap (upload, back-up) loopt, gecontroleerd vlak voor het downloaden en nog eens erna (`FlowSession.busy()`).
- **Elke nacht.** `UpdateService.nightly()` draait elke nacht om 00:00 (`hkh.workflow.update-cron`, Nederlandse tijd) als de instelling `nightlyUpdate` aan staat. Is er dan nog een programma
  bezig, dan probeert `retryNightly()` het elke 10 minuten opnieuw tot 03:00; daarna slaat hij die nacht over. Alle gebeurtenissen staan als INFO in de app-log (dus ook in hkh-admin) en
  de laatste twintig op de pagina Versie.
- Instellingen: `hkh.workflow.update-dir` (standaard `updates`), `hkh.workflow.update-repo` (standaard `robbertvdzon/hkh-workflow`), `hkh.workflow.update-cron`, `hkh.service`.

## 9. Bekende beperkingen en open punten

- De echte `images.exe`/`pdf2zcbs.exe` zijn op de testlaptop (Windows 10, als service onder LocalSystem) vanuit een map van een gebruiker bevestigd; op de echte server met de echte servicegebruiker nog niet; zie [testen-bij-hkh.md](testen-bij-hkh.md) en [../tools/windows-server/README.md](../tools/windows-server/README.md).
- Alleen het archief is ingeschakeld (`IMPLEMENTED_COLLECTIONS`); de instellingen per collectie bestaan al.
- De collectie is per browsersessie; geen collectie in het adres.
- Zonder slot kunnen twee gebruikers tegelijk dezelfde nummers kiezen; alleen de controle vlak voor images vangt dat af.
- Het ftp-wachtwoord staat leesbaar in het instellingenbestand en (optioneel) in het logboek bij hkh-admin.

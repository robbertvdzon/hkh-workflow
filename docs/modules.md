# Modules, DTO's en tests

De afspraken over hoe de code in elkaar zit. Ze gelden voor iedereen die code schrijft, ook voor AI. Wat afdwingbaar is, wordt door de build afgedwongen.

## Modules

Elk direct subpackage van `nl.vdzon.hkhworkflow` is een Spring Modulith-module.

```
zcbs/
  ZcbsClient.kt      interface: het enige wat andere modules van ZCBS zien
  ItemForm.kt        domeinklassen die bij die interface horen
  Page.kt
  internal/          de implementatie: http, html lezen, de record-opbouw. Voor andere modules onzichtbaar
items/
  ZcbsSession.kt     de kleine kant voor andere modules
  api/               controllers
  internal/          services en de rest
```

- **De root is de openbare kant.** Daar staan alleen interfaces en de domeinklassen die daarbij horen, zo weinig mogelijk.
- **Een nieuwe klasse hoort standaard in `internal`.** Alleen in de root als een andere module hem echt nodig heeft. Begin bij de vraag: wat heeft een andere module hiervan nodig?
- **Controllers staan in `api`,** bij de module waar de functie hoort.
- **`package-info.java`** noemt per module welke andere modules hij mag gebruiken, zonder wildcards. Nieuwe modules komen ook in `ModulithArchitectureTest`.

| Module | Wat anderen zien |
|---|---|
| `settings` | `SettingsService`, `WorkflowSettings`, `WorkflowProperties`, `SettingsChanged` |
| `exerunner` | `ExeRunner`, `ExeRunnerFactory` en hun gegevens |
| `ftp` | `FtpService` en zijn gegevens |
| `zcbs` | `ZcbsClient`, `ZcbsClientFactory`, `ItemForm`, `Page`, `FreeNumbers`, `ZcbsText` |
| `adminlog` | `AdminLog`, `SessionReport`, `SnapshotSanitizer`, `AppVersion` |
| `items` | `ZcbsSession`, `LoginCookie`, `Current`, `NewRecordsSource` |
| `testrun` | `RunningFlows` |
| `update`, `ux` | niets: alleen pagina's |

`ModuleApiConventionTest` faalt als er een service, client, configuratie of controller in een root staat, als er in een root een gewone klasse staat die niet op de korte lijst van domeinklassen staat, of als een module in de subpackages van een andere module kijkt. `ModulithArchitectureTest` bewaakt de toegestane afhankelijkheden.

## DTO's en domein

Aan elke rand van de app staat een eigen klasse voor wat daar over de lijn gaat, en daarbinnen werkt de app met domeinklassen. Een DTO heeft alleen zin aan een echte rand; binnen de app worden klassen niet gespiegeld.

| Rand | DTO of vertaling | Domein |
|---|---|---|
| Het instellingenbestand | `SettingsFile`, leest ook oudere bestanden | `WorkflowSettings` |
| ZCBS (html over http) | `HttpZcbsClient`, `ZcbsPages`, `FormParser` | `ItemForm`, `Page`, `RecordSummary`, `FreeNumbers` |
| GitHub | `GitHubRelease` | `Release` |
| Workflow-logboek | `LoginMessage`, `SessionReport` | wat de reporters verzamelen |
| Verwerkt-log (tsv) | `ProcessedLine` | `ProcessedFile` |
| Knop Open (json) | `MediaResponse` | `Media` |
| Pagina's | viewklassen zoals `RecentRow`, `TestRunView` | de domeinklassen |

## Tests

- **Integratietests** beschrijven wat de app doet, van buitenaf: zie [integratietests.md](integratietests.md). Nieuw gedrag krijgt eerst een scenario.
- **Unit-tests** zijn voor logica en vervangen de poort van een andere module, zonder netwerk. `StubZcbs` vervangt `ZcbsClient` in het geheugen.
- **`ZcbsContractTest`** legt vast hoe de client met ZCBS praat, tegen de nabouw en met `-Dzcbs.real=true` tegen de echte scripts.

**Klaar is:** `mvn clean verify` is groen.

# Instructies voor agents

- Lees eerst **[docs/specs.md](docs/specs.md)** (context, keuzes, status, volgende stappen) en
  **[docs/zcbs-achtergrond.md](docs/zcbs-achtergrond.md)** (hoe ZCBS, images.exe en pdf2zcbs.exe werken) en
  **[docs/zcbs-analyse-en-wrapper.md](docs/zcbs-analyse-en-wrapper.md)** (bevindingen uit de serverbestanden en het ontwerp van de wrapper).
- Taal: Nederlands voor UI, documentatie en commitberichten.
- Stijlreferentie: `~/git/hkh/backend` (Kotlin, Spring Boot 4, Spring Modulith, Jackson 3). Elke module een
  `package-info.java` met `allowedDependencies` (geen `*`); nieuwe modules toevoegen aan `ModulithArchitectureTest`.
- **Modules:** lees [docs/modules.md](docs/modules.md) voordat je code schrijft. De root van een module bevat alleen een kleine interface en domeinklassen; **een nieuwe klasse hoort standaard in `internal`**. Controllers in `api`. DTO's aan de randen, domeinklassen daarbinnen. `ModuleApiConventionTest` dwingt dit af.
- **REST-API:** zie [docs/api.md](docs/api.md). De API gaat over dingen en niet over schermen, `GET` verandert niets, en een fout is altijd een `ApiProblem`.
- **Frontend:** zie [docs/frontend.md](docs/frontend.md). React in `frontend/`, praat alleen met de API, en de types komen uit `frontend/openapi.json`. Verandert de API, draai dan `tools/update-openapi.sh`.
- **Klaar is:** `mvn clean verify` is groen.
- Bouwen/testen: `mvn clean package` (Java 21). Thuis testen zonder Windows met `tools/mock/`.
- **Integratietests:** zie [docs/integratietests.md](docs/integratietests.md). Nieuw gedrag krijgt eerst een Cucumber-scenario in `src/test/resources/features`. Code onder `integration/` kent de app niet.
- **Nooit code van de echte ZCBS committen** (`objecten.pl`, `cfg-cbs.pl` enzovoort): die is van Gerard van Nes en deze repository is publiek.
- Houd `docs/specs.md` bij als er iets besloten of gebouwd wordt (beslissingenlog, status, volgende stappen).

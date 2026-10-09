# Instructies voor agents

- Lees eerst **[docs/specs.md](docs/specs.md)** (context, keuzes, status, volgende stappen) en
  **[docs/zcbs-achtergrond.md](docs/zcbs-achtergrond.md)** (hoe ZCBS, images.exe en pdf2zcbs.exe werken).
- Taal: Nederlands voor UI, documentatie en commitberichten.
- Stijlreferentie: `~/git/hkh/backend` (Kotlin, Spring Boot 4, Spring Modulith, Jackson 3). Elke module een
  `package-info.java` met `allowedDependencies` (geen `*`); nieuwe modules toevoegen aan `ModulithArchitectureTest`.
- Bouwen/testen: `mvn clean package` (Java 21). Thuis testen zonder Windows met `tools/mock/`.
- Houd `docs/specs.md` bij als er iets besloten of gebouwd wordt (beslissingenlog, status, volgende stappen).

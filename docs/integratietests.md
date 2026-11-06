# Integratietests

De integratietests starten de hele app één keer en praten er alleen van buitenaf mee, zoals de buitenwereld dat ook doet: http naar de app, http en ftp naar ZCBS, en bestanden op schijf. Ze kennen de code van de app niet, dus ze blijven gelijk als de code eronder wordt omgebouwd.

```bash
mvn test -Dtest=CucumberIntegrationTest
```

`mvn clean package` draait ze ook. Zonder Docker worden ze overgeslagen.

## Hoe het in elkaar zit

| Onderdeel | In de test |
|---|---|
| De app | De echte Spring Boot-app, eenmaal gestart op een willekeurige poort (`CucumberSpringConfiguration`) |
| De gebruiker | `Api`: praat met de REST-API zoals de frontend dat doet (JSON en cookies). `Browser` is er alleen voor wat buiten de API valt: een bestand van de site en het formulier naar ZCBS zelf |
| ZCBS | Een Docker-container met Apache, een eigen Perl-script en vsftpd (`src/test/resources/zcbs-docker`, `ZcbsServer`) |
| pdf2zcbs.exe en images.exe | Shellscripts (`src/test/resources/integration/programs`): dezelfde vragen, en images uploadt echt met ftp naar de container en zet de records in `updates.txt`, zoals het echte programma |
| GitHub en het workflow-logboek van hkh-admin | WireMock (`TestEnvironment`) |
| Scans, werkmap, back-ups, instellingen | Een tijdelijke map (`TestEnvironment`) |

De scenario's staan in `src/test/resources/features`. Elk scenario begint schoon: `zcbs-reset` zet de bestanden van ZCBS terug in de beginstand (leeg archief met de testrecords 99003 en 99067) en elke persoon in een scenario krijgt een eigen, nieuw ZCBS-account, zodat de app niets onthoudt van een eerder scenario.

**De regel:** code onder `integration/` importeert niets uit de app behalve `HkhWorkflowApplication`. `BlackBoxRuleTest` dwingt dat af.

## De nagebouwde ZCBS

`public_html/cgi-bin/archief.pl` is eigen code; er zit niets van de echte ZCBS in. De code van de echte ZCBS is van Gerard van Nes en deze repository is publiek, dus die hoort hier nooit in (`.gitignore` sluit `objecten.pl` en `cfg-cbs*.pl` uit).

De nabouw doet alleen wat de app gebruikt: inloggen, `search=LAST`, de vrije nummers, een record bekijken, het bewerkformulier met de vergrendeling, opslaan, en de bestanden op de site. De database zijn tekstbestanden met dezelfde indeling en op dezelfde plek als bij de echte ZCBS: `archief.txt`, `updates.txt` (de laatste regel per nummer wint), `permit.lst` en `logging/lock_object.txt`. Daardoor werkt dezelfde beginstand op allebei.

Eén ding bestaat alleen bij de nabouw: `logging/sessions.txt`, om sessies te laten verlopen. Dat scenario heeft de tag `@alleen-nabouw`.

## Het ijkpunt: de echte ZCBS

Een nabouw bewijst alleen dat de app werkt met ons begrip van ZCBS. Daarom kunnen dezelfde tests ook tegen de echte scripts draaien, uit de lokale, niet in git opgenomen map `tools/docker/zcbs/files`. Die scripts worden tijdelijk in het image gezet, naast dezelfde beginstand; jouw eigen container op poort 8081 en je bestanden blijven ongemoeid.

```bash
mvn test -Dtest='ZcbsContractTest,CucumberIntegrationTest' -Dzcbs.real=true
```

Draai dit als er iets verandert aan de kant van ZCBS (de client, het script, of een nieuwe ZCBS-versie). Zo vonden we al dat de echte ZCBS na het opslaan de vergrendeling niet vrijgeeft en veld 33 ongemoeid laat. `ZcbsContractTest` legt dit soort afspraken vast met de client van de app zelf; hij mag de app wel kennen en staat daarom buiten `integration/`.

Niet nagebootst: de routervraag (HKH-netwerk) en de snelheidsrem van ZCBS. Die worden getest met de opgeslagen echte pagina's in `src/test/resources/zcbs` (`ZcbsFixturesTest`).

## Wat ze niet testen

- Windows: de echte exe's, de codepagina IBM850 en Windows-paden. Daarvoor blijft `tools/windows-testlaptop` en [testen-bij-hkh.md](testen-bij-hkh.md).
- De schermen: de scenario's gaan via de REST-API en openen geen browser. De oude Thymeleaf-pagina's zijn sinds de overstap op de API niet meer gedekt.
- Het echte archief bij HKH.

## Een scenario toevoegen

Nieuw gedrag krijgt eerst een scenario. Schrijf het in gewone taal over wat iemand doet en ziet, niet over knoppen of code. Gebruik bestaande stappen waar het kan; nieuwe stappen komen in `integration/steps`.

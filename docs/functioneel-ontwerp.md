# Functioneel ontwerp — ZcbsServer (hkh-workflow)

Stand: 1 november 2026. Dit document beschrijft wat de app doet en voor wie, zonder techniek. De techniek staat in
[technisch-ontwerp.md](technisch-ontwerp.md), de besluiten en de geschiedenis in [specs.md](specs.md), en de
gebruikershandleidingen in de map [../manual](../manual/README.md).

## 1. Waarvoor is de app

Historische Kring Heemskerk (HKH) bewaart zijn archief in **ZCBS**. Nieuwe scans (pdf's en afbeeldingen) in ZCBS zetten was handwerk
met twee Windows-programma's (**pdf2zcbs.exe** en **images.exe**), een ftp-upload en een back-up. ZcbsServer legt die werkwijze in
één webapp vast, met controles op elke stap, zodat een medewerker alleen hoeft te klikken. Daarnaast kunnen records in ZCBS via de app
worden ingevuld en aangepast.

De app **vervangt ZCBS niet**: alles gebeurt in ZCBS zelf (via zijn eigen formulier en scripts) en met de bestaande programma's.
De app draait ze aan, controleert wat ze doen en onthoudt de stand.

## 2. Wie gebruikt de app

| Rol | Wat ze doen |
|---|---|
| **Medewerker** (bijvoorbeeld Carla, Jelly, Ria) | Logt in met de eigen ZCBS-naam, kiest een collectie en zet nieuwe bestanden in ZCBS of past records aan. |
| **Beheerder** | Logt daarnaast in als beheerder: instellingen, de pagina Sessies, de technische testpagina. |
| **Robbert (ontwikkelaar)** | Ziet via hkh-admin (workflow-logboek) wat er gebeurt: inlogpogingen, doorlopen, instellingen, app-log. |

De app draait op één server. Medewerkers openen hem in de browser op hun eigen pc.

## 3. Begin: inloggen en collectie kiezen

1. **Inloggen bij ZCBS** met de eigen naam en het eigen wachtwoord. De app probeert die combinatie bij het script van elke collectie;
   de rechten komen dus van ZCBS zelf (per collectie). Het wachtwoord blijft in het geheugen; voor "ingelogd blijven na een herstart"
   staat het versleuteld in een cookie van 30 dagen.
2. **Een collectie kiezen.** Alle zeven collecties van HKH staan op het scherm; alleen collecties waar de gebruiker rechten heeft **en**
   die de app ondersteunt, zijn te kiezen. Nu is dat alleen het **archief**.
3. **Een van twee dingen doen:** *Nieuwe bestanden in ZCBS zetten* of *ZCBS records aanpassen*.

Het menu bevat Start en (voor de beheerder) Instellingen en Sessies. Onderin staan de rechten van de ingelogde gebruiker en, als de app met het echte
archief werkt, een waarschuwing.

## 4. Nieuwe bestanden in ZCBS zetten

Eén doorloop van **zeven fasen**; de app laat steeds zien waar je bent en wat de volgende stap is. Details en alle controles:
[manual/invoeren.md](../manual/invoeren.md).

| Fase | Wat | Kern |
|---|---|---|
| 1 | Controleren | De app controleert of alles klaarstaat (ZCBS bereikbaar, programma's, mappen, back-upmappen, ftp). Resten van een eerdere keer moeten eerst worden opgeruimd. |
| 2 | Bestanden kiezen | Bladeren door de mappen met scans (per scanner/persoon), pdf's, jpg's en png's openen in een popup, kiezen wat mee moet. De app werkt met kopieën; de originelen blijven staan. |
| 3 | Nummers | Beginnummer kiezen uit de vrije nummers van ZCBS. De bestanden krijgen een recordnummer als naam. |
| 4 | pdf2zcbs | Maakt tekst (ocr), pdf en afbeeldingen. Daarna moet de medewerker de uitvoer controleren en bevestigen. |
| 5 | images | Maakt de afgeleide afbeeldingen, uploadt ze en maakt de lege records in ZCBS. Ook hier controleren en bevestigen. |
| 6 | Upload | Tekst en pdf's gaan per ftp naar de server; er wordt nooit iets overschreven. Een lijst laat zien wat er op de server staat. |
| 7 | Back-up | Kopieën van de 100pct-afbeeldingen en de pdf's naar de back-upmappen, daarna wordt opgeruimd. Het verwerkt-log onthoudt wat klaar is. |

Belangrijke regels:

- **jpg en png** kunnen net als pdf's als bron worden gekozen. Ze slaan pdf2zcbs en de ftp-upload van tekst/pdf over (er is geen tekst of pdf)
  en gaan rechtstreeks naar images; een png wordt bij het kiezen een jpg.
- **Nooit overschrijven of verwijderen** op de server; wat al bestaat blijft staan en de app meldt het.
- **Nummercontrole vlak voor images.** Meer mensen kunnen tegelijk werken, ook in dezelfde collectie. De recordnummers zijn het enige dat ze
  kunnen delen; daarom controleert de app vlak voordat images de records aanmaakt nog eens of de nummers vrij zijn. Is er intussen een nummer
  ingenomen, dan start images niet en moet je opnieuw beginnen met een ander beginnummer.
- **Opnieuw beginnen** is altijd mogelijk; het wist de gekozen kopieën maar laat originelen en eigen bestanden staan.
- **Herstart:** de stand van elke doorloop wordt bewaard. Na een herstart van de app staat de gebruiker in dezelfde fase; een stap die op dat
  moment liep staat dan op *mislukt* en kan opnieuw.
- Wat al verwerkt is staat op de pagina **Verwerkt**.

## 5. ZCBS records aanpassen

Records kiezen (een nummer of een reeks, maximaal 30 tegelijk), zien welke titel ze hebben en of ze al op internet staan, en ze één voor één invullen
via het formulier van ZCBS. Wat *Later* en *Volgende zonder opslaan* doen: wat je in het formulier typte wordt niet bewaard. Details en controles: [manual/records-aanpassen.md](../manual/records-aanpassen.md).

- Per record in de lijst: **Bewerk** (of Verder / Nog eens), **Open in ZCBS** en de **pdf of afbeelding** in een popup.
- Opslaan gaat **direct** vanuit het formulier (*Opslaan en volgende*); een apart voorbeeldscherm met de wijzigingen bestaat in de code (`/records/{nr}/voorbeeld`),
  maar het formulier heeft er geen knop meer naartoe. De app slaat alleen op wat hij helemaal begrijpt (anders: *niet veilig*) en weigert tekens en woorden die ZCBS niet aankan.
- Opslaan = opnieuw inloggen, het formulier opnieuw ophalen (ZCBS vergrendelt dan het record 10 minuten), controleren of iemand anders het record intussen
  heeft gewijzigd (zo ja: een conflict met per veld de keuze), posten en **teruglezen**. De app verstuurt zelf geen e-mail; ZCBS doet dat bij een opslag.
- *Later* zet een record opzij; meer mensen kunnen tegelijk records aanpassen (ZCBS vergrendelt per record).

## 6. Beheer

- **Instellingen** (alleen beheerder): werkmap, mappen met scans, programma's, per collectie de resolutie en back-upmappen, ftp en de keuze voor de
  test-ZCBS of het echte archief. Zie [manual/instellingen.md](../manual/instellingen.md).
- **Sessies:** wie is er ingelogd en wie werkt aan nieuwe bestanden (collectie, fase, sinds wanneer, laatst actief); de beheerder kan een doorloop stoppen.
- **Technische testpagina** (`/test`): de losse stappenketen voor ontwikkelaars.

## 7. Meer gebruikers tegelijk

- Elke gebruiker heeft een **eigen ZCBS-sessie** en een **eigen doorloop** per collectie, met eigen mappen en een eigen kopie van de programma's.
- Er is **geen slot per collectie**: twee gebruikers kunnen tegelijk in dezelfde collectie nieuwe bestanden zetten. Het risico op dezelfde recordnummers is bewust
  genomen (besluit 1 november 2026) en wordt afgedekt door de nummercontrole vlak voor images.
- Dezelfde gebruiker op een andere pc komt gewoon in zijn eigen doorloop terug.
- De gekozen collectie hoort bij de browsersessie: twee collecties in dezelfde browser tegelijk gaat niet.

## 8. Logboek en meldingen

Elke inlogpoging, elke doorloop (met instellingen zonder wachtwoorden, bestanden, stappen en logs van de programma's), elke wijziging van de instellingen en
de app-log worden gemeld aan **hkh-admin** (pagina Workflow-logboek). De bron van een melding is de naam van de computer. Een fout of een onbereikbare server hindert de app
nooit. Het ftp-wachtwoord gaat alleen mee als de beheerder dat aanzet (standaard aan, bedoeld voor de beginperiode); andere wachtwoorden nooit.

## 9. Buiten beeld (nu)

- Andere collecties dan het archief (de app kent ze, maar ze staan nog uit).
- Zelf afbeeldingen maken zonder images.exe/pdf2zcbs.exe (idee, zie [specs.md](specs.md) §10).
- Automatisch bijwerken van de app op de Windows-server (voorstel, zie [auto-update.md](auto-update.md)).
- Bevestiging dat de programma's op Windows goed draaien vanuit de map van een gebruiker (uit de code gelezen, nog niet getest met de echte exe's).

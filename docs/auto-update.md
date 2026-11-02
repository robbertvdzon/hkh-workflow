# Automatisch bijwerken van de app op de Windows-server

Status (2 november 2026): **de Windows-service met het startscript is gebouwd en getest** op de testlaptop (§3, §5 en §8 stap 1, 2 en 5, zie
[../tools/windows-server/README.md](../tools/windows-server/README.md)). De updatecontrole in de app, het buildnummer en de download van GitHub (§6 en §7, zie ook §0) zijn
nog niet gebouwd. Oorspronkelijk voorstel van 31 oktober 2026, Robbert en Claude.

## 0. Besluiten van 2 november 2026

- **Waar de jar vandaan komt:** van GitHub, niet uit een map die Robbert zelf vult. Bij elke push naar `main` bouwt een GitHub Action de jar (eerst de tests) en zet hem als
  **GitHub Release** `build-<nummer>` met `hkh-workflow.jar` en een sha256. De repo is publiek, dus de server downloadt zonder token; in de jar staat niets geheims. Oude releases
  worden opgeruimd (bijvoorbeeld alles behalve de laatste 20). De map `updates\` blijft als tussenstap en voor een handmatige noodupdate.
- **Buildnummer:** het `run_number` van de Action gaat in `build-info` (`-Dbuild.number=`) en in `AppVersion` (nu `versie+bouwtijd`); de app vergelijkt nummers als gehele getallen.
- **Wat er live gaat:** alles wat op `main` staat en groen is. Zodra de app bij HKH draait werkt Robbert met branches en moet `main` altijd goed zijn; een aparte
  "stable"-markering is daarom niet nodig.
- **Bijwerken door de beheerder:** een blok Versie in Beheer ("Build X draait, build Y staat klaar", knoppen *Controleer nu* en *Nu bijwerken*). Alleen als geen enkele doorloop een
  programma draait (zie §6).
- **Elke nacht om 00:00** (Europe/Amsterdam) controleert de app zelf en werkt bij als er een nieuwere build is. Is er dan nog iemand bezig, dan elke 10 minuten opnieuw tot ongeveer 03:00; daarna
  slaat hij die nacht over en meldt dat in het logboek. Een schakelaar in Beheer zet dit aan of uit.
- **Eerst de service:** voordat de updater gebouwd wordt is getest of de app (met `images.exe` en `pdf2zcbs.exe`) als Windows-service goed werkt. Dat werkt (testlaptop, 2 november).

## 1. Wat we willen

- De app draait op de **fileserver** van HKH (Windows). Alles staat lokaal op die server; een netwerkletter als `K:` is er niet nodig.
- Robbert zet een nieuwe `hkh-workflow.jar` op één vaste plek op de server. De app ziet dat, en na een bevestiging (of als er niets
  gebeurt) vervangt hij zijn jar en start hij opnieuw op.
- Een mislukte update mag de app niet onbereikbaar maken: de vorige versie komt dan vanzelf terug.
- Instellingen (`settings.json`), de mapjes van de gebruikers en de logs blijven bij een update gewoon staan.

## 2. Waarom de app zijn eigen jar niet kan vervangen

Windows vergrendelt een draaiende jar: je kunt hem niet overschrijven of hernoemen zolang java hem gebruikt. De app moet dus eerst
stoppen, en iets anders (een klein startscript) moet de jar verwisselen en opnieuw starten. Daarom bestaat de oplossing uit twee delen:
een **service met startscript** eromheen, en een **updatecontrole in de app** zelf.

## 3. Als Windows-service draaien

De gebruikelijke manier om een programma bij het opstarten van Windows te laten draaien (zonder ingelogde gebruiker, met automatisch
herstarten na een crash) is een **Windows-service**. Een jar is zelf geen service; daarom gebruik je een wrapper:

| Keuze | Opmerking |
|---|---|
| **WinSW** (aanbevolen) | Eén `.exe` plus een XML-bestand; wordt onderhouden (Jenkins gebruikt het ook). Kan herstarten bij een crash, loggen en onder een gekozen account draaien. |
| NSSM | Oude favoriet, maar niet meer onderhouden. |
| Apache Procrun / commons-daemon | Werkt, maar omslachtiger voor Spring Boot. |
| Geplande taak "bij opstarten" | Noodoplossing: minder controle over stoppen en herstarten, en Windows ziet het niet als service. |

Installeren is eenmalig, in een beheerdersvenster: `WinSW.exe install`. Daarna starten en stoppen met `net start/stop <naam>` of via
Services.

### Aandachtspunten

1. **Servicegebruiker.** Liever geen `LocalSystem`, maar een eigen lokaal account dat alleen schrijfrechten heeft op `C:\zcbs-workflow` en op de
   mappen met scans en back-ups. Dat account heeft het recht "Aanmelden als service" nodig.
2. **images.exe en pdf2zcbs.exe in een service.** De app start ze nu al als kindprocessen, met in- en uitvoer via pipes en zonder eigen venster.
   Dat zou ook onder een service moeten werken, maar een service heeft geen interactieve sessie. **Dit moet als eerste op de server getest
   worden**, vóór er iets anders omheen gebouwd wordt. Werkt het niet, dan is het alternatief een geplande taak voor een vaste gebruiker
   met automatisch inloggen.
3. **Firewall.** De poort van de app (standaard 9000) moet open staan voor de computers van de vrijwilligers. De regel uit
   `tools/windows-testlaptop/app-start.ps1` is een voorbeeld.
4. **Paden.** Op de server zijn het gewone lokale paden. Staan er nog oude `K:\`-paden in de instellingen, dan moeten die aangepast worden.

## 4. Mapindeling op de server

```
C:\zcbs-workflow\
  app\current.jar        de draaiende versie
  app\previous.jar       de vorige versie, voor terugdraaien
  app\run.ps1            het startscript (zie §5)
  updates\               hier zet Robbert een nieuwe jar neer
  config\application.properties   externe instellingen naast de jar (blijft staan)
  settings.json          de instellingen van de app (blijft staan)
  logs\                  app.log, update.log
  service\               WinSW.exe en het XML-bestand
```

Alles buiten `app\` blijft bij een update onaangeroerd.

## 5. Het startscript (`run.ps1`)

De service start niet java, maar dit script, dat in een lus draait:

1. Staat er een jar in `updates\` (en een bijbehorend `.sha256` als controle)? Dan wordt `current.jar` hernoemd naar `previous.jar` en
   de nieuwe jar wordt `current.jar`. De gebruikte jar verdwijnt uit `updates\`.
2. Start `java -jar current.jar` (met de instellingen die nu in `app-start.ps1` staan).
3. Wacht na de start tot `/actuator/health` antwoordt (maximaal een minuut). Lukt dat niet en was dit een nieuwe jar, dan: `previous.jar`
   terugzetten, de mislukte jar naar `updates\mislukt\` verplaatsen, een regel in `update.log` en opnieuw starten.
4. Eindigt java met **exitcode 10** (de app vraagt een update), dan begint de lus opnieuw bij stap 1. Bij elke andere code, ook 0, wacht het script
   een paar seconden en start hij opnieuw: de service blijft altijd draaien, stoppen kan met `Stop-Service`. (Eerst was 0 een "gewone stop" waarbij het script eindigde; dat
   ging mis toen een daemon-thread de exitcode 10 verloor, zie hieronder.)

Zo is de service zelf eenvoudig (alleen "start dit script en herstart als het eindigt") en zit alle logica in één leesbaar script.

## 6. De updatecontrole in de app

- Een nieuwe module (bijvoorbeeld `update`, met `package-info.java` en een regel in `ModulithArchitectureTest`) kijkt elke minuut in
  `updates\` of er een jar ligt.
- **Welke versie is nieuwer?** De app kent zijn eigen versie via `META-INF/build-info.properties` (Spring Boot's `build-info`-goal in de
  pom, met versie en buildtijd; **al gebouwd** als `AppVersion`, bijvoorbeeld `0.0.1-SNAPSHOT+20261031.1405`, ook te zien in het workflow-logboek van hkh-admin). De bestandsnaam van een nieuwe jar bevat de versie, bijvoorbeeld `hkh-workflow-0.1.7.jar`; de app leest
  de versie uit de jar zelf (`build-info.properties` erin) in plaats van uit de bestandsnaam te vertrouwen. De versie in de pom moet dus bij
  elke release omhoog, of we gebruiken de buildtijd.
- **In Beheer** staat dan "Versie X draait, versie Y staat klaar" met een knop **Nu bijwerken**. Eerst alleen met die knop; automatisch
  bijwerken wanneer de app niet bezig is kan later.
- **Alleen als de app niet bezig is.** Een herstart midden in een upload of in images.exe zou een half archief geven. De app weigert
  (of wacht) zolang er een doorloop actief is. Sinds de mapjes per gebruiker en collectie kunnen er meerdere doorlopen tegelijk zijn: de
  controle kijkt dus naar **alle** `ExeRunner`s en niet alleen naar die van de gebruiker die klikt. Doorlopen die op een bevestiging wachten
  (niet actief) overleven een herstart omdat hun stand in `sessie.json` staat; daar hoeft de update dus niet op te wachten, maar de
  gebruiker ziet wel even een melding dat de app opnieuw start.
- **Stoppen.** Na de bevestiging sluit de app netjes af met `System.exit(10)` (Spring sluit eerst het contextpad af).
- **Terugdraaien** doet het script (§5, stap 3); de app hoeft er niets van te weten, behalve dat hij na de start in het logboek meldt welke
  versie draait.
- Het workflow-logboek (`adminlog`) kan de versie erbij melden, zodat in het beheerscherm van hkh te zien is welke versie van de app wat deed.

## 7. Waar de nieuwe versie vandaan komt

- **Map op de server (gekozen voor de eerste versie).** `deploy-app.sh` bouwt de jar en kopieert hem met scp naar `updates\`, net zoals nu
  naar de testlaptop. De stap "app op de laptop stoppen" en het WMI-starten vervallen daarmee: de app doet de herstart zelf.
- **Een URL (bijvoorbeeld een GitHub-release), later.** De app downloadt zelf en controleert een sha256. Handig als Robbert niet meer bij de
  server kan, maar meer werk (zekerheid over de bron, inloggegevens voor GitHub). Alleen bouwen als het nodig blijkt.
- **Veiligheid.** Wie in `updates\` kan schrijven, kan code op de server laten draaien. Geef alleen de beheerder schrijfrechten op die map.
  Een sha256 naast de jar beschermt tegen een half gekopieerd bestand, niet tegen iemand met schrijfrechten. Een bestand dat nog groeit
  (kopie bezig) wordt pas opgepakt als de grootte een halve minuut gelijk is gebleven of als het `.sha256`-bestand er is.

## 8. Volgorde van bouwen

1. Op de server testen of images.exe en pdf2zcbs.exe onder een service draaien (§3, punt 2), met een handmatig geïnstalleerde WinSW. **Gedaan op de testlaptop (2 november): werkt onder LocalSystem; nog te bevestigen op de echte server met de echte servicegebruiker.**
2. Het startscript `run.ps1` en de WinSW-configuratie, met een installatiescript (`tools/windows-server/`); eerst zonder updatecontrole. **Gedaan en getest, ook het terugdraaien (stap 5) met bewust kapotte jars.**
3. `build-info` in de pom en de updatemodule met de knop in Beheer.
4. `deploy-app.sh` aanpassen naar "bouwen en uploaden naar `updates\`".
5. Het terugdraaien en de statusmelding in het logboek testen met een bewust kapotte jar.
6. Pas daarna eventueel automatisch bijwerken en/of een download via een URL.

## 9. Open vragen

- Wat is het adres van de fileserver en kan Robbert er per SSH (OpenSSH) of via een share bij om de jar neer te zetten?
- Welk account wordt de servicegebruiker, en kan HKH dat aanmaken?
- Mag de app zichzelf automatisch bijwerken als er niemand werkt (bijvoorbeeld 's nachts), of altijd alleen na een klik?
- Moet de updatemap ook een eerdere versie kunnen terugzetten vanuit Beheer ("terug naar de vorige")?

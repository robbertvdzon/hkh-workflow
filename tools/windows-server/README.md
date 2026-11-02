# hkh-workflow als Windows-service

Hiermee draait de app op een Windows-server als **service**: hij start vanzelf na een reboot, herstart na een crash en kan zijn jar laten
vervangen zonder dat iemand de service met de hand stopt. Het ontwerp en de plannen voor de update zelf (downloaden van GitHub, de knop in
Beheer, elke nacht om 00:00) staan in [../../docs/auto-update.md](../../docs/auto-update.md).

Gebouwd en getest op de testlaptop (Windows 10 Home, Java, 2 november 2026); zie [Wat getest is](#wat-getest-is).

## Onderdelen

| Bestand | Wat |
|---|---|
| `run.ps1` | De kern: draait `java -jar`, wacht tot `/actuator/health` antwoordt, plaatst een nieuwe jar uit `updates\` en draait terug als die niet start |
| `hkh-workflow.xml` | Configuratie voor [WinSW](https://github.com/winsw/winsw) (v2.12.0): de service start `run.ps1`, vertraagd automatisch, herstart bij een crash |
| `install.ps1` | Eenmalig, als administrator: zet de mappen, WinSW, het script en de jar neer, maakt de firewallregel en installeert en start de service |
| `uninstall.ps1` | Haalt de service weg; mappen, instellingen en logs blijven staan |
| `deploy-testlaptop.sh` | Vanaf de Mac: bouwt de jar en installeert of werkt bij op de testlaptop (`--status`, `--update`, `--verwijderen`) |
| `firewall-werkmap.ps1` | Alleen testlaptop: blokkeert de kopieen van images.exe en pdf2zcbs.exe in de werkmap voor alles behalve de test-ZCBS |
| `winsw/WinSW.exe` | Niet in git; haal `WinSW.NET461.exe` van v2.12.0 op bij github.com/winsw/winsw/releases en noem hem zo |

## Mapindeling op de server

```
C:\zcbs-workflow\
  app\current.jar          de draaiende versie
  app\previous.jar         de versie daarvoor, om terug te draaien
  app\run.ps1              het startscript van de service
  updates\                 hier komt een nieuwe hkh-workflow.jar (met eventueel hkh-workflow.jar.sha256)
  updates\mislukt\         jars die niet geldig waren of niet startten
  config\application.properties   instellingen naast de jar (blijft staan)
  hkh-workflow-settings.json      de instellingen van de app (blijft staan)
  logs\update.log          wat run.ps1 doet; logs\app-uit.log en app-fout.log zijn de uitvoer van java
  service\                 WinSW (hkh-workflow.exe) en zijn xml
```

Alles buiten `app\` blijft bij een update onaangeroerd.

## Installeren (op de server)

Vereist Java 21 of nieuwer en een administratorvenster.

```powershell
.\install.ps1 -WinSw C:\pad\WinSW.exe -Jar C:\pad\hkh-workflow.jar                  # draait als LocalSystem
.\install.ps1 -WinSw ... -Jar ... -Account '.\hkhservice' -Wachtwoord '...'          # onder een eigen account (aanbevolen)
```

- **Eigen account (aanbevolen voor de echte server):** dat account heeft schrijfrechten nodig op `C:\zcbs-workflow`, de mappen met scans, de
  werkmap en de back-upmappen, en het recht "Aanmelden als service". Bij mappen op het netwerk moet het account daar ook bij kunnen
  (gebruik UNC-paden zoals `\\server\scans`, geen stationsletters).
- Opnieuw draaien mag; een bestaande service wordt eerst gestopt en opnieuw geinstalleerd.
- De poort is 9000 (`-Poort` om dat te wijzigen). De firewallregel laat alleen het lokale netwerk toe.
- Instellingen via omgevingsvariabelen (bijvoorbeeld `HKH_WORKFLOW_ADMIN_PASSWORD`) kunnen in `config\application.properties` (`hkh.workflow.admin-password=...`).

Beheer met gewone Windows-middelen: `Get-Service hkh-workflow`, `Stop-Service`, `Start-Service`, of het venster Services.

## Hoe het bijwerken werkt

Het startscript doet bij elke (her)start van java het volgende:

1. Staat er `updates\hkh-workflow.jar`? Dan controleert het de sha256 (als `hkh-workflow.jar.sha256` ernaast staat) en of het een geldige jar is
   (zip met manifest). Een jar die niet klopt gaat naar `updates\mislukt\` en de draaiende versie blijft.
2. Is de jar goed, dan wordt `current.jar` de nieuwe `previous.jar` en komt de nieuwe jar op `current.jar`.
3. Het script start java en wacht maximaal 90 seconden tot `/actuator/health` antwoordt. Lukt dat niet, of stopt java eerder met een fout, en de jar
   was nieuw, dan gaat die naar `mislukt\`, komt `previous.jar` terug en wordt de vorige versie gestart.
4. Eindigt java met **exitcode 10**, dan is het een verzoek van de app om bij te werken: de lus begint opnieuw bij stap 1. Alles anders (ook exitcode 0) is een onverwachte stop,
   waarna het script na 10 seconden opnieuw start: de service blijft draaien. Stoppen doe je met `Stop-Service` (WinSW beeindigt dan het hele proces).

Een nieuwe jar neerzetten gaat in twee stappen, zodat het script nooit een half bestand ziet: eerst kopieren als `hkh-workflow.jar.part`, dan hernoemen (en het
`.sha256`-bestand ervoor zetten). `deploy-testlaptop.sh --update` doet dat. De updatemodule van de app doet dat straks ook.

## Wat getest is

Op de testlaptop (Windows 10 Home, Java, service als LocalSystem), met de echte `images.exe` en `pdf2zcbs.exe` tegen de test-ZCBS op de Mac:

| Test | Uitkomst |
|---|---|
| Service installeren en starten, SSH-sessie sluiten | App blijft draaien; bereikbaar vanaf de Mac op poort 9000 |
| Volledige doorloop met pdf's (pdf2zcbs, images, ftp-upload, back-up) onder de service | Gelukt: 2 records 99120 t/m 99121, alle controles groen |
| Volledige doorloop met jpg's (images, back-up) | Gelukt: 2 records 99118 en 99119 |
| `Stop-Service` | Duurt 1 s; geen java of script blijft achter; poort vrij |
| Nieuwe jar in `updates\` + java laten crashen | Nieuwe versie geplaatst (bouwtijd in `/actuator/info` veranderde), `previous.jar` bewaard |
| Bestand dat geen jar is | Geweigerd, naar `mislukt\`, draaiende versie blijft |
| Verkeerde sha256 | Geweigerd, naar `mislukt\`, draaiende versie blijft |
| Geldige zip die niet start | Na 4 s teruggedraaid naar de vorige versie; de app draait weer |
| Bijwerken vanaf de pagina Versie (build 1 naar de nieuwste op GitHub) | Download met voortgang, sha256- en buildnummercontrole, app sluit af met exitcode 10, `run.ps1` plaatst de jar, nieuwe versie gezond na ~12 s |
| Nachtelijke update (cron tijdelijk elke 2 minuten) | De app werkte zichzelf twee keer bij (build 4 naar 5 en naar 6) zonder dat iemand klikte |
| Reboot van de laptop | Service startte zelf (ruim 3 minuten na het opstarten door de vertraagde start); app antwoordt na nog 35 s |

**Nog niet getest:**
- Een eigen servicegebruiker in plaats van LocalSystem, en mappen op het netwerk.
- Een reboot zonder dat iemand inlogt: de testlaptop logt zelf in (explorer draaide), dus dat is niet te scheiden van een service die wacht op een inlog. Een service start
  in Windows los van een inlog, maar dit is niet apart bewezen.
- Een nette afsluiting van de app: WinSW beeindigt het proces (`Stop-Service` doet dat binnen 1 s); lopende stappen van `images.exe` en dergelijke worden dan afgebroken. De stand
  van elke doorloop wordt elke 5 seconden bewaard en een stap die liep staat na een herstart op *mislukt*.

## Waar je op moet letten

- **Vertraagde start:** de service start na een reboot na ongeveer twee tot drie minuten. Wil je dat sneller, haal dan `<delayedAutoStart>` uit `hkh-workflow.xml`.
- **Java in de service:** `install.ps1` zet het pad naar java vast in de service (`HKH_JAVA`). Wissel je van Java-installatie, draai `install.ps1` dan opnieuw.
- **Console-uitvoer:** de uitvoer van java staat in `logs\app-uit.log` en `app-fout.log`; bij elke start worden de vorige als `.1` bewaard.
- **Testlaptop:** `deploy-app.sh` in `tools/windows-testlaptop` start de app los van de service op dezelfde poort; gebruik op de laptop nu `deploy-testlaptop.sh` en laat de service zijn werk doen.

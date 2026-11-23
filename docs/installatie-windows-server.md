# Installatie op een Windows-server, met de hand

Stap voor stap de app als Windows-service installeren, zonder Mac, SSH of `deploy-testlaptop.sh`: alles gebeurt op de server zelf in een
PowerShell-venster. Bedoeld voor de server bij HKH; probeer het eerst op de testlaptop (de verschillen staan in [hoofdstuk 8](#8-eerst-proberen-op-de-testlaptop)).

Wat de service doet en hoe het bijwerken werkt staat in [../tools/windows-server/README.md](../tools/windows-server/README.md); wat elk veld van de
instellingen betekent staat in [../manual/instellingen.md](../manual/instellingen.md).

**Wat getest is en wat niet.** De service, `install.ps1` en het bijwerken zijn op de testlaptop getest (Windows 10 Home, service als LocalSystem), maar steeds
aangestuurd vanaf de Mac. De stappen hieronder met de hand (downloaden op de server zelf, het configuratiebestand vooraf aanmaken) zijn **nog niet
uitgevoerd**; de download-adressen zijn op 23 november 2026 gecontroleerd. Niets is getest op de echte HKH-server, met een eigen service-account of met mappen op het netwerk.

## 1. Wat je vooraf moet weten of regelen

| Nodig | Toelichting |
|---|---|
| Een account dat **administrator** is op de server | Voor het installeren van de service en de firewallregel |
| Windows 10 / Server 2016 of nieuwer | Voor Windows PowerShell 5.1 en .NET Framework 4.6.1 (WinSW heeft dat nodig) |
| Internet vanaf de server | Naar `github.com`, `api.github.com`, `objects.githubusercontent.com` en `raw.githubusercontent.com` (installatie en updates), de website en ftp-server van HKH, en `hkh-admin.vdzonsoftware.nl` (workflow-logboek). Geen internet naar GitHub: zie [Zonder internet](#zonder-internet-op-de-server) |
| Waar `images.exe` en `pdf2zcbs.exe` staan | De hele map van elk programma, met `images.ini`, `pdf2zcbs.ini`, `i_view32` en de plugins. Staan ze alleen op een pc, kopieer de mappen dan naar de server |
| De **lokale** paden van de scans en de back-ups | Wat de gebruikers als `K:\...` zien is op de server een gewone map. Een service kent geen gekoppelde schijven, dus gebruik het lokale pad (of een UNC-pad) |
| Het ftp-wachtwoord van het account van images.exe | Staat niet in de repo of de documentatie |
| Een beheerderswachtwoord dat je zelf kiest | Voor de pagina's Instellingen, Sessies, Versie en Logs. Alleen letters, cijfers en leestekens uit ASCII, zonder `\` |
| Een back-up van `/cgi-bin/archief/` en `/cgi-bin/misc/permit.lst` | Met ftp, voordat er voor het eerst iets naar het echte archief gaat |

De poort is 9000; de firewallregel die het script maakt laat alleen het lokale netwerk toe.

## 2. Java

Open **PowerShell als administrator** (Start, typ `powershell`, rechtsklik, *Als administrator uitvoeren*) en kijk of Java er is:

```powershell
java -version
```

Staat er versie 21 of hoger, dan ben je klaar. Anders: download **Temurin 21 (JRE, Windows x64, .msi)** van [adoptium.net](https://adoptium.net/temurin/releases/?version=21&os=windows),
installeer het en laat in het installatiescherm *Add to PATH* aan staan. Sluit daarna het PowerShell-venster, open een nieuw (als administrator) en controleer opnieuw.

## 3. De bestanden ophalen

Zes bestanden: de vier scripts uit `tools/windows-server`, WinSW en de jar. In hetzelfde administratorvenster:

```powershell
[Net.ServicePointManager]::SecurityProtocol = 'Tls12'
$ProgressPreference = 'SilentlyContinue'
New-Item -ItemType Directory -Force C:\zcbs-install | Out-Null
Set-Location C:\zcbs-install

$scripts = 'https://raw.githubusercontent.com/robbertvdzon/hkh-workflow/main/tools/windows-server'
foreach ($f in 'install.ps1', 'run.ps1', 'uninstall.ps1', 'hkh-workflow.xml') {
    Invoke-WebRequest "$scripts/$f" -OutFile $f -UseBasicParsing
}
Invoke-WebRequest 'https://github.com/winsw/winsw/releases/download/v2.12.0/WinSW.NET461.exe' -OutFile WinSW.exe -UseBasicParsing

$release = 'https://github.com/robbertvdzon/hkh-workflow/releases/latest/download'
Invoke-WebRequest "$release/hkh-workflow.jar" -OutFile hkh-workflow.jar -UseBasicParsing
Invoke-WebRequest "$release/hkh-workflow.jar.sha256" -OutFile hkh-workflow.jar.sha256 -UseBasicParsing
```

De jar is ruim 40 MB; op een trage lijn duurt dat een paar minuten. Controleer daarna wat er binnen is:

```powershell
Get-ChildItem C:\zcbs-install | Select-Object Name, Length

# De jar is de jar van de Release (moet True geven)
((Get-Content hkh-workflow.jar.sha256 -TotalCount 1) -split '\s+')[0] -eq (Get-FileHash -Algorithm SHA256 hkh-workflow.jar).Hash

# WinSW is dezelfde als op de testlaptop (moet True geven)
(Get-FileHash -Algorithm SHA256 WinSW.exe).Hash -eq 'b5066b7bbdfba1293e5d15cda3caaea88fbeab35bd5b38c41c913d492aadfc4f'
```

`releases/latest` is de laatste build die op GitHub door de tests kwam. Welke dat is zie je op
[github.com/robbertvdzon/hkh-workflow/releases](https://github.com/robbertvdzon/hkh-workflow/releases); na de installatie staat het buildnummer onderin het menu van de app.

### Zonder internet op de server

Haal dezelfde zes bestanden (plus het `.sha256`-bestand) op een andere computer op, zet ze op een usb-stick en kopieer ze naar `C:\zcbs-install`. De rest van de stappen blijft gelijk.
Het bijwerken vanuit de app (pagina Versie en elke nacht) werkt dan niet; zet een nieuwe jar met de hand in `C:\zcbs-workflow\updates` (zie de README van `tools/windows-server`).

## 4. Het configuratiebestand, vóór de eerste start

In de jar zit een voorbeeldwachtwoord voor de beheerder. Zet daarom je eigen wachtwoord klaar **voordat** de service voor het eerst start:

```powershell
New-Item -ItemType Directory -Force C:\zcbs-workflow\config | Out-Null
Set-Content -Path C:\zcbs-workflow\config\application.properties -Encoding ASCII -Value @(
    'hkh.workflow.admin-password=KIES-HIER-EEN-WACHTWOORD'
)
```

- Gebruik `-Encoding ASCII` (of Kladblok). `-Encoding UTF8` zet in Windows PowerShell een onzichtbaar teken vooraan het bestand, waardoor de eerste regel niet wordt herkend.
- Het bestand blijft bij elke update staan. Een wijziging werkt pas na `Restart-Service hkh-workflow`.
- Alleen het account van de service en de beheerders hoeven dit bestand te kunnen lezen.

## 5. Installeren

```powershell
Set-Location C:\zcbs-install
powershell -NoProfile -ExecutionPolicy Bypass -File .\install.ps1 -WinSw .\WinSW.exe -Jar .\hkh-workflow.jar
```

Het script maakt `C:\zcbs-workflow` aan (mappen `app`, `updates`, `logs`, `service`, `config`), zet de jar neer als `app\current.jar`, maakt de firewallregel
`hkh-workflow (9000)`, installeert de service `hkh-workflow` en start hem. Het wacht tot de app antwoordt en meldt dan **De app draait op poort 9000.** (meestal binnen een halve minuut).

Zonder verdere opties draait de service als **LocalSystem**. Dat is de geteste vorm en genoeg zolang alles op lokale schijven van de server staat.
Een eigen account kan met `-Account '.\hkhservice' -Wachtwoord '...'`; dat account heeft het recht *Aanmelden als service* nodig en schrijfrechten op
`C:\zcbs-workflow`, de werkmap, de scans en de back-upmappen. Dat is nog niet getest.

Het script opnieuw draaien mag altijd: het stopt en vervangt de service en laat instellingen, logs en configuratie staan.

## 6. Controleren dat het draait

```powershell
Get-Service hkh-workflow                                                                  # Running, Automatic
(Invoke-WebRequest http://localhost:9000/actuator/health -UseBasicParsing).Content        # {"status":"UP"...}
(Invoke-WebRequest http://localhost:9000/actuator/info -UseBasicParsing).Content          # buildnummer en bouwtijd
Get-Content C:\zcbs-workflow\logs\update.log -Tail 10
```

Daarna in de browser:

1. Op de server zelf: `http://localhost:9000`. Vanaf een andere pc in het netwerk: `http://<naam of IP van de server>:9000`. Lukt het tweede niet, dan zit de firewall of het netwerkprofiel ertussen.
2. Menu **Beheer**: log in met het wachtwoord uit stap 4. Lukt het voorbeeldwachtwoord uit de jar nog, dan is het configuratiebestand niet gelezen (zie [Problemen](#10-problemen)).
3. **Versie**: er hoort *niet* te staan dat de app niet als Windows-service draait. Klik **Controleer nu**; de app moet GitHub kunnen bereiken.

## 7. Instellen

Alles op de pagina **Instellingen**; de controles worden uitgevoerd door de service, dus een rode melding betekent dat de *service* de map of het bestand niet ziet.

| Veld | Wat je invult |
|---|---|
| Werkmap | Een bestaande map op de server, bijvoorbeeld `C:\zcbs-werk` (maak hem eerst aan). Elke gebruiker krijgt hier zijn eigen submappen |
| pdf2zcbs.exe (hoofdmap) | Het volledige pad naar `pdf2zcbs.exe` in zijn eigen map |
| images.exe (hoofdmap) | Het volledige pad naar `images.exe` in zijn eigen map |
| Mappen met scans | Eén per regel als `naam=pad`, bijvoorbeeld `Jelly=D:\data\scans jelly` |
| FTP-wachtwoord | Het wachtwoord van het ftp-account van images.exe |
| Per collectie, archief | Resolutie (6) en de twee back-upmappen (bestaande mappen) |
| Gebruik de test-ZCBS op localhost | Zie hieronder |

**De test-ZCBS staat standaard aan.** Bij HKH is er geen test-ZCBS: zolang het vakje aan staat kan niemand bij ZCBS inloggen, maar kun je wel als beheerder
alle paden instellen en de service, een reboot en het bijwerken testen zonder dat er iets naar het archief gaat. Zet het vakje pas uit als je bewust in het **echte archief**
wilt werken (het menu toont dan in rood *Het echte archief*). Klik daarna op **Test verbinding** (leest alleen, schrijft niets).

Let op: het vakje geldt alleen voor wat de app zelf doet. `images.exe` heeft zijn ftp-server ingebouwd en uploadt bij HKH altijd naar de echte server, ook als het vakje aan staat.

Daarna als gewone gebruiker: inloggen met een ZCBS-naam, **Nieuwe bestanden in ZCBS zetten**, fase 1 **Controleren**. Alle zeven regels horen groen te zijn.
De eerste echte doorloop: zie [testen-bij-hkh.md](testen-bij-hkh.md) (let op de kanttekening bovenaan dat document).

**Elke nacht bijwerken** staat standaard aan: om 00:00 haalt de app de nieuwste build van GitHub. Wil je tijdens het testen zelf bepalen welke build draait, zet dat dan uit op de pagina **Versie**.

Tot slot een reboot: start de server opnieuw op en controleer dat de app na drie tot vier minuten vanzelf weer antwoordt (de service start vertraagd).

## 8. Eerst proberen op de testlaptop

Op de laptop draait de service al, geïnstalleerd vanaf de Mac. Om het zelf te doen moet de oude installatie eerst weg.

**Opruimen** (PowerShell als administrator op de laptop):

```powershell
Get-Content C:\zcbs-workflow\config\application.properties      # noteer het IP van de Mac dat hierin staat
powershell -NoProfile -ExecutionPolicy Bypass -File C:\zcbs-workflow\install\uninstall.ps1 -Firewall
Get-NetTCPConnection -LocalPort 9000 -State Listen -ErrorAction SilentlyContinue      # hoort niets te geven
Rename-Item C:\zcbs-workflow C:\zcbs-workflow-oud
```

Geeft de derde regel nog iets, dan draait er een losse app van `deploy-app.sh`; stop die eerst (`Get-Process java | Stop-Process`). Door het hernoemen begin je zonder
instellingen, net als straks bij HKH; de oude instellingen staan nog in `C:\zcbs-workflow-oud\hkh-workflow-settings.json` om over te typen. De werkmap (`C:\zcbs-werk`) en
`C:\images-test` blijven staan.

**Daarna** de hoofdstukken 2 tot en met 7, met deze verschillen:

- **Stap 4:** de test-ZCBS draait op de Mac, niet op de laptop. Zet daarom drie regels extra in het configuratiebestand, met het IP van de Mac:

  ```powershell
  Set-Content -Path C:\zcbs-workflow\config\application.properties -Encoding ASCII -Value @(
      'hkh.workflow.admin-password=KIES-HIER-EEN-WACHTWOORD',
      'hkh.workflow.test-zcbs-url=http://192.168.178.144:8081',
      'hkh.workflow.test-ftp-host=192.168.178.144',
      'hkh.workflow.test-ftp-port=21'
  )
  ```

- **Stap 7:** laat **Gebruik de test-ZCBS** aan staan. Op de Mac moet de container draaien (`tools/docker/zcbs`, `docker compose up -d`). Inloggen met `user1`.
- **De omleiding van images.exe moet nog aan staan**, anders uploadt het echte `images.exe` naar de echte HKH-server. Controleer vóór de eerste doorloop:

  ```powershell
  [Net.Dns]::GetHostAddresses('ftp.historischekringheemskerk.nl')      # moet het IP van de Mac geven
  (Get-NetFirewallRule -DisplayName 'hkh-workflow blok*' | Measure-Object).Count      # 12 per gebruiker
  ```

  Klopt het eerste niet: `hosts-instellen.ps1` uit `tools/windows-testlaptop`. Is het tweede 0 of werk je met een andere werkmap of gebruiker dan eerst: `firewall-werkmap.ps1` uit
  `tools/windows-server`.

Wat je op de laptop wel en niet bewijst: de stappen 2 tot en met 6 zijn gelijk aan die bij HKH. Niet gelijk zijn het echte archief, de paden van HKH en een Windows Server in plaats van Windows 10 Home.

## 9. Weghalen of terug naar de oude situatie

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File C:\zcbs-install\uninstall.ps1 -Firewall
```

Dat haalt de service en de firewallregel weg; `C:\zcbs-workflow` met instellingen en logs blijft staan en kun je daarna zelf verwijderen. Java en `C:\zcbs-install` blijven ook staan.
Op de testlaptop terug naar hoe het was: service weghalen, `C:\zcbs-workflow` verwijderen, `C:\zcbs-workflow-oud` terug hernoemen en vanaf de Mac `tools/windows-server/deploy-testlaptop.sh` draaien.

## 10. Problemen

| Wat je ziet | Oorzaak en oplossing |
|---|---|
| `java` wordt niet herkend, of `install.ps1` vindt `java.exe` niet | Java staat niet in het PATH van dit venster. Open een nieuw venster na het installeren, of geef het pad mee: `-Java 'C:\Program Files\Eclipse Adoptium\jre-21...\bin\java.exe'` |
| *Draai dit script als administrator.* | Het PowerShell-venster is niet als administrator geopend |
| Downloaden geeft een fout over SSL/TLS | De regel met `Tls12` is niet in dit venster uitgevoerd |
| *De app antwoordt nog niet op poort 9000* | Kijk in `C:\zcbs-workflow\logs\update.log` en `app-fout.log`. Vaak: poort 9000 is al in gebruik, of Java is ouder dan 21 |
| Het voorbeeldwachtwoord werkt nog, het eigen wachtwoord niet | `config\application.properties` is niet gelezen: verkeerde map (moet `C:\zcbs-workflow\config`), opgeslagen met een onzichtbaar teken vooraan (`-Encoding UTF8`), of de service is na de wijziging niet herstart |
| Vanaf een andere pc geen verbinding, op de server zelf wel | De firewallregel laat alleen het eigen subnet toe; zit de pc in een ander netwerk, pas dan de regel `hkh-workflow (9000)` aan |
| Rode melding *Map bestaat niet* terwijl jij de map wel ziet | De service ziet geen gekoppelde schijven en heeft als LocalSystem geen rechten op netwerkshares. Gebruik een lokaal pad, of een UNC-pad met een eigen service-account |
| Pagina Versie: *GitHub is niet bereikbaar* | De server mag niet naar `api.github.com`; zie [Zonder internet](#zonder-internet-op-de-server) |

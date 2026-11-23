# Installatie op een Windows-server, met de hand

Stap voor stap de app als Windows-service installeren vanuit één map die je meeneemt, zonder SSH of `deploy-testlaptop.sh`: alles gebeurt op de server zelf. Bedoeld voor de server bij HKH; probeer het eerst op de testlaptop (de verschillen staan in [hoofdstuk 8](#8-eerst-proberen-op-de-testlaptop)).

Wat de service doet en hoe het bijwerken werkt staat in [../tools/windows-server/README.md](../tools/windows-server/README.md); wat elk veld van de
instellingen betekent staat in [../manual/instellingen.md](../manual/instellingen.md).

**Wat getest is en wat niet.** De service en het bijwerken zijn op de testlaptop getest (Windows 10 Home, service als LocalSystem), aangestuurd vanaf de Mac.
Nieuw op 23 november 2026 en **nog niet uitgevoerd**: installeren vanuit de installatiemap, de service onder het ingelogde account met het gevraagde wachtwoord (inclusief het recht
*Aanmelden als service*) en het vragen om het beheerderswachtwoord. Wel gecontroleerd op de laptop: de scripts lezen zonder fouten in en de code voor dat recht compileert.
Niets is getest op de echte HKH-server of met mappen op het netwerk.

## 1. Wat je vooraf moet weten of regelen

| Nodig | Toelichting |
|---|---|
| Een account dat **administrator** is op de server, met een wachtwoord | Voor het installeren; de service gaat onder dit account draaien |
| Windows 10 / Server 2016 of nieuwer | Voor Windows PowerShell 5.1 en .NET Framework 4.6.1 (WinSW heeft dat nodig) |
| Internet vanaf de server | Naar `github.com`, `api.github.com` en `objects.githubusercontent.com` (updates), de website en ftp-server van HKH, en `hkh-admin.vdzonsoftware.nl` (workflow-logboek). Voor het installeren zelf is geen internet nodig |
| Waar `images.exe` en `pdf2zcbs.exe` staan | De hele map van elk programma, met `images.ini`, `pdf2zcbs.ini`, `i_view32` en de plugins. Staan ze alleen op een pc, kopieer de mappen dan naar de server |
| De **lokale** paden van de scans en de back-ups | Wat de gebruikers als `K:\...` zien is op de server een gewone map. Een service kent geen gekoppelde schijven, dus gebruik het lokale pad (of een UNC-pad) |
| Het ftp-wachtwoord van het account van images.exe | Staat niet in de repo of de documentatie |
| Een beheerderswachtwoord dat je zelf kiest | Voor de pagina's Instellingen, Sessies, Versie en Logs. Alleen letters, cijfers en gewone leestekens, zonder spaties |
| Een back-up van `/cgi-bin/archief/` en `/cgi-bin/misc/permit.lst` | Met ftp, voordat er voor het eerst iets naar het echte archief gaat |

De poort is 9000; de firewallregel die het script maakt laat alleen het lokale netwerk toe.

## 2. Java

Open **PowerShell als administrator** (Start, typ `powershell`, rechtsklik, *Als administrator uitvoeren*) en kijk of Java er is:

```powershell
java -version
```

Staat er versie 21 of hoger, dan ben je klaar. Anders: download **Temurin 21 (JRE, Windows x64, .msi)** van [adoptium.net](https://adoptium.net/temurin/releases/?version=21&os=windows),
installeer het en laat in het installatiescherm *Add to PATH* aan staan. Sluit daarna het PowerShell-venster, open een nieuw (als administrator) en controleer opnieuw.

## 3. De installatiemap

Alles wat nodig is staat in één map, `zcbs-workflow-install`, die je meeneemt naar de server (usb-stick of kopiëren) en daar neerzet als `C:\zcbs-workflow-install`.
Op de Mac maak je hem met:

```bash
tools/windows-server/maak-installatiemap.sh
```

Het resultaat staat in `target/zcbs-workflow-install`; met `--laptop` erachter zet het script hem ook meteen als `C:\zcbs-workflow-install` op de testlaptop.
De jar is die van de nieuwste Release op GitHub (de sha256 wordt gecontroleerd; welke build het is staat in `VERSIE.txt`).

| Bestand | Wat |
|---|---|
| `installeren.cmd`, `verwijderen.cmd` | Starten `install.ps1` en `uninstall.ps1`; rechtsklik, *Als administrator uitvoeren* |
| `install.ps1`, `uninstall.ps1` | De scripts zelf |
| `run.ps1`, `hkh-workflow.xml` | Het startscript van de service en de configuratie voor WinSW |
| `WinSW.exe` | Windows Service Wrapper 2.12.0, een open-sourceprogramma (github.com/winsw/winsw) dat van het script een service maakt; niet van ons |
| `hkh-workflow.jar`, `VERSIE.txt` | De app, en welke build het is |
| `LEESMIJ.txt` | De korte versie van dit document |

De server heeft voor het installeren dus geen internet nodig. Voor het bijwerken daarna (pagina Versie en elke nacht) wel; zonder internet zet je een nieuwe jar met de
hand in `C:\zcbs-workflow\updates` (zie de README van `tools/windows-server`).

## 4. Installeren

1. Log in op de server met het account waaronder de service moet draaien. Dat account moet administrator zijn en een wachtwoord hebben.
2. Rechtsklik op `C:\zcbs-workflow-install\installeren.cmd` en kies **Als administrator uitvoeren**.
3. Het script meldt onder welk account de service gaat draaien (het account waarmee je bent ingelogd) en vraagt om het **Windows-wachtwoord** van dat account.
   Het controleert het wachtwoord meteen (drie pogingen). Het wordt niet getoond en staat daarna nergens in een bestand: Windows bewaart het bij de service.
4. De eerste keer vraagt het ook om een **beheerderswachtwoord voor de app** (twee keer intypen). Dat kies je zelf; het komt in `C:\zcbs-workflow\config\application.properties`.
   Staat daar al een wachtwoord, dan vraagt het script er niet om. Zonder eigen wachtwoord geldt het voorbeeldwachtwoord uit de jar.
5. Het script maakt `C:\zcbs-workflow` aan (mappen `app`, `updates`, `logs`, `service`, `config`), zet de jar neer als `app\current.jar`, maakt de firewallregel
   `hkh-workflow (9000)`, geeft het account het recht *Aanmelden als service*, installeert de service `hkh-workflow` en start hem. Na ongeveer een halve minuut staat er **De app draait** met het adres.

Het script opnieuw draaien mag altijd: het stopt en vervangt de service en laat instellingen, logs en configuratie staan.

Andere mogelijkheden, vanuit een PowerShell-venster als administrator:

```powershell
Set-Location C:\zcbs-workflow-install
powershell -NoProfile -ExecutionPolicy Bypass -File .\install.ps1 -Account '.\hkhservice'      # een ander account; vraagt om het wachtwoord
powershell -NoProfile -ExecutionPolicy Bypass -File .\install.ps1 -LocalSystem                 # zonder account (zoals tot nu toe op de testlaptop)
```

Een service onder een gewoon account ziet nog steeds geen gekoppelde schijven (`K:`), wel netwerkmappen via een UNC-pad waar dat account rechten op heeft.
Verandert het Windows-wachtwoord van het account, dan start de service niet meer: draai `installeren.cmd` dan opnieuw.

## 5. Het configuratiebestand

`C:\zcbs-workflow\config\application.properties` bevat na het installeren het beheerderswachtwoord. Andere systeeminstellingen (hoofdstuk 7 van
[../manual/instellingen.md](../manual/instellingen.md)) zet je er met Kladblok bij.

- Sla op als gewone tekst (ANSI of UTF-8 zonder BOM). Een onzichtbaar teken vooraan het bestand maakt de eerste regel onleesbaar.
- In dit bestand is `\` een speciaal teken; het script verdubbelt hem in het wachtwoord zelf.
- Het bestand blijft bij elke update staan. Een wijziging werkt pas na `Restart-Service hkh-workflow`.

## 6. Controleren dat het draait

```powershell
Get-Service hkh-workflow                                                                  # Running, Automatic
(Invoke-WebRequest http://localhost:9000/actuator/health -UseBasicParsing).Content        # {"status":"UP"...}
(Invoke-WebRequest http://localhost:9000/actuator/info -UseBasicParsing).Content          # buildnummer en bouwtijd
Get-Content C:\zcbs-workflow\logs\update.log -Tail 10
```

Daarna in de browser:

1. Op de server zelf: `http://localhost:9000`. Vanaf een andere pc in het netwerk: `http://<naam of IP van de server>:9000`. Lukt het tweede niet, dan zit de firewall of het netwerkprofiel ertussen.
2. Menu **Beheer**: log in met het beheerderswachtwoord dat je bij het installeren koos. Lukt het voorbeeldwachtwoord uit de jar nog, dan is het configuratiebestand niet gelezen (zie [Problemen](#10-problemen)).
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

Op de laptop draait de service al (als LocalSystem, geïnstalleerd vanaf de Mac) en staat de installatiemap in `C:\zcbs-workflow-install`.

1. Rechtsklik op `C:\zcbs-workflow-install\verwijderen.cmd`, **Als administrator uitvoeren**. De service en de firewallregel voor poort 9000 gaan weg; `C:\zcbs-workflow` blijft staan.
2. Kies hoe schoon je wilt beginnen:
   - **Met de bestaande instellingen:** laat `C:\zcbs-workflow` staan. Het configuratiebestand met het adres van de test-ZCBS op de Mac en alle instellingen blijven dan gelden.
   - **Leeg, zoals bij HKH:** hernoem de map (`Rename-Item C:\zcbs-workflow C:\zcbs-workflow-oud`). Zet dan na het installeren deze drie regels in
     `C:\zcbs-workflow\config\application.properties` (met het IP van de Mac) en herstart de service, want de test-ZCBS draait op de Mac en niet op de laptop:

     ```
     hkh.workflow.test-zcbs-url=http://192.168.178.144:8081
     hkh.workflow.test-ftp-host=192.168.178.144
     hkh.workflow.test-ftp-port=21
     ```

3. Rechtsklik op `installeren.cmd`, **Als administrator uitvoeren**, en volg hoofdstuk 4 tot en met 7.

Verder op de laptop:

- Laat **Gebruik de test-ZCBS** aan staan. Op de Mac moet de container draaien (`tools/docker/zcbs`, `docker compose up -d`). Inloggen met `user1`.
- **De omleiding van images.exe moet nog aan staan**, anders uploadt het echte `images.exe` naar de echte HKH-server. Controleer vóór de eerste doorloop:

  ```powershell
  [Net.Dns]::GetHostAddresses('ftp.historischekringheemskerk.nl')      # moet het IP van de Mac geven
  (Get-NetFirewallRule -DisplayName 'hkh-workflow blok*' | Measure-Object).Count      # 12 per gebruiker
  ```

  Klopt het eerste niet: `hosts-instellen.ps1` uit `tools/windows-testlaptop`. Is het tweede 0 of werk je met een andere werkmap of gebruiker dan eerst: `firewall-werkmap.ps1` uit
  `tools/windows-server`.

Wat je op de laptop wel en niet bewijst: het installeren zelf is gelijk aan dat bij HKH. Niet gelijk zijn het echte archief, de paden van HKH en een Windows Server in plaats van Windows 10 Home.

## 9. Weghalen of terug naar de oude situatie

Rechtsklik op `C:\zcbs-workflow-install\verwijderen.cmd`, **Als administrator uitvoeren**. Dat haalt de service en de firewallregel weg; `C:\zcbs-workflow` met instellingen en logs
blijft staan en kun je daarna zelf verwijderen. Java en de installatiemap blijven ook staan.
Op de testlaptop terug naar hoe het was: vanaf de Mac `tools/windows-server/deploy-testlaptop.sh` draaien (installeert weer als LocalSystem).

## 10. Problemen

| Wat je ziet | Oorzaak en oplossing |
|---|---|
| `java` wordt niet herkend, of het script vindt `java.exe` niet | Java staat niet in het PATH. Probeer het opnieuw na het installeren van Java, of geef het pad mee: `install.ps1 -Java 'C:\Program Files\Eclipse Adoptium\jre-21...\bin\java.exe'` |
| *Draai dit script als administrator* | Gebruik rechtsklik, *Als administrator uitvoeren* |
| *Het wachtwoord van ... klopt niet* | Het is het Windows-wachtwoord van het genoemde account, niet een pincode. Bij een Microsoft-account: het wachtwoord van dat account |
| De service start niet en in Logboeken staat een aanmeldfout (1069) | Het wachtwoord van het account is veranderd, of het recht *Aanmelden als service* is door beleid weer weggehaald. Draai `installeren.cmd` opnieuw |
| *De app antwoordt nog niet op poort 9000* | Kijk in `C:\zcbs-workflow\logs\update.log` en `app-fout.log`. Vaak: poort 9000 is al in gebruik, of Java is ouder dan 21 |
| Het voorbeeldwachtwoord werkt nog, het eigen wachtwoord niet | `config\application.properties` is niet gelezen: opgeslagen met een onzichtbaar teken vooraan (UTF-8 met BOM), of de service is na een wijziging niet herstart |
| Vanaf een andere pc geen verbinding, op de server zelf wel | De firewallregel laat alleen het eigen subnet toe; zit de pc in een ander netwerk, pas dan de regel `hkh-workflow (9000)` aan |
| Rode melding *Map bestaat niet* terwijl jij de map wel ziet | De service ziet geen gekoppelde schijven en heeft als LocalSystem geen rechten op netwerkshares. Gebruik een lokaal pad, of een UNC-pad met een eigen service-account |
| Pagina Versie: *GitHub is niet bereikbaar* | De server mag niet naar `api.github.com`; bijwerken gaat dan met de hand (hoofdstuk 3) |

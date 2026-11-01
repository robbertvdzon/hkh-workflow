# Nieuwe bestanden in ZCBS zetten: de volledige handleiding

Stand: 1 november 2026. Voor beheerders en technisch geïnteresseerden. Medewerkers die alleen willen weten welke knoppen ze moeten klikken, lezen de
[quickstart](quickstart-invoeren.md).

**Bronnen en betrouwbaarheid.** Deze handleiding is geschreven vanuit de code en de schermen van de app (de pagina `/nieuw`, de klassen `FlowSession`,
`TestRunService`, `WorkFolders`, `ProgramFiles`, `SourceFilesService`, `ExeAnswers`, `ExeRunner`, `FtpService` en de tests). Waar de code iets
doet wat niet is geprobeerd op de echte HKH-server, staat dat erbij: *uit de code gelezen, niet bevestigd op de echte server*. De code wint van
oudere teksten in `docs/` en de README; de verschillen staan onderaan in [Bekende verschillen met oudere documentatie](#bekende-verschillen-met-oudere-documentatie).

## Inhoud

1. [Wat de flow doet](#1-wat-de-flow-doet)
2. [Wat er op schijf staat](#2-wat-er-op-schijf-staat)
3. [De zeven fasen](#3-de-zeven-fasen)
4. [Afbeeldingen (jpg en png) tegenover pdf's](#4-afbeeldingen-jpg-en-png-tegenover-pdfs)
5. [De vaste antwoorden van de programma's](#5-de-vaste-antwoorden-van-de-programmas)
6. [De controle na pdf2zcbs en na images.exe](#6-de-controle-na-pdf2zcbs-en-na-imagesexe)
7. [De nummercontrole vlak voor images.exe](#7-de-nummercontrole-vlak-voor-imagesexe)
8. [Resten van een eerdere keer](#8-resten-van-een-eerdere-keer)
9. [Opnieuw beginnen](#9-opnieuw-beginnen)
10. [Herstart van de app](#10-herstart-van-de-app)
11. [Meerdere gebruikers tegelijk](#11-meerdere-gebruikers-tegelijk)
12. [Het verwerkt-log](#12-het-verwerkt-log)
13. [Het workflow-logboek naar hkh-admin](#13-het-workflow-logboek-naar-hkh-admin)
14. [Problemen en oplossingen](#14-problemen-en-oplossingen)
15. [Woordenlijst](#15-woordenlijst)
16. [Bekende verschillen met oudere documentatie](#bekende-verschillen-met-oudere-documentatie)

---

## 1. Wat de flow doet

Scans van de Historische Kring staan als pdf of afbeelding op `K:` (in de mappen van Jelly en Ria). De flow **Nieuwe bestanden in ZCBS zetten** brengt
ze op het archief: ze krijgen een recordnummer, ZCBS' eigen programma's `pdf2zcbs.exe` en `images.exe` maken er tekst, pdf en afbeeldingen
van en maken de lege records aan, de app zet tekst en pdf met ftp op de server en maakt tenslotte een back-up en ruimt op. De app is een
*wrapper* om die programma's heen: hij zet de bestanden klaar, start de programma's, controleert of er is gekomen wat er moest komen en laat
de medewerker tussendoor kijken of het goed is.

Wat de flow **niet** doet: het invullen van de gegevens (titel enzovoort). Dat gebeurt op een andere dag via de knop **ZCBS records aanpassen**.
De originelen op `K:` worden nooit gewijzigd of verwijderd.

### Hoe je er komt

1. **Inloggen** op de startpagina met je eigen ZCBS-naam en -wachtwoord (knop **Inloggen**).
2. **Collectie kiezen** op de pagina "Met welke collectie wil je werken?". Alleen `archief` is aan te klikken; andere collecties staan er grijs
   met "Geen rechten" (je hebt geen recht in ZCBS) of "Nog niet beschikbaar" (de app kan er nog niet mee werken).
3. Kies **Nieuwe bestanden in ZCBS zetten**. De andere kaart is **ZCBS records aanpassen**; onderaan staat de link **Bekijk wat al verwerkt is**.

Alle pagina's van de flow vragen alleen om de ZCBS-login. Instellingen en Sessies vragen daarnaast het beheerderswachtwoord.

### De zeven fasen

Bovenaan de pagina staat een balk met de zeven fasen. Een afgeronde fase heeft een vinkje; sommige hebben een korte notitie ("3 gekozen",
"11935 t/m 11946", "overgeslagen").

| Fase | Titel in de balk | Kort |
|---|---|---|
| 1 | Controleren | Staat alles klaar? (inlog, vrije nummers, mappen, programma's, back-ups, ftp, geen resten) |
| 2 | Bestanden kiezen | Bladeren door `K:` en aanvinken |
| 3 | Nummers | Beginnummer kiezen; de bestanden krijgen hun nummer (op een kopie) |
| 4 | pdf2zcbs | `pdf2zcbs.exe` draait, daarna jouw controle |
| 5 | images.exe | `images.exe` draait (uploadt de afbeeldingen, maakt de records), daarna jouw controle |
| 6 | Upload ocr en pdf | De app uploadt tekst en pdf met ftp |
| 7 | Back-up | Back-up van 100pct en pdf, opruimen, verwerkt-log |

Na fase 7 volgt het scherm **Klaar**.

---

## 2. Wat er op schijf staat

### De werkmap en de mappen per gebruiker

De instelling **Werkmap** (in het instellingenbestand `testFolder`, voorheen "invoermap") is de gemeenschappelijke map. Elke gebruiker krijgt daarin een
eigen map per collectie:

```
<werkmap>\<gebruiker>\<collectie>\
    invoer\      de gekozen bestanden (kopieën van K:), plus herkomst.tsv van de app zelf
    werk\        kopieën met het recordnummer als naam (11935.pdf, 11936.jpg, ...)
    pdf2zcbs\    een kopie van pdf2zcbs.exe met zijn ini; hier draait het programma; uitvoer in output\
    images\      een kopie van images.exe met zijn ini, i_view32 en plugins; hier draait het programma
    resten\      hierheen verplaatst de app resten van een eerdere keer (tenzij een andere map is ingesteld)
    sessie.json  de stand van de doorloop (zie hoofdstuk 10)
```

Voorbeeld: `<werkmap>\Carla\archief\invoer\`. De gebruikersmap heet zoals de ZCBS-naam; tekens die op schijf niet mogen of lastig zijn worden `_`.
Zonder ingelogde gebruiker (de technische pagina van de beheerder) heet de map `gebruiker`. De werkmap zelf moet al bestaan; de app maakt de
submappen aan. De werkmap mag op het netwerk staan, zodat gebruikers kunnen meekijken. Draait de app als Windows-service, gebruik dan een
UNC-pad in plaats van een gekoppelde schijf (zie de README).

### Kopieën van de programma's

Elke gebruiker draait zijn **eigen kopie** van `pdf2zcbs.exe` en `images.exe`, zodat de uitvoer van twee gebruikers niet door elkaar loopt. De
"hoofdmap" van een programma is de map waarin het bij de instellingen staat (bijvoorbeeld `C:\zcbs\pdf2zcbs\pdf2zcbs.exe`). De app kopieert de
inhoud van die hoofdmap (exe, ini, i_view32, plugins, submappen) naar `pdf2zcbs\` en `images\` van de gebruiker:

- bij het afronden van fase 1 en opnieuw vlak voor elke start van een programma;
- alleen wat ontbreekt, een andere grootte heeft of nieuwer is in de hoofdmap (een wijziging aan een ini komt er dus bij de volgende start in);
- **nooit** wordt er iets in de map van de gebruiker verwijderd;
- niet gekopieerd worden de gegevensmappen (`output`, `images`, `small`, `large`, `100pct`, `log`, `werk`, `invoer`) en gegevensbestanden
  (pdf, jpg, jpeg, png, tif, tiff en `log-*.txt`).

### Instellingen die voor deze flow tellen

Deze staan bij **Instellingen** (alleen beheerder); zie ook de handleiding [Instellingen](instellingen.md).

| Instelling | Waarvoor |
|---|---|
| Werkmap | Zie hierboven |
| pdf2zcbs.exe (hoofdmap), images.exe (hoofdmap) | Volledige paden naar de programma's |
| Start met PERLIO=:unix | Zet de uitvoerbuffer van Perl uit, zodat de vragen meteen zichtbaar zijn. Standaard aan; nodig voor automatisch uitvoeren |
| Mappen met scans | Eén regel per map: `naam=pad`, bijvoorbeeld `Jelly=K:\scans jelly`. De naam toont wie het scande |
| Map voor resten van een eerdere run | Leeg = de map `resten` van de gebruiker |
| FTP-wachtwoord (en host, gebruiker) | Voor `images.exe` en voor de upload van de app. Eén wachtwoord voor de hele server |
| Per collectie: Resolutie van images.exe | Het antwoord op "Kies 1,2,3 ..." (standaard 6) |
| Per collectie: Back-upmap voor 100pct, Back-upmap voor de pdf's | Waar de back-up heen gaat |
| Gebruik de test-ZCBS op localhost | Aan: test-ZCBS in Docker. Uit: het **echte** archief en de echte ftp-server. Onderin het menu staat "Het echte archief" als je daarmee werkt |

---

## 3. De zeven fasen

Bij elke fase staat: wat het scherm toont, wat de knoppen doen, welke controles de app uitvoert (met de melding als ze falen) en wat er op schijf
gebeurt. Alle meldingen verschijnen in een rode balk bovenaan de pagina (fouten) of een groene/blauwe balk (info). Een volledige lijst met
oorzaak en oplossing staat in [hoofdstuk 14](#14-problemen-en-oplossingen).

### Fase 1. Controleren

**Scherm.** Kop "1. Controleer of alles klaarstaat" met een lijst van zeven punten, elk met een groen vinkje (in orde) of een rood kruisje. Bij een rood
punt staat eronder wat er aan de hand is en wat je kunt doen. Als er resten van een eerdere keer zijn, staat eronder het kader "Eerst opruimen:
er staat nog werk van een eerdere keer" (zie [hoofdstuk 8](#8-resten-van-een-eerdere-keer)).

**Knop.** Onderaan staat één knop. Zijn alle punten in orde, dan heet hij **Alles in orde: verder met bestanden kiezen**. Is er iets rood, dan is hij
grijs en heet hij **Los eerst de rode punten op**. Bij het indrukken controleert de app alles nog eens en meldt het eerste punt dat niet klopt
(als "label: uitleg").

**Controles** (de app voert ze uit bij elk laden van de pagina en bij het indrukken van de knop):

| Punt (groen) | Punt (rood) | Wat de app controleert | Uitleg die erbij staat |
|---|---|---|---|
| Ingelogd bij ZCBS als *naam* | Niet ingelogd bij ZCBS | Er is een ZCBS-sessie voor deze gebruiker | Log eerst in via Start. |
| ZCBS geeft de vrije recordnummers | De vrije recordnummers uit ZCBS zijn niet op te halen | De lijst met vrije nummers is op te halen (de sessie is niet verlopen en de gebruiker mag records aanmaken) | Is je sessie verlopen, of heb je geen recht om records aan te maken? Log opnieuw in via Start. |
| De mappen met scans zijn bereikbaar | Er zijn geen mappen met scans ingesteld / De mappen met scans zijn niet bereikbaar | Er is minstens één map met scans ingesteld en elke bestaat | "Dit stelt de beheerder in bij Instellingen." resp. "Niet bereikbaar: *pad*. Is de schijf (K:) gekoppeld?" |
| pdf2zcbs.exe en images.exe zijn gevonden | pdf2zcbs.exe of images.exe is niet gevonden | Beide paden zijn ingesteld en wijzen naar een bestand | Niet gevonden: *pad of "(niet ingesteld)"*. Dit stelt de beheerder in bij Instellingen. |
| De back-upmappen zijn bereikbaar | De back-upmappen zijn niet bereikbaar | De back-upmap voor 100pct en die voor de pdf's bestaan | Niet bereikbaar: *pad*. Is de schijf (K:) gekoppeld? Anders stelt de beheerder dit in bij Instellingen. |
| De ftp-server is bereikbaar en de mappen voor tekst en pdf bestaan | De ftp-gegevens voor de upload zijn niet ingesteld / De ftp-server is niet bereikbaar / Op de ftp-server ontbreken mappen | Host, gebruiker en wachtwoord zijn ingesteld; er wordt echt verbinding gemaakt (time-out 5 seconden), ingelogd en gekeken of de mappen `<basismap>/<collectie>/ocr` en `.../pdf` bestaan. Er wordt niets geschreven | "Host, gebruiker of wachtwoord ontbreekt. Dit stelt de beheerder in bij Instellingen." / de foutmelding van de verbinding / "Niet gevonden: *map*." |
| Er staat geen werk van een eerdere keer | Er staat nog werk van een eerdere keer | Zie hoofdstuk 8 | Zie hieronder: verplaats of verwijder het. |

**Op schijf.** Bij een geslaagde start (knop ingedrukt, alles groen) maakt de app de mappen `invoer`, `pdf2zcbs` en `images` van de gebruiker aan en
kopieert de programma's erheen. Vanaf dat moment bestaat er een `sessie.json` en geldt de gebruiker als "bezig" (zichtbaar bij Sessies).

### Fase 2. Bestanden kiezen

**Scherm.** Kop "2. Bestanden kiezen". Bovenaan "Scans van:" met een knop per map met scans (bijvoorbeeld **Jelly**, **Ria**); de eerste uit de instellingen
staat meteen open. Daaronder een kruimelpad, de submappen (klik om te openen) en, als er bestanden in de map staan, een tabel met de kolommen
Bestand, Grootte, Afmeting / pagina's, Gescand (wijzigingsdatum van het bestand), Status en een knop **Open**. De lijst staat in natuurlijke volgorde (scan 2 voor scan 10).

- Het aantal pagina's van een pdf of de afmeting van een afbeelding wordt apart nagelaadd (tot dan staat er "…"); de lijst verschijnt dus snel.
- **Open** toont het bestand in een venster, zodat je kunt zien of het de goede scan is.
- Een bestand dat al verwerkt is, staat grijs met het label "Al verwerkt: *nummer*" en is niet aan te vinken. Dat komt uit het verwerkt-log ([hoofdstuk 12](#12-het-verwerkt-log)).
- Het vakje bovenin de tabel vinkt alles aan. Met **Shift-klik** vink je alles tussen twee bestanden aan ("X t/m Y").
- Onder de tabel staat hoeveel je hebt aangevinkt.
- De knop **Verder** kopieert wat in de **huidige map** is aangevinkt en gaat meteen door naar fase 3. Je kiest dus uit één map per doorloop; wil je uit een andere
  map kiezen, dan moet dat in een volgende doorloop (of via **Opnieuw beginnen**, dat de keuze wist).

**Knoppen.**

- **Verder** kopieert de aangevinkte bestanden naar `invoer` en gaat door naar fase 3.
- **Opnieuw beginnen** (rechtsboven) wist je keuze en gaat terug naar fase 1; zie [hoofdstuk 9](#9-opnieuw-beginnen).
- Staan er al bestanden in `invoer` die "bij deze doorloop horen" (na **Mijn gekozen bestanden horen erbij**), dan staat er een blauw kader "*N* bestanden stonden al klaar en doen mee" met de knop **Haal ze weg**. Die haalt alleen de kopieën weg die de app zelf heeft neergezet.

**Controles en meldingen.**

| Melding | Oorzaak |
|---|---|
| Vink minstens één bestand aan. (eventueel gevolgd door "Overgeslagen: ...") | Er is niets aangevinkt en er stond ook niets in `invoer` |
| *naam* (staat al in de invoermap) | Dit bestand is al gekozen; het wordt nooit overschreven |
| *naam* (al verwerkt als *nummer*) | Het staat in het verwerkt-log; wordt niet nog eens gekopieerd |
| Dit is geen pdf of afbeelding in de map: *naam* / Dit bestand staat niet in de map: *naam* | Alleen pdf, jpg, jpeg en png in de gekozen map zijn toegestaan |
| Kopie van *naam* is niet even groot als het origineel. | De kopie is afgekeurd (netwerkprobleem?) |
| Kan de png niet lezen: *naam* | De png is niet om te zetten naar jpg |
| De map bestaat niet of is niet bereikbaar: *pad*. Is de schijf gekoppeld? | `K:` is niet gekoppeld of het pad klopt niet |
| Dit pad valt buiten de map *naam*. / Onbekende map met scans: *naam* | Een pad of mapnaam die niet bij de instellingen hoort wordt geweigerd |
| Er staan nog bestanden van een eerdere run in je invoermap of in de mappen van pdf2zcbs.exe of images.exe. Verplaats of verwijder ze eerst (of kies bij je invoermap voor "horen erbij"). | Na het kopiëren zijn er toch resten gevonden (bijvoorbeeld in de map van een programma) |
| Stel eerst de werkmap in bij Instellingen. / De werkmap bestaat niet: *pad* | De werkmap is leeg ingesteld of bestaat niet |

**Op schijf.** Voor elk aangevinkt bestand komt in `invoer` een kopie (eerst als `<naam>.kopie`, dan hernoemd; zo blijft er geen halve kopie staan). Van een
png maakt de app een **jpg** (op een witte achtergrond, kwaliteit 0,95; de naam wordt `<naam>.jpg`). Een pdf of jpg wordt letterlijk gekopieerd en de grootte
gecontroleerd. In `invoer\herkomst.tsv` (tab-gescheiden) onthoudt de app per bestand uit welke bronmap het kwam (Jelly of Ria) en de map zelf; dat gebruikt het
verwerkt-log later. De originelen blijven staan.

### Fase 3. Nummers

**Scherm.** Kop "3. Nummers". Tekst: "Je hebt *N* bestanden gekozen. Ze krijgen aaneengesloten recordnummers: kies een beginnummer uit de nummers die in ZCBS nog vrij zijn."
Daaronder "Laatste record in ZCBS: *11934*" en een tabel van de **vrije nummers** zoals ZCBS ze toont bij een nieuw record: per reeks de omvang
(of "onbeperkt" bij een open einde) en of de reeks groot genoeg is ("Ja, past" / "Te klein"). Naast een passende reeks staat een knop **Begin hier**
die het beginnummer invult. Het veld **Eerste recordnummer** is vooringevuld met een voorstel: het nummer na het laatste record als daar ruimte is, anders het
begin van de eerste reeks die groot genoeg is. Onder het veld staat live: "De bestanden krijgen de nummers *11935* t/m *11946*."

**Knoppen.** **Geef de bestanden deze nummers en ga verder** en (rechtsboven) **Opnieuw beginnen**. Is de lijst met vrije nummers niet op te halen, dan staat er
een rode melding met een link **Log opnieuw in** en kan niet worden doorgegaan.

**Controles** (bij het indrukken van de knop, in deze volgorde):

1. Er staan bestanden in `invoer` ("Er staan geen bestanden in de invoermap.").
2. Er zijn geen resten (zie hoofdstuk 8).
3. Het beginnummer bestaat alleen uit cijfers ("Geef een beginnummer van alleen cijfers op.").
4. ZCBS geeft de lijst met vrije nummers (anders: "ZCBS gaf de lijst met vrije nummers niet. Is je sessie verlopen, heb je geen recht om records aan te maken, of is ZCBS niet bereikbaar? Log opnieuw in.").
5. Het beginnummer ligt in een vrije reeks: anders "Recordnummer *n* is niet vrij in ZCBS (of valt buiten wat jij mag gebruiken). Kies een beginnummer uit de lijst met vrije nummers."
6. **Alle** bestanden passen aaneengesloten in die ene reeks: anders "De *N* bestanden passen niet vanaf *n*: daar is maar *k* aaneengesloten nummers vrij (t/m *m*). Kies een reeks waar alle *N* bestanden in passen, of kies minder bestanden."

Voorloopnullen blijven staan; het aantal cijfers volgt uit de lijst van ZCBS (`00001`).

**Op schijf.** De app maakt een kopie van elk bestand in `werk\` met het recordnummer als naam: `11935.pdf` voor een pdf, `11936.jpg` voor een afbeelding. Het
origineel in `invoer` en op `K:` blijft staan. De nummers worden uitgedeeld in de volgorde van de bestandsnamen in `invoer`, **alfabetisch zonder
onderscheid tussen hoofd- en kleine letters**. Let op: dat is niet de natuurlijke volgorde van de lijst in fase 2, dus "scan 10" komt vóór "scan 2" (uit de code gelezen; niet getest met zulke namen).

De stappen zijn nu in de app aangemaakt: per bestand een stap Hernoemen (die meteen wordt uitgevoerd), en daarna één stap voor pdf2zcbs, images.exe, Upload (ftp) en Back-up en opruimen. Mislukt het hernoemen
(bijvoorbeeld "Origineel niet gevonden: *pad*" of "*11935.pdf* bestaat al in *werkmap*."), dan blijft de gebruiker in fase 3 met die melding; **Opnieuw beginnen** is dan de weg
(en de resten opruimen in fase 1).

### Fase 4. pdf2zcbs

**Scherm.** Kop "4. pdf2zcbs", een vraagregel "Start pdf2zcbs.exe" en een uitklapper **Wat gebeurt er?** (zet de bestanden uit `werk` in de map van
het programma, start het één keer, verwacht per bestand tekst, pdf, small en large in `output`). Staat er iets in de weg, dan staat in rood de reden en zijn de knoppen grijs.

**Knoppen.** **Handmatig uitvoeren** en **Automatisch uitvoeren** (zie [hoofdstuk 5](#5-de-vaste-antwoorden-van-de-programmas)). Bij een mislukte stap staat de melding in rood met de knop **Opnieuw**.

**Controles vooraf** (komen als rode regels in het scherm; de stap start dan niet):

| Melding | Betekenis |
|---|---|
| Stel eerst het pad naar pdf2zcbs.exe in. / pdf2zcbs.exe niet gevonden: *pad* | Het programma is niet ingesteld of niet aanwezig |
| Werkbestand ontbreekt: *pad* (is de hernoem-stap gedaan?) | De kopie met recordnummer in `werk` ontbreekt |
| In *map* staan nog andere pdf's die pdf2zcbs.exe ook zou verwerken: ... Verwijder die eerst. | pdf2zcbs verwerkt alles in zijn map; er staan pdf's die niet bij deze doorloop horen |
| In *output* staan resultaten van een vorige run: ... pdf2zcbs.exe wist die map bij de start. Zet ze eerst veilig of verwijder de map. | Er staan bestanden in `output` met een nummer van een andere run |

**Op schijf.** De app kopieert `werk\<nr>.pdf` naar `pdf2zcbs\<nr>.pdf` (bij opnieuw proberen overschrijft ze wat er van de vorige poging staat), kopieert zo nodig de
programmabestanden bij, en start het programma **één keer voor alle pdf's samen** in `pdf2zcbs\`. Het programma schrijft in `pdf2zcbs\output\`:
`ocr\<nr>.txt` (tekst), `pdf\<nr>.pdf`, `small\<nr>.jpg` en `large\<nr>.jpg`.

**Wanneer is de stap klaar?** Niet op de exitcode alleen. Na afloop controleert de app of alle verwachte bestanden bestaan en niet leeg zijn (een
tekstbestand mag wel leeg zijn: een pagina zonder herkende tekst). Pas dan staat de stap op **Klaar** met "Klaar: alle *N* verwachte bestanden zijn aangemaakt."
(*N* = 4 per pdf). Anders staat hij op **Mislukt**:

- "Gestopt met exitcode *c*. Controleer de uitvoer; zet de stap handmatig op klaar als het toch goed ging." Op de pagina Nieuwe bestanden is **Toch klaar** er niet; dat is een knop op de technische pagina Test van de beheerder.
- "Het programma is klaar (exitcode 0), maar er ontbreken bestanden: ..."

Staan er afbeeldingen tussen de gekozen bestanden, dan eindigt de melding met "*N* afbeelding(en) is/zijn overgeslagen (geen pdf: ze gaan rechtstreeks naar images.exe)". Zijn er **alleen** afbeeldingen, dan slaat de stap zichzelf over en gaat de doorloop naar fase 5.

Daarna volgt de **controle na de stap** ([hoofdstuk 6](#6-de-controle-na-pdf2zcbs-en-na-imagesexe)).

### Fase 5. images.exe

**Scherm.** Kop "5. images.exe", vraagregel "Start images.exe", uitklapper **Wat gebeurt er?** met onder meer de vaste antwoorden, en dezelfde knoppen **Handmatig uitvoeren**
en **Automatisch uitvoeren**. Dit is de stap die iets op de server en in ZCBS doet: `images.exe` uploadt de afbeeldingen small, large en 100pct met ftp en
maakt de lege records aan.

**Controles vooraf:**

| Melding | Betekenis |
|---|---|
| Controleer eerst de bestanden van pdf2zcbs.exe en bevestig dat ze goed zijn. | De controle na pdf2zcbs is nog niet gedaan |
| Stel eerst het pad naar images.exe in. / images.exe niet gevonden: *pad* | Als bij fase 4 |
| Werkbestand ontbreekt: *pad* (is de hernoem-stap gedaan?) | Voor een afbeelding: `werk\<nr>.jpg` ontbreekt of is leeg |
| Large-jpg van pdf2zcbs ontbreekt: *pad* (is de pdf2zcbs-stap gedaan?) | Voor een pdf: `output\large\<nr>.jpg` ontbreekt |
| In *images* staan nog andere bestanden die images.exe ook zou verwerken: ... Verwijder die eerst. | Er staan vreemde bestanden in de bronmap van `images.exe` |
| In *small / large / 100pct* staan resultaten van een vorige run: ... Die zouden opnieuw worden geüpload. Ruim eerst op. | Oude afgeleide afbeeldingen zouden opnieuw meegaan |
| Voordat images.exe de records aanmaakt: ... Er is niets aangemaakt. Kies "Opnieuw beginnen" en begin met een ander beginnummer. | De nummercontrole, [hoofdstuk 7](#7-de-nummercontrole-vlak-voor-imagesexe) |
| Voor automatisch uitvoeren moet bij Instellingen het FTP-wachtwoord staan. Gebruik anders Handmatig uitvoeren: typ het één keer in, dan neemt de app het over. | Alleen bij Automatisch uitvoeren zonder ftp-wachtwoord |

**Op schijf.** Voor een pdf kopieert de app `pdf2zcbs\output\large\<nr>.jpg` naar `images\images\<nr>.jpg`; voor een afbeelding gaat `werk\<nr>.jpg` rechtstreeks
naar `images\images\`. `images.exe` draait één keer voor alles, maakt `small\`, `large\` en `100pct\` in zijn map, schrijft een `log-*.txt` en een
`updates-add.txt` (dat laatste uit docs/zcbs-achtergrond.md, niet bevestigd in deze code), uploadt en maakt de records aan.

**Wanneer is de stap klaar?** Als alle drie de afgeleide bestanden per nummer bestaan (3 per bestand). Dan staat er "Klaar: alle *N* verwachte bestanden zijn aangemaakt."
plus een opmerking van de app over wat `images.exe` gedaan heeft:

- " Let op: de afbeeldingen zijn niet naar de server geüpload." (op de vraag over uploaden is niet met j geantwoord)
- " Let op: er zijn geen records aangemaakt in ZCBS." (op de vraag over records niet met j)
- " De *N* records staan in ZCBS." — de app heeft dat zelf gecontroleerd door de records in ZCBS op te vragen
- " Let op: deze records staan nog niet in ZCBS: ..."
- " De records in ZCBS konden niet worden gecontroleerd."

Daarna volgt de controle na de stap (hoofdstuk 6). Opnieuw uitvoeren van deze stap kan problemen geven (er is al geüpload en er zijn al records): daarom vraagt de knop **Niet goed: images.exe opnieuw uitvoeren** eerst om bevestiging.

### Fase 6. Upload ocr en pdf

**Scherm.** Kop "6. Upload ocr en pdf", een tabel met de kolommen Bestand en Nummer (alle gekozen bestanden), de vraag "Upload tekst en pdf van *N* bestanden naar de server?" en
**Wat gebeurt er?**. Eén knop: **Uitvoeren**. De upload van de app is een eigen stap en gaat niet via een programma: de pagina laadt opnieuw als hij klaar is.

**Wat hij doet.** Verbindt met de ftp-server en zet per **pdf** twee bestanden neer: `output\ocr\<nr>.txt` naar `<basismap>/<collectie>/ocr/<nr>.txt` en `output\pdf\<nr>.pdf`
naar `<basismap>/<collectie>/pdf/<nr>.pdf`. Afbeeldingen hebben geen tekst of pdf: zijn er alleen afbeeldingen gekozen, dan slaat de stap zichzelf over (notitie "overgeslagen" in de balk).
De upload doet bewust voorzichtig, want hij schrijft op de echte server:

1. **Eerst alles controleren, dan pas uploaden.** De mappen bestaan (er worden *nooit* mappen aangemaakt), de lokale bestanden bestaan en zijn niet leeg, en **geen enkel doelbestand
   bestaat al** (er wordt nooit overschreven). Faalt iets, dan wordt er niets geüpload.
2. Upload onder een tijdelijke naam (`.<naam>.uploading`), hernoemen, de rechten op 644 zetten (als de server dat kent), en de grootte vergelijken met het origineel.
3. Bij de eerste fout stopt hij. De bestanden die wel staan, blijven staan; bij **Opnieuw** worden alleen de nog niet geüploade bestanden geprobeerd.

**Controles vooraf:** "Controleer eerst de bestanden van images.exe en bevestig dat ze goed zijn." (controle na images.exe niet gedaan); "Ftp-host is niet ingesteld (Instellingen).", "Ftp-gebruiker is niet ingesteld ...", "Ftp-wachtwoord
is niet ingesteld ...", "Collectie is niet ingesteld ..."; "Tekstbestand ontbreekt: *pad*"; "Pdf ontbreekt: *pad*".

**Klaar** bij: "*N* bestanden staan op de server en zijn gecontroleerd: *ocr-map* en *pdf-map*."

### Fase 7. Back-up

**Scherm.** Kop "7. Back-up", dezelfde tabel, de vraag "Maak een back-up van de 100pct en de pdf's en ruim de mappen op?", **Wat gebeurt er?** en de knop **Uitvoeren**.

**Wat hij doet, in deze volgorde:**

1. **Back-up.** Kopieert van elk bestand de 100pct (`images\100pct\<nr>.jpg`) naar de back-upmap voor 100pct, en van elke pdf `werk\<nr>.pdf` (de pdf met recordnummer) naar de back-upmap voor de pdf's. Staat
   hetzelfde bestand er al (opnieuw proberen), dan gebeurt er niets; staat er een *ander* bestand met dezelfde naam, dan wordt de kopie `<nr>-1.jpg`, `<nr>-2.jpg`, ... genoemd. Na elke kopie wordt de grootte vergeleken.
2. **Opruimen**, pas ná de back-up, en alleen binnen de mappen van de gebruiker: `pdf2zcbs\output` (alles), `images\small`, `large`, `100pct` (alles), `images\images\<nr>.jpg`, `pdf2zcbs\<nr>.pdf`, `werk\<nr>.pdf|jpg`, de kopieën in `invoer`, en `log-*.txt` in de map van images; de map `werk` verdwijnt als hij leeg is. `output\log` valt onder `output` en verdwijnt dus ook; `herkomst.tsv` wordt bijgewerkt. De originelen op `K:` blijven staan.
3. **Verwerkt-log.** De bestanden worden toegevoegd aan het verwerkt-log (hoofdstuk 12).

**Controles vooraf:** "Stel eerst de paden van pdf2zcbs.exe en images.exe in."; "Stel eerst de back-upmap voor de 100pct in (Instellingen)." / "Back-upmap voor de 100pct bestaat niet: *pad*"; hetzelfde voor de pdf's; "100pct ontbreekt, dus geen back-up mogelijk: *pad*"; "De pdf met recordnummer ontbreekt, dus geen back-up mogelijk: *pad*".
**Fouten onderweg:** "Back-up van *naam* is niet even groot als het origineel; er is niets verwijderd." en "Weigert te verwijderen buiten de map van het programma: ..." (veiligheidscontrole).

**Klaar** met "Back-up gemaakt in *mappen* (*n* nieuw), daarna *m* bestanden en mappen opgeruimd. De *N* bestanden staan in het verwerkt-log."

### Het scherm Klaar

Kop "Klaar": "*N* bestanden staan nu op het archief, met de recordnummers *11935* t/m *11946*. De 100pct en de pdf's hebben een back-up, de werkmappen zijn opgeruimd ..."
met een link naar het overzicht van wat al verwerkt is. Een tabel (Waar, Soort, Bestanden, Map) toont wat er is gemaakt en waar het staat:

- **Server (ftp ...):** small, large en 100pct (jpg, voor alle bestanden), tekst (ocr) en pdf (alleen voor pdf's), met de mappen op de server.
- **Back-up:** 100pct (jpg) en pdf.

De knop **Nieuwe bestanden verwerken** begint een nieuwe doorloop. Het scherm Klaar is **één keer** te zien: open je `/nieuw` daarna opnieuw, dan begint de pagina weer bij fase 1.
De originelen op `K:` zijn niet aangeraakt.

---

## 4. Afbeeldingen (jpg en png) tegenover pdf's

Je kunt in één doorloop pdf's en afbeeldingen mengen.

| | pdf | jpg / jpeg | png |
|---|---|---|---|
| Kiezen in fase 2 | ja | ja | ja |
| In `invoer` | zoals het is | zoals het is | **omgezet naar jpg** (naam `<naam>.jpg`, witte achtergrond) |
| Naam in `werk` | `<nr>.pdf` | `<nr>.jpg` | `<nr>.jpg` |
| pdf2zcbs | ja: maakt tekst, pdf, small, large | **overgeslagen** | **overgeslagen** |
| images.exe | krijgt de large-jpg van pdf2zcbs | krijgt de afbeelding zelf | idem jpg |
| Upload ocr en pdf | tekst en pdf | **overgeslagen** (geen tekst of pdf) | **overgeslagen** |
| Back-up | 100pct en pdf | alleen 100pct | alleen 100pct |

Gevolgen: bij alleen afbeeldingen slaan fase 4 en 6 zichzelf over (de balk toont "overgeslagen"). Bij een mix draait pdf2zcbs alleen voor de pdf's (met de melding over de overgeslagen afbeeldingen),
en krijgen afbeeldingen alleen in de controle na images.exe een rij zonder tekst/pdf. Een gekozen bestand met een andere extensie komt niet voor: alleen pdf, jpg, jpeg en png worden toegelaten.

---

## 5. De vaste antwoorden van de programma's

De programma's stellen vragen op een DOS-console. De app kent die vragen (images.exe versie 3.27, gemeten op 30 oktober 2026 volgens de code) en beantwoordt ze zelf, of stelt het antwoord voor.
De antwoorden zijn **vast**; de app leert niets bij, behalve het ftp-wachtwoord (zie onder).
Vragen worden herkend zonder te letten op hoofdletters of het aantal spaties (het echte programma schrijft "Kies  1,2,3" met twee spaties).

### De twee knoppen

| Knop | Wat er gebeurt |
|---|---|
| **Automatisch uitvoeren** | De app geeft alle vaste antwoorden zelf. In het scherm staat alleen "Bezig ... Even geduld, de pagina gaat vanzelf verder." De console is verborgen tenzij er een vraag komt die de app niet kent. |
| **Handmatig uitvoeren** | De console is zichtbaar met een invoerveld ("Typ een antwoord en druk op Enter") en de knoppen **Verstuur**, **Enter**, **J**, **N** en **Stop programma**. De app herkent de vraag waar het programma op wacht en vult het vaste antwoord *voor* ("Voorgesteld antwoord op deze vraag: ..."). De gebruiker bevestigt of typt iets anders. |

Bij beide knoppen beantwoordt de app **de veiligheidsvragen altijd zelf met nee** (zie hieronder), ook bij handmatig. Dat is bewust: images.exe zou anders de afbeeldingen wissen die de app nog nodig heeft voor de controle en de back-up. In de console staan die antwoorden als "(auto) n".

Bij **Handmatig uitvoeren** van images.exe geeft de app het ingestelde ftp-wachtwoord ook zelf, één keer per run (vraagt het programma er daarna nog eens om, dan klopte het niet en moet de gebruiker het zelf typen). Is er nog geen wachtwoord ingesteld, dan typt de gebruiker het één keer
en **neemt de app het over als instelling** (in de wijzigingshistorie met de reden "Het FTP-wachtwoord is overgenomen van het handmatig uitvoeren van images.exe"). Een wachtwoord staat nooit leesbaar in de console of in de logs ("********").

### pdf2zcbs.exe

Bij HKH stelt dit programma geen vragen zolang `pdf2zcbs.ini` ernaast staat; de console blijft dan stil en dat is normaal. Zonder die ini kent de app de vragen en antwoordt:

| Vraag (herkende tekst) | Antwoord |
|---|---|
| Deze PDF splitsen | n |
| verwerken naar txt | j |
| (presentatie) slides | n |
| Type return to exit | (Enter) |

### images.exe

| Vraag (herkende tekst) | Vast antwoord |
|---|---|
| Type ENTER/RETURN indien OK | `a` (bestandstype) |
| afgeleide images small/large/100pct opnieuw aanmaken | `j` |
| Kies 1,2,3 | de **resolutie van de collectie** (standaard 6) |
| UPLOADEN aangemaakte images | `j` |
| Nummer, naam of plaats instelling | `4` |
| FTP wachtwoord | het ingestelde ftp-wachtwoord (geheim) |
| Kies de ZCBS-toepassing | de collectie (bijvoorbeeld `archief`) |
| AANMAKEN en UPLOADEN van de bijbehorende ... records | `j` |
| VERWIJDEREN (tijdelijk) PC-bestand | `j` |
| Geef een ENTER/RETURN ... (submap) | Enter: de afbeeldingen komen in de root van de collectie |
| om dit DOS-window te verlaten | Enter |

**Veiligheidsvragen, altijd `n`** (ook bij handmatig, niet te overrulen):

| Vraag | Antwoord |
|---|---|
| OVERSCHRIJVEN | n |
| VERWIJDEREN (tijdelijk) aangemaakte mappen | n |
| VERWIJDEREN inhoud van de map [images] | n |

Kent de app een vraag niet (bijvoorbeeld een andere versie van het programma), dan **stopt de automatische stand** en verschijnt de console met de melding "Automatisch: deze vraag wordt door geen enkele regel herkend. Beantwoord hem hieronder handmatig ...". De tekst
"of voeg een regel toe bij Instellingen" is verouderd: de antwoorden staan vast in de code en zijn niet meer in de instellingen aan te passen. Na 100 automatische antwoorden in één run zet de app het automatisch antwoorden uit (voor het geval het programma in een kringetje loopt).

### Console, logbestanden en stoppen

- Elke start van een programma krijgt een eigen logbestand `run-<datum-tijd>-<id>-<label>.log` in `logs\<collectie>\<gebruiker>\` (de logmap volgt uit `HKH_WORKFLOW_LOG_DIR`, standaard `logs`) met tijdstempels per stuk uitvoer en per verstuurde invoer. Te lezen via de technische pagina **Test** onder Runs, of via de link **Volledig log** in de console.
- De console-codepagina is standaard IBM850 (Nederlandse Windows; `HKH_WORKFLOW_CONSOLE_CHARSET`).
- **Stop programma** (alleen in de handmatige console) stopt het programma en zijn onderliggende processen. De stap eindigt dan waarschijnlijk als **Mislukt** (exitcode niet 0; uit de code gelezen, niet bevestigd op de echte server).
- Er kan per gebruiker maar één programma tegelijk draaien ("Er draait nog een programma.").

---

## 6. De controle na pdf2zcbs en na images.exe

Na een geslaagde fase 4 en fase 5 gaat de doorloop niet automatisch door. De gebruiker moet eerst zien wat er gemaakt is en dat bevestigen; zolang dat niet gebeurd is, weigert de volgende stap te starten ("Controleer eerst de bestanden van ... en bevestig dat ze goed zijn.").

**Scherm.** Kop "Dit heeft pdf2zcbs.exe gemaakt" resp. "Dit heeft images.exe gemaakt". Bij images staat bovenin de groene melding van de stap, met onder meer "De *N* records staan in ZCBS." Daaronder een tabel:

| Kolom | Inhoud |
|---|---|
| Nummer | Het recordnummer (eenmaal per groep) |
| Bestand | De oorspronkelijke bestandsnaam |
| Soort | pdf2zcbs: tekst, pdf, small, large. images.exe: small, large, 100pct |
| Grootte | In B, kB of MB |
| Afmeting | Breedte × hoogte in px voor een afbeelding; "–" voor tekst en pdf |
| Op de server | **Alleen bij images.exe:** staat dit bestand op de ftp-server? **ja** / **nee**; "–" als de verbinding niet lukte |
| (knop) **Open** | Opent de tekst, pdf of afbeelding in een venster (alleen nummers van deze doorloop) |

Opmerkingen in de kolom Afmeting: "leeg: geen tekst gevonden" (een leeg tekstbestand is **geen fout**, maar kijk of de pagina echt leeg is) en "geen geldige afbeelding".

**Knoppen.**

| Controle na | Verder | Terug |
|---|---|---|
| pdf2zcbs | **Ja, goed: verder met images.exe** | **Niet goed: pdf2zcbs.exe opnieuw uitvoeren** |
| images.exe | **Ja, goed: verder met de upload** | **Niet goed: images.exe opnieuw uitvoeren** (met de bevestiging "images.exe uploadt en maakt records aan. Opnieuw uitvoeren kan problemen geven. Weet je het zeker?") |

"Niet goed" zet de stap terug op "Wacht" en laat de gebruiker dezelfde stap opnieuw starten (de controle op vrije nummers wordt bij zo'n herhaling niet nog eens uitgevoerd, zie hoofdstuk 7). Een foute bevestiging ("Er is nog niets gestart." of "*stap* is nog niet klaar.") is alleen mogelijk als de pagina verouderd is.

Slaat de app pdf2zcbs over (alleen afbeeldingen), dan is die controle ook weg. Wordt de upload overgeslagen, dan gebeurt dat pas ná de controle van images.exe, zodat die controle niet wordt omzeild.

---

## 7. De nummercontrole vlak voor images.exe

`images.exe` maakt de records aan. Er is **geen slot per collectie** meer: twee gebruikers (of een gebruiker in ZCBS zelf) kunnen tussen fase 3 (nummers kiezen) en fase 5 dezelfde nummers gebruiken. Daarom controleert de app bij de **eerste poging** van de stap images.exe nogmaals bij ZCBS of de nummers nog vrij zijn en aaneengesloten passen. Is dat niet zo, dan start het programma niet en staat de stap op **Mislukt** met:

> Voordat images.exe de records aanmaakt: *(reden)* Waarschijnlijk heeft iemand anders intussen dezelfde nummers gebruikt. Er is niets aangemaakt. Kies "Opnieuw beginnen" en begin met een ander beginnummer.

*(reden)* is dezelfde tekst als bij fase 3, bijvoorbeeld "Recordnummer 11936 is niet vrij in ZCBS ...". Er is dan nog niets gekopieerd naar `images.exe` (de test controleert dat).
Gebruiker: **Opnieuw beginnen**; de bestanden staan nog op schijf en worden bij fase 1 als resten aangeboden (verplaatsen), waarna een nieuwe doorloop met een ander beginnummer kan.

Randgevallen (uit de code gelezen):

- Geeft ZCBS op dat moment geen lijst met vrije nummers, dan **kan niet worden gecontroleerd** en gaat de stap gewoon door.
- Bij een herhaling (**Opnieuw** of **Niet goed: images.exe opnieuw uitvoeren**) wordt de controle niet gedaan: de records van de eerste poging bestaan dan al.
- Na een herstart van de app wordt een stap die al eens liep als "geprobeerd" teruggezet, dus ook dan geen nieuwe controle.

---

## 8. Resten van een eerdere keer

pdf2zcbs wist zijn map `output` bij de start, images.exe zou oude small-, large- en 100pct-afbeeldingen opnieuw uploaden, en eerder gekozen bestanden zouden ongemerkt meedoen. Daarom kijkt fase 1 naar **resten** en staat de doorloop stil zolang die er zijn (ook fase 2 en 3 weigeren verder te gaan).

**Wat telt als rest.**

| Onderdeel | Wat |
|---|---|
| `invoer` | Elk bestand (behalve `herkomst.tsv`) dat er staat zonder dat de gebruiker het in deze doorloop koos |
| `werk` | Elk bestand, zolang er geen doorloop is |
| `pdf2zcbs\` | Pdf's naast het programma; alles in `output\` behalve de submap `log` en `updates.txt` |
| `images\` | Bestanden in `images\images\`; alles in `small\`, `large\` en `100pct\` |

Logs en de programmabestanden zelf tellen niet mee.

**Het kader "Eerst opruimen".** Het toont per onderdeel wat er staat (de eerste zes namen, daarna "en *n* andere") en drie knoppen:

| Knop | Wat hij doet |
|---|---|
| **Verplaats ze naar een map met resten** | Verplaatst de bestanden naar een submap `yyyyMMdd-HHmmss` onder de map voor resten (`resten\` van de gebruiker, of de ingestelde map met `<gebruiker>\<collectie>` erbij), met dezelfde mapindeling. Grijs als er geen map voor resten is te bepalen |
| **Mijn gekozen bestanden horen erbij** (alleen als er resten in `invoer` zijn) | Zegt dat de bestanden in `invoer` bij deze doorloop horen. Handig als de app is herstart en de gebruiker de bestanden zelf had klaargezet. Meldt "Je gekozen bestanden horen erbij." |
| **Verwijder ze** (met bevestiging "Deze bestanden definitief verwijderen? Dit kan niet ongedaan worden gemaakt.") | Verwijdert alleen die bestanden |

De app raakt alleen de genoemde bestanden aan en weigert alles buiten de map van het betreffende programma. Lege mappen eronder verdwijnen. Meldingen: "*n* bestanden verplaatst naar *map*." / "*n* bestanden verwijderd." / "Er stond niets meer om op te ruimen." Opruimen kan niet tijdens een doorloop ("Er is al een doorloop bezig. Begin eerst opnieuw.") of terwijl een programma draait ("Er draait nog een programma.").

---

## 9. Opnieuw beginnen

De knop **Opnieuw beginnen** staat rechtsboven en verschijnt in twee situaties; telkens met een bevestiging.

| Wanneer | Bevestiging | Wat er gebeurt |
|---|---|---|
| Fase 2 of 3, nog **voor** de nummers zijn uitgedeeld | "Opnieuw beginnen? Je keuze van bestanden wordt gewist; de bestanden op K: blijven staan." | De door de app gemaakte kopieën in `invoer` worden weggehaald (wat de gebruiker er zelf neerzette blijft staan); de doorloop begint weer bij fase 1 |
| Vanaf het uitdelen van de nummers: fase 4 t/m 7 (en fase 3 als het hernoemen mislukte), zolang het niet klaar is | "Opnieuw beginnen? Wat je hier hebt gedaan wordt gewist; bestanden op schijf blijven staan." | Een nog lopend programma wordt gestopt, de stand wordt gewist en de doorloop begint bij fase 1. **Alle bestanden op schijf blijven staan** (`invoer`, `werk`, de mappen van de programma's) en komen bij fase 1 terug als resten |

Opnieuw beginnen **maakt niets ongedaan** wat al op de server of in ZCBS staat: heeft `images.exe` records aangemaakt of heeft de upload bestanden neergezet, dan blijven die staan; de app zet niets terug.
Een nieuwe doorloop met dezelfde nummers stuit dan al in fase 3 op "niet vrij in ZCBS", en bij de upload op "Bestaat al op de server". Wat daar mee moet gebeuren is een beslissing voor de beheerder.

Na de afgeronde flow heet de knop **Nieuwe bestanden verwerken**; hij doet hetzelfde. De beheerder kan een doorloop van een ander stoppen via **Sessies** (hoofdstuk 11); de gebruiker moet dan opnieuw beginnen.

---

## 10. Herstart van de app

De stand van elke doorloop wordt om de 5 seconden bewaard in `sessie.json` in de map van de gebruiker (`<werkmap>\<gebruiker>\<collectie>\sessie.json`; geschreven via een tijdelijk bestand, dus nooit half). Het
bestand bevat de gekozen bestanden en hun nummers, alle stappen met hun status en melding, welke controles gedaan zijn, welke bestanden al zijn geüpload en in welke fase de doorloop zit.

Bij het starten van de app leest hij alle `sessie.json`-bestanden onder de werkmap terug en zet de doorlopen terug (logregel "Doorloop van *x* in *y* teruggezet (fase *n*)"). Wat daarbij geldt:

- **Een stap die liep, is mislukt.** Het programma is er niet meer. De stap staat op **Mislukt** met: "De app is herstart terwijl deze stap bezig was. Controleer wat er al staat en probeer het opnieuw." Kijk in de mappen van de gebruiker (en bij `images.exe`: op de server en in ZCBS) wat er al is gebeurd voordat je **Opnieuw** kiest.
- Alle andere stappen en de controles blijven zoals ze waren; de gebruiker gaat verder waar hij was.
- Een doorloop zonder stand (niets gestart, of afgerond/gewist) heeft geen `sessie.json`; de app verwijdert het bestand zodra hij weet dat er niets meer te bewaren valt.
- Is een `sessie.json` onleesbaar, dan staat er een waarschuwing in het app-log ("De bewaarde doorloop in ... is onleesbaar") en wordt de doorloop overgeslagen; de bestanden op schijf zijn dan resten.
- Is de werkmap niet ingesteld of bereikbaar bij het opstarten, dan wordt er niets teruggezet.
- De ZCBS-login: de README beschrijft dat de naam en het wachtwoord versleuteld in een cookie van 30 dagen staan en dat de gebruiker daardoor ingelogd blijft na een herstart (uit de README gelezen; hier niet nagelopen). Is dat niet zo, dan moet de gebruiker opnieuw inloggen voordat de pagina Nieuwe bestanden iets toont.
- Een lopend programma dat de app bij het herstarten niet zelf stopt, kan nog doordraaien (uit de code gelezen, niet bevestigd op de echte server).

---

## 11. Meerdere gebruikers tegelijk

- **Er is geen slot per collectie meer.** Elke gebruiker heeft per collectie een eigen doorloop, met een eigen programmaproces, eigen logbestanden (`logs\<collectie>\<gebruiker>\`), eigen mappen en een eigen kopie van de programma's. Twee gebruikers (ook in dezelfde collectie) kunnen dus gelijktijdig werken. De sleutel is "gebruikersnaam (hoofdletterongevoelig) + collectie": "Jelly" en "jelly" zijn dezelfde doorloop.
- Wat ze **wel delen**: de recordnummers in ZCBS (daarom de nummercontrole van hoofdstuk 7), de ftp-server en de back-upmappen (een bestaand bestand met een andere inhoud krijgt in de back-up een volgnummer in de naam; de upload overschrijft nooit), het instellingenbestand en het verwerkt-log.
- Wie alleen kijkt (de pagina opent zonder iets te starten) telt niet als bezig.
- **Sessies** (beheerder: Instellingen, in het menu links **Sessies**): twee kaarten.
  - "Bezig met nieuwe bestanden": per doorloop Collectie, Gebruiker, Fase ("*n* van 7"), Sinds en Laatst actief, met de knop **Stoppen** ("Deze doorloop stoppen? Wat de gebruiker hier doet wordt gestopt en hij moet opnieuw beginnen."). Het stopt een lopend programma en wist de stand; de bestanden op schijf blijven staan. Een gebruiker die de technische pagina's zonder login gebruikt staat als "(beheerder)".
  - "Ingelogd bij ZCBS": wie er nu een ZCBS-sessie heeft.
- De instelling `hkh.workflow.session-timeout-minutes` (README, 120 minuten) staat nog in `application.properties`, maar **wordt door de code niet meer gebruikt**: er verloopt niets vanzelf. Een vastgelopen doorloop haal je weg via Sessies, Stoppen.

---

## 12. Het verwerkt-log

Na een geslaagde fase 7 schrijft de app voor elk bestand een regel in het verwerkt-log: standaard `hkh-workflow-verwerkt.tsv` naast het instellingenbestand (of het pad uit `HKH_WORKFLOW_PROCESSED_LOG`). Het is een tekstbestand met tabs, in UTF-8:

`doorloop-id` · `tijd` · `ZCBS-gebruiker` · `collectie` · `recordnummer` · `gescand door (Jelly/Ria)` · `map waar het bestand vandaan kwam` · `bestandsnaam`

Het doorloop-id is de datum en tijd van de back-up (`yyyyMMdd-HHmmss`); alle bestanden van één doorloop delen het.

- De pagina **Wat is al verwerkt** (link **Bekijk wat al verwerkt is**) toont het per doorloop, nieuwste boven: aantal bestanden, eerste en laatste nummer, datum, "door *naam*", de collectie, en per bestand Nummer, Bestand, Gescand door, Map en een knop **In ZCBS** (opent het record, alleen als het adres van ZCBS bekend is).
- De lijst in fase 2 markeert met dit log welke scans al verwerkt zijn ("Al verwerkt: *nummer*"). De sleutel is **map + bestandsnaam** (bij een png de naam met `.jpg`).
- Wil je een bestand opnieuw verwerken (bijvoorbeeld een betere scan onder dezelfde naam), haal dan de regel uit het log (uit docs/specs-nieuwe-bestanden.md; de code leest het bestand bij elke aanroep opnieuw).
- Regels die niet te lezen zijn (minder dan acht velden, ongeldige datum) worden stilzwijgend overgeslagen.

---

## 13. Het workflow-logboek naar hkh-admin

Als `hkh.workflow.admin-log-url` (of `HKH_WORKFLOW_ADMIN_LOG_URL`) is ingesteld, meldt de app elke doorloop aan het workflow-logboek van hkh-admin (leeg = niets melden). Kort:

- Een doorloop is een **sessie** met een eigen uuid, vanaf het moment dat de gebruiker fase 1 heeft afgerond. Ze eindigt bij Klaar of bij Opnieuw beginnen ("Opnieuw begonnen bij stap 1; deze doorloop is niet afgemaakt").
- Elke ± 3 seconden kijkt de app of er iets veranderd is en stuurt dan de **volledige stand**: fase, instellingen, gekozen bestanden, stappen met status en melding, de lijst van gemaakte bestanden, en de uitvoer en het log van `pdf2zcbs.exe` en `images.exe`. Het versturen gebeurt op een eigen draad: een fout of onbereikbare server hindert de app nooit, en een gemiste melding herstelt zich bij de volgende. De uitvoer van een programma gaat hooguit eens per zes seconden mee.
- Wachtwoorden worden eruit gehaald. **Uitzondering:** de instelling "Het ftp-wachtwoord meesturen naar het logboek" (`logFtpPassword`, standaard aan) stuurt het ftp-wachtwoord leesbaar mee, bedoeld om in de beginperiode te controleren wat is ingesteld; zet die uit zodra het kan. Andere wachtwoorden gaan nooit mee.
- Ook elke inlogpoging wordt gemeld, en volgens docs/specs.md gaan de app-logregels (INFO en hoger) ook mee (niet in deze handleiding nagelopen).

---

## 14. Problemen en oplossingen

Alle meldingen komen uit de code. Hoofdletters en leestekens kunnen in het scherm iets afwijken omdat de app er namen en paden in zet. Waar "beheerder" staat, bedoelen we de persoon met het beheerderswachtwoord.

### Inloggen en voorbereiding

| Melding | Oorzaak | Wat te doen |
|---|---|---|
| Niet ingelogd bij ZCBS: Log eerst in via Start. | Geen (geldige) ZCBS-sessie; de app is herstart of de gebruiker is elders uitgelogd | Opnieuw inloggen |
| De vrije recordnummers uit ZCBS zijn niet op te halen | De sessie is verlopen, de gebruiker mag in deze collectie geen records aanmaken, of ZCBS is niet bereikbaar | Opnieuw inloggen; rechten in ZCBS controleren (onderin het menu staat "Rechten in ZCBS"); is ZCBS onbereikbaar, wachten |
| Er zijn geen mappen met scans ingesteld | Instelling "Mappen met scans" is leeg | Beheerder: regels `naam=pad` toevoegen bij Instellingen |
| De mappen met scans zijn niet bereikbaar / De map bestaat niet of is niet bereikbaar: *pad*. Is de schijf gekoppeld? | `K:` is niet gekoppeld op de computer waar de app draait, of het pad klopt niet (draait de app als service: gebruik een UNC-pad) | Schijf koppelen / pad corrigeren |
| pdf2zcbs.exe of images.exe is niet gevonden / Niet gevonden: *pad* | Pad leeg of fout | Beheerder: paden corrigeren bij Instellingen |
| De back-upmappen zijn niet bereikbaar / Back-upmap voor de 100pct (pdf's) bestaat niet | De mappen per collectie zijn niet ingesteld, bestaan niet of `K:` is niet gekoppeld | Beheerder: mappen controleren bij Instellingen (sectie "Per collectie") |
| De ftp-gegevens voor de upload zijn niet ingesteld: Host, gebruiker of wachtwoord ontbreekt. | Ftp-instelling onvolledig | Beheerder: gegevens invullen; het wachtwoord mag ook via Handmatig uitvoeren van images.exe worden overgenomen |
| De ftp-server is niet bereikbaar: *melding* (bijv. "Verbinden mislukt: ...", "de server weigert de verbinding", "inloggen is mislukt") | Server uit de lucht, firewall, verkeerd wachtwoord. Time-out 5 seconden | Beheerder: **Test verbinding** bij Instellingen; controleren van host, poort 21, gebruiker, wachtwoord |
| Op de ftp-server ontbreken mappen: Niet gevonden: *map* | `<basismap>/<collectie>/ocr` of `/pdf` bestaat niet; de app maakt nooit mappen aan | Beheerder: de map op de server laten aanmaken of de collectie/basismap corrigeren |
| Er staat nog werk van een eerdere keer | Zie hoofdstuk 8 | **Verplaats ze naar een map met resten** (veilig), of **Verwijder ze** |
| Los eerst de rode punten op | Minstens één punt van fase 1 is rood | Alle rode punten oplossen |
| Stel eerst de werkmap in bij Instellingen. / De werkmap bestaat niet: *pad* | Werkmap leeg of bestaat niet (de app maakt alleen de submappen aan) | Beheerder: werkmap instellen en aanmaken |

### Bestanden kiezen en nummers

| Melding | Oorzaak | Wat te doen |
|---|---|---|
| Vink minstens één bestand aan. / Er staan geen bestanden in je invoermap. Kies eerst bestanden en kopieer ze. | Niets gekozen | Bestanden aanvinken, dan **Verder** |
| (Overgeslagen:) *naam* (staat al in de invoermap) / (al verwerkt als *nr*) | Dubbele keuze, of de scan staat al in het verwerkt-log | Normaal. Wil je een verwerkte scan opnieuw doen: regel uit het verwerkt-log halen |
| Kopie van *naam* is niet even groot als het origineel. / Kan de png niet lezen | Netwerkstoring of een kapot bestand | Opnieuw proberen; is de scan zelf kapot, opnieuw scannen |
| Er staan nog bestanden van een eerdere run in je invoermap of in de mappen van pdf2zcbs.exe of images.exe. ... | Resten gevonden | Terug naar fase 1 (**Opnieuw beginnen**) en de resten opruimen |
| Geef een beginnummer van alleen cijfers op. | Letters of spaties in het veld | Alleen cijfers |
| ZCBS gaf de lijst met vrije nummers niet. ... | Sessie verlopen, geen recht of ZCBS onbereikbaar | Opnieuw inloggen via de link **Log opnieuw in** |
| Recordnummer *n* is niet vrij in ZCBS (of valt buiten wat jij mag gebruiken). ... | Dat nummer is bezet of ligt buiten het cluster van de gebruiker | Beginnummer uit de tabel kiezen (knop **Begin hier**) |
| De *N* bestanden passen niet vanaf *n*: daar is maar *k* aaneengesloten nummers vrij (t/m *m*). ... | Er zit een bezet nummer binnen de reeks | Een grotere reeks kiezen of minder bestanden kiezen (**Opnieuw beginnen**) |
| Origineel niet gevonden: *pad* / *nr*.pdf bestaat al in *werk*. | Een bestand in `invoer` is intussen weg, of `werk` bevat al een bestand met dat nummer | **Opnieuw beginnen**, dan resten opruimen |

### pdf2zcbs en images.exe

| Melding | Oorzaak | Wat te doen |
|---|---|---|
| Er is geen testrun gestart. / Onbekende stap *n*. / Deze stap is nog niet aan de beurt. / Deze stap is al bezig. | Verouderde pagina (twee tabbladen, dubbele klik) | Pagina verversen |
| Er draait nog een programma. | Er loopt al een programma voor deze gebruiker | Wachten tot het klaar is of in de console **Stop programma** |
| Het lopende programma wil niet stoppen; probeer het nog eens. | Het proces reageert niet binnen 5 seconden | Nogmaals proberen; zo nodig het proces op de server beëindigen |
| Starten mislukt: *reden* (log: *pad*) | Het programma is niet te starten (bijv. verkeerd bestandstype of geen rechten) | Beheerder: pad en rechten controleren, logbestand lezen |
| Werkbestand ontbreekt: ... / Large-jpg van pdf2zcbs ontbreekt: ... | Eerdere stap niet gedaan of bestanden tussentijds weggehaald | **Opnieuw beginnen** |
| In *map* staan nog andere pdf's / bestanden die ... ook zou verwerken. ... Verwijder die eerst. | Vreemde bestanden in de programmamap | De bestanden verplaatsen/verwijderen (fase 1 laat ze zien als de doorloop opnieuw begint) |
| In *output* staan resultaten van een vorige run ... | Oude uitvoer in `output` | Opnieuw beginnen en opruimen via fase 1, of de map zelf veiligstellen |
| In *small / large / 100pct* staan resultaten van een vorige run ... Ruim eerst op. | Oude afgeleide afbeeldingen | Idem |
| Controleer eerst de bestanden van pdf2zcbs.exe (images.exe) en bevestig dat ze goed zijn. | De controle van hoofdstuk 6 is nog niet gedaan | Op **Ja, goed: ...** klikken (nadat je gekeken hebt) |
| Gestopt met exitcode *c*. Controleer de uitvoer; zet de stap handmatig op klaar als het toch goed ging. | Het programma gaf een foutcode, of is gestopt via **Stop programma** | Beheerder: log lezen (Test, Runs). Ging het toch goed, dan **Toch klaar** op de pagina Test. Anders **Opnieuw** |
| Het programma is klaar (exitcode 0), maar er ontbreken bestanden: ... | Het programma deed zijn werk niet volledig (bijv. een pagina werd niet verwerkt) | Log lezen; **Opnieuw**, of de scan nakijken |
| Het programma is klaar, maar het afronden is mislukt: ... | Een uitzondering bij het controleren | Log van de app lezen; **Opnieuw** |
| De app is herstart terwijl deze stap bezig was. ... | Zie hoofdstuk 10 | Eerst kijken wat er al staat, dan **Opnieuw** |
| Voordat images.exe de records aanmaakt: ... Er is niets aangemaakt. Kies "Opnieuw beginnen" ... | Zie hoofdstuk 7: iemand anders gebruikte de nummers | **Opnieuw beginnen**, ander beginnummer |
| Voor automatisch uitvoeren moet bij Instellingen het FTP-wachtwoord staan. ... | Geen ftp-wachtwoord ingesteld | Beheerder vult het in, of gebruik **Handmatig uitvoeren** en typ het één keer |
| Automatisch: deze vraag wordt door geen enkele regel herkend. ... | `images.exe` stelt een vraag die de app niet kent (andere versie?) | Beantwoord de vraag in de console die nu verschijnt; meld de vraag aan de beheerder zodat de code kan worden aangepast |
| Let op: de afbeeldingen zijn niet naar de server geüpload. / Let op: er zijn geen records aangemaakt in ZCBS. | Op de vraag over uploaden/records is niet met `j` geantwoord (alleen bij handmatig mogelijk) | De stap met **Niet goed: images.exe opnieuw uitvoeren** herhalen, na overleg met de beheerder |
| Let op: deze records staan nog niet in ZCBS: ... / De records in ZCBS konden niet worden gecontroleerd. | Records niet (zichtbaar) aangemaakt, of ZCBS niet bereikbaar | In ZCBS kijken (opent via **In ZCBS** op de pagina Verwerkt, of in ZCBS zelf); bij twijfel de beheerder |
| Kolom "Op de server" toont **nee** | Een afbeelding staat niet op de ftp-server | Niet verder gaan; de beheerder laat de ftp-map en het log nakijken |
| leeg: geen tekst gevonden / geen geldige afbeelding | De pagina had geen herkenbare tekst, resp. de jpg is kapot | Kijk met **Open**: een lege pagina is geen fout; een kapotte afbeelding wel (**Niet goed: ... opnieuw uitvoeren**) |

### Upload en back-up

| Melding | Oorzaak | Wat te doen |
|---|---|---|
| Ftp-host / Ftp-gebruiker / Ftp-wachtwoord / Collectie is niet ingesteld (Instellingen). | Instelling ontbreekt | Beheerder vult de instelling in |
| Tekstbestand ontbreekt: *pad* / Pdf ontbreekt: *pad* | pdf2zcbs heeft het niet gemaakt of het is weg | **Opnieuw beginnen** |
| Niets geüpload, de controle vooraf vond: ... | Zie de regels erna: "Map bestaat niet op de server: *map* (er worden nooit mappen aangemaakt)", "Lokaal bestand ontbreekt of is leeg: *pad*", "**Bestaat al op de server:** *pad*", "Tijdelijk bestand van een eerdere poging staat er nog: *pad*" | Beheerder: kijken wat er op de server staat. De app overschrijft nooit; een tijdelijk bestand `.<naam>.uploading` van een mislukte poging moet op de server worden verwijderd |
| Twee bestanden zouden op dezelfde plek terechtkomen: ... | Twee bestanden met hetzelfde nummer | Opnieuw beginnen |
| Upload gestopt na *x* van *y* bestanden: *reden*. De bestanden die wel staan, blijven staan en worden bij opnieuw proberen overgeslagen. | Bijv. "uploaden is geweigerd", "het bestand is er intussen al", "hernoemen is geweigerd", "grootte op de server (...) wijkt af van het origineel (...)", verbindingsfout | **Opnieuw**: alleen wat nog ontbreekt wordt geprobeerd. Bij een groottefout: beheerder controleert het bestand op de server |
| Stel eerst de paden van pdf2zcbs.exe en images.exe in. / Stel eerst de back-upmap voor de 100pct (pdf's) in (Instellingen). / Back-upmap ... bestaat niet: *pad* | Instelling ontbreekt of schijf niet gekoppeld | Beheerder |
| 100pct ontbreekt, dus geen back-up mogelijk: *pad* / De pdf met recordnummer ontbreekt, dus geen back-up mogelijk: *pad* | De bron voor de back-up is weg | **Opnieuw beginnen** is niet zonder risico (records en upload bestaan al): beheerder laat de situatie beoordelen |
| Back-up van *naam* is niet even groot als het origineel; er is niets verwijderd. | Kopie naar de back-upmap mislukte (schijf vol, netwerk) | Back-upmap controleren, daarna **Opnieuw**: er is niets opgeruimd |
| Weigert te verwijderen buiten de map van het programma: ... / Weigert iets buiten de map van het programma aan te raken: ... | Veiligheidscontrole; de paden van het programma en de werkmap overlappen vreemd | Beheerder: instellingen controleren |
| Opruimen is niet helemaal gelukt: ... Controleer de mappen van de programma's. | Een bestand was in gebruik of te beschermd | De genoemde mappen bekijken en handmatig opruimen |

### Overig

| Melding / situatie | Oorzaak | Wat te doen |
|---|---|---|
| "De doorloop is gestopt." / "Deze doorloop was er al niet meer." | Resultaat van **Stoppen** bij Sessies | Gebruiker moet opnieuw beginnen (fase 1) |
| De pagina blijft op "Bezig…" staan | Een programma wacht op een vraag (handmatig), of de app wacht op het afronden | Ga naar de console; bij Automatisch: wacht tot "Automatisch: deze vraag wordt door geen enkele regel herkend" verschijnt. Kijk in Test, Runs naar het logbestand |
| Onderin het menu staat "Het echte archief" | De app werkt tegen het echte archief (de instelling "Gebruik de test-ZCBS op localhost" staat uit) | Dit is geen fout; wees voorzichtig: alles wordt echt geschreven |

---

## 15. Woordenlijst

| Woord | Betekenis |
|---|---|
| ZCBS | Het systeem achter de archieven van de Historische Kring Heemskerk (collecties met records) |
| Collectie | Een archief in ZCBS, nu alleen `archief` |
| Record, recordnummer | Eén item in een collectie; het nummer is ook de bestandsnaam (11935.pdf) |
| Vrije nummers | De nummers die nog niet gebruikt zijn; ZCBS toont ze bij een nieuw record |
| Reeks | Een aaneengesloten rij vrije nummers; alle bestanden van een doorloop moeten in één reeks passen |
| Doorloop | Eén keer de flow Nieuwe bestanden voor één gebruiker in één collectie |
| Fase / stap | Een fase is wat de gebruiker ziet (balk met 7 fasen); een stap is de interne actie (hernoemen, pdf2zcbs, images, upload, back-up) |
| Werkmap | De gemeenschappelijke map met per gebruiker en collectie de submappen `invoer`, `werk`, `pdf2zcbs`, `images`, `resten` |
| Invoer, werk | De gekozen kopieën, resp. de kopieën met recordnummer als naam |
| Rest | Een bestand van een eerdere keer dat de flow in de weg zit |
| pdf2zcbs.exe | ZCBS-programma dat van een pdf tekst (ocr), pdf, small en large maakt |
| images.exe | ZCBS-programma dat van een jpg small, large en 100pct maakt, die uploadt en de records aanmaakt |
| small, large, 100pct | De afgeleide afbeeldingen. 100pct is de volle resolutie en krijgt een back-up |
| Ocr, tekst | De herkende tekst van een pdf (`<nr>.txt`) |
| Ftp | Het protocol waarmee bestanden naar de webserver gaan; niet versleuteld |
| Controle na de stap (review) | De bevestiging die de gebruiker geeft na pdf2zcbs en na images.exe |
| Verwerkt-log | Tekstbestand met alle bestanden die al op het archief zijn gezet |
| sessie.json | De bewaarde stand van een doorloop |
| Workflow-logboek | Het scherm in hkh-admin waar de app elke doorloop aan meldt |
| UNC-pad | Netwerkpad als `\\server\map`; stabiel voor een app die als service draait |

---

## Bekende verschillen met oudere documentatie

Deze handleiding volgt de code. Dit wijkt af van (of ontbreekt in) andere teksten:

| Waar | Staat er | Code zegt |
|---|---|---|
| `FlowSession.kt`, kopcommentaar | "Per collectie is er hooguit één doorloop tegelijk" | Er is geen slot per collectie; één doorloop per gebruiker en collectie (`TestRunService`) |
| `FlowLogReporter.kt` | "Er zijn meer doorlopen tegelijk: één per collectie" | Eén per gebruiker en collectie |
| README en `application.properties` | `session-timeout-minutes`: een in gebruik zijnde collectie komt na 120 minuten vrij | Niet door de code gebruikt |
| README (fase 5) en tooltips van **Handmatig uitvoeren** / **Automatisch uitvoeren** | "De app onthoudt de antwoorden en stelt ze de volgende keer voor" | De antwoorden zijn vast (`ExeAnswers`); alleen het ftp-wachtwoord wordt overgenomen |
| Melding "Automatisch: ... voeg een regel toe bij Instellingen" | Regels in Instellingen | Regels bestaan niet meer in Instellingen |
| `docs/specs-nieuwe-bestanden.md` §9 | "Twee mensen tegelijk is dus niet bedoeld", "De stand zit in het geheugen" | Meerdere gebruikers kunnen tegelijk; de stand staat in `sessie.json` |
| `docs/specs.md` (meerdere gebruikers) | "Een slot per collectie ... vervalt na 2 uur" | Geen slot meer |
| `docs/zcbs-achtergrond.md` | Vraag 11 ("VERWIJDEREN (tijdelijk) PC-bestand") in de test beantwoord met `n`; de vragen 2, 8, 11 en 13 zitten nog niet in de automatische antwoorden | De code antwoordt op vraag 11 `j` en kent vragen 2, 8 en 13 |

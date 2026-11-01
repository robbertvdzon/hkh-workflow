# ZCBS records aanpassen - volledige handleiding

Voor de beheerder en voor wie wil weten wat de app precies doet en controleert. Een korte handleiding voor medewerkers staat in
[quickstart-records-aanpassen.md](quickstart-records-aanpassen.md).

> **Over de bronnen.** Deze handleiding is geschreven vanuit de code en de schermen (templates) van de app, de bijbehorende tests en de
> ontwerpdocumenten in `docs/`. Waar code en documenten elkaar tegenspreken, geldt de code. De app is getest tegen een nagemaakte en een
> lokale ZCBS, niet tegen de echte server. Alles wat ZCBS zelf doet (vergrendelen, e-mail, tijdslimieten) is **uit de code gelezen,
> niet bevestigd op de echte server**; dat staat er steeds bij.

## Inhoud

1. [Wat doet deze functie?](#1-wat-doet-deze-functie)
2. [Inloggen bij ZCBS en rechten per collectie](#2-inloggen-bij-zcbs-en-rechten-per-collectie)
3. [Records kiezen](#3-records-kiezen)
4. [De lijst met records](#4-de-lijst-met-records)
5. [Het bewerkformulier](#5-het-bewerkformulier)
6. [Het voorbeeld van de wijzigingen](#6-het-voorbeeld-van-de-wijzigingen)
7. [Opslaan: wat de app stap voor stap doet](#7-opslaan-wat-de-app-stap-voor-stap-doet)
8. [Alle controles op een rij](#8-alle-controles-op-een-rij)
9. [Later, volgende record en meerdere records](#9-later-volgende-record-en-meerdere-records)
10. [Meer mensen tegelijk](#10-meer-mensen-tegelijk)
11. [Het workflow-logboek](#11-het-workflow-logboek)
12. [Problemen en oplossingen](#12-problemen-en-oplossingen)
13. [Woordenlijst](#13-woordenlijst)

---

## 1. Wat doet deze functie?

Met **ZCBS records aanpassen** kies je records die al in ZCBS bestaan (bijvoorbeeld net aangemaakt met de flow *Nieuwe bestanden in ZCBS
zetten*) en vul je de gegevens in: titel, omschrijving, thema enzovoort. De app is daarbij geen eigen database. Hij werkt als een browser:

- hij haalt het **bewerkformulier van ZCBS** op, precies zoals ZCBS het zelf aanbiedt (de velden en de keuzelijsten komen uit ZCBS);
- hij toont dat formulier in de eigen schermen van de app, naast de pdf of afbeelding van het record;
- bij **Opslaan** stuurt hij het formulier terug naar ZCBS, als een gewone ZCBS-opslag, en leest het record daarna terug om te controleren
  of ZCBS het echt zo heeft bewaard.

De app schrijft dus nooit rechtstreeks in bestanden van ZCBS. Alles wat de app doet, kun je ook in ZCBS zelf doen; bij elk record staat
daarom de knop **Open in ZCBS** als terugvaloptie.

Het pad door de app: **Start** (inloggen) - collectie kiezen - **Wat wil je doen?** - **ZCBS records aanpassen**.

Alleen de collectie **archief** is nu beschikbaar. Andere collecties staan wel op de keuzepagina, maar als "Nog niet beschikbaar" of
"Geen rechten".

---

## 2. Inloggen bij ZCBS en rechten per collectie

### 2.1 Inloggen

Op de startpagina (**Welkom bij ZcbsServer**) staat het blok **Inloggen bij ZCBS** met de velden **Naam** en **Wachtwoord** en de knop
**Inloggen**. Gebruik dezelfde gegevens als op de ZCBS-website; typ de naam precies zoals in ZCBS. Zonder inlog stuurt de app je bij elke
pagina van Carla's functies terug naar de startpagina met de melding "Log eerst in bij ZCBS."

Het ZCBS-adres hoeft de medewerker niet te kennen. De beheerder zet dat bij **Instellingen** (daar kiest hij ook tussen de test-ZCBS op de
eigen computer en het echte archief). Is er geen adres ingesteld, dan staat op de startpagina "Het adres van ZCBS is nog niet ingesteld."

Wat er met naam en wachtwoord gebeurt (uit de code gelezen):

- De app bewaart ze in het **geheugen** van de server, zodat hij later opnieuw kan inloggen als de ZCBS-sessie verloopt (zie 7).
- Bij een geslaagde inlog zet de app ook een **versleuteld cookie** in de browser (naam: `hkh_inloggen`, 30 dagen, alleen voor deze app, met
  een sleutelbestand naast het instellingenbestand). Daarmee logt de app je na een herstart van de app vanzelf weer in. **Uitloggen** haalt
  het cookie weg. Een computer waar anderen bij kunnen, is dus geen plek om ingelogd te blijven.
- Wijzigt het ZCBS-adres in de instellingen (bijvoorbeeld van test naar echt archief), dan geldt de oude inlog niet meer en word je uitgelogd.
- Het wachtwoord komt niet in het workflow-logboek; wel meldt de app een inlogpoging (naam, gelukt of niet, via wachtwoord of cookie, en de
  foutmelding) aan dat logboek.

> Let op: de tekst op de startpagina zegt dat de app de gegevens "alleen bewaart zolang hij draait". Het versleutelde cookie hierboven is
> daar een uitzondering op. Zie ook het overzicht onderaan.

### 2.2 Rechten per collectie

In ZCBS gelden rechten **per collectie**. De app probeert daarom dezelfde naam en hetzelfde wachtwoord bij het ZCBS-script van **elke
collectie** (archief, artikelen, beeldbank, bidprent, library, objecten, transcripties). Je bent ingelogd zodra het bij **minstens één**
collectie lukt.

- Linksonder in het menu staat **Rechten in ZCBS**: de collecties waar je inlog is gelukt (of "geen").
- Op de pagina **Met welke collectie wil je werken?** zijn alleen collecties klikbaar waar je rechten hebt **en** waar de app mee werkt.
  Andere tonen "Geen rechten" of "Nog niet beschikbaar". Nu werkt alleen **archief**.
- Heb je alleen rechten bij een andere collectie (bijvoorbeeld de beeldbank), dan kun je wel inloggen, maar niet met het archief werken.
- ZCBS geeft bij een **fout wachtwoord** en bij een **ontbrekend recht** hetzelfde antwoord. De app kan die twee niet uit elkaar houden en
  meldt in beide gevallen: "Naam of wachtwoord niet herkend, of je hebt nergens rechten."

### 2.3 Uitloggen

De knop **Uitloggen** (in het menu) wist de ZCBS-sessie van deze gebruiker, **inclusief de gekozen records, de statussen en alles wat nog
niet is opgeslagen**. Daarna moet je de records opnieuw kiezen.

In het menu staat onderaan in rood **Het echte archief** zodra je bent ingelogd en de app naar het echte ZCBS wijst (niet naar de test-ZCBS).
Dan is alles wat je opslaat echt.

---

## 3. Records kiezen

Op de pagina **ZCBS records aanpassen** staat het blok **Welke records?** (na de eerste keuze heet het **Andere records kiezen**).

1. Typ bij **Recordnummer of reeks** een nummer, een reeks of meer daarvan, bijvoorbeeld `11934`, `11934-11945` of `11934-11945, 11950`.
   Scheiden mag met een komma, puntkomma of spatie; in een reeks mag een streepje of een gedachtestreepje.
2. Klik op **Toon deze records**.

Regels:

- **Maximaal 30 records tegelijk.** Meer geeft de melding "Kies maximaal 30 records tegelijk."
- Nummers worden aangevuld met nullen tot de lengte die ZCBS gebruikt (standaard 5 cijfers: `42` wordt `00042`). Dubbele nummers tellen één keer.
- De app zoekt elk nummer **meteen** op in ZCBS, met de gewone weergave van het record. Dat is alleen lezen en **zet geen vergrendeling**.
  Zo zijn de **titel** en de kolom **Op internet** direct gevuld. De app kijkt ook of er een pdf (`archief/pdf/<nummer>.pdf`) of anders een
  grote afbeelding (`archief/large/<nummer>.jpg`) op de site staat; die zie je later achter de knop **Pdf** of **Afbeelding**.
- **"Bestaat niet"** betekent: ZCBS antwoordt dat het record niet beschikbaar is. Zo'n nummer komt **niet** in de lijst. De app meldt het
  in het blauwe bericht: "Deze bestaan niet in ZCBS en staan er niet bij: ..." (maximaal 8 nummers, de rest als "en N andere"). Het
  bericht begint met "N records staan klaar om te bewerken." (of "1 record staat klaar om te bewerken."). Bestaat geen enkel nummer, dan
  staat er "Deze records bestaan niet in ZCBS: ..." en blijft de oude lijst staan.
- Geeft ZCBS geen duidelijk antwoord, dan telt het record als "bestaat"; het bewerken meldt het zelf als het toch niet klopt.
- Een **nieuwe keuze vervangt de oude lijst** (inclusief de statussen). Records die je al had opgeslagen, blijven natuurlijk opgeslagen in
  ZCBS, maar de lijst en teller beginnen opnieuw.
- Een record dat op dat moment door iemand anders is vergrendeld, staat wel in de lijst, maar zonder titel (en met "?" bij Op internet).

Meldingen bij een ongeldige invoer: zie [hoofdstuk 12](#12-problemen-en-oplossingen).

---

## 4. De lijst met records

Boven de lijst staat een **voortgangsbalk** en de tekst "x van y opgeslagen" (met ", z later" als er records op later staan).
Onder de lijst staat: "Een geopend record is 10 minuten voor anderen vergrendeld."

### 4.1 Kolommen

| Kolom | Betekenis |
|---|---|
| **Nummer** | Het recordnummer. |
| **Titel** | De titel in ZCBS (na een opslag de nieuwe titel). "(geen titel)" als die leeg is. Houd de muis erboven: bij een probleem verschijnt de foutmelding. |
| **Op internet** | **ja** = het record staat op internet, **nee** = niet, **?** = onbekend. Komt uit het veld Plaatsing op internet. |
| **Status** | Zie 4.2. |
| (knoppen) | Zie 4.3. |

### 4.2 Statussen

| Status | Wat het betekent |
|---|---|
| **nog niet bewerkt** | Gekozen, maar nog niet geopend. |
| **bezig** | Geopend (het formulier is bij ZCBS opgehaald, het record is vergrendeld), nog niet opgeslagen. |
| **opgeslagen** | Opgeslagen **en** teruggelezen uit ZCBS zonder verschil. Pas dan telt het als opgeslagen. Het record mag je opnieuw openen. |
| **later** | Door jou bewust overgeslagen (**Doe ik later** of **Volgende zonder opslaan**). Er is niets opgeslagen. |
| **geblokkeerd** | ZCBS meldt dat iemand anders het record gebruikt (de vergrendeling). Probeer het later opnieuw. |
| **niet veilig** | De app begrijpt dit record niet goed genoeg om het veilig op te slaan (zie 8.2). Er is niets gewijzigd. Bewerk het in ZCBS zelf. |
| **fout** | Er ging iets mis: het record bestaat niet (meer), ZCBS weigerde iets, geen verbinding, een onverwacht antwoord, of de terugcontrole na het opslaan klopte niet. De melding staat op de recordpagina. |

Een record dat al **opgeslagen** is, houdt die status, ook als je het opnieuw opent en daarbij een melding krijgt.
De status **later** is nooit "klaar" voor ZCBS: het record is gewoon niet bewerkt.

### 4.3 Knoppen per rij

| Knop | Wat hij doet |
|---|---|
| **Bewerk** | Opent het record voor bewerken (status nog niet bewerkt, later, geblokkeerd of fout). |
| **Verder** | Zelfde, voor een record dat al **bezig** is. |
| **Nog eens** | Zelfde, voor een record dat al **opgeslagen** is. |
| **Open in ZCBS** | Opent het record in ZCBS zelf, in een nieuw tabblad (gewone weergave, zonder te vergrendelen). |
| **Pdf** of **Afbeelding** | Opent het document in een popup. De knop staat alleen als de pdf of afbeelding op de site staat. Zie hieronder. |

Bij een record met status **niet veilig** staat de knop **Bewerk** niet; gebruik **Open in ZCBS**.

**Popup bij Pdf/Afbeelding.** Een venster met bovenin de titel ("nummer - titel"), de knoppen **Download** en **Sluiten**. Klik naast het venster
om het ook te sluiten. De popup toont de pdf als die er is; staat er geen pdf maar wel een grote afbeelding, dan heet de knop **Afbeelding**.

---

## 5. Het bewerkformulier

Klik op **Bewerk** (of **Verder** / **Nog eens**). De app haalt dan het formulier van ZCBS op. **Dat vergrendelt het record voor andere
gebruikers (10 minuten)**. De kop toont: "geopend om 14:32, voor anderen vergrendeld tot 14:42" (voorbeeldtijden).

Bovenaan: **← Terug naar de lijst**, het recordnummer, "record x van y" en rechts **Open in ZCBS** (dat opent hier het bewerkformulier van
ZCBS zelf).

### 5.1 Links: het document

- De pdf van het record, ingebed. Ontbreekt die, dan de grote afbeelding met de uitleg dat de pdf nog niet op de site staat.
- Ontbreken ze allebei: een kader "de pdf van dit record staat (nog) niet op de site" met het pad.
- De link **Open de pdf in een apart venster**.
- Als de site een tekstbestand (ocr) heeft: een kader **Tekst uit het document (ocr)** met de eerste 800 tekens.

### 5.2 Rechts: de velden

De velden, hun volgorde en hun keuzelijsten komen **rechtstreeks uit ZCBS**. De app heeft geen vaste veldenlijst; een nieuw veld in ZCBS
verschijnt vanzelf. Een vraagteken (**?**) achter een veldnaam toont de helptekst die ZCBS erbij geeft. Voorbeelden van velden (uit de
testopname van een ZCBS-formulier, de echte lijst kan verschillen): Titel, Omschrijving, Thema, Trefwoord, Plaatsing op internet.

| Soort veld | Hoe het eruitziet en werkt |
|---|---|
| Tekstveld | Een regel tekst. |
| Tekstvak | Meerdere regels. Regeleinden mogen; ZCBS maakt er `<br>` van. |
| Keuzelijst | Een uitklaplijst; je kiest er één. |
| **Meerkeuze** (bijvoorbeeld Thema) | Een lijst waarin je er meer dan één kiest: **houd Ctrl (of Cmd) ingedrukt**. Kies je niets, dan wordt het veld leeg. Meerdere keuzes worden in ZCBS opgeslagen gescheiden door "; ". |
| Keuzerondjes | Eén keuze. **Plaatsing op internet** heeft de keuzes **neen** en **ja**. |

**"Anders".** Veel keuzelijsten van ZCBS hebben de keuze `== Anders ==`. Kies je die, dan vul je de eigen waarde in het bijbehorende veld
met de toevoeging **(namelijk)**. Bij opslaan vervangt ZCBS `== Anders ==` door die tekst. Velden met de toevoeging **(extra)** zijn aanvullende
tekstvelden bij een keuzelijst; hun inhoud wordt in ZCBS achter de keuze gezet, gescheiden door een verticale streep (`keuze|extra`). Wie
een nieuwe "Anders"-waarde opslaat, veroorzaakt (volgens de ontwerpdocumenten, niet bevestigd op de echte server) een mail van ZCBS aan de
beheerder, zodat die de waarde aan de lijst kan toevoegen.

**Plaatsing op internet.** Je kiest alleen **ja** of **neen**. De waarde achter "ja" komt uit het formulier van ZCBS zelf (ZCBS legt de datum vast);
de app verzint niets. In de lijst zie je het resultaat als **ja** of **nee**.

**Wat de app aan je invoer verandert** (voordat hij wordt vergeleken en opgeslagen):

- Spaties voor en na de tekst verdwijnen.
- Tekens uit Word worden vervangen door tekens die ZCBS aankan: gebogen aanhalingstekens worden gewone (`"` en `'`), een lange of korte
  streep wordt `-`, het beletselteken (drie puntjes in één teken) wordt `...`, niet-brekende spaties worden gewone spaties en onzichtbare
  nulbreedte-spaties verdwijnen.
- Regeleinden worden gelijkgetrokken.

Velden die jij niet aanraakt, blijven zoals ze in ZCBS stonden. Ook velden die het formulier niet toont (bijvoorbeeld een veld dat ZCBS
niet als invoerveld aanbiedt) neemt de app uit het oorspronkelijke record mee, zodat ze niet per ongeluk leeg worden opgeslagen.

### 5.3 De knoppen onderaan

| Knop | Wanneer zichtbaar | Wat hij doet |
|---|---|---|
| **Opslaan** | altijd | Slaat dit record op (zie hoofdstuk 7) en gaat daarna terug naar de lijst. |
| **Opslaan en volgende** | als er nog een volgend, niet-opgeslagen record in de lijst staat | Slaat op en opent meteen het volgende record. |
| **Volgende zonder opslaan** | als er een volgend record is | Slaat dit record **niet** op (het krijgt status **later**) en opent het volgende. |
| **Doe ik later** | als er **geen** volgend record is | Slaat niets op, zet het record op **later** en gaat naar de lijst. |

Na een geslaagde opslag staat er een groen bericht: "Record N is opgeslagen en teruggecontroleerd. De beheerder krijgt een e-mail met het record."
Die e-mail wordt door ZCBS verstuurd bij een opslag, niet door deze app (volgens de ontwerpdocumenten; niet bevestigd op de echte server).

Het "volgende record" is het eerstvolgende record **na** het huidige in de lijst dat nog niet is opgeslagen; records op **later**, **fout**,
**geblokkeerd** of **niet veilig** tellen daarbij dus mee.

---

## 6. Het voorbeeld van de wijzigingen

De app heeft een scherm **Voorbeeld van de wijziging** (record-voorbeeld): een tabel met de kolommen **Veld**, **Was** en **Wordt**, alleen
voor de velden die je hebt gewijzigd (een lege waarde staat als "(leeg)"). Daaronder:

- "Er is niets gewijzigd. Opslaan doet dan niets." als er geen verschil is;
- een rode melding **Dit kan zo niet worden opgeslagen.** met per probleem een regel als er tekens of woorden in staan die ZCBS niet aankan
  (zie 8.3 en 8.4); de knop **Opslaan** is dan uitgeschakeld;
- "Opslaan stuurt een e-mail naar de beheerder met het hele record. Daarna leest de app het record terug uit ZCBS en controleert het.";
- de knoppen **Opslaan** en **Terug naar het formulier** (je invoer blijft dan staan).

Het voorbeeld zet **niets** naar ZCBS en vergrendelt niets extra.

> **Let op (uit de code gelezen):** in het huidige bewerkformulier staat **geen knop** die dit voorbeeldscherm opent. De pagina en de
> bijbehorende aanroepen bestaan in de code, maar de knoppen die ernaartoe leidden (**Voorbeeld**) staan niet meer op het formulier; zie 5.3.
> De ontwerpdocumenten en de ondertitel van de pagina ("met een voorbeeld vooraf") beschrijven het voorbeeld nog wel. Wie wil zien wat er
> verandert, kijkt dus op dit moment in het conflictscherm of na het opslaan in ZCBS. De controles op tekens en woorden (hoofdstuk 8) werken wél
> gewoon bij **Opslaan**.

---

## 7. Opslaan: wat de app stap voor stap doet

Een klik op **Opslaan** zet de volgende stappen in gang. Stopt een stap, dan gebeurt er niets in ZCBS tenzij anders vermeld, en je invoer
blijft in de app staan (je ziet haar terug in het formulier).

1. **Wijzigingen bepalen.** De app vergelijkt je invoer met het formulier zoals het was toen je het record opende. Is er niets anders:
   "Er is niets gewijzigd; er is niets opgeslagen." en er gaat niets naar ZCBS.
2. **Tekens en woorden controleren** (8.3 en 8.4) op de velden die je wijzigde. Bij een probleem: rode melding **Dit kan zo niet worden
   opgeslagen.** Niets naar ZCBS.
3. **Opnieuw inloggen** bij ZCBS met de bewaarde gegevens. Zo kan een verlopen ZCBS-sessie het opslaan niet meer breken.
   Lukt dat niet, dan volgt de melding over de verlopen sessie (8.6).
4. **Het formulier opnieuw ophalen.** Dat vergrendelt het record opnieuw op jouw naam (ook als de eerste 10 minuten al voorbij zijn) en geeft de
   nieuwste stand van het record. Is het record intussen door een ander vergrendeld: status **geblokkeerd** (8.1).
5. **Opnieuw controleren of de app het record begrijpt** (8.2) op die verse versie.
6. **Controle op tussentijdse wijzigingen** (8.5): heeft iemand anders het record veranderd sinds jij het opende? Zo ja en is het **hetzelfde veld** dat
   jij wijzigde: conflictscherm. Veranderde de ander een **ander** veld, dan blijft die wijziging gewoon behouden en gaat jouw wijziging erbij.
7. **Versturen.** De app stuurt het formulier naar ZCBS (met jouw wijzigingen op de verse versie, alle andere velden ongewijzigd) in het
   tekensysteem van ZCBS (latin-1).
8. **Teruglezen.** De app haalt het record nogmaals op en vergelijkt het met wat hij verstuurde, veld voor veld (8.7). Pas als alles gelijk is,
   wordt de status **opgeslagen** en volgt het groene bericht.

Er is **één poging**; bij een netwerkfout herhaalt de app niet automatisch, omdat de opslag toch gelukt kan zijn. Je krijgt dan een melding
om in ZCBS te controleren.

---

## 8. Alle controles op een rij

### 8.1 Vergrendeling (10 minuten, door ZCBS)

Het ophalen van het bewerkformulier (en ook het opslaan) vergrendelt het record in ZCBS voor **andere gebruikersnamen**. Uit de ontwerpdocumenten
(code van ZCBS gelezen, niet bevestigd op de echte server):

- de vergrendeling duurt 10 minuten en wordt bij elk nieuw verzoek met bewerken verlengd;
- ze hoort bij de **gebruikersnaam**: wie met dezelfde naam is ingelogd, blokkeert zichzelf niet;
- ze wordt na het opslaan **niet** vrijgegeven; een collega moet dus nog tot 10 minuten wachten;
- gewoon bekijken (de lijst, **Open in ZCBS** vanuit de lijst) vergrendelt niet. Wel vergrendelt de knop **Open in ZCBS** op de recordpagina,
  want die opent het bewerkformulier van ZCBS;
- de app geeft vergrendelingen nooit zelf vrij.

Is een record door een ander vergrendeld, dan meldt ZCBS dat, bijvoorbeeld: "Record 11934 is nog ongeveer 10 minuten in gebruik bij Jelly. Je invoer
blijft bewaard." De status wordt **geblokkeerd** en je krijgt de knoppen **Probeer opnieuw** en **Doe ik later**. Bij het opslaan verschijnt
in plaats daarvan de knop **Probeer opnieuw op te slaan**, met je invoer nog in het formulier.

Open in de app alleen de records die je echt gaat bewerken: elk geopend record is 10 minuten voor anderen vergrendeld.

### 8.2 Begrijpt de app het record veilig genoeg? (status "niet veilig")

Bij elk formulier dat de app van ZCBS ophaalt, bouwt hij uit de ongewijzigde velden het record **zelf opnieuw op**, op dezelfde manier als ZCBS bij
het opslaan (keuzes samenvoegen met "; ", `== Anders ==` vervangen, subvelden met "|", regeleinden als `<br>`, dubbele spaties weg enzovoort).
Dat resultaat moet **gelijk zijn aan het oorspronkelijke record** dat ZCBS meestuurt. In de code heet dat `RecordRebuilder.differences`.

Is er een verschil, dan zou de app bij opslaan iets anders wegschrijven dan er stond. Daarom weigert hij het record: status **niet veilig**, kop
"Dit record is niet veilig te bewerken in deze app.", met de tekst "De app begrijpt dit record niet goed genoeg om het veilig op te slaan
(verschil in: ...). Er is niets gewijzigd. Open het record in ZCBS." Je kunt dan **Open in ZCBS** gebruiken of **Doe ik later**.
Dezelfde controle gebeurt opnieuw vlak voor het opslaan; verschilt het formulier dan, dan krijg je: "Het formulier van ZCBS wijkt nu af van wat
de app begrijpt; er is niets opgeslagen. Open het record in ZCBS."

Een leeg record dat in het formulier "neen" toont terwijl het veld leeg is, en regeleinden waar een browser een spatie voor zet, worden bewust
niet als verschil gezien.

### 8.3 Tekens die ZCBS niet kan opslaan (latin-1)

ZCBS slaat tekst op in latin-1 (ISO-8859-1). Letters als e met trema, e met accent en n met tilde passen daarin. Tekens daarbuiten, zoals het
euroteken of Japanse tekens, niet. Veelvoorkomende Word-tekens worden automatisch vervangen (5.2). Voor de rest blokkeert de app het opslaan
met een melding als: "In "Titel" staat het teken '€'. ZCBS kan dat niet opslaan; haal het weg of vervang het." Er gaat niets naar ZCBS.

### 8.4 Woorden die ZCBS weigert

ZCBS weigert bepaalde woorden in een veld; dan mislukt de hele opslag met een lege pagina. De app controleert dat vooraf en meldt:
"In "Omschrijving" staat het woord "SELECT". ZCBS weigert dat in een veld; gebruik een ander woord." De geweigerde woorden zijn:

- SQL-achtige woorden **in hoofdletters**: `SELECT`, `UNION`, `FROM`, `CONCAT`, `CHAR(`, `NULL`, `THEN(`, ook `name_const`, `benchmark` en `select*from`;
- gebeurtenisnamen uit webpagina's, ook in kleine letters: `onload`, `onclick`, `onerror`, `onfocus`, `onselect`, `onsubmit`;
- bestandsnamen van ZCBS: `permit.lst`, `objecten.pl`, `cfg-cbs.pl` (en varianten met een cijfer).

Gewone woorden in kleine letters (`select`, `from`, `null`) en woorden als Heemskerk, scriptie of manuscript zijn geen probleem.
De controle geldt voor de velden die je wijzigt.

### 8.5 Tussentijdse wijziging door een ander (conflict)

Bij het opslaan vergelijkt de app het verse record met de versie van toen je opende.

- Is er niets veranderd, of is een **ander** veld veranderd dan jij wijzigde: geen probleem. De wijziging van de ander blijft staan en de jouwe
  komt erbij.
- Is **hetzelfde veld** door iemand anders gewijzigd: er verschijnt een geel kader **Iemand heeft record N aangepast terwijl jij bezig was.**
  met de tabel **Veld / Jouw versie / Nu in ZCBS**. Er is nog niets opgeslagen. Je kiest:
  - **Mijn versie bewaren**: jouw wijzigingen worden opgeslagen, en vervangen voor die velden wat de ander schreef.
  - **Die van ZCBS gebruiken**: voor de velden die botsen kiest de app de versie uit ZCBS. Jouw andere wijzigingen blijven in het formulier staan.
    Er wordt nog niets opgeslagen; je ziet het bericht "De velden die botsten zijn vervangen door de versie uit ZCBS. Controleer het
    formulier en sla dan op." Daarna klik je zelf op **Opslaan**.

Overleg bij twijfel met de collega die het record ook bewerkte.

### 8.6 Verlopen sessie: opnieuw inloggen

ZCBS beëindigt een sessie na enkele minuten stilstand (volgens de ontwerpdocumenten 10 minuten op een gewoon adres, 60 op het HKH-netwerk; niet
bevestigd op de echte server). De app vangt dat zelf op:

- bij **openen**: ziet de app dat de sessie verlopen is, dan logt hij met de bewaarde gegevens opnieuw in en probeert het nog eens;
- bij **opslaan**: de app logt altijd eerst opnieuw in (stap 3 in hoofdstuk 7).

Lukt het opnieuw inloggen niet (bijvoorbeeld omdat het wachtwoord intussen is gewijzigd), dan zie je "Je sessie bij ZCBS is verlopen. Log
opnieuw in." (bij opslaan met de toevoeging "je invoer blijft bewaard"). Zie [hoofdstuk 12](#12-problemen-en-oplossingen) voor wat je dan doet.

### 8.7 Teruglezen na opslaan

Na het versturen haalt de app het record opnieuw op uit ZCBS en vergelijkt hij alle velden van het record met wat hij verstuurde, rekening
houdend met wat ZCBS zelf mag aanpassen. Klopt een veld niet, dan krijg je bijvoorbeeld:

> Opgeslagen, maar "Titel" is nu "..." in plaats van "...". Controleer het record in ZCBS.

De status wordt dan **fout** (ook al kan een deel wel zijn opgeslagen): controleer het record in ZCBS. Kan het record niet teruggelezen worden:
"Het record kon niet teruggelezen worden (...). Controleer in ZCBS of het is opgeslagen."

### 8.8 E-mail naar de beheerder

Een opslag in ZCBS stuurt een e-mail. De app zegt dat in het groene bericht en in het voorbeeldscherm ("De beheerder krijgt een e-mail met het
record"). Volgens de ontwerpdocumenten (ZCBS-code gelezen) mailt ZCBS bij elke opslag het volledige record naar het archief-adres en bij een
nieuwe "Anders"-waarde ook naar de beheerder. Bij een opslag waarbij niets is veranderd, stuurt ZCBS niets. De app verstuurt zelf geen e-mail.
**Niet bevestigd op de echte server**; de lokale ZCBS voor de tests verstuurt geen e-mail.

---

## 9. Later, volgende record en meerdere records

- **Doe ik later** en **Volgende zonder opslaan** zetten het record op status **later**. Ze slaan **niets** op en de app bewaart bij deze knoppen
  de tekst die je net had getypt **niet**. Wil je je invoer houden, sla dan eerst op.
- Een record dat al **opgeslagen** is, blijft opgeslagen als je op later drukt.
- Records op **later** blijven in de lijst staan; open ze met **Bewerk** wanneer je er klaar voor bent.
- Het opgehaalde formulier is een momentopname. Open je een record dat je eerder liet liggen opnieuw, dan haalt de app de nieuwste versie uit ZCBS
  op (en vergrendelt het opnieuw).
- De lijst is "klaar" (in het logboek: afgerond) als elk record **opgeslagen** of op **later** staat.
- Je invoer blijft in de app bewaard als een **opslagpoging** mislukt (geblokkeerd, conflict, verboden teken of woord, verlopen sessie). Dat is
  zo zolang de app draait en je niet uitlogt.
- Er is geen beperking dat je maar één record tegelijk opent: elk geopend record is 10 minuten vergrendeld, ook als je het niet bewerkt.

---

## 10. Meer mensen tegelijk

Dat mag. Elke ingelogde gebruiker heeft een **eigen sessie** in de app: een eigen inlog, een eigen lijst met gekozen records, eigen statussen en
een eigen ingevoerde tekst. Eén gebruiker uitloggen logt een ander niet uit. De beheerder ziet onder **Instellingen** - **Sessies** wie er is
ingelogd bij ZCBS.

Wat ze elkaar kunnen doen, regelt **ZCBS**: de vergrendeling per record (8.1) en, daarbovenop, de conflictcontrole van de app (8.5). Twee mensen met
**dezelfde ZCBS-naam** delen in de app één sessie (de naam telt, niet hoofdletters) en blokkeren elkaar in ZCBS niet. Gebruik dus liever elk de eigen naam.

---

## 11. Het workflow-logboek

Als de beheerder een logboek (hkh-admin) heeft ingesteld, meldt de app elke doorloop van ZCBS records aanpassen daaraan. Een doorloop begint zodra
je records kiest (een nieuwe keuze is een nieuwe doorloop). De app kijkt om de paar seconden naar de stand en meldt een verandering. In het logboek staat:

- **Samenvatting:** wie, hoeveel records, hoeveel opgeslagen, hoeveel op later, en of de doorloop klaar is (alle records opgeslagen of op later);
- **per record:** nummer, titel, status, de laatste melding en het tijdstip van openen;
- **wijzigingen:** per opgeslagen record de gewijzigde velden met oude en nieuwe waarde (elk afgekapt op 2000 tekens);
- **tijdlijn:** regels als "Gestart: 3 records gekozen", "Record 11934 geopend", "Record 11934 opgeslagen (gewijzigd: Titel)", "Record 11935 voor later gezet" of
  "Record 11936: fout - ...".

Wachtwoorden staan er niet in. De velden bevatten wel de ingevulde gegevens van de records, dus behandel het logboek als vertrouwelijk.
Is er geen logboek ingesteld, dan merkt de medewerker daar niets van.

---

## 12. Problemen en oplossingen

Een medewerker die vastloopt, kan bijna altijd terug naar **Open in ZCBS** en het record daar bewerken.

### 12.1 Inloggen

| Melding | Oorzaak | Wat te doen |
|---|---|---|
| Stel eerst het adres van ZCBS in bij Instellingen. | In de instellingen staat geen ZCBS-adres. | De beheerder vult het adres in bij Instellingen. |
| Vul je naam en je wachtwoord in. | Een van beide velden is leeg. | Beide invullen. |
| Geen verbinding met ZCBS (adres): ... | De app kan ZCBS niet bereiken (netwerk, server, adres). | Later opnieuw; de beheerder controleert adres en verbinding. |
| Naam of wachtwoord niet herkend, of je hebt nergens rechten. | Fout wachtwoord, naam anders gespeld dan in ZCBS, of geen rechten bij welke collectie dan ook (ZCBS geeft hetzelfde antwoord). | Naam precies zoals in ZCBS typen en het wachtwoord controleren. Blijft het misgaan, vraag de beheerder naar je rechten. Probeer niet steeds opnieuw: een mislukte poging komt met naam en wachtwoord in het ZCBS-log. |
| ZCBS vindt dat er te veel verzoeken zijn. Wacht even en probeer het opnieuw. | ZCBS beperkt het aantal verzoeken. | Een paar minuten wachten. |
| Log eerst in bij ZCBS. | Je bent niet (meer) ingelogd, bijvoorbeeld na een herstart van de app zonder bruikbaar cookie. | Inloggen. |
| Je bent niet ingelogd bij ZCBS. | Interne melding als er geen sessie is. | Inloggen. |

### 12.2 Records kiezen

| Melding | Oorzaak | Wat te doen |
|---|---|---|
| "X" is geen recordnummer of reeks. Gebruik bijvoorbeeld 11934 of 11934-11945. | Iets anders dan cijfers en streepjes getypt. | Alleen cijfers, streepje voor een reeks, komma tussen delen. |
| In "X" is het eerste nummer hoger dan het laatste. | Een reeks als 11945-11934. | Het laagste nummer eerst. |
| Kies maximaal 30 records tegelijk. | Te veel nummers in één keer. | Verdeel over meerdere keuzes. |
| Vul een recordnummer of een reeks in, bijvoorbeeld 11934 of 11934-11945. | Het veld was leeg (of bevatte alleen scheidingstekens). | Een nummer invullen. |
| Deze records bestaan niet in ZCBS: ... | Geen van de nummers bestaat. | Nummers controleren (of het record eerst aanmaken via Nieuwe bestanden). De oude lijst blijft staan. |
| Deze bestaan niet in ZCBS en staan er niet bij: ... (blauw bericht) | Een deel van de nummers bestaat niet. | Controleren of dat klopt; de rest staat klaar. |
| Geen verbinding met ZCBS: ... | ZCBS niet bereikbaar tijdens het opzoeken. | Later opnieuw proberen. |

### 12.3 Openen van een record

| Melding / status | Oorzaak | Wat te doen |
|---|---|---|
| Record N is nog ongeveer M minuten in gebruik bij X. Je invoer blijft bewaard. (status geblokkeerd; knoppen **Probeer opnieuw**, **Doe ik later**) | Een ander heeft het record geopend of opgeslagen; ZCBS vergrendelt 10 minuten. | Wacht de genoemde tijd en klik **Probeer opnieuw**, of kies **Doe ik later** en ga verder. Kent je collega het record niet meer, wacht dan gewoon tot de tijd om is. |
| Dit record is niet veilig te bewerken in deze app. De app begrijpt dit record niet goed genoeg om het veilig op te slaan (verschil in: ...). Er is niets gewijzigd. Open het record in ZCBS. (status niet veilig) | De opnieuw opgebouwde versie wijkt af van het echte record (8.2). Meestal door een ongebruikelijke inhoud in een veld. | **Open in ZCBS** en bewerk daar. Meld het record aan de beheerder: hij kan onderzoeken welk veld afwijkt. |
| Record N bestaat niet in ZCBS. (status fout) | Het record is intussen verdwenen of het nummer klopt niet. | Controleer het nummer; kies de records opnieuw. |
| ZCBS weigert dit verzoek (==Sorry:N==). (fout) | ZCBS heeft het verzoek geweigerd (bijvoorbeeld een verboden woord of een geweigerde aanroep). Het cijfer geeft de reden bij ZCBS. | Niet blijven herhalen; meld het aan de beheerder met het getal. |
| ZCBS vindt dat er te veel verzoeken zijn. Wacht even. (fout) | Te veel verzoeken in korte tijd. | Even wachten, dan opnieuw openen. |
| Geen verbinding met ZCBS: ... (fout) | Netwerkfout bij het ophalen. | Opnieuw proberen. |
| Onverwacht antwoord van ZCBS: ... (fout) | ZCBS gaf een pagina die de app niet kent. | **Open in ZCBS**; meld het aan de beheerder. |
| Je sessie bij ZCBS is verlopen. Log opnieuw in. | De app kon niet opnieuw inloggen. | Zie 12.5. |

### 12.4 Opslaan

| Melding | Oorzaak | Wat te doen |
|---|---|---|
| Er is niets gewijzigd; er is niets opgeslagen. | Je invoer is gelijk aan wat al in ZCBS stond. | Niets; ga door met het volgende record. |
| Dit kan zo niet worden opgeslagen. In "Veld" staat het teken '€'. ZCBS kan dat niet opslaan; haal het weg of vervang het. | Een teken buiten latin-1. | Teken verwijderen of vervangen (bijvoorbeeld "EUR"). |
| Dit kan zo niet worden opgeslagen. In veld a5 staat het teken '...'. ZCBS kan dat niet opslaan. | Zelfde, maar opgemerkt bij het versturen (met de interne veldnaam). | Zoek het teken in dat veld (a5 is meestal de titel) en verwijder het. |
| Dit kan zo niet worden opgeslagen. In "Veld" staat het woord "SELECT". ZCBS weigert dat in een veld; gebruik een ander woord. | Een door ZCBS geweigerd woord (8.4). | Een ander woord gebruiken of kleine letters. |
| Record N is nog ongeveer M minuten in gebruik bij X. Probeer het over een paar minuten opnieuw, of doe dit record later. (status geblokkeerd) | Terwijl jij bezig was, opende een ander het record. | Wacht en klik **Probeer opnieuw op te slaan**; je invoer staat er nog. |
| Iemand heeft record N aangepast terwijl jij bezig was. | Conflict (8.5). | Kies **Mijn versie bewaren** of **Die van ZCBS gebruiken**. |
| Record N bestaat niet (meer) in ZCBS. | Het record is weg. | Controleer in ZCBS. |
| Onverwacht antwoord van ZCBS bij het ophalen van het formulier: ... | ZCBS gaf een onbekende pagina. | **Open in ZCBS** en controleer; meld het aan de beheerder. |
| Het formulier van ZCBS wijkt nu af van wat de app begrijpt; er is niets opgeslagen. Open het record in ZCBS. | Het record werd tussendoor zo gewijzigd dat de app het niet meer veilig kan opslaan (8.2). | Open in ZCBS en bewerk daar. |
| ZCBS weigerde het opslaan (==Sorry:N==). Er is niets opgeslagen. | ZCBS wees het opslaan af. | Controleer je invoer op bijzondere woorden of tekens; meld het getal aan de beheerder. |
| ZCBS vindt dat er te veel verzoeken zijn. Wacht even en probeer het opnieuw. | Te veel verzoeken. | Even wachten. |
| Geen verbinding met ZCBS of onverwachte fout: ... Controleer in ZCBS of het record is opgeslagen. | Netwerkfout of onverwachte fout. De opslag kan toch gelukt zijn, daarom doet de app niet automatisch een tweede poging. | Open het record in ZCBS en kijk of je wijziging erin staat. Zo niet, probeer opnieuw. |
| Opgeslagen, maar "Veld" is nu "..." in plaats van "...". Controleer het record in ZCBS. (status fout) | ZCBS heeft een veld anders bewaard dan verstuurd. | Controleer en corrigeer het record in ZCBS; meld het aan de beheerder. |
| Het record kon niet teruggelezen worden (...). Controleer in ZCBS of het is opgeslagen. (status fout) | De terugcontrole mislukte. | Controleer in ZCBS. |
| Er is geen conflict meer. | Je koos bij het conflict, maar de gegevens waren al verwerkt (bijvoorbeeld na het opnieuw laden). | Open het record opnieuw. |
| Open het record eerst. | Je probeert een record op te slaan dat de app niet geopend heeft (bijvoorbeeld na een herstart van de app). | Open het record via de lijst (**Bewerk**). |
| Onbekend record N. | Het record staat niet in de lijst van deze sessie. | Kies de records opnieuw. |

### 12.5 Je sessie is verlopen en opnieuw inloggen lukt niet

De melding is: "Je sessie bij ZCBS is verlopen. Log opnieuw in" (bij opslaan: "...; je invoer blijft bewaard"). De app stuurt je daarbij naar de startpagina.
Uit de code gelezen: omdat de app jou nog als ingelogd beschouwt, gaat de startpagina direct door naar de keuze van de collectie. Probeer dan eerst
gewoon het record opnieuw te openen of op te slaan (misschien was het een tijdelijke storing). Werkt dat niet, dan is het wachtwoord waarschijnlijk
gewijzigd of de verbinding weg: gebruik **Uitloggen**, log opnieuw in en kies de records opnieuw. Let op: **Uitloggen wist de gekozen records en je
nog niet opgeslagen invoer** (2.3).

### 12.6 Overige situaties

| Situatie | Oorzaak | Wat te doen |
|---|---|---|
| Na een herstart van de app is de lijst weg. | De gekozen records en statussen staan alleen in het geheugen. Opgeslagen records staan wel in ZCBS. | Opnieuw inloggen (gebeurt vanzelf als het cookie nog geldt) en de records opnieuw kiezen. |
| Een knop **Pdf**/**Afbeelding** ontbreekt. | De pdf of afbeelding staat (nog) niet op de site, of ontbrak toen de records werden gekozen. | Kies de records opnieuw nadat de bestanden zijn geüpload. |
| De titel in de lijst is leeg of staat op "(geen titel)". | Het record heeft nog geen titel, of was vergrendeld toen het werd gekozen. | Normaal; na het openen staat de actuele titel erin. |
| Het voorbeeldscherm is niet te vinden. | Zie hoofdstuk 6. | - |
| Het volgende record springt naar een record op "fout" of "niet veilig". | "Volgende" is elk nog niet opgeslagen record. | Gebruik daar **Open in ZCBS** of **Doe ik later**. |

---

## 13. Woordenlijst

| Woord | Betekenis |
|---|---|
| **ZCBS** | Het archiefsysteem van de Historische Kring Heemskerk waarin de records staan. |
| **Collectie** | Een onderdeel van ZCBS met eigen records en eigen rechten (archief, beeldbank, ...). |
| **Record** | Eén beschrijving in ZCBS met een nummer, bijvoorbeeld 11934. |
| **Rechten in ZCBS** | Per collectie bepaalt ZCBS wie mag werken. De app toont de collecties waar jouw inlog is gelukt. |
| **Vergrendeling (lock)** | ZCBS houdt een record 10 minuten voor andere gebruikers dicht zodra iemand het bewerkformulier opent. |
| **Conflict** | Een ander heeft hetzelfde veld gewijzigd terwijl jij bezig was. |
| **Niet veilig** | De app kan het record niet betrouwbaar opnieuw opbouwen en weigert het te bewerken. |
| **Latin-1** | Het tekensysteem van ZCBS. Letters met accenten passen erin, het euroteken niet. |
| **Meerkeuze** | Een veld waarin je meer dan één waarde kiest (Ctrl of Cmd ingedrukt houden). |
| **Anders / (namelijk)** | De keuze `== Anders ==` met een eigen tekst in het veld erachter. |
| **Teruglezen** | Na het opslaan haalt de app het record opnieuw op en controleert hij of het klopt. |
| **Workflow-logboek** | Het logboek (hkh-admin) waaraan de app meldt wat er gebeurt. |
| **Sessie** | De ZCBS-inlog van één gebruiker in de app, met zijn gekozen records. |
| **Dagcode** | De waarde die ZCBS bij "ja" (Plaatsing op internet) zelf bepaalt; de app neemt hem uit het formulier over. |

---

### Bijlage: onbevestigde punten en afwijkingen tussen code en ontwerpdocumenten

- Alles over ZCBS-gedrag (10 minuten vergrendeling, sessieduur, e-mail, weigerwoorden) is uit de code en de ontwerpdocumenten gehaald en lokaal getest,
  maar **niet bevestigd op de echte server**.
- Het voorbeeldscherm bestaat, maar er staat geen knop op het bewerkformulier die ernaartoe leidt (hoofdstuk 6).
- De startpagina zegt dat de app de inloggegevens "nergens opschrijft"; er is een versleuteld cookie van 30 dagen (2.1).
- De ontwerpdocumenten noemen een automatische uitlog na 4 uur stilstand, een pauze na drie mislukte inlogpogingen, een status "ontbreekt", een knop
  "Annuleren" en dat er maar één record tegelijk geopend wordt. Dat is in de code niet te vinden (hoofdstuk 4, 9).
- De melding bij een verlopen sessie belooft "je invoer blijft bewaard", terwijl **Uitloggen** (dat nodig kan zijn) de invoer wist (12.5).
- Het groene bericht zegt dat "de beheerder" een e-mail krijgt; volgens de ontwerpdocumenten gaat de mail bij elke opslag naar een archief-adres en
  alleen bij een nieuwe "Anders"-waarde naar de beheerder.

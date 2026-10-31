# ZCBS bij HKH: wat we gevonden hebben en een voorstel voor een wrapper

Concept van 22 oktober 2026. Dit document bundelt wat de analyse van alle bestanden heeft opgeleverd, beschrijft hoe de
medewerkers van HKH nu werken, en doet een voorstel voor een nieuwe versie van de workflow die als **wrapper om ZCBS heen** draait.

**Bronnen.** De ZCBS-handleidingen in deze map (technische documentatie 4.0, images.exe 3.3, pdf2zcbs 1.16), de download van de
server in `docs/server/` (lokaal, niet in git: databases van alle zeven collecties, het hoofdprogramma `objecten.pl`, de configs
van archief en beeldbank, `images.log`, `backup.log`, `downloads.txt`, de bestanden van Images 2.0 en voorbeeldbestanden) en de
testsessie van 19 oktober 2026 (stappenplan van Carla, een images.exe-log, de uitvoer van pdf2zcbs). Beveiligingsbevindingen
staan apart in `docs/server/beveiligingsbevindingen.md` (lokaal, niet in git).

**Hoe zeker is het.** Wat uit de bestanden of de code volgt, staat als feit. Wat is afgeleid uit tijdstippen of uit een stuk
code zonder het uit te proberen, staat als *afgeleid*. Er is niets uitgeprobeerd op de live server.

## Samenvatting

1. ZCBS is een Perl/CGI-programma met platte tekstbestanden. HKH heeft zeven collecties, elk met een masterdatabase en een
   updatebestand. Het hoofdprogramma is voor alle collecties hetzelfde.
2. Een nieuw of gewijzigd record komt als complete regel achteraan `updates.txt`. ZCBS schrijft dezelfde regel in een cumulatieve
   back-up en mailt de beheerder. De master wordt pas bij een merge bijgewerkt.
3. Archief en beeldbank krijgen records in batches. Bidprent en library krijgen ze met de hand, één voor één.
4. Records van een batch: volgens Robbert en het stappenplan laat Carla ze door images.exe aanmaken (stap D, antwoord J). Een
   eerdere afleiding uit de aanmaaktijden, dat ze sinds februari 2026 `NEWRECORD` gebruikt, is niet bevestigd. De database laat
   zien dat alle records sinds 2022 het stempel `system` hebben en meestal een lege titel, terwijl de handleiding (2022) voor
   images.exe een titel "Een nog niet beschreven record (datum)" noemt (7 records in 2022, 1 in 2026). Dat past niet eenduidig bij
   één van beide, en de nieuwere images.exe-versie kan het anders doen.
5. Recordnummers zijn per collectie anders opgebouwd. Archief, beeldbank en bidprent hebben doorlopende nummers van vijf
   cijfers, library van vier, objecten van één tot vier zonder voorloopnullen, en artikelen en transcripties tien cijfers.
6. images.exe draait bij HKH in drie versies naast elkaar. Er is een nieuwere versie beschikbaar.
7. Inloggen kan vanaf elk IP-adres met een naam en wachtwoord. Wachtwoorden staan leesbaar in `permit.lst`.
8. De wrapper praat met ZCBS alleen over HTTP, gebruikt de login van de gebruiker, schrijft nooit in de databasebestanden en
   laat ZCBS de bron blijven. Tekst, pdf en afbeeldingen gaan via (s)FTP of images.exe.
9. De testversie `archief2.pl` schrijft wijzigingen in een eigen `updates2.txt`, zonder back-up en zonder mail. Dat is een veilige
   oefenomgeving voor het bewerken van records.
10. Voorstel voor de volgorde: eerst de importflow met controles bij elke stap (grotendeels gebouwd), dan een alleen-lezen
    ZCBS-client, dan records bewerken op de testversie, en pas daarna op productie.

---

# Deel A. Bevindingen

## A1. Hoe ZCBS is opgebouwd

| Wat | Waar |
|---|---|
| Startscript per collectie, twee regels | `/cgi-bin/<collectie>.pl`, bijvoorbeeld `$ZCBS = 2; require "./objecten.pl";` |
| Hoofdprogramma voor alle collecties | `/cgi-bin/objecten.pl` (ruim 29.000 regels Perl, datum 26-09-2026) |
| Testversie van het hoofdprogramma | `/cgi-bin/objecten2.pl` via `<collectie>2.pl` |
| Configuratie per collectie | `/cgi-bin/<collectie>/misc/cfg-cbs.pl` en `layout.html` |
| Master en updates | `/cgi-bin/<collectie>/<collectie>.txt` en `updates.txt` |
| Cumulatieve back-up van alle wijzigingen | `/cgi-bin/<collectie>/backup1/upd-cumm.txt` |
| Gebruikers en rechten | `/cgi-bin/misc/permit.lst`, centraal voor alle collecties |
| Afbeeldingen small en large | `public_html/<collectie>/small` en `large` |
| Afbeelding 100pct | `cgi-bin/<collectie>/100pct`, dus beveiligd |
| Tekst en pdf | `public_html/<$OCR_MAP>` en `public_html/<$PDF_VIEWER>`, voor archief `archief/ocr` en `archief/pdf` |

- De tekenset is ISO-8859-1. De master van archief bevat toch 40 records met UTF-8-resten, zichtbaar als `Ã©` in plaats van é.
- Een record bestaat uit **35 tab-gescheiden velden**. Veld 0 en 1 zijn de instelling (`HKH`, `997`), veld 2 het recordnummer.
  Veld 32 is de aanmaakstempel, veld 33 de datum van beschrijven, veld 34 de laatste wijziging. Een stempel heeft de vorm
  `jjmmdd-uu:mm|naam`. Wat de overige velden betekenen staat per collectie in de config.
- ZCBS leest bij elke zoekopdracht eerst de master en dan `updates.txt`. Per recordnummer wint de laatste regel. Een merge
  voegt ze samen en kan alleen door een beheerder met recht 1+ worden gedaan.
- Veld 27, de originele bestandsnaam, is in archief bij geen enkel record gevuld.

## A2. De zeven collecties in cijfers

Peildatum: de download van 21 en 22 oktober 2026, master en updates samengevoegd.

| Collectie | Records | Nummers | Cijfers | Zonder beschreven-datum | Updatebestand sinds |
|---|---|---|---|---|---|
| archief | 1908 | 10000 tot 11933 | 5 | 73, bijna 4 procent | 7 oktober 2026 |
| beeldbank | 12088 | 10000 tot 25632 | 5 | 4725, 39 procent | 14 oktober 2026 |
| bidprent | 1342 | 10001 tot 16464 | 5 | 946, 70 procent | 2 februari 2026 |
| library | 1652 | 1001 tot 5396 | 4 | 13, 1 procent | 25 maart 2026 |
| objecten | 1733 | 1 tot 9100 | 1 tot 4 | 681, 39 procent | 26 november 2025 |
| artikelen | 525 | 1989040101 tot 2026044040 | 10 | 24, 5 procent | geen bestand |
| transcripties | 5197 | 1032250999 tot 1811020701 | 10 | 4057, 78 procent | geen bestand |

- De kolom "sinds" is de eerste datum in `updates.txt`. Dat is ongeveer het moment van de laatste merge.
- Artikelen en transcripties hebben geen `updates.txt`. Daar is sinds de laatste merge niets gewijzigd.
- Artikelen en transcripties hebben geen doorlopende nummers. Ze lijken op een datum of jaar met een volgnummer, maar dat is niet
  nagegaan. Het idee van "hoogste nummer plus één" geldt alleen voor archief, beeldbank, bidprent en library.
- Archief heeft 26 gaten in de reeks. Het hoogste nummer is 11933, en tot en met 99999 zijn er nog ruim 88.000 over. Op het tempo
  van 2026 is dat voor ongeveer 120 jaar, en bij 25 procent groei per jaar voor ongeveer 15 jaar.
- Het aantal cijfers staat in de config als `$MAX_OBJECT`. Archief en beeldbank hebben `99999`. De overige configs zijn nog niet
  gedownload. Paragraaf 10.3 van de technische documentatie beschrijft hoe je een cijfer toevoegt.

### Keuzelijsten in archief

Vijf velden hebben een keuzelijst in de config: type publicatie, uitgever, oorspronkelijk archief, thema en plaats. Thema is een
meervoudige keuze.

- In de database staat de tekst van de gekozen optie, geen code. Een waarde buiten de lijst is geldig, via de optie `== Anders ==`.
- Het formulier verstuurt twee velden per lijst, bijvoorbeeld `a24` en `a24b`. ZCBS vervangt `== Anders ==` door de vrije tekst.
- Meerdere keuzes worden bewaard als `waarde; waarde`. Items bevatten zelf komma's, dus nooit op een komma splitsen.
- Ruim 98 procent van de waarden is een lijstwaarde. Bij uitgever staan 36 verschillende vrije waarden, bijvoorbeeld notarissen en
  kranten. Dat zijn kandidaten voor de lijst, en dat is een wijziging in de config.

## A3. Wat ZCBS doet bij aanmaken en opslaan

Uit de code van `objecten.pl`:

- **Opslaan via het formulier.** Het formulier heeft de standen `new`, `edt`, `del`, `cop` en `clr`, via de parameter `upd_form`.
  De velden heten `a0` tot en met `a34`. ZCBS vergelijkt het oude en het nieuwe record. Alleen bij een verschil komt de volledige
  nieuwe regel achteraan `updates.txt`, met een bestandslock.
- **Cumulatieve back-up.** In productie gaat dezelfde regel ook naar `backup1/upd-cumm.txt`.
- **Mail.** In productie krijgt het adres uit `$EMAIL_TO` een mail met het hele record, de link naar de pagina en de bewerklink.
  Het onderwerp bevat de naam van de gebruiker. Is `$EMAIL_TO` leeg, dan is er geen mail. Archief heeft het ingesteld.
- **Stempels.** Veld 34 krijgt datum en naam. Veld 33 wordt alleen gezet als de lengte van het record meer dan `$BESCHREVEN`
  tekens verschilt, standaard 125.
- **Vergrendeling.** Een record dat wordt bewerkt, is tien minuten geblokkeerd voor andere gebruikers.
- **Hooks.** De config kan Perl-code meegeven die bij het opslaan velden aanpast. Archief heeft er geen, de beeldbank wel.
- **Verwijderen.** Het record krijgt `==DELETED==` in de titel en verdwijnt daarna uit de telling. Ook de afbeeldingen worden
  verwijderd als ze nergens anders in gebruik zijn.
- **`NEWRECORD <begin> <eind>`.** Vereist recht 1+. Maximaal 1000 records, getallen van exact `$MAX_OBJECT_LENGTH` cijfers, en een
  fout als een nummer al bestaat. Het record bestaat daarna alleen uit veld 0, 1 en 2 en de stempels in 32 en 34, met `system`
  als naam. De titel is leeg.
- **Nieuw record met de hand.** Het formulier vraagt eerst een vrij nummer uit een menu, of `anders`, en daarna de velden. Dat
  werkt met recht 1.
- **`LAST` en `LAST30`.** Sorteren alle nummers van master en updates, zonder verwijderde records, en tonen de hoogste. Dat is het
  hoogste bestaande nummer en niet het laatst aangemaakte. Records van `NEWRECORD` tellen mee, ook als ze nog leeg zijn.

### De testversie als oefenomgeving

`archief2.pl` roept `objecten2.pl` aan en werkt in testmodus. In die modus:

- worden wijzigingen niet in `updates.txt` geschreven, maar in `updates2.txt`;
- worden de cumulatieve back-up en de mail overgeslagen;
- leest ZCBS de productie-master en productie-updates plus `updates2.txt`, zolang er geen aparte testmaster is;
- werkt `NEWRECORD` niet.

Productie blijft dus ongemoeid, en de testwijzigingen verdwijnen door `updates2.txt` te verwijderen. Het bestand `objecten2.pl`
zat niet in de download en moet op de server bestaan. De afbeeldingsmappen en de map met tekst en pdf zijn wel gedeeld met
productie, dus voor uploads is dit geen veilige omgeving.

## A4. Zoeken: ocr-tekst en pdf

- Bij elke zoekopdracht met ocr aan doorzoekt ZCBS met `grep` alle `.txt`-bestanden in de ocr-map, en de mappen `ocr1` en `ocr2`
  als die bestaan. Er is geen index en geen merge nodig. De bestandsnaam is het recordnummer.
- Archief heeft `archief/ocr` zonder slash ingesteld. Daardoor staat op de homepage "Zoeken in OCR-tekst: Ja of Neen", standaard
  Ja. Zoeken op velden via het linkblok doorzoekt de tekst nooit.
- Het record moet bestaan. Een tekstbestand voor een nummer zonder record doet niets. Een fout nummer geeft een ander record
  meteen die treffers.
- De pdf-link op de objectpagina verschijnt pas als `archief/pdf/<nummer>.pdf` bestaat.
- Een scan zonder tekstlaag levert een tekstbestand met alleen de kop van pdf2zcbs, dus geen treffers. pdf2zcbs doet geen ocr.

## A5. Toegang en inloggen

- Gebruikers en rechten staan in één bestand: `/cgi-bin/misc/permit.lst`, met een eventuele lokale kopie per collectie. Per regel:
  collectie, recht, IP-adres, naam met eventueel een wachtwoord, en een eventueel bereik van recordnummers.
- **Rechten.** 1 is bewerken, 1+ is systeembeheerder met onder meer `NEWRECORD`, merge en het aanpassen van `permit.lst`, 3 is lezen
  van interne velden, 5 is extern.
- **IP-adres.** Staat het adres van de bezoeker in een regel, dan is de bezoeker automatisch die persoon, zonder wachtwoord.
  Bij `<ROUTER>` geeft het adres geen rechten en volgt een inlogscherm. Bij een IPv6-adres telt alleen het voorvoegsel.
- **Naam en wachtwoord.** Daarmee kan iemand vanaf elk adres inloggen, via het zoekwoord `LOGIN`. Een sessie vervalt na tien
  minuten zonder activiteit, standaard. Een fout wachtwoord geeft een externe gebruiker.
- Er is geen "onthoud dit adres". Alleen een beheerder past `permit.lst` aan, met FTP of via de beheerderspagina.
- Voor archief staan er 13 regels voor negen personen en twee gemeenschappelijke routerregels. Vier personen hebben geen echt
  IP-adres in hun regel en loggen dus altijd in met naam en wachtwoord.
- **Het inlogscherm is niet zichtbaar.** De publieke homepage heeft geen inlogknop en de publieke pagina's tonen geen bewerkknoppen. Je
  komt bij het inlogscherm door `LOGIN` in het zoekveld te typen, of `INLOGGEN`, `INLOG` of `USER_RESET`, of door
  `archief.pl?user=reset` te openen. Daarna heb je een regel met wachtwoord in `permit.lst` nodig. Wie er niet in staat, wordt een
  externe gebruiker.

## A6. De programma's

**images.exe** is een perl2exe-programma voor Windows.
- Het maakt small, large en 100pct van afbeeldingen, uploadt ze via FTP en kan lege records aanmaken. Het vraagt het bestandstype,
  de resolutie, of het moet uploaden, de instelling en het FTP-wachtwoord, de collectie, de submap, overschrijven, records aanmaken
  en het opruimen van mappen.
- Het schrijft per sessie een regel naar `images.log` op de server, met versie, aantal afbeeldingen, type, stappen en gebruiker.
- HKH draait versie 3.27 bij Carla, 3.34 bij Marcel en 3.62 bij Jan. Op de ZCBS-site staat 3.62 van 29 oktober 2025 als actueel.

**pdf2zcbs.exe**, versie 1.16 bij HKH, versie 1.17 is actueel.
- Haalt de tekstlaag uit een pdf en zet die om naar één zin per regel. Het maakt ook een small- en een large-jpg van pagina 1 en
  verplaatst de pdf naar `output\pdf`. Het wist `output\` bij elke start en verwerkt alle pdf's in zijn eigen map.
- Bij HKH staat een `pdf2zcbs.ini`. Het log meldt dat die is verwerkt, dus de vragen komen waarschijnlijk niet.

**Images 2.0** is een webversie van images.exe voor afbeeldingen, versie 1.1.6.14, in `zcbs_frontend`.
- Het verkleint in de browser, uploadt met HTTP naar een PHP-backend op de ZCBS-server en kan records in `updates.txt` zetten.
  Het heeft keuzes voor type, resolutie, map, dubbele afbeeldingen en records. Geen pdf, geen ocr.
- In `config.json` staat `FIRST_START`. Het lijkt dus niet ingesteld bij HKH. De PHP-backend zat niet in de download.

## A7. Beheer, back-ups en versies

- `backup.log` toont het laatste `backup.exe`-run op 1 januari 2024. Daarvoor waren het er ongeveer twee per jaar. Of er een
  andere back-up bestaat, bijvoorbeeld van de hoster, is niet bekend.
- Merges gebeuren door een beheerder met recht 1+. Wie het doet en hoe vaak is niet bekend. De eerste regel in elk updatebestand
  wijst op: archief 7 oktober, beeldbank 14 oktober, library 25 maart, bidprent 2 februari, objecten 26 november 2025.
- `downloads.txt` op de server noemt als actueel: objecten 4.16a, images.exe 3.62, Images 2.0 1.1.5, pdf2zcbs 1.17, backup 3.7. De
  publieke pagina toont onderaan echter versie 4.18s4, dus de server draait een nieuwere versie dan de downloadlijst noemt.

## A8. Overige opvallende dingen

- De 100pct-afbeelding in het voorbeeld is even klein als de small. De large-jpg uit pdf2zcbs is klein, ongeveer 650 bij 500.
  Een goede 100pct ontstaat zo niet. De pdf is dan het volwaardige document.
- `archief/11753.jpg` staat los in de verkeerde map.
- Op 14 oktober 2026 zijn 92 records aangemaakt, 11842 tot en met 11933, zonder dat er daarna een images.exe-sessie voor is gelogd.
  Voor de 104 records van 15 oktober 2025 is er ook geen sessie met dat aantal.
- Er staat een `phpinfo.php` en een `printenv.pl` op de server.

## A9. Wat zonder inloggen te lezen is

De publieke pagina's van archief laten zien wat een buitenstaander ziet, en dat is nuttig voor de wrapper omdat het zonder login kan.

- De homepage toont **Aantal publicaties op internet** en **Aantal publicaties op intranet**, plus **Laatste update** met datum en tijd,
  en onderaan de **versie** van het hoofdprogramma. Op 24 oktober 2026 stond daar 1838 en 1908, laatste update 21-10-2026 om 11:42,
  versie 4.18s4.
- Het aantal op intranet, 1908, is gelijk aan het aantal records in onze download. De download is dus actueel tot en met 21 oktober.
  Het verschil van 70 zijn records die niet op internet staan.
- Een record staat op `archief.pl?ident=10030`, met de velden, de afbeelding en een ingebouwde pdf-viewer als de pdf bestaat.
- Zoeken en lijsten gaan met parameters als `search`, `veld`, `display=list`, `istart`, `sort` en `inword`, bijvoorbeeld een zoekopdracht
  in één veld met `misc=12&a8a0=...&a8a1=...`. Een lijst toont 30 records per pagina.
- De ocr-zoekkeuze staat standaard op Ja, zoals de code aangaf.

Voor de wrapper betekent dat: het versienummer en de teller met de laatste update zijn zonder login uit te lezen en geschikt voor de
contracttest en om te zien dat er iets is gewijzigd. Interne velden en records die niet op internet staan blijven alleen met een login
zichtbaar.

---

# Deel B. Hoe de medewerkers van HKH nu werken

## B1. Archief: de importflow met scans

Dit is de werkwijze zoals die uit het stappenplan van Carla (19 oktober 2026), `images.log`, de database en de bestanden volgt.

1. **Scannen.** Ria en anderen zetten pdf's op het netwerk, in `K:\scabslelly`, elk in een eigen map. Carla krijgt een melding.
2. **Records aanmaken.** Volgens Robbert en het stappenplan doet images.exe dit zelf (stap D). Eerdere afleiding uit de aanmaaktijden:
   sinds eind februari 2026 zouden de records ongeveer tien minuten vóór images.exe met `NEWRECORD` zijn aangemaakt. Niet bevestigd,
   zie punt 4 hierboven.
3. **Hernoemen.** De pdf's krijgen het recordnummer als naam, in `c:\zcbs\invoer`.
4. **pdf2zcbs.** `copy invoer\*.*` naar `c:\zcbs\pdf2zcbs` en het programma draaien. Per pdf komen er in `output\` een tekstbestand,
   de pdf zelf, een small en een large.
5. **images.exe.** Niet de pdf's maar de large-jpg's gaan naar `c:\zcbs\images\images`. In images.exe: bestandstype `a`, resolutie 6,
   uploaden naar de webserver ja, records aanmaken nee. Daarna een controle met `dir` op `small`, `large` en `100pct`.
6. **Tekst en pdf naar de server.** Met Total Commander en sftp naar `/domains/historischekringheemskerk.nl/public_html/archief/ocr`
   en `.../archief/pdf`. De afbeeldingen zelf zet images.exe op de server met FTP.
7. **Back-up en opruimen.** De 100pct naar `c:\zcbs\backup_100pct`, de invoer naar `c:\zcbs\backup_invoer_org`. Dan `output`,
   `small`, `large`, `100pct` en de inhoud van `images\images` weg, plus het images.exe-log in `c:\zcbs\images`.
8. **Beschrijven.** In ZCBS vult iemand per record de velden in. Bij elke opslag stuurt ZCBS een mail naar de beheerder.

Wat verder opvalt:
- **Opnieuw draaien is gewoon.** Op 19 van 76 dagen was er meer dan één images.exe-sessie voor archief, bijna altijd met
  hetzelfde aantal afbeeldingen. In 9 sessies moest opnieuw worden ingelogd.
- **Batchgrootte.** Mediaan 11 afbeeldingen per sessie, maximaal 108. Zie de tabel verderop.
- **Drie versies.** Carla werkt met images.exe 3.27 en Marcel met 3.34. Jan gebruikte op 13 oktober 3.62.
- **images.exe uploadt in vrijwel elke sessie.** FTP werkt dus bij HKH.

## B2. De andere collecties

- **Beeldbank.** Kleinere, vaker voorkomende batches: mediaan acht nieuwe records op een actieve dag, op 36 dagen in twaalf
  maanden. Alle nieuwe records komen van `system`. De collectie wordt het meest bewerkt.
- **Bidprent.** Twee medewerkers maken de records met de hand in het ZCBS-formulier, met een titel erbij. Daarna uploaden ze de ene
  afbeelding van dat bidprentje met images.exe, gemiddeld zeven minuten later. Sinds 2025 zijn er 94 nieuwe records en 371
  images.exe-sessies met één afbeelding per sessie. Er wordt dus ook opnieuw geüpload. images.exe maakt hier geen records aan.
  *Afgeleid* uit tijdstippen en namen in de database en in `images.log`.
- **Library.** Twee medewerkers maken de records met de hand. In veld 15 staat bij bijna alle records het pad van de afbeelding.
  De afbeeldingen komen in aparte sessies van gemiddeld vier tegelijk, meestal niet op dezelfde dag.
- **Objecten.** Kleine batches, een paar dagen per jaar, en bulkbewerkingen, bijvoorbeeld 120 records in april 2026.
- **Artikelen.** Twee keer per jaar een klein pakket, 9 in november 2025 en 12 in mei 2026.
- **Transcripties.** Sinds 2022 is er niets meer gebeurd.

## B3. Tempo en patronen

Cijfers van de laatste twaalf maanden tot en met 22 oktober 2026, uit de aanmaak- en wijzigingsstempels.

| Collectie | Nieuwe records | Op hoeveel dagen | Records met een wijziging na de aanmaakdag | Opslagen per week in `updates.txt` |
|---|---|---|---|---|
| archief | 631 | 16 | 594 | 71 |
| beeldbank | 427 | 36 | 3770 | 349 |
| bidprent | 61 | 46 | 22 | 1,5 |
| library | 31 | 16 | 495 | 11 |
| objecten | 37 | 3 | 186 | 8 |
| artikelen | 21 | 2 | 25 | geen bestand |
| transcripties | 0 | 0 | 0 | geen bestand |

- Het aantal records met een wijziging is een ondergrens. Veld 34 bewaart alleen de laatste wijziging, dus een record dat vaker is
  bewerkt telt één keer. De volledige geschiedenis staat in `backup1/upd-cumm.txt`, dat niet is gedownload.
- De kolom met opslagen per week komt uit het venster van `updates.txt`, dus uit de periode sinds de laatste merge. Dat venster is
  per collectie anders: 15 dagen bij archief, 6 dagen bij beeldbank en 254 dagen bij bidprent.
- Per collectie bewerken vier tot zes personen.
- Archief groeide in aantal nieuwe records per jaar als volgt: 140 in 2022, 265 in 2023, 388 in 2024, 524 in 2025 en 591 in 2026 tot nu toe.

## B4. Knelpunten

- Veel handwerk: hernoemen, kopiëren tussen mappen, Total Commander voor tekst en pdf, back-ups en opruimen. Een fout in het
  nummer is bij tekst en pdf direct zichtbaar voor bezoekers.
- Verschillende versies van images.exe, en een nieuwere beschikbaar.
- Records worden aangemaakt voordat de bestanden er zijn. Op 14 oktober 2026 liggen er 92 lege records zonder afbeeldingen.
- De ZCBS-bewerkpagina is gedateerd, en dat is de reden om een eigen bewerkscherm te willen.
- Back-ups: het laatst gelogde `backup.exe`-run is van 1 januari 2024.
- Wachtwoorden en rechten staan in een tekstbestand dat met de hand wordt onderhouden.
- Veel records zijn nog onbeschreven: 39 procent bij beeldbank, 70 procent bij bidprent, 78 procent bij transcripties.

---

# Deel C. Voorstel: een nieuwe versie als wrapper om ZCBS

## C1. Uitgangspunten

De wrapper werkt onder deze afspraken. Ze komen uit de situatie bij HKH en uit wat ZCBS toestaat.

1. **ZCBS blijft de bron.** De records staan in ZCBS en blijven daar. Iedereen kan nog steeds alles rechtstreeks in ZCBS doen,
   ook als de rest van HKH de nieuwe flow gebruikt. De app moet wijzigingen die buiten hem om gebeuren aankunnen.
2. **De app draait op een aparte Windows-pc.** Daarop staan ook pdf2zcbs.exe en images.exe, met hun mappen.
3. **Alleen HTTP naar ZCBS.** Records lezen en schrijven gaat via de `.pl`-bestanden van ZCBS, met formulieren en commando's.
4. **Nooit de databasebestanden aanpassen.** De app leest en schrijft nooit `<collectie>.txt`, `updates.txt`, `upd-cumm.txt`,
   `permit.lst` of de config. Tekst, pdf en afbeeldingen mogen wel via (s)FTP of images.exe.
5. **Eigen login per gebruiker.** De gebruiker geeft zijn ZCBS-naam en wachtwoord op. ZCBS bepaalt zijn rechten, en zijn naam
   komt in de stempels en mails. Er is geen gedeeld account voor de app.
6. **Kleine, controleerbare stappen.** Elke stap toont vooraf wat hij doet, controleert achteraf of de bestanden er zijn, en kan
   worden overgeslagen of op de oude manier worden afgemaakt.
7. **Fouten zijn zichtbaar.** Een fout geeft een melding. Er mag niets stilletjes mislukken of stilletjes iets overschrijven.

## C2. Architectuur

```
Browser van de medewerker
   |  HTTPS, login met ZCBS-naam en wachtwoord
   v
HKH-workflow, een jar op de Windows-pc
   |  modules: zcbs-client, batch en testrun, exerunner, sftp, settings, bewerkscherm
   |  lokaal: pdf2zcbs.exe, images.exe, werkmappen, eigen opslag voor batchstatus en audittrail
   |
   |--- HTTP naar ZCBS: /cgi-bin/<collectie>.pl
   |       inloggen, LAST, NEWRECORD, record lezen, formulier opslaan, export
   |--- FTP, door images.exe: small, large en 100pct
   |--- SFTP, apart account met beperkte rechten: archief/ocr en archief/pdf
```

De app slaat zelf alleen op wat ZCBS niet kent: de status van batches, een audittrail met wie wat deed, en zijn instellingen met
geheimen die niet in de browser terugkomen. Het huidige systeem heeft die onderdelen al deels: `settings`, `exerunner`, `sftp` en
`testrun`, zie `docs/specs.md`.

## C3. Een client voor ZCBS

Een aparte module, `zcbs`, doet alle HTTP-verkeer met ZCBS. Zo staat de afhankelijkheid van niet-gedocumenteerde pagina's op
één plek.

- **Inloggen.** Naam en wachtwoord gaan met een POST naar de collectie. Dat is nog niet uitgeprobeerd; de code leest ze als `user` en `pswd`. De client bewaart het cookie van ZCBS per gebruikerssessie,
  alleen in het geheugen. Een fout wachtwoord geeft een externe gebruiker met een melding, en de client moet dat herkennen en
  weigeren. Een sessie van ZCBS vervalt na tien minuten zonder activiteit, dus de app logt opnieuw in of houdt de sessie levend.
- **Operaties.** Het hoogste nummer via `LAST`. Records aanmaken via `NEWRECORD` voor gebruikers met 1+. Een record lezen. Een
  record opslaan en een nieuw record maken via het formulier, met de parameters `upd_form`, `ident` en de velden `a0` tot en met
  `a34`. Bulk lezen via het commando `EXPORT`. Verwijderen bieden we niet aan.
- **Elke schrijfactie wordt gecontroleerd.** Eerst een overzicht van de wijzigingen, dan opslaan, dan het record teruglezen en
  vergelijken met wat is verstuurd. Bij een verschil een duidelijke fout.
- **Gelijktijdig werken.** Voor het opslaan vergelijkt de app het stempel in veld 34 met dat van het moment dat de gebruiker het
  record opende. Is het veranderd, dan volgt een melding en wordt er niet overschreven.
- **Vergrendeling.** ZCBS blokkeert een record tien minuten voor anderen als het wordt geopend om te bewerken. *Afgeleid*. De app
  opent de bewerkpagina dus alleen als de gebruiker echt gaat bewerken, en leest voor weergave via een andere weg.
- **Versie en wijzigingen zonder login.** De homepage toont zonder inloggen het versienummer en de teller met de laatste update. Die gebruiken
  we voor de versiecontrole en om te zien of er iets is veranderd, voordat we gegevens ophalen.
- **Fragiliteit.** De pagina's zijn HTML zonder contract en kunnen bij een versie-update veranderen. Daarom: parsers klein houden,
  contracttests tegen bewaarde voorbeeldpagina's en tegen de testversie, en een controle op de versie van `objecten.pl`.
- **Veilig oefenen.** `archief2.pl` schrijft naar `updates2.txt` zonder mail en zonder back-up. Daarmee testen we alles wat records
  bewerkt. `NEWRECORD` en uploads zijn daar niet veilig te oefenen.

Nog uit te zoeken, op de testversie: de precieze parameters en antwoorden van elk onderdeel, of het commando `EXPORT` ook de
interne velden geeft, en waar een geëxporteerd bestand terechtkomt en wie het kan zien.

## C4. Records bewerken en aanmaken, alle velden

Een modern bewerkscherm op ZCBS-data.

- **Velden uit ZCBS.** De namen en de keuzelijsten halen we uit het bewerkformulier of de config, nooit vast in de app. Jan kan de
  lijsten wijzigen.
- **Alleen-lezen.** Veld 0, 1, 2, 32, 33 en 34 zijn niet bewerkbaar. ZCBS zet de stempels zelf.
- **Keuzelijsten.** Een combinatieveld: kies uit de lijst of typ met suggesties. Een bestaande waarde die niet in de lijst staat, tonen
  we als "Anders" met die tekst, zodat opslaan hem niet wist. Opslaan stuurt de lijstwaarde of `== Anders ==` met de tekst in
  het `b`-veld. Meerdere keuzes gaan als `waarde; waarde`.
- **Tekens.** Latin-1. Tekens daarbuiten, zoals € en gekrulde aanhalingstekens, omzetten of weigeren. Geen UTF-8 in de database.
  Hoe tabs, regeleinden en html in velden worden opgeslagen, is nog uit te zoeken.
- **Subvelden.** Sommige velden hebben een subveld, zie hoofdstuk 4.1.3 van de technische documentatie. Dat is nog niet onderzocht.
- **Hulpfuncties in het scherm.** Een lijst "nog te beschrijven", dat wil zeggen een lege titel of een lege beschrijfdatum, met de
  volgende onbeschreven record als knop. Een koppeling terug naar de pagina in ZCBS.
- **Nieuwe records met de hand**, zoals bidprent en library nu doen. Het formulier vraagt een vrij nummer en daarna de velden, met
  recht 1. De app stelt het nummer voor via `LAST` en laat de gebruiker bevestigen.
- **Audittrail.** De app bewaart wie wat wanneer heeft opgeslagen, omdat ZCBS in zijn stempel en mail alleen de ingelogde naam heeft.
  Dat is al de echte persoon, maar de app houdt een eigen overzicht voor het geval een mail ontbreekt.
- **Mail.** ZCBS mailt de beheerder bij elke opslag. Bij een bulkbewerking zijn dat evenveel mails. Dat moet bekend zijn bij de
  beheerder.

## C5. De importflow voor archief en beeldbank

De flow volgt het stappenplan van Carla. Elke stap wordt apart goedgekeurd.

| Stap | Wat | Status in de code |
|---|---|---|
| 0 | Controleren dat de nummers van de batch nog niet bestaan (images.exe maakt de records aan). | Nog niet. |
| 1 | Hernoemen naar recordnummers, in een werkmap. | Gebouwd |
| 2 | pdf2zcbs één keer voor de hele batch. Controle op tekst, pdf, small en large. | Gebouwd |
| 3 | images.exe met de large-jpg's. Type `a`, resolutie uit de instelling. Uploaden en records staan standaard uit. | Gebouwd. Het vinkje om te overschrijven ontbreekt nog. |
| 4 | Tekst en pdf per sftp naar `<collectie>/ocr` en `<collectie>/pdf`. Nooit overschrijven. | Gebouwd |
| 5 | Back-up van de 100pct, daarna opruimen van de mappen van de programma's. | Gebouwd |
| 6 | Archiveren van de originelen. | Gebouwd |
| 7 | Beschrijven, via een link naar ZCBS of het bewerkscherm. | Nog niet |

- **Nummering.** Het aantal cijfers hoort bij de collectie en is niet in de app vast te leggen. ZCBS noemt het aantal in zijn
  foutmelding. Per collectie een instelling, met controle bij stap 0. De nummers worden altijd met voorloopnullen opgevuld.
- **Bestaan van records.** Voor stap 2 controleert de app dat de records van de batch bestaan en leeg zijn.
- **Opnieuw draaien.** Een derde vinkje, standaard uit, om bestaande afbeeldingen bij images.exe te overschrijven, want
  hergebruik van een batch komt vaak voor.
- **Batchstatus bewaren.** Nu staat de status van een run in het geheugen. Voor het echte gebruik moet die op schijf, met een
  batch-ID, per bestand de status en het koppelnummer van origineel naar record.
- **Collecties met andere nummers.** Objecten heeft nummers zonder vaste lengte, artikelen en transcripties hebben nummers die
  uit het document lijken te komen. Die doen we niet in de eerste versie.

## C6. Uploaden van bestanden

- **Afbeeldingen** blijven via images.exe lopen, op de Windows-pc zelf. Het FTP-wachtwoord staat bij de instellingen van de app,
  als verborgen waarde in een antwoordregel. Het is niet bevestigd of dat hetzelfde wachtwoord is als dat van sftp.
- **Tekst en pdf** gaan via sftp, met een eigen account dat alleen bij `archief/ocr` en `archief/pdf` kan. De app overschrijft niets,
  verwijdert niets, maakt geen mappen aan, controleert eerst alles, zet een tijdelijke naam, hernoemt en controleert de grootte.
- **Eigen verkleinen en uploaden** van afbeeldingen, bijvoorbeeld voor bidprent met één afbeelding per record, is een apart
  project. De 100pct staat in een beveiligde map, dus het account zou daar ook bij moeten kunnen. Een alternatief voor later is
  Images 2.0.

## C7. Inloggen en rechten in de wrapper

- De gebruiker logt in de app in met zijn ZCBS-gegevens. De app geeft die door aan ZCBS. Zo is er geen eigen gebruikersbestand nodig
  voor het bewerken.
- De app bewaart wachtwoorden niet. Wachtwoord en ZCBS-sessie blijven in het geheugen, zolang de sessie van de gebruiker duurt.
- Voor de batchflow met exe's en uploads is een eigen regel nodig over wie dat mag, bijvoorbeeld alleen bepaalde namen of 1+.
- ZCBS-wachtwoorden kunnen kort zijn, en ZCBS beperkt foute pogingen niet. Het loginscherm van de app mag daarom geen manier worden
  om te raden: beperk pogingen per gebruiker en per adres, en geef geen verschil in melding tussen een onbekende naam en een fout
  wachtwoord.
- De app moet versleuteld bereikbaar zijn, of alleen via de pc zelf. Nu draait hij op gewoon HTTP, zonder login, op alle netwerkadressen.
  Dat moet veranderen voordat gebruikers hun wachtwoord invoeren.
- Mensen die alleen op IP-adres zijn aangemeld, zonder wachtwoord, kunnen niet inloggen via de app.

## C8. Testen en veiligheid

1. **Een testcollectie aanmaken.** Gerard van Nes antwoordde dat HKH die zelf moet maken, met een aparte map aan beide kanten van
   `cgi-bin`, en dat images.exe die dan als aparte beeldbank herkent. Dat is de oefenplek voor uploads en `NEWRECORD`.
2. **`archief2.pl`** voor het bewerken van records, zie A3.
3. **Contracttests** voor elke aanroep naar ZCBS, met een controle van het versienummer en een melding bij afwijkingen.
4. **Terugvaloptie.** Tot en met het opruimen blijft alles op schijf staan zoals bij de handmatige werkwijze. Elke stap kan worden
   overgeslagen of met de hand worden afgemaakt.
5. **Vóór het eerste echte gebruik** moet er een back-up zijn van de collectie en van `permit.lst`, bijvoorbeeld door de hoster.

## C9. Fasering

| Fase | Inhoud | Klaar als |
|---|---|---|
| 0 | Voorwaarden: back-up gecontroleerd, testcollectie, versleutelde toegang en login voor de app, sftp-account met beperkte rechten, Gerard en Jan geïnformeerd, `objecten2.pl` bestaat. | Alle punten afgevinkt |
| 1 | De importflow in het echte archief, stap voor stap, met stap 0 met de hand. Het runlog van de echte exe's bekijken. | Een batch volledig zonder terugval |
| 2 | ZCBS-client alleen-lezen: inloggen, `LAST`, een record tonen, `EXPORT`. Contracttests. | Het hoogste nummer klopt met ZCBS |
| 3 | Records bewerken en nieuw aanmaken op `archief2.pl`, daarna op archief. Met terugleescontrole en conflictcontrole. | Tien records bewerkt, terugleescontrole altijd gelijk |
| 4 | Batchoverzicht met opslag op schijf, scanmappen, `NEWRECORD` vanuit de app, links naar ZCBS en de status beschreven. | De schermen van het UX-ontwerp werken met echte gegevens |
| 5 | Overige collecties, eigen verkleinen en uploaden of Images 2.0, en uiteindelijk artikelen en transcripties. | Per collectie een eigen acceptatiecontrole |

## C10. Wat we niet doen

- Geen enkele lees- of schrijfactie op de databasebestanden of `permit.lst` buiten ZCBS om.
- Geen verwijderknop voor records. Dat blijft in ZCBS.
- Geen wachtwoorden van gebruikers bewaren.
- Geen gedeeld ZCBS-account voor de app.
- Geen code van ZCBS overnemen of aanpassen. De gebruiksvoorwaarden staan configuratie toe en geen aanpassing van het programma. Een
  eigen programma op de eigen gegevens mag. Informeer Gerard daarover.
- Geen aanpassing van de ZCBS-config zonder Jan of Gerard.

## C11. Risico's

| Risico | Gevolg | Maatregel |
|---|---|---|
| Een ZCBS-update verandert het formulier | Opslaan mislukt of verandert een veld | Contracttests, versiecontrole, terugleescontrole |
| De app overschrijft een wijziging van iemand anders | Gegevensverlies | Stempel in veld 34 vergelijken voor het opslaan |
| Een verkeerd nummer bij tekst of pdf | Treffers bij het verkeerde record, direct publiek | Nummercontrole, nooit overschrijven, controle vooraf |
| Wachtwoord van een gebruiker lekt via de app | Schrijfrechten vanaf elk adres | Alleen geheugen, versleutelde toegang, geen logging |
| Sessie van ZCBS vervalt midden in een bewerking | Verloren invoer | Opnieuw inloggen zonder de invoer te verliezen |
| Bulkbewerking geeft veel mails | Beheerder overspoeld | Vooraf afspreken, of de mail tijdelijk uitzetten via Jan |
| Een foutieve regel in de database | Direct zichtbaar voor iedereen | Nooit zelf schrijven; alleen via het ZCBS-formulier |

---

# Deel D. Open vragen en onzekerheden

**Aan Gerard van Nes of de ZCBS-groep**
- Welke parameters horen bij `LAST`, `EXPORT` en het opslaan van het formulier, en mogen we die gebruiken?
- Hoe worden tabs, regeleinden en html in velden opgeslagen, en wat doen de subvelden?
- Beperkt ZCBS het aantal inlogpogingen, en wat doet de header `X-Forwarded-For` bij de hoster?
- Bestaat `objecten2.pl` op de server, en mag HKH daarmee oefenen?
- Wat zijn de verschillen tussen images.exe 3.27 en 3.62?
- Mogen we de bron van images.exe inzien, die als bijlage bij record 42 in de documentatiebank staat?

**Aan HKH**
- Waarom is Carla in februari 2026 overgestapt op `NEWRECORD` vooraf?
- Wie doet de merges en hoe vaak? Is er een andere back-up dan `backup.exe`?
- Wat gebeurt er met de 92 records van 14 oktober en de 104 van 15 oktober 2025 zonder images.exe-sessie?
- Is Images 2.0 ooit gebruikt?

**Nog niet gedownload en gewenst**
- De configs van bidprent, library, objecten, artikelen en transcripties.
- `backup1/upd-cumm.txt` per collectie en een lijst van de bestanden in `backup1`, voor de volledige wijzigingsgeschiedenis en de mergedata.
- De startscripts van de andere collecties.

**Niet gecontroleerd**
- Hoe de bewerkpagina precies werkt, en of openen de record vergrendelt.
- Of ocr-tekst van een record dat niet op internet staat, extern zichtbaar kan zijn.
- Of het sftp- en het FTP-wachtwoord gelijk zijn.
- ~~Wat images.exe vraagt bij het kiezen van een submap bij uploaden.~~ Gemeten op 30 oktober 2026: "Geef een ENTER/RETURN of indien images naar een nieuwe submap moeten, de naam van deze submap" (zie [zcbs-achtergrond.md](zcbs-achtergrond.md)).
- FTP-gebruiker van images.exe: ingebouwd `ZCBS@historischekringheemskerk.nl` op `ftp.historischekringheemskerk.nl`. Of het FTP-wachtwoord gelijk is aan het sftp-wachtwoord blijft onbekend.

---

# Bijlage

## Nuttige ZCBS-commando's

| Commando | Wat | Recht |
|---|---|---|
| `LAST`, `LAST30` | Het hoogste nummer, of de dertig hoogste | beheerder |
| `NEWRECORD <begin> <eind>` | Lege records aanmaken | 1+ |
| `MERGE` | Updates in de master opnemen | 1+ |
| `IMAGENO` | Records zonder afbeelding | beheerder |
| `NORECORDS` | Afbeeldingen zonder record | beheerder |
| `IMAGES100` | De laatst geüploade afbeeldingen | beheerder |
| `EXPORT` | Records exporteren, tab-gescheiden | beheerder |
| `OVERZICHT` | Overzicht van de inhoud van de database | beheerder |
| `LOGIN` | Inlogscherm met naam en wachtwoord | iedereen |

## Waar in `objecten.pl` te kijken

Regelnummers in de versie van 26-09-2026. Dit is de code van Gerard van Nes. Hij staat alleen lokaal en wordt niet overgenomen.

| Onderwerp | Regels, ongeveer |
|---|---|
| Testmodus, `$TESTING`, `$UPDATES2` | 354 en 371 |
| `NEWRECORD` | 1433 |
| `LAST` | 2242 |
| Ocr doorzoeken | 2402 |
| Pdf-link op de objectpagina | 6733 |
| Opslaan van een record, upd-cumm en mail | 17335 |
| Inloggen en rechten | 19000 en 19284 |
| Permissiebestand bewerken | 19470 |
| Versturen van mail | 28588 |

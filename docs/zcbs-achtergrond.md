# ZCBS — achtergrond voor de ZcbsServer

Wat we weten over ZCBS bij HKH, images.exe en pdf2zcbs.exe. Bronnen in deze map:
[zcbs-technische-documentatie-v4.0.pdf](zcbs-technische-documentatie-v4.0.pdf) (versie 1 maart 2017, 246 blz.),
[042.pdf](042.pdf) (Bijlage B: images.exe, v3.3, 12-05-2022) en [050.pdf](050.pdf) (Bijlage J: pdf2zcbs, v1.16,
25-01-2022). Bijlage B en J staan **niet** in v4.0 (B alleen in de inhoudsopgave; J bestond nog niet).
Waar iets een afleiding is in plaats van letterlijk uit de documentatie, staat dat erbij.

## 1. ZCBS in het kort

- **ZCBS** = Zijper Collectie Beheer Systeem, van Gerard van Nes (vannes@zcbs.nl, info@zcbs.nl). Varianten ZCBS
  (objecten), ZBBS (beeldbank/foto's), ZPBS (publicaties). Beheer via Genootschap ZCBS.
- **Perl/CGI** op Apache, **platte tekstdatabases**, tekenset **ISO-8859-1 (latin-1)**. HKH = instelling **#004**;
  softwareversie bij HKH: 13 aug 2026.
- **Zeven collecties** bij HKH (peildatum 11 sep 2026): artikelen 523 · archief 1.710 · beeldbank 12.002 ·
  library 1.612 · bidprent 1.332 · objecten 760 · transcripties 5.187 (≈ 23.100).
- Record-URL: `/cgi-bin/<collectie>.pl?ident=<nummer>`. Zoeklijst: `?search=%25&veld=all&display=list&istart=N`
  (30 per pagina).

## 2. Opbouw op de server

Elke collectie is een **aparte installatie**:

| Wat | Waar |
|---|---|
| Startscript (klein; roept het hoofdprogramma `objecten.pl` aan) | `/cgi-bin/<collectie>.pl` |
| Master-database | `/cgi-bin/<collectie>/<collectie>.txt` |
| Update-database | `/cgi-bin/<collectie>/updates.txt` |
| Back-ups, cumulatieve wijzigingen | `/cgi-bin/<collectie>/backup1/` (o.a. `upd-cumm.txt`, `upd-init.txt`, back-ups per merge) |
| Configuratie, layout | `/cgi-bin/<collectie>/misc/cfg-cbs.pl`, `layout.html` |
| Afbeeldingen | `/public_html/<collectie>/small`, `large`, `100pct` (evt. submappen per 1000) |
| Pdf's, OCR-tekst | map volgens `$PDF_VIEWER` en `$OCR_MAP` in de config (bijv. `documenten/pdf`, `documenten/ocr`) |

Gedeeld tussen collecties: het hoofdprogramma (`objecten.pl`), `/cgi-bin/misc/permit.lst` (centraal),
`replace.txt` (synoniemen), logbestanden (o.a. `/cgi-bin/misc/images.log`), en "zoeken in alle databases".

### Master- en update-database

- Nieuwe en gewijzigde records komen **achteraan `updates.txt`**; de master blijft gesorteerd.
- Bij elke zoekopdracht leest ZCBS eerst de master, dan `updates.txt`; records uit updates overschrijven die uit de
  master. **Wijzigingen zijn dus direct zichtbaar, ook zonder merge.**
- **MERGE**: zoekcommando `MERGE` (oud: `UPDATE_DATABASE`) of beheerderspagina optie **T**; alleen
  **systeembeheerder (permissie 1+)**, **per collectie**. Maakt een nieuwe master, back-ups in `backup1/` met datum,
  mailt de database naar `$EMAIL_TO`, ruimt `guest.lst` en een lock-bestand op. Advies: bij > ~1000 wijzigingen of per
  kwartaal. `updates.txt` kan dus jaren aan wijzigingen bevatten als niemand merget.
- Er is geen apart merge-script; automatiseren zou een zoekopdracht met een ingelogde sessie zijn (niet uitgezocht).

### Nuttige zoek-/systeemcommando's

`LAST[n]` (laatste records), `NEWRECORD <begin> <eind> [bestand]` (lege records aanmaken, ook beheerderspagina optie Q),
`MERGE`, `LOGIN`, `MUTATIES`, `NORECORDS`, `MASTERDB`.

### Velden (relevant)

Veld 02 = recordnummer · 08 = jaartal · 14 = adres · 21 = plaatsing op internet · 26 = vaak GPS ·
**27 = vaak bestandsnaam van het bron-image op de pc** ("Locatie afbeelding op PC"; gevuld door images.exe bij types B–E) ·
33/34 = datum beschreven/laatste wijziging. Betekenis per collectie staat in de config (`$fieldname{"VELDxx"}`), dus
niet gegarandeerd gelijk tussen collecties. `$LOC_IMAGE` = veld met afwijkende afbeeldingsnaam (nodig bij type B/C).

### Toegang: `permit.lst`

Kolommen: toepassing(en) · recht (1–4, 1+ = systeembeheerder, 5 = extern; 1- e.d. zonder 100%-afbeeldingen) ·
(WAN-)IP-adres · naam (evt. `naam - wachtwoord`, of `<ROUTER>`) · recordbereik. Herkenning gaat via IP; van buiten
(bijv. thuis) inloggen met zoekwoord `LOGIN` en een regel met gebruikersnaam + wachtwoord. Een gebruiker kan rechten
voor alleen één collectie krijgen (eerste kolom). Ook een lokale `permit.lst` per collectie mogelijk.

### Testversies en een testcollectie

- Ingebouwd: `beeldbank2.pl` → `objecten2.pl`, met testbestanden `cfg-cbs2.pl`, `layout2.html`, `replace2.txt`,
  `<collectie>2.txt` als die bestaan (anders de productiebestanden). Bedoeld voor config/layout/nieuwe versies.
  **Correctie van 22 oktober 2026, uit de code van `objecten.pl`:** in testmodus schrijft ZCBS wijzigingen naar `updates2.txt`
  (variabele `$UPDATES2`), zonder cumulatieve back-up en zonder mail, en leest het productie-master en productie-updates plus
  `updates2.txt` zolang er geen testmaster is. Voor het bewerken van records is de testversie dus een veilige oefenomgeving.
  `NEWRECORD` werkt in testmodus niet, en de afbeeldingsmappen en de map met tekst en pdf zijn gedeeld, dus voor het importproces
  met uploads is hij niet geschikt. Zie [zcbs-analyse-en-wrapper.md](zcbs-analyse-en-wrapper.md), A3.
- **Testcollectie** (bijv. `test`): is gewoon een collectie (startscript + `/cgi-bin/test/` + `/public_html/test/`),
  data gescheiden. In de config zetten: `$INTERNET = 0;` (niet publiek) en `test` in `$NODB` van de andere collecties
  (niet meezoeken in "alle databases").
- images.exe kent intern een lijst van instellingen en beeldbanken; een zelfgemaakte collectie wordt mogelijk niet
  herkend.

### Licentie (zcbs.nl, gebruikersvoorwaarden)

Auteursrecht bij Gerard van Nes; gebruik alleen voor eigen collecties; **nieuwe toepassingen alleen met toestemming**;
installatie door de auteur of aangewezen persoon; bronvermelding verplicht; geen garantie.
- **Configuratie aanpassen mag** (cfg-cbs.pl, layout.html, permit.lst, replace.txt, `$INSERT`'s in de config).
- **Programmacode aanpassen niet** (en updates overschrijven het). Eigen testkopie (`objecten3.pl`) "om te spelen" mag.
- **Data is van HKH.** Zelf iets nieuws bouwen op eigen data/ontwerp mag; geen ZCBS-code overnemen.
- Advies: testcollectie via een mail aan de auteur laten regelen.

## 3. images.exe (Bijlage B, docs/042.pdf)

- Windows-programma, **Perl omgezet met perl2exe** (IndigoStar). Start vanuit zijn eigen map in een DOS-box.
  **Geen opdrachtregelargumenten**; alles via vragen + `images.ini`.
- Zip bevat: `images.exe`, `images.ini`, `i_view32.exe` (IrfanView; gebruikt voor conversie), `manual-images.pdf`,
  `plugins/postscript.dll` (voor pdf's). Maak een submap `images` naast de exe.
- **Stappen:** A inlezen/type bepalen → B afgeleiden maken (small/large/100pct, 40% compressie) → C upload via **FTP**
  (Perl `Net::FTP`, **geen SFTP**) → D lege records aanmaken en uploaden → E opruimen.
- **Bestandstypes:** A = alleen cijfers (= recordnummer; aanbevolen) · B = vaste letters + recordnummer · C = vrije naam
  (vraagt beginnummer) · D/E = bron in andere map, wordt hernoemd; `images-rename.txt` met oud|nieuw. Oude naam in
  **veld 27** bij B–E. Probeert bij C/D/E adres (veld 14) en jaartal (veld 08) uit de naam te halen. 5–1000 bestanden per keer.
- **Resoluties:** 1/2/3 = small 100×100 (objecten, documenten, boeken), 6/7/8 = small 250×250 (foto's); large 700×500;
  100pct ongewijzigd / max 3000×2000 / max 4000×3000.
- **Upload (C):** vraagt nummer/naam/plaats instelling (HKH: `4`), FTP-wachtwoord (gecontroleerd door te verbinden),
  toont de ZCBS-toepassingen op de server → kies collectie (= uploadmap), kies (sub)map of maak er een.
  FTP-gebruikersnaam zit in het programma. Eén FTP-wachtwoord voor de hele server: wie images.exe kan gebruiken, kan
  alles op de server.
- **Records (D):** maakt lege records ("Een nog niet beschreven record (datum)") en zet ze in de **update-database op de
  server**. Uitvoer: "Oude update database op webserver bevatte: 26 records / Nieuwe … bevat nu: 32". Afleiding: het haalt
  `updates.txt` via FTP op, voegt toe en zet het terug. Roept geen pl-script aan.
- **`images.ini`:** `Naam/plaats/nummer instelling`, `FTP wachtwoord webserver`, `Echo` (1 = wachtwoord verborgen),
  `Resolutieh`, `Irfanview` (pad), `Image type`.
- **`images.pm`:** `$INSERTF` (bestandsnaam aanpassen vóór upload) en `$INSERTD` (velden vullen vóór wegschrijven record,
  `$veld[n]`).
- **Pdf's:** worden net als afbeeldingen verwerkt; afgeleiden van de **eerste pagina**.
- **Logs:** op de pc `log-datum-tijd.txt`; op de server een cumulatieve regel per sessie (`/cgi-bin/misc/images.log`).
- Tips uit de handleiding: firewall bij hangen na wachtwoord; altijd starten vanuit eigen map; voor thuis: Windows-pc,
  de zip, instellingsnummer en FTP-wachtwoord; FTP kan IP-beperkt zijn.
- Vragen (letterlijk, voor de automatische antwoorden): "Type ENTER/RETURN indien OK", "Toch de afgeleide images
  small/large/100pct opnieuw aanmaken? [J/N]", "Kies 1,2,3 (objecten,boeken,etc), of kies 6,7,8 (foto's,etc)",
  "UPLOADEN aangemaakte images in small/large/100pct naar webserver? [J/N]", "Nummer, naam of plaats instelling?",
  "FTP wachtwoord van uw webserver?", "Kies de ZCBS-toepassing", "Waar wilt u de images geuploaded hebben",
  "Oude images (met dezelfde naam) … OVERSCHRIJVEN? [J/N]", "AANMAKEN en UPLOADEN van de bijbehorende N nieuwe lege
  DATABASE RECORDS? [J/N]", "VERWIJDEREN (tijdelijk) aangemaakte mappen …", "VERWIJDEREN (tijdelijk) PC-bestand …",
  "VERWIJDEREN inhoud van de map [images] …", "Geef een Return/Enter om dit DOS-window te verlaten".

## 4. pdf2zcbs.exe (Bijlage J, docs/050.pdf)

- Windows, "het gecompileerde Perl programma"; map `bin` met hulpprogramma's (o.a. pdftotext), `doc`.
  Vervangt `splittxt.exe`. Start vanuit zijn eigen map, **geen argumenten**.
- **Doet zelf geen OCR.** Haalt de bestaande tekstlaag uit de pdf (pdftotext) en zet die om naar **één zin per regel**
  (ZCBS toont zo de zinnen met het zoekwoord). Een gescande pdf moet eerst door een OCR-programma (PDF24, ABBYY,
  Acrobat, …; OCRmyPDF is een goede gratis optie).
- **Werking:** verwerkt de pdf's **in zijn eigen map**. Precies één pdf → vraagt of die gesplitst moet worden
  (prefix, loze pagina's, paginabereiken). Meerdere pdf's → automatisch naar `output/pdf` en meteen verwerken.
  Uitvoer: `output/ocr/<naam>.txt`, `output/small/<naam>.jpg` (eerste pagina, max 250×250), eventueel
  `output/updates.txt`, log in `output/log/`.
- Bestandsnamen: alleen kleine letters, cijfers, `-` en `_`.
- **`pdf2zcbs.ini`:** antwoorden `SP` (splitsen), `OP` (output-prefix), `LP` (loze pagina's), `PN` (paginanummers),
  `TJ` (txt+jpg), `PS` (presentatieslides); `OCR -raw`; velden `00` (afk. instelling) en `01` (instellingsnummer) →
  dan wordt `updates.txt` gemaakt, plus vaste veldwaarden; `$INSERT_DB` met `$file`, `$str`, `%IN{"nn"}`.
- **Uploadt niets.** Output naar `/public_html/<collectie>/`; `updates.txt` naar `/cgi-bin/<collectie>/` maar **eerst
  MERGE** (anders overschrijf je niet-gemergde wijzigingen). Alternatief (afleiding): eigen records achteraan de
  bestaande `updates.txt` toevoegen, zoals images.exe doet.
- Config van de collectie: `$PDF_VIEWER` (waar de pdf's staan) en `$OCR_MAP` (waar de OCR-teksten staan).
- Problemen met splitsen/paginanummers/compressie: pdf eerst door PDF24 ("Pagina's uithalen").
- Vragen (letterlijk): "Deze PDF splitsen in (groepen van) losse pagina's (default = N)?", "Naam output PDF's (prefix)…",
  "Aantal loze pagina's aan het begin van de PDF?", "In welke pagina's?", "PDF's in map output/pdf verwerken naar txt +
  jpg's? (default = J)", "Betreft het (presentatie) slides? (default= N)", "Type return to exit".

## 5. Waarom de exe's lastig zijn vanuit een andere applicatie

- Perl buffert stdout als die geen console is (tenzij de code `$| = 1` zet) → vragen verschijnen niet, het programma
  lijkt te hangen maar wacht op invoer. Getest met de nep-scripts: zo is het; **`PERLIO=:unix` lost het op**
  (Perl schrijft dan ongebufferd; invoer dan met LF afsluiten).
- Verborgen wachtwoordinvoer gebruikt vaak de Windows-console direct → kan mislukken zonder echte console
  (omzeilen: `Echo : 0` of wachtwoord in images.ini).
- Robuuste oplossing als PERLIO niet werkt met perl2exe: **pseudo-console (ConPTY) via pty4j**.

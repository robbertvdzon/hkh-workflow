# Nieuwe bestanden in ZCBS zetten — de eerste, simpele versie

Besloten na het overleg met Jan op 30 oktober 2026. De vrijwilligers zijn gewend aan hun eigen werkwijze, dus de eerste versie
doet precies wat Jans stappenplan doet, zonder meer. Dit document is de bron voor de flow en de beheerderslogin;
[specs.md](specs.md) verwijst ernaar. De bewerkfunctie voor records staat in
[specs-item-bekijken-bewerken.md](specs-item-bekijken-bewerken.md) en is nu een aparte knop.

## 1. Wat er veranderd is

| Voor | Nu |
|---|---|
| Eén batch met acht fasen, waarvan het bewerken van de records er een was | Zes fasen, **zonder bewerken**: het invullen van titel enzovoort gebeurt op een andere dag, soms door iemand anders |
| Het woord "batch", met een batchoverzicht | Geen batches. Een **log** van wat er verwerkt is (pagina Verwerkt) |
| Een bronmap met een submap per persoon, en vinkjes in één lange lijst | **Twee mappen met scans** (`K:\scans jelly`, `K:\scans ria`) waar Carla zelf doorheen **bladert**, met vinkjes per map |
| Beginnummer = hoogste nummer + 1 (`LAST`) | Een beginnummer uit **de vrije nummers van ZCBS**, aaneengesloten |
| De originelen gaan naar een archived-map | De originelen **blijven staan** op K:. De **100pct en de pdf's** (met recordnummer) krijgen een **back-up op K:** |
| Voor images.exe twee vinkjes (uploaden, records) | **Handmatig** typen, waarbij de app de antwoorden onthoudt en voorstelt, of **automatisch** met die onthouden antwoorden |
| Geen beheerder; de instellingen stonden open | **Beheerderslogin**, wachtwoord in het configuratiebestand op de server |
| Demoschermen | Verwijderd: ze toonden de oude flow |

## 2. Het begin van de app

1. **Inloggen bij ZCBS** met je eigen naam en wachtwoord (alleen in het geheugen). Zonder inlog komt niemand op de pagina's van Carla.
2. **Collectie kiezen.** Nu alleen `archief` (de instelling *Collectie*).
3. Twee knoppen: **Nieuwe bestanden in ZCBS zetten** (`/nieuw`) en **ZCBS records aanpassen** (`/records`). Verder in het menu: **Verwerkt**
   (`/verwerkt`), en onder Beheer de pagina's voor de beheerder.

## 3. De flow Nieuwe bestanden (`/nieuw`)

Jans stappenplan, met erboven de stap die er nog niet in stond (de bestanden staan op K:):

| Fase | Wat Carla ziet en doet | Jans stap |
|---|---|---|
| 1. Bestanden kiezen | Kiest Jelly of Ria, bladert door de submappen en vinkt de pdf's aan (Shift-klik kiest alles tussen twee bestanden: "X t/m Y uit map Z"). Kopieert ze naar de invoermap. Al verwerkte bestanden zijn gemarkeerd en niet aan te vinken. | `copy K:\… c:\zcbs\invoer` |
| 2. Nummers | Ziet het laatste record en alle vrije reeksen uit ZCBS, kiest een beginnummer. Passen alle bestanden niet aaneengesloten in een vrije reeks, dan geeft de app een foutmelding. De app maakt kopieën `<nummer>.pdf` in de submap `werk`. | het nummer geven in de bestandsnaam |
| 3. pdf2zcbs | Start `pdf2zcbs.exe` (zet de pdf's in de map van het programma), en **controleert daarna**: per bestand een kleine afbeelding, de eerste tekst, links naar de pdf en de tekst, en de vinkjes voor tekst, pdf, small en large. "Ja, goed" of "pdf2zcbs opnieuw". | `pdf2zcbs.exe` en het nakijken van `output\large\pdf\ocr` |
| 4. images.exe | Kopieert de large-jpg's en start `images.exe`; daarna weer **controleren**: de afbeelding, de vinkjes small, large en 100pct, en of de records in ZCBS staan. | `copy large\*.jpg images\images`, `images.exe`, `dir small large 100pct` |
| 5. Upload | Tekst en pdf's met sftp naar `<collectie>/ocr` en `<collectie>/pdf`. Nooit overschrijven, eerst alles controleren. | Total Commander |
| 6. Back-up | Kopieert de **100pct** en de **pdf's** (met hun recordnummer) naar de back-upmappen op K:, ruimt dan de werkmappen op en zet de bestanden in het verwerkt-log. | `backup_100pct`, `backup_invoer_org`, `rmdir`, `del` |

De programma's draaien één keer voor alle bestanden samen (ze verwerken alles wat in hun map staat; pdf2zcbs leegt zijn `output` bij elke start).
Een stap is pas klaar als de verwachte bestanden er zijn; de exitcode alleen is niet genoeg. De volgende stap kan niet voordat Carla de
uitvoer heeft gecontroleerd en bevestigd (fase 3 en 4). Elke stap toont vooraf wat hij gaat doen en wat hem nu tegenhoudt.

**Opruimen** verwijdert alleen wat bij deze doorloop hoort: de mappen `output`, `small`, `large`, `100pct` en de eigen bestanden in `images`,
`log-*.txt`, de pdf's in de map van pdf2zcbs, de kopieën in `werk` en in de invoermap. Eerst komt de back-up, met een controle van de grootte.
De originelen op K: blijven staan.

## 4. De vrije nummers

ZCBS toont bij een nieuw record "Niet gebruikte (dus vrije) record/databasenummers": reeksen (`11935-99000`), losse nummers (`10006`)
en een open einde (`99003-`). De app leest dezelfde lijst met `GET archief.pl?ident=0&upd_form=new` (alleen lezen: het maakt niets aan en zet
geen vergrendeling; het werkt voor een gebruiker die records mag aanmaken). Gecontroleerd in `objecten.pl` en tegen de lokale ZCBS.

- **Clusters.** Een collectie kan nummers per cluster uitgeven (bijvoorbeeld 1000–1999 en 2000–2999). ZCBS laat alleen zien wat in het cluster van
  de gebruiker past. De app toont de hele lijst en het laatste record (`search=LAST`), zodat ook vrije nummers tussen bestaande records
  zichtbaar zijn.
- **Aaneengesloten.** Aansluitende nummers tellen als één reeks (`10001` en `10002` zijn samen `10001 t/m 10002`). Een reeks bestanden moet helemaal in
  één vrije reeks passen; anders weigert de app met een melding hoeveel ruimte er wel is.
- Het voorstel is het nummer na het laatste record als daar ruimte is, anders het begin van de eerste reeks die groot genoeg is.
- Voorloopnullen blijven staan (`00001`); het aantal cijfers volgt uit de lijst van ZCBS.
- De controle gebeurt vóór images.exe. Dat programma maakt zelf de lege records aan, waarna de nummers niet meer vrij zijn.

## 5. De antwoorden van images.exe

- **Handmatig uitvoeren**: Carla typt de antwoorden in de console. De app herkent de vraag waar het programma op wacht, **vult het onthouden
  antwoord alvast in** en onthoudt wat ze intypt (alleen als het programma goed eindigt). Wachtwoorden worden nooit voorgesteld of onthouden.
- **Automatisch uitvoeren**: de app geeft de onthouden antwoorden. Alleen de collectie (uit de instellingen), het overschrijven en het verwijderen van de
  mappen van images.exe staan vast en gaan voor: altijd `n`, want de app ruimt zelf op, ná de back-up.
- Zolang er niet handmatig is geantwoord, zijn uploaden en records aanmaken `n`: de app waarschuwt daarvoor. Na afloop meldt de app of de
  afbeeldingen zijn geüpload en of de records in ZCBS staan.
- Het FTP-wachtwoord voor automatisch uploaden staat bij de beheerder in de antwoordenlijst (veld "wachtwoord").

## 6. Het verwerkt-log

Een bestand met een regel per bestand (tabs; `hkh-workflow-verwerkt.tsv` naast het instellingenbestand, of een eigen pad met
`hkh.workflow.processed-log`): doorloop-id, tijd, ZCBS-gebruiker, collectie, recordnummer, bronmap (Jelly of Ria), map op K: en bestandsnaam. De app schrijft erin na een geslaagde back-up. De pagina Verwerkt toont het per doorloop; de lijst met scans markeert wat al
verwerkt is. Wil je een bestand opnieuw verwerken (bijvoorbeeld een betere scan onder dezelfde naam), haal dan de regel uit het log.

## 7. Beheerder

Het wachtwoord staat in `hkh.workflow.admin-password` (bestand `application.properties` naast de jar, of de omgevingsvariabele
`HKH_WORKFLOW_ADMIN_PASSWORD`). Is er niets ingesteld, dan kan niemand als beheerder inloggen en blijven de instellingen dicht. Een foute poging duurt een
seconde langer. De beheerder (een sessie in de browser) heeft toegang tot **Instellingen** en **Test (technisch)**; de rest vraagt alleen de ZCBS-login.

## 8. Instellingen (alleen beheerder)

`testFolder` (invoermap, `c:\zcbs\invoer`), `sourceFolders` (één regel per map met scans, `naam=pad`: `Jelly=K:\scans jelly`), `pdf2zcbsExe`, `imagesExe`,
`backup100pctFolder` en `backupPdfFolder` (op K:), `collection`, `zcbsBaseUrl`, de sftp-instellingen en de lijsten met antwoorden. Vervallen: `archivedFolder`,
`sourceFolder`, `imagesFileType`, `imagesResolution`, `imagesInstitution` (die zitten nu in de onthouden antwoorden).

## 9. Wat nog niet kan of niet getest is

- **Eén ZCBS-login voor de hele app.** Er is geen eigen login per gebruiker; de ingelogde ZCBS-gebruiker geldt voor iedereen die de app opent. Twee mensen
  tegelijk is dus niet bedoeld.
- **De stand van een doorloop zit in het geheugen.** Na een herstart van de app staan de bestanden nog op schijf (invoermap, `werk`, map van de
  programma's), maar de app weet niet meer waar hij was; dan eerst met de hand opruimen of "Opnieuw beginnen".
- Niets is getest tegen de echte HKH-server of op Windows met `K:\`-paden; alleen op de Mac met de nep-programma's, de lokale ZCBS in Docker en een sftp-server
  in Docker.
- Het invoeren van gegevens (wie wat nog moet invullen) houden de vrijwilligers zelf bij; de app biedt alleen "kies een nummer of reeks".
  Robbert vraagt later na hoe ze dat doen.
- Wat met de originelen op K: moet gebeuren na verwerking is niet besloten; nu blijven ze staan.
- Logs naar de server van Robbert sturen komt in een latere fase.

## 10. Lokaal proberen

Start de lokale ZCBS (`tools/docker/zcbs`, `http://localhost:8081`, gebruiker `user1`, wachtwoord `abc`). Zet de mappen met scans en de nep-programma's
(`tools/mock`) in de instellingen en start de app met bijvoorbeeld
`mvn spring-boot:run -Dspring-boot.run.arguments="--hkh.workflow.admin-password=…"`. De live-test tegen de lokale ZCBS draait met `mvn test -Dzcbs.live=true`.

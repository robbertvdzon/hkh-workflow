# Specificatie: item bekijken en bewerken in de workflow

Status: voorstel, 24 oktober 2026, bijgewerkt na de beslissingen van Robbert (geen login op de app, testen op het echte archief, lock bij openen). Bedoeld voor de eerste run met Carla in het echte archief, volgende week.
Achtergrond en bewijs staan in [zcbs-edit-formulier.md](zcbs-edit-formulier.md), [zcbs-api.openapi.yaml](zcbs-api.openapi.yaml) en
[zcbs-analyse-en-wrapper.md](zcbs-analyse-en-wrapper.md). Alles over ZCBS is daar **uit de broncode en de HTML van het bewerkscherm gelezen,
niet uitgeprobeerd**. Er is vóór de run geen testomgeving beschikbaar. Deze spec gaat daarom uit van veilig falen: als iets niet klopt,
stopt de app en kan Carla het in ZCBS zelf afmaken.

## 0. Beslissingen van Robbert

- De app krijgt nu **nog geen eigen login**; het is een testversie. Dat komt later.
- We testen op **het echte archief**, stap voor stap met Carla.
- Het bewerkscherm wordt gevuld met de gegevens uit ZCBS. Carla vult rustig in. De app houdt haar invoer vast. Bij indienen zet de app opnieuw de lock en post direct daarna.
- Er wordt nog **niets gebouwd** tot Robbert een sein geeft.

## 1. Doel en afbakening

**Doel.** Na het uploaden van een batch (pdf2zcbs, images.exe, sftp) toont de app voor elk bestand een bewerkpagina met de velden van het
ZCBS-record, zodat Carla de gegevens in onze flow kan invullen. Opslaan gebeurt door het ZCBS-formulier te posten, zoals een browser dat doet.

**In deze versie**
- Inloggen met de eigen ZCBS-naam en het wachtwoord, voor de hele run.
- Een lijst van de records van de batch met een status.
- Een pagina per record: bekijken, bewerken, voorbeeld van de wijziging, opslaan, terugcontroleren.
- Alleen de collectie `archief`.

**Niet in deze versie**
- Records aanmaken (doet images.exe, stap D) of verwijderen.
- Bijlagen, kopiëren, "volgende bewerken" (`write=2`; de knop is in archief uitgecommentarieerd).
- Zoeken in het archief, andere collecties, meerdere gebruikers tegelijk.
- Het schrijven naar bestanden van ZCBS. Dat blijft verboden. Alleen via het ZCBS-formulier.

## 2. Uitgangspunten

1. **ZCBS is de bron.** Alles wat de app doet, kan iemand ook in ZCBS doen. Elke pagina heeft een link "open in ZCBS" als terugvaloptie.
2. **De app is een formulierclient.** Hij leest het formulier dat ZCBS levert en post er een terug. Velden en keuzelijsten staan niet vast in de app.
3. **Elke opslag is een gewone ZCBS-opslag** (ook voor de mail naar de beheerder, de log en de update-database).
4. **Geen aannames die niet terug te controleren zijn.** Na elke opslag wordt het record opnieuw gelezen en vergeleken.
5. **Wachtwoorden alleen in het geheugen**, nooit in logs, bestanden, URL's of meldingen.

## 3. Plaats in de flow

Volgorde in de run (stappenplan van Carla, aangevuld):

1. Hernoemen.
2. pdf2zcbs.
3. **Controle vooraf:** de nummers van de batch mogen nog **niet** als record bestaan (images.exe maakt ze aan, stap D).
4. images.exe, met upload en records aan. Pas na het vinkje van Carla.
5. Upload (sftp) van ocr en pdf.
6. **Records controleren en bewerken** (deze spec). Vanaf stap 4 beschikbaar, onafhankelijk van opruimen.
7. Opruimen en archiveren.

**Controle vooraf (stap 3).** Voor elk nummer: bestaat er al een record? De app haalt per nummer het bewerkformulier op (zie 6.2). Een "niet gevonden"
betekent dat het record nog niet bestaat. Bestaat er al een record, dan blokkeert de app stap 4 en toont de nummers. Kan de app dit niet vaststellen
(inlog mislukt, onbekende pagina), dan zegt hij dat en laat hij Carla beslissen. Dit is een waarschuwing, geen harde blokkade.

**Na stap 4** controleert de app per nummer of het record nu bestaat. Ontbreekt er een, dan staat het in de lijst met status "ontbreekt" en blijft het bewerken
voor dat nummer uit.

## 4. Schermen

### 4.1 Inloggen bij ZCBS
- Twee velden: naam en wachtwoord. De naam zoals die in ZCBS staat (bijvoorbeeld "Voornaam Achternaam"). Een korte uitleg bij het veld.
- Wordt getoond bij de start van de run en bij het openen van de bewerklijst als er geen sessie is.
- Bij een fout: duidelijke melding ("naam of wachtwoord niet herkend"), niet verder.
- Het wachtwoord wordt niet getoond, niet opgeslagen en niet gelogd. Bij een herstart van de app vraagt hij opnieuw.
- Knop "Uitloggen" wist de gegevens uit het geheugen. De app wist ze ook na een vaste tijd stilstand (instelbaar, standaard 4 uur).

### 4.2 Lijst van de batch
Tabel met per record: nummer, bestandsnaam, status, knop.

| Status | Betekenis |
|---|---|
| ontbreekt | het record bestaat (nog) niet in ZCBS |
| nog niet bewerkt | bestaat, nog niet door de app opgeslagen |
| bezig | door de app geopend, niet opgeslagen |
| opgeslagen | opgeslagen en teruggecontroleerd |
| fout | opslaan mislukt of terugcontrole klopt niet |
| geblokkeerd | door een andere gebruiker vergrendeld |

Verder: teller (x van y bewerkt), per record de link "open in ZCBS", en een knop "Verder met opruimen". De status staat in het geheugen
van de run en gaat na een herstart verloren; de bron van waarheid is ZCBS zelf.

### 4.3 Pagina per record
Indeling:
- Kop: nummer en bestandsnaam, status, link "open in ZCBS".
- Links of boven: het document (pdf van `https://<site>/archief/pdf/<nr>.pdf`, indien aanwezig na de upload, anders een melding) en de large-jpg.
- Het formulier met alle velden in de volgorde van ZCBS, met hun label.
- Knoppen: **Voorbeeld**, **Opslaan** (pas na het voorbeeld), **Annuleren**.

**Velden** volgens paragraaf 6. Per veld de huidige waarde. Keuzelijsten met de opties van ZCBS. Bij `== Anders ==` verschijnt een tekstveld.
Thema is een meerkeuzeveld. "Plaatsing op internet" is ja of neen (de dagcode regelt de app).
Niet bewerkbaar: documentnummer en de stempels (aangemaakt, beschreven, aangepast); die staan als tekst.

**Voorbeeld.** Een lijst "veld, oud, nieuw" met alleen de wijzigingen. Geen wijzigingen: geen opslaan. Daaronder de melding dat opslaan een e-mail naar de beheerder stuurt.

**Na het opslaan.** (zie ook paragraaf 7) Een groen scherm "Opgeslagen en gecontroleerd" met de gelezen waarden, of een fout met uitleg (zie 8). Daarna "Volgende record".

## 5. Techniek

Nieuwe Spring Modulith-modules, in dezelfde stijl als `sftp` en `testrun`:

| Module | Inhoud | Mag gebruiken |
|---|---|---|
| `zcbs` | Client voor ZCBS: sessie, inloggen, formulier ophalen, opslaan, terugleggen | `settings` |
| `items` | Lijst en recordpagina's (controller, templates), statusbeheer van de run | `zcbs`, `settings`, `testrun` (alleen leesbare batchgegevens) |

**Onderdelen van `zcbs`**
- `ZcbsSession`: eigen `HttpClient` met cookiebeheerder (alleen geheugen), de gebruikersnaam en het wachtwoord in een `CharArray` dat gewist kan worden.
  Vaste `User-Agent` (niet `curl`), geen `X-Forwarded-For`, redirects aan, timeouts van 15 seconden, een enkel verzoek tegelijk per sessie.
- `ZcbsClient`: `login()`, `openForm(nr)`, `save(form)`, `readBack(nr)`.
- `FormParser`: leest de HTML van het formulier (zie 6) in een `ItemForm`: lijst van velden (naam, label, soort, waarde, opties), de verborgen velden en de
  oorspronkelijke `a0`. Gebruikt **Jsoup** (geen reguliere expressies): de pagina heeft attributen zonder aanhalingstekens, hoofdletters (`CHECKED`, `SELECTED`,
  `MULTIPLE`) en `<option>` zonder sluitende tag. Het antwoord wordt als ISO-8859-1 gelezen. Alleen `<form name=zcbsform>` telt; de rest van de pagina wordt genegeerd.
  Velden worden op `name` gevonden en labels uit de `<th>` van dezelfde rij, niet op de tabelindeling. Geen vaste lijst van velden in de code, behalve de
  verwachte namen (voor de controle in 6.5) en de velden die niet in het formulier staan (6.3).
- `RecordRebuilder`: bouwt uit de ingelezen velden het record op zoals ZCBS dat doet (zie 6.5), voor de controle tegen `a0`.
- `FormEncoder`: bouwt het `multipart/form-data`-lichaam in ISO-8859-1. Een teken dat niet in latin-1 past, geeft een fout met het teken en het veld.
- `RecordDiff`: vergelijkt twee versies veldsgewijs.
- `ZcbsErrors`: herkent de bekende antwoorden (6.4).

De instellingen krijgen: basis-URL van ZCBS, collectie, naam van het script (`archief.pl`), de maximale stilstand van de ZCBS-inlog.
De app slaat de ZCBS-naam wel op in de run (voor de weergave), nooit het wachtwoord.

## 6. Het ZCBS-contract (samenvatting)

Volledig beschreven in [zcbs-edit-formulier.md](zcbs-edit-formulier.md); formeel in [zcbs-api.openapi.yaml](zcbs-api.openapi.yaml).

### 6.1 Inloggen
`POST /cgi-bin/archief.pl`, `application/x-www-form-urlencoded`, velden `user` en `pswd`. Cookies uit het antwoord bewaren en bij elk volgend verzoek meesturen.
Op een router-adres (het HKH-netwerk) kan ZCBS in plaats daarvan het routerformulier geven (`veld=access`); de app herkent dat aan de pagina en probeert dan die variant.
**Onbevestigd:** de precieze antwoordtekst. Het inlogsucces wordt dus niet uit de tekst afgeleid maar door daarna het formulier van het eerste record te krijgen.

### 6.2 Formulier ophalen (zet de vergrendeling)
`GET /cgi-bin/archief.pl?ident=<nr>&upd_form=edt`. Antwoord: de pagina met `<form name="zcbsform" ...>`, of "Tijdelijk geblokkeerd object", of een inlogpagina.
Een niet bestaand nummer: gedrag onbevestigd; de app behandelt een antwoord zonder formulier en zonder bekende melding als "niet gevonden / onbekend" en toont de tekst.

### 6.3 Opslaan
`POST /cgi-bin/archief.pl`, `multipart/form-data`, latin-1. De velden zijn alle velden van het formulier (verborgen en zichtbaar) met `write=1`.
Regels die de app moet naleven (details in het andere document):
- De verborgen velden en `a0` ongewijzigd terugsturen. Het stempel `a34` is van het openen van het formulier.
- Velden die de gebruiker niet wijzigt, met hun oude waarde sturen. ZCBS bouwt het record alleen uit de velden die het ontvangt.
- **Velden zonder invoerveld meesturen:** `a17`, `a20`, `a23`, `a28` t/m `a31`, met de waarde uit `a0` (anders worden ze leeg).
- `a24` (Thema): één deel per gekozen optie. `== Anders ==` met de tekst in `aNb`. `a10x` en `a15x` blijven los (ZCBS maakt `hoofdveld|subveld`).
- `a21`: de waarde uit het formulier (0 of de dagcode); de gebruiker kiest alleen ja of neen.
- Bijlagevelden leeg met `add_inter=1`.
- Regeleinden in tekstvakken zijn toegestaan (ZCBS maakt er `<br>` van). Tabs worden spaties.

### 6.4 Wat de app uit antwoorden herkent
| Signaal in het antwoord | Betekenis | Actie |
|---|---|---|
| geen `zcbsform`, wel tekst "Tijdelijk geblokkeerd object" | vergrendeld door een ander | status "geblokkeerd", toon de tekst, opnieuw proberen na enkele minuten |
| tekst "Uw sessie is verlopen" of een inlogscherm | sessie weg | opnieuw inloggen met het geheugen en één keer herhalen |
| "niet herkend" of "onjuist" bij inloggen | verkeerde naam of wachtwoord | melding, opnieuw invoeren |
| `==Sorry` | verzoek geweigerd door het filter | stoppen, tekst tonen, niet herhalen |
| geen van de bovenstaande en geen formulier | onbekend | stoppen, het antwoord (zonder gegevens) in het log, link "open in ZCBS" |

### 6.5 Het formulier lezen: veiligheidscontroles

1. **Alles terugsturen.** Elk veld dat ZCBS levert (ook onbekende nieuwe), gaat ongewijzigd terug tenzij de gebruiker het wijzigde. Een nieuw veld in ZCBS breekt dus niets.
2. **Controle tegen `a0`.** Bij elk gelezen formulier bouwt `RecordRebuilder` het record uit de **ongewijzigde** velden volgens de regels van ZCBS (subvelden `hoofdveld|subveld`,
   `== Anders ==` vervangen, meerdere keuzes met `; `, regeleinden, tabs, dubbele spaties, `== Nog onbekend ==` en `==Maak uw keuze==` weghalen). Velden 0 t/m 32 moeten gelijk
   zijn aan `a0`. Verschilt het: de app zegt "ik begrijp dit record niet goed", **slaat niet op**, toont het record alleen-lezen en geeft de link naar ZCBS. Dit voorkomt dat een
   vreemde waarde of een fout van ons stil verkeerd wordt opgeslagen.
3. **Controle op veldnamen.** Mist een verwacht veld (`a3` t/m `a27`, `a0`, `a32` t/m `a34`) of staat er een onbekend zichtbaar veld bij, dan komt er een waarschuwing en een regel in het log.
4. **Nieuwe invoer die niet in latin-1 past.** Veelvoorkomende tekens uit Word worden vervangen (“ ” `"`, ‘ ’ `'`, – en — `-`, … `...`, niet-brekende spatie een spatie). Andere tekens buiten
   latin-1 blokkeren het opslaan met een melding welk teken in welk veld staat. Een browser zou ze als `&#8220;` opsturen en zo in het archief zetten; de app doet dat niet.
5. **Wat we in de echte data zagen** (1908 records, 24 oktober): geen tabs, regeleinden, `<br>`, stuurtekens, `<` of `</textarea`; één losse `>`; 19 keer "B&W" (geen entiteit);
   151 records met `"` en 224 met `'` in Titel en Omschrijving; 294 met tekens als ë en é. Er staat dus niets in dat een HTML-parser laat falen. Lastige gevallen
   (`&amp;` in een tekstvak, `</textarea>`, een leeg veld, `&quot;`) worden in de parsertests nagebootst.

## 7. Openen en opslaan: stap voor stap

Gekozen model: **lock bij openen, hercontrole bij indienen.** Het openen van het formulier levert in één aanroep de gegevens, de verborgen velden (met `a0`) en de keuzelijsten, en zet
de lock van ZCBS (10 minuten, op de gebruikersnaam). Er is geen aparte lock-vrije bron nodig.

**Openen**
1. Gebruiker opent het record in de lijst. De app logt in (indien nodig) en haalt het formulier op: `F1`. De lock staat.
2. De app voert de controles van 6.5 uit en toont het bewerkscherm met de huidige waarden en de keuzelijsten uit `F1`. De eerste `a0` wordt in de run bewaard.
3. Per sessie wordt maar één record tegelijk geopend. Records die niet worden bewerkt, worden niet geopend (dat zou ze voor anderen vergrendelen).

**Invullen.** Carla vult rustig in; de app doet niets naar ZCBS. Haar invoer staat in de app en gaat niet verloren, ook niet als het langer duurt dan de lock of de ZCBS-sessie.

**Voorbeeld.** Een lijst "veld, oud, nieuw" met alleen de wijzigingen. Geen wijzigingen: geen opslaan.

**Opslaan (indienen)**
1. Opnieuw inloggen met de gegevens uit het geheugen (de sessie van een gewoon adres verloopt na 10 minuten, op het HKH-netwerk na 60).
2. Formulier opnieuw ophalen: `F2`. Dat zet de lock opnieuw, ook als de eerste verlopen was, en geeft een verse `a0` en verse lijsten.
   - Vergrendeld door een ander: status "geblokkeerd", haar invoer blijft staan, de app toont "tot ongeveer hh:mm" en een knop "Probeer opnieuw".
3. **Conflictcontrole:** is `F2.a0` anders dan `F1.a0`, dan heeft iemand anders het record intussen veranderd. Per veld: heeft de gebruiker een veld gewijzigd dat ook in `F2` anders is dan in `F1`,
   dan is er een conflict; toon oud, jouw waarde en nieuwe waarde en laat haar kiezen. Zonder conflict gaan haar wijzigingen op `F2`.
4. Controles van 6.5 op `F2`. `FormEncoder` bouwt het verzoek uit `F2` met de wijzigingen.
5. `save` (POST), direct na stap 2. Eén poging; bij een netwerkfout niet automatisch herhalen (de opslag kan toch gelukt zijn): eerst terugleggen.
6. `readBack`: formulier opnieuw ophalen en alle velden vergelijken met wat verstuurd is, rekening houdend met wat ZCBS zelf aanpast.
7. Uitkomst: gelijk "opgeslagen"; verschilt "fout" met per veld verwacht en gelezen. Nooit stil doorgaan.

**Na het opslaan** blijft de lock nog tien minuten op haar naam staan; een collega die hetzelfde record opent, wordt tot dan geblokkeerd. De app stuurt **nooit** `upd_form=clr`
(dat geeft alle vergrendelingen van het HKH-adres vrij). De app houdt de lock niet vast met herhaalde aanroepen. Open nooit meer records tegelijk dan nodig.

## 8. Foutafhandeling en meldingen

Alle meldingen in gewoon Nederlands, zonder technische tekst, met de volgende stap. Voorbeelden:
- "Record 11387 is bezig bij een ander (tot ongeveer 14:35). Probeer het later opnieuw of open het in ZCBS."
- "Je sessie bij ZCBS was verlopen. De app heeft opnieuw ingelogd; je wijzigingen zijn nog niet opgeslagen. Probeer het nog eens."
- "Opgeslagen, maar een veld lijkt anders dan je invulde: Titel. Controleer het record in ZCBS."
- "Het teken '€' kan niet worden opgeslagen in ZCBS (latin-1). Haal het weg of vervang het."
- "ZCBS gaf een onverwacht antwoord. Er is niets bevestigd. Controleer in ZCBS of het record is opgeslagen." + link.

## 9. Beveiliging en privacy

- Naar ZCBS alleen via `https`. De app zelf heeft nu **bewust nog geen login** (beslissing Robbert, testversie): iedereen die de app kan bereiken kan met de ZCBS-sessie van Carla werken. Zet daarom
  `HKH_WORKFLOW_ADDRESS=127.0.0.1` of draai alleen op een beschermd netwerk. Een eigen login volgt later.
- Het ZCBS-wachtwoord staat alleen in het geheugen van de run, wordt nooit gelogd en niet in meldingen of URL's gezet. Cookies van ZCBS worden niet getoond.
- Logregels van verzoeken bevatten alleen methode, pad, nummer en uitkomst, niet de velden of de cookies.
- De app stuurt niet vaker dan één verzoek tegelijk en niet sneller dan één per seconde.
- Bij drie mislukte inlogpogingen achter elkaar stopt de app en wacht een minuut. Dat voorkomt dat Carla's account door herhalen wordt geblokkeerd.
- Elke opslag stuurt een e-mail naar `archief@historischekringheemskerk.nl` (volledig record) en, bij een nieuwe "Anders"-waarde, naar de beheerder. De app waarschuwt hierover in het voorbeeld.

## 10. Testplan zonder echte server

Tot de run bij HKH is er niets tegen ZCBS te testen. Wat wel kan:

**Fixtures** (in `src/test/resources/zcbs/`, namen van personen vervangen door neutrale):
- `edit-11387.html`: de geplakte pagina van record 11387.
- `blocked.html`, `session-expired.html`, `login-ok.html`, `login-fail.html`: nagemaakt volgens de code. Ze zijn aannames en staan zo gelabeld.

**Eenheidstests**
- Lastige HTML voor `FormParser`: `&quot;`, `&amp;` en `&copy` in een tekstvak, `</textarea>` in een waarde, een tekstvak dat met een regeleinde begint, lege waarden, een `<` in een attribuut, hoofdletters en niet-gesloten `<option>`.
- `RecordRebuilder`: het ongewijzigde 11387 geeft precies `a0`; varianten met `== Anders ==`, meerdere keuzes en subvelden.
- Tekens uit Word en tekens buiten latin-1 in `FormEncoder`.
- `FormParser`: alle velden, de selects met niet-gesloten `<option>`, de meerkeuze `a24`, `== Anders ==` met `a3b`, de radio `a21`, de verborgen velden en `a0` met de tabs.
- `FormEncoder`: latin-1, tabs en regeleinden, een teken buiten latin-1, `a24` als meerdere delen, velden zonder invoerveld uit `a0`.
- `RecordDiff` en de terugvergelijking met de aanpassingen van ZCBS.
- Conflictregels (7, stap 4.3).

**Nepserver** (JDK `HttpServer`, in de test): spreekt het contract na (inloggen, formulier, opslaan, geblokkeerd, verlopen sessie, `==Sorry`) en bewaart
de ontvangen velden zodat de test kan controleren wat er is verstuurd. Dient ook als demo van de app zonder ZCBS.

**Modulith-test**: de afhankelijkheden uit paragraaf 5.

## 11. Eerste echte run (protocol)

1. **Alleen lezen eerst.** Inloggen, het formulier van één bestaand record openen (met haar toestemming; het zet de vergrendeling), de velden controleren tegen het scherm van ZCBS zelf. Niets opslaan.
2. Eén nieuw record van de batch (net door images.exe aangemaakt, dus nog leeg) bewerken en opslaan. Daarna in ZCBS openen en vergelijken. De beheerder krijgt de mail.
3. Gaat dat goed: de rest van de batch. Bij elke onverwachte uitkomst stoppen en in ZCBS afmaken.
4. Na de run: de logbestanden van de app en van ZCBS (`archief/logging/<naam>.txt`) bekijken. Daarin staan de `upd_form=edt`-regels en type 4 voor elke opslag.

**Afbreken als:** de inlog niet werkt, het formulier niet te lezen is, of een terugcontrole verschilt. Er is dan niets verloren.

## 12. Onbevestigde punten (eerste run bevestigt ze)

1. Het inlogverzoek en het antwoord, ook op het routeradres van HKH.
2. Dat `ident` en `upd_form=edt` genoeg zijn om het formulier te krijgen.
3. Wat ZCBS toont voor een niet bestaand record.
4. De meervoudige keuze (`a24`) als meerdere delen.
5. Het gedrag van tekens als `ë`, `"`, `&` en `<` en van regeleinden.
6. Dat velden zonder invoerveld (`a17`, ...) bij meesturen behouden blijven.
7. Of het woordfilter op het POST-lichaam echt niet werkt (`$content_length` is leeg).
8. Of de nepfixtures voor de foutantwoorden lijken op de echte pagina's.
9. Of `RecordRebuilder` voor alle bestaande records exact `a0` geeft (te meten op de eerste run en daarna steekproefsgewijs).

## 13. Bouwvolgorde

1. `zcbs`: parser (Jsoup), `RecordRebuilder` en encoder met de fixture (geen netwerk), eenheidstests.
2. `zcbs`: sessie en client, nepserver, tests.
3. `items`: lijst, recordpagina, voorbeeld, opslaan; koppeling aan de testrun (nummers van de batch).
4. Controle vooraf (stap 3) en na stap 4 (bestaat het record).
5. Instellingen, meldingen, logging zonder geheimen.
6. Documentatie bijwerken (README, `docs/testen-bij-hkh.md`).

## 14. Acceptatiecriteria voor de run

- Carla kan met haar eigen naam en wachtwoord inloggen en ziet de lijst van de batch.
- Voor elk record opent de pagina met de huidige waarden van ZCBS; keuzelijsten en meerkeuze kloppen met het ZCBS-scherm.
- Een opgeslagen wijziging staat daarna zichtbaar in ZCBS, en velden die ze niet aanraakte zijn niet veranderd.
- Een record met gevulde velden zonder invoerveld (zoals veld 17) behoudt die inhoud.
- Bij een fout (sessie, vergrendeld, onverwacht antwoord) staat er een duidelijke melding en een link naar ZCBS, en gaat er niets verloren.
- Geen wachtwoorden in logs of schermen.

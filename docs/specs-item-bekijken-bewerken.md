# Specificatie: item bekijken en bewerken in de workflow

Status: voorstel, 24 oktober 2026. Bedoeld voor de eerste run met Carla in het echte archief, volgende week.
Achtergrond en bewijs staan in [zcbs-edit-formulier.md](zcbs-edit-formulier.md), [zcbs-api.openapi.yaml](zcbs-api.openapi.yaml) en
[zcbs-analyse-en-wrapper.md](zcbs-analyse-en-wrapper.md). Alles over ZCBS is daar **uit de broncode en de HTML van het bewerkscherm gelezen,
niet uitgeprobeerd**. Er is vóór de run geen testomgeving beschikbaar. Deze spec gaat daarom uit van veilig falen: als iets niet klopt,
stopt de app en kan Carla het in ZCBS zelf afmaken.

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

**Na het opslaan.** Een groen scherm "Opgeslagen en gecontroleerd" met de gelezen waarden, of een fout met uitleg (zie 8). Daarna "Volgende record".

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
  oorspronkelijke `a0`. Geen vaste lijst in de code, behalve de bekende namen en de velden die niet in het formulier staan (6.3).
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

## 7. Opslaan: stap voor stap

Uitgangspunt: de sessie van een gewoon adres verloopt na 10 minuten stilstand (op het HKH-netwerk 60), dus voor elke opslag wordt opnieuw ingelogd.

1. Gebruiker opent het record: `openForm` → `F1` (de basis). De vergrendeling is gezet.
2. Gebruiker wijzigt velden in de app. Niets gaat naar ZCBS.
3. **Voorbeeld:** de lijst van wijzigingen. Geen wijzigingen → geen POST.
4. **Opslaan:**
   1. Opnieuw inloggen met de gegevens uit het geheugen.
   2. `openForm` opnieuw → `F2` (ook dit verlengt de vergrendeling).
   3. **Conflictcontrole:** is `F2.a0` anders dan `F1.a0`, dan heeft iemand anders het record intussen veranderd. Per veld: heeft de gebruiker een veld gewijzigd dat ook
      in `F2` anders is dan in `F1`, dan is er een conflict. Toon oud, jouw waarde en nieuwe waarde, en laat de gebruiker kiezen. Zonder conflict gaan de eigen wijzigingen op `F2`.
   4. `FormEncoder` bouwt het verzoek uit `F2` met de wijzigingen. Een teken buiten latin-1: stoppen met de melding.
   5. `save` (POST). Eén poging; bij een netwerkfout niet automatisch herhalen (de opslag kan toch gelukt zijn): eerst terugleggen.
   6. `readBack`: formulier opnieuw ophalen en alle velden vergelijken met wat verstuurd is. De vergelijking houdt rekening met wat ZCBS zelf aanpast
      (tabs, regeleinden, dubbele spaties, `== Anders ==`, samenvoegen van subvelden).
5. Uitkomst: gelijk → "opgeslagen". Verschilt → "fout", toon per veld verwacht en gelezen. Nooit stil doorgaan.

Bij annuleren gebeurt er niets. De vergrendeling verloopt vanzelf na tien minuten; de app stuurt **nooit** `upd_form=clr`, omdat dat alle vergrendelingen van het HKH-adres opheft.

## 8. Foutafhandeling en meldingen

Alle meldingen in gewoon Nederlands, zonder technische tekst, met de volgende stap. Voorbeelden:
- "Record 11387 is bezig bij een ander (tot ongeveer 14:35). Probeer het later opnieuw of open het in ZCBS."
- "Je sessie bij ZCBS was verlopen. De app heeft opnieuw ingelogd; je wijzigingen zijn nog niet opgeslagen. Probeer het nog eens."
- "Opgeslagen, maar een veld lijkt anders dan je invulde: Titel. Controleer het record in ZCBS."
- "Het teken '€' kan niet worden opgeslagen in ZCBS (latin-1). Haal het weg of vervang het."
- "ZCBS gaf een onverwacht antwoord. Er is niets bevestigd. Controleer in ZCBS of het record is opgeslagen." + link.

## 9. Beveiliging en privacy

- Alleen via `https`. De app zelf draait met dezelfde bescherming als nu (nog **geen login** op de app; zie README): minimaal tot de run alleen op het eigen netwerk of `127.0.0.1`.
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

## 13. Bouwvolgorde

1. `zcbs`: parser en encoder met de fixture (geen netwerk), eenheidstests.
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

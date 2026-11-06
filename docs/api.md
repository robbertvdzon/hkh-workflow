# REST-API

Dit is de afspraak tussen de backend en de losse React-frontend. **Status: de API is gebouwd en draait naast de oude pagina's; de React-frontend ([frontend.md](frontend.md)) wordt er scherm voor scherm op gebouwd.**

## Regels

1. **De API gaat over dingen, niet over schermen.** Geen `/nieuw` of `/records/lijst`, en geen `?terug=`, redirects, flash-meldingen of htmx-headers.
2. **Engelse namen.** Paden en JSON-velden zijn Engels, net als de code. Meldingen voor de gebruiker (`detail`) blijven Nederlands.
3. **`GET` verandert niets.** Iets dat een record vergrendelt of de flow verder zet, is dus nooit een `GET`.
4. **De URL zegt waar je werkt.** De collectie staat in het pad en niet verborgen in de sessie. Daardoor werken twee tabbladen met verschillende collecties en kun je een link delen.
5. **De sessie zegt wie je bent.** Inloggen gaat met de sessie-cookie; het cookie dat je na een herstart weer inlogt, blijft.
6. **DTO's aan de rand.** Controllers in `api` geven eigen DTO's terug, nooit klassen uit `internal`. Wachtwoorden komen nooit in een antwoord.
7. **De statuscode is de uitkomst.** Een fout is altijd `application/problem+json`, met een vaste `code` voor de frontend en een `detail` om te tonen.

| Status | Betekenis | Voorbeeld van `code` |
|---|---|---|
| 200 / 204 | Gelukt, met of zonder inhoud | |
| 400 | De invoer klopt niet | `invalid-input`, `invalid-number`, `nothing-selected`, `numbers-refused` |
| 401 | Niet ingelogd, of naam en wachtwoord kloppen niet | `not-logged-in`, `wrong-credentials` |
| 403 | Ingelogd, maar geen recht | `admin-required`, `no-rights` |
| 404 | Bestaat niet | `not-found`, `record-not-found`, `no-media`, `file-not-found`, `step-not-found` |
| 409 | Botst met de huidige stand | `record-locked`, `edit-conflict`, `not-open`, `wrong-phase`, `checks-failed`, `review-pending` |
| 422 | Begrepen, maar kan zo niet | `save-blocked`, `record-unsafe` |
| 502 | ZCBS of de ftp-server gaf een fout | `zcbs-failed` |

```json
{
  "status": 409,
  "code": "record-locked",
  "detail": "Bob heeft dit record open, nog ongeveer 8 minuten."
}
```

## Sessie

| | |
|---|---|
| `GET /api/session` | Wie ben ik: `user`, `admin`, `rights`, `collections` (met `available` en `reason`), `zcbs`, `build`. Werkt ook zonder login. |
| `POST /api/session` | Inloggen met `{name, password}` |
| `DELETE /api/session` | Uitloggen |
| `POST /api/admin-session` | Beheerder inloggen met `{password}` |
| `DELETE /api/admin-session` | Beheerder uitloggen |

## Records

Onder `/api/collections/{collection}`:

| | |
|---|---|
| `GET /records?scope=mine\|all\|to-describe&offset=0&limit=20` | Recente records: `{items, more}` |
| `GET /records/next-to-describe?after=99003` | Het volgende eigen record om te beschrijven: `{number}`, of `null` |
| `GET /records/{number}/media` | Het bestand op de site: `{kind, url, title}`, of 404 |
| `PUT /records/{number}` | Opslaan met `{values, onConflict?}`: `{saved, unchanged}` |

Bewerken is een eigen ding, omdat openen in ZCBS het record vergrendelt:

| | |
|---|---|
| `POST /records/{number}/edit` | Openen en vergrendelen: `{number, fields, openedAt, lockedUntil, media, ocrText}`. 404, `record-locked` of `record-unsafe` als het niet kan. |
| `GET /records/{number}/edit` | Dezelfde stand opnieuw lezen, bijvoorbeeld na verversen van de pagina. Vergrendelt niet. |
| `POST /records/{number}/edit/preview` | Wat verandert er met `{values}`: `{changes, blockers}`. Bewaart de invoer. |
| `DELETE /records/{number}/edit` | Annuleren |

`PUT /records/{number}` geeft bij een botsing 409 `edit-conflict`, met de rijen `{field, label, mine, theirs}`. Daarna:

- **Mijn versie houden:** nogmaals `PUT` met `"onConflict": "keep-mine"`.
- **De versie uit ZCBS nemen:** nogmaals `PUT` met `"onConflict": "take-theirs"`. Dat slaat niets op en geeft de formuliervelden terug om te controleren.

`GET /records/{number}/open-in-zcbs` is de enige uitzondering op JSON: een html-pagina die de browser ingelogd naar ZCBS stuurt. Daar staat het wachtwoord in, dus de frontend-code krijgt het zo nooit te zien.

## De flow voor nieuwe bestanden

Iedere gebruiker heeft per collectie één doorloop. Daarom is het `flow` in het enkelvoud en is er geen id. Onder `/api/collections/{collection}`:

| | |
|---|---|
| `GET /flow` | De stand: `{phase, phases, review, finished}` |
| `DELETE /flow` | Opnieuw beginnen; de originelen blijven staan |
| `POST /flow/start` | Controles zijn groen: door naar bestanden kiezen |

**1. Controleren**

| | |
|---|---|
| `GET /flow/checks` | `{checks, leftovers, leftoversTarget}` |
| `POST /flow/leftovers/move` | Resten verplaatsen |
| `POST /flow/leftovers/accept` | Mijn gekozen bestanden horen erbij |
| `DELETE /flow/leftovers` | Resten verwijderen |

**2. Bestanden kiezen**

| | |
|---|---|
| `GET /flow/sources` | De mappen met scans |
| `GET /flow/sources/{source}/entries?path=` | `{crumbs, folders, files}` |
| `GET /flow/sources/{source}/details?path=` | Pagina's en afmetingen; apart omdat het even duurt |
| `GET /flow/sources/{source}/file?path=&name=&download=` | Het bestand zelf |
| `GET /flow/files` | Wat er nu gekozen is |
| `PUT /flow/files` | Kiezen met `{source, path, names}` en door naar de nummers |
| `DELETE /flow/files` | Keuze wissen |

**3. Nummers**

| | |
|---|---|
| `GET /flow/numbers` | `{count, ranges, lastNumber, suggestion, width}` |
| `PUT /flow/numbers` | Toekennen met `{start}` |

**4 t/m 7. Stappen**

`{step}` is `pdf2zcbs`, `images`, `upload` of `backup`.

| | |
|---|---|
| `GET /flow/steps` | Alle stappen met status, plan, problemen en waarschuwingen |
| `POST /flow/steps/{step}/run` | Uitvoeren. `{"answers": "auto"}` laat de app antwoorden, `{"answers": "manual"}` laat jou de vragen van het programma beantwoorden via de console. Een programma loopt door na het antwoord; de upload en de back-up zijn dan al klaar. Mislukt de stap, dan staat dat in zijn status: dat is een uitkomst en geen API-fout. |
| `POST /flow/steps/{step}/retry` | Opnieuw |
| `POST /flow/steps/{step}/approve` | "Ja, goed" |
| `GET /flow/results?step=` | Wat het programma gemaakt heeft |
| `GET /flow/results/{number}/{kind}?download=` | Een gemaakt bestand |
| `GET /flow/summary` | Het overzicht aan het eind |

**Console**

| | |
|---|---|
| `GET /flow/console` | De stand van het lopende programma: uitvoer, de vraag waar het op wacht en het voorgestelde antwoord |
| `GET /flow/console/events` | Live uitvoer en stand (server-sent events) |
| `GET /flow/console/log` | Het volledige log als platte tekst |
| `POST /flow/console/input` | Antwoorden met `{line}` |
| `POST /flow/console/stop` | Stoppen |

## Beheer

Alles hieronder vraagt de beheerder.

| | |
|---|---|
| `GET /api/settings` | Instellingen, `problems` per veld en `ftpPasswordSet`; nooit het wachtwoord zelf |
| `PUT /api/settings` | Opslaan. Bij `ftpPassword`: weggelaten is houden, `""` is wissen. |
| `POST /api/settings/ftp-test` | Verbinding testen: `{ok, message, folders}` |
| `GET /api/sessions` | `{users, flows}` |
| `DELETE /api/sessions/flows/{collection}/{user}` | Een doorloop stoppen |
| `GET /api/version` | Huidige build, build op GitHub, downloadvoortgang |
| `POST /api/version/check` | Op GitHub kijken |
| `POST /api/version/update` | Nu bijwerken |
| `PUT /api/version/nightly` | Nachtelijke update aan of uit met `{enabled}` |
| `GET /api/program-logs` | Eerdere runs |
| `GET /api/program-logs/{file}` | Eén log |
| `DELETE /api/program-logs/{file}` | Eén log verwijderen |
| `DELETE /api/program-logs` | Alle logs verwijderen |

## Bewuste veranderingen ten opzichte van nu

- **"Klaar" blijft staan.** Nu is die pagina één keer te zien, omdat het ophalen de flow wist. Dat mag niet bij een `GET`, dus de flow blijft op "Klaar" staan tot `DELETE /flow`.
- **Records hebben geen lijst met gekozen records meer.** Een nummer openen is direct `POST /records/{number}/edit`, zonder de tussenstap `/records/kies`.

- **De technische pagina `/test` komt niet terug.** Een stap overslaan, een stap als gedaan markeren, starten zonder de eerste stappen en een programma in een eigen Windows-venster draaien vervallen. Een programma handmatig uitvoeren en zelf de invoer geven hoort bij de flow en blijft (beslissing van Robbert, 6 november 2026).
- **Twee records tegelijk.** Een tweede record openen sluit het eerste niet meer, dus twee tabbladen kunnen naast elkaar werken.

## Bekende beperking

Iemand die in ZCBS alleen mag lezen, krijgt bij `POST .../edit` een 502 `zcbs-failed` in plaats van 403: ZCBS toont dan de gewone weergave en de app herkent dat niet apart. Er wordt niets gewijzigd.

## OpenAPI

De backend publiceert de beschrijving op `/api/openapi.json`. Een kopie staat in `frontend/openapi.json`; daaruit maakt de build de TypeScript-types. Een scenario controleert dat de kopie gelijk is aan wat de backend publiceert, dus een gewijzigd endpoint valt altijd op. Bijwerken doe je met `tools/update-openapi.sh`; past de frontend er dan niet meer bij, dan faalt de build.

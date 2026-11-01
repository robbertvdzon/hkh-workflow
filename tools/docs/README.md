# Hulpmiddelen voor de handleidingen

De handleidingen in [../../manual](../../manual/README.md) bevatten screenshots en bestaan ook als pdf. Beide worden met scripts gemaakt, zodat ze na een wijziging van de
app opnieuw te maken zijn. Alles draait alleen op de lokale testomgeving met fictieve gegevens; er wordt nooit iets naar het echte archief of de echte server gestuurd.

## Screenshots opnieuw maken

`maak-screenshots.cjs` loopt de hele app door in Google Chrome (via Playwright), markeert de knoppen met een genummerd rood kader en bewaart de plaatjes in
`manual/afbeeldingen/`. `schermen.cjs` bevat de hulpfuncties (inloggen, markeren, bewaren). Tijdelijke mappen van de test worden op het scherm vervangen door
`K:\…` en `\\server\werkmap\…`, en de testgebruiker heet daar Carla; de app zelf verandert niet.

Voorbereiding (eenmalig en bij elke nieuwe ronde):

1. Start de test-ZCBS (`tools/docker`) en de app op poort 9010 met de test-ZCBS, de nep-programma's (`tools/mock`), een leeg verwerkt-log en de meldingen aan hkh-admin uit
   (`HKH_WORKFLOW_ADMIN_LOG_URL=` leeg). Gebruik een eigen kopie van de instellingen en de mappen, niet die van de echte installatie.
2. Zet in de eerste map met scans een submap `Kerkplein` met `kerkplein_1950.pdf`, `kerkplein_1962.pdf`, `raadhuis_voorgevel.pdf` en `markt_1935.jpg`, en een submap
   `Schoolfoto's` met `school_001.pdf`, `school_002.pdf` en `klas_foto.jpg`. De bestanden mogen nog niet als verwerkt zijn aangemerkt.
3. De testgebruiker van de test-ZCBS staat in `ZCBS_USER` en `ZCBS_PASSWORD` (standaard de lokale testgebruiker) en het beheerderswachtwoord van de lokale app in
   `ADMIN_PASSWORD`.

```bash
NODE_PATH=$(npm root -g) node tools/docs/maak-screenshots.cjs
```

Vereist Node, Playwright (`npm i -g playwright`) en Google Chrome (Chrome toont pdf's in de popups, de ingebouwde headless Chromium niet). `APP_URL` wijst een andere app aan.

## PDF's maken

```bash
tools/docs/bouw-pdf.sh                      # alle handleidingen en quickstarts
tools/docs/bouw-pdf.sh manual/invoeren.md   # één bestand
```

`Md.java` zet de Markdown om in HTML (commonmark, uit de Maven-opslag; de build van de app haalt die al binnen), `pdf.css` bepaalt de opmaak (A4, paginanummers, een quickstart met
grotere letters en kleinere plaatjes) en Google Chrome drukt het af naar `manual/pdf/`. Links naar andere handleidingen worden links naar de bijbehorende pdf.

# Hulpmiddelen voor de handleidingen

De handleidingen in [../../manual](../../manual/README.md) bevatten screenshots en bestaan ook als pdf. Beide worden met scripts gemaakt, zodat ze na een wijziging van de
app opnieuw te maken zijn. Alles draait alleen op de lokale testomgeving met fictieve gegevens; er wordt nooit iets naar het echte archief of de echte server gestuurd.

## Screenshots opnieuw maken

`maak-screenshots.cjs` loopt de hele app door in Google Chrome (via Playwright), markeert de knoppen met een genummerd rood kader en bewaart de plaatjes in
`manual/afbeeldingen/`. `schermen.cjs` bevat de hulpfuncties (inloggen, markeren, bewaren). Tijdelijke mappen van de test worden op het scherm vervangen door
`K:\…` en `\\server\werkmap\…`, en de testgebruiker heet daar Carla; de app zelf verandert niet.

Voorbereiding (bij elke nieuwe ronde): `tools/docs/voorbereiden.sh` zet alles klaar en start de app op poort 9010. Het script

1. controleert dat de test-ZCBS in Docker (`tools/docker/zcbs`, poort 8081 en ftp 21) draait;
2. maakt in een werkmap (standaard `/private/tmp/hkh-docs`) de mappen, de nep-programma's (`tools/mock`), de scans (`Kerkplein` met `kerkplein_1950.pdf`, `kerkplein_1962.pdf`,
   `raadhuis_voorgevel.pdf` en `markt_1935.jpg`, en `Schoolfoto's` met `school_001.pdf`, `school_002.pdf` en `klas_foto.jpg`), een eigen instellingenbestand en een leeg verwerkt-log;
3. downloadt de nieuwste release-jar van GitHub (`gh release download`; of geef zelf een jar mee) en start hem met de meldingen aan hkh-admin uit en beheerderswachtwoord `abc`.

Bouw voor de screenshots niet met `mvn` in dezelfde map als iemand anders: gebruik de release-jar. De testgebruiker van de test-ZCBS staat in `ZCBS_USER` en `ZCBS_PASSWORD`
(standaard de lokale testgebruiker) en het beheerderswachtwoord in `ADMIN_PASSWORD`. De bestanden mogen nog niet als verwerkt zijn aangemerkt; het script begint daarom steeds met een leeg log.
`tools/docs/voorbereiden.sh --stop` stopt de app weer.

Het buildnummer onderin het menu wordt op de plaatjes verborgen en op de pagina Versie vervangen door voorbeeldwaarden, zodat de handleiding niet bij elke build verouderd is.

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

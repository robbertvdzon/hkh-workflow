# Frontend

De nieuwe schermen zijn een losse React-app in `frontend/`, die alleen met de [REST-API](api.md) praat. De gebouwde frontend gaat mee in de jar, dus op de server is geen Node nodig en blijft het één programma.

**Status:** de basis staat: inloggen, de keuze van de collectie, inloggen als beheerder en het menu. Records, instellingen en de flow komen hierna. Tot de oude schermen weg zijn, woont de frontend onder `/app`; daarna gaat hij naar de root.

## Ontwikkelen

Start de backend op poort 9000 en daarnaast:

```bash
cd frontend && npm install && npm run dev
```

De frontend staat dan op http://localhost:5173/app/, ververst vanzelf en stuurt `/api` door naar de backend.

`mvn clean verify` bouwt de frontend mee: eerst de types, dan `tsc`, ESLint en Vite. Wie alleen aan de backend werkt en sneller wil, slaat dat over met `-Dskip.npm -Dskip.installnodenpm`. De oude build in `target/classes/frontend` blijft dan staan zolang er geen `clean` draait.

## Afspraken

- **Techniek:** TypeScript, Vite, React Router, TanStack Query en componenten in de stijl van shadcn/ui in `src/components/ui`.
- **Alleen de API.** De frontend gebruikt alleen `/api` en leest nergens html van de oude schermen.
- **Types komen uit de API.** Een kopie van de beschrijving staat in `frontend/openapi.json`; de build maakt daaruit `src/api/schema.ts`. Schrijf geen eigen types voor wat de API teruggeeft.
- **Als de API verandert:** draai `tools/update-openapi.sh` en commit `frontend/openapi.json`. Vergeet je dat, dan faalt een scenario; past de frontend niet meer bij de nieuwe API, dan faalt de build.
- **Fouten:** `unwrap` in `src/api/client.ts` maakt van elke fout een `ApiError`, en `errorMessage` geeft de tekst om te tonen. Kijk naar `error.code`, nooit naar de tekst.
- **Kleuren en letters** staan in `src/index.css`. Componenten gebruiken die namen en geen losse kleurcodes.
- **Tekst op het scherm** is Nederlands, de code is Engels. Adressen van schermen zijn Nederlands (`/collectie/archief`), adressen van de API Engels.

## Mappen

| | |
|---|---|
| `src/main.tsx` | De schermen en hun adressen |
| `src/api/` | De verbinding met de API; per onderwerp een bestand met hooks |
| `src/pages/` | Een bestand per scherm |
| `src/components/` | Het menu en de controle op inloggen |
| `src/components/ui/` | Knoppen, invoervelden en dergelijke, zonder kennis van de app |

Spring Boot serveert de frontend uit de jar met `ux/internal/FrontendConfig.kt`: een adres dat geen bestand is, krijgt `index.html`, en de frontend kiest dan zelf het scherm.

## Testen

De scenario's in `features/screens.feature` openen de schermen in een echte browser (Chromium via Playwright, gestart door `integration/Screen.kt`), tegen dezelfde app als de andere scenario's. Playwright downloadt Chromium de eerste keer zelf. Een fout in het JavaScript laat het scenario falen, en van elk scenario komt een schermafbeelding in `target/screenshots`.

De regels van de flow en de records test je in de scenario's via de API. Een scenario over een scherm bewijst dat het scherm de API goed gebruikt.

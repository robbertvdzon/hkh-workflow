# Documentatie ZcbsServer (hkh-workflow)

Alle specificaties en achtergronddocumenten van de app staan in deze map. De gebruikershandleidingen staan in [../manual](../manual/README.md).
Stand: 1 november 2026.

## Beginnen

| Je wilt… | Lees |
|---|---|
| Weten **wat de app doet** en voor wie | [functioneel-ontwerp.md](functioneel-ontwerp.md) |
| Weten **hoe de app is gebouwd** (modules, opslag, verbindingen, testen) | [technisch-ontwerp.md](technisch-ontwerp.md) |
| De **besluiten, status en volgende stappen** zien | [specs.md](specs.md) (logboek van beslissingen; het functionele en technische ontwerp hierboven zijn de actuele samenvatting) |
| De app **gebruiken** | [../manual/README.md](../manual/README.md) (handleidingen en quickstarts) |

## Functioneel

| Document | Inhoud |
|---|---|
| [functioneel-ontwerp.md](functioneel-ontwerp.md) | De app als geheel: rollen, inloggen, nieuwe bestanden in zeven fasen, records aanpassen, beheer, meer gebruikers, logboek |
| [specs-nieuwe-bestanden.md](specs-nieuwe-bestanden.md) | Het ontwerp van de flow Nieuwe bestanden (30 oktober 2026): fasen, schermen, regels |
| [specs-item-bekijken-bewerken.md](specs-item-bekijken-bewerken.md) | Het ontwerp van het bewerken van records: formulier, voorbeeld, conflicten, controles |
| [ZcbsServer UX.pdf](ZcbsServer%20UX.pdf) | Het oorspronkelijke UX-ontwerp (schermen) |

## Technisch

| Document | Inhoud |
|---|---|
| [technisch-ontwerp.md](technisch-ontwerp.md) | Architectuur, modules, meerdere gebruikers, bewaarde bestanden, verbindingen, bouwen en testen |
| [specs.md](specs.md) | Techniek, modules, routes en instellingen zoals gebouwd, bevindingen uit het testen, besluitenlogboek, status |
| [zcbs-edit-formulier.md](zcbs-edit-formulier.md) | Hoe het bewerkformulier van ZCBS in elkaar zit (velden, a0, vergrendeling, opslaan) |
| [zcbs-api.openapi.yaml](zcbs-api.openapi.yaml) | De HTTP-aanroepen naar ZCBS als OpenAPI-beschrijving |
| [auto-update.md](auto-update.md) | Voorstel: draaien als Windows-service met automatisch bijwerken (nog niet gebouwd) |

## Achtergrond over ZCBS en de programma's

| Document | Inhoud |
|---|---|
| [zcbs-achtergrond.md](zcbs-achtergrond.md) | Hoe ZCBS, pdf2zcbs.exe en images.exe werken, vragen en antwoorden van de programma's |
| [zcbs-analyse-en-wrapper.md](zcbs-analyse-en-wrapper.md) | Bevindingen uit de serverbestanden en het ontwerp van de wrapper |
| [zcbs-lokaal-testresultaten.md](zcbs-lokaal-testresultaten.md) | Resultaten van het testen tegen de lokale ZCBS |
| `042.pdf`, `050.pdf`, `zcbs-technische-documentatie-v4.0.pdf` | De handleidingen van ZCBS en images.exe |

## Testen

| Document | Inhoud |
|---|---|
| [testen-bij-hkh.md](testen-bij-hkh.md) | Checklist om de echte programma's bij HKH stap voor stap te testen |
| `../tools/mock/README.md` | Nep-programma's om zonder Windows te testen |
| `../tools/windows-testlaptop/` | Scripts voor de testlaptop met de echte exe's |

## Niet in git

`docs/server/` (bestanden van de echte server, met gevoelige gegevens) en `docs/docs-19-oct-testsessie/` staan bewust niet in de repository.

# Het ZCBS-bewerkformulier van archief

Bron: de HTML van de bewerkpagina van record 11387, geplakt op 24 oktober 2026. Aangevuld met een paar regels uit `objecten.pl`.
Dit is een **specificatie**. Er is niets van uitgeprobeerd tegen de server. Namen van personen zijn vervangen door `<naam>`.

## 1. Het formulier

```html
<form name="zcbsform" method="POST" enctype="multipart/form-data"
      accept-charset="ISO-8859-1" action="/cgi-bin/archief.pl">
```

- Het adres is `https://www.historischekringheemskerk.nl/cgi-bin/archief.pl`.
- Het is `multipart/form-data` en de tekens gaan als **ISO-8859-1**. De client moet latin-1 sturen, geen UTF-8.
- Er is **geen sessie- of csrf-token** in het formulier. De server herkent de gebruiker aan de cookies van de inlog, of aan het IP-adres.
- De knop VERWERK heeft geen naam en stuurt dus zelf geen veld mee. De knop "+ edit volgende" zet via JavaScript `write=2`,
  `misc=0` en `ident_next` op het volgende nummer.

## 2. Het formulier ophalen

Met een GET op de bewerkpagina, ingelogd:

`archief.pl?ident=<nummer>&upd_form=edt`

De pagina zelf gebruikt een langere vorm met extra zoekparameters. Of `ident` en `upd_form` genoeg zijn, is niet uitgeprobeerd. Het
openen van deze pagina vergrendelt het record waarschijnlijk tien minuten voor anderen. Open hem dus alleen als iemand echt gaat bewerken.

## 3. De verborgen velden

Stuur ze terug zoals je ze ontvangt.

| Naam | Voorbeeld | Betekenis |
|---|---|---|
| `ident` | `11387` | Het recordnummer |
| `upd_form` | `edt` | Bewerken. Andere standen zijn `new`, `cop`, `del` en `clr` |
| `write` | `1` | 1 is opslaan en het record tonen. 2 is opslaan en het volgende bewerken |
| `ident_next` | `11387` | Bij `write=2` het volgende nummer |
| `misc` | `1` | Weergave na het opslaan. Bij "edit volgende" is het `0` |
| `misc2`, `search_dt`, `veld`, `conf`, `spec`, `sub` | leeg | Weergave en navigatie |
| `search` | `VELD number 11387` | De zoekopdracht waaruit je kwam |
| `bool`, `inword`, `ocr`, `display`, `istart`, `sort`, `onload` | `and`, `1`, `1`, `list`, `1`, `02`, `11387` | Zoekweergave voor het terugkeren |
| `a32` | `260107-10:00|system` | De aanmaakstempel |
| `a33` | `260107-11:47|<naam>` | De stempel van het laatste beschrijven |
| `a34` | `261024-19:46|<naam>` | Het **nieuwe** stempel. Het bevat het tijdstip waarop het formulier is geopend en de naam van de ingelogde gebruiker |
| `a0` | `HKH⇥997⇥11387⇥...` | Het volledige, ongewijzigde record, 35 velden met echte tab-tekens ertussen |

- `a0` is de oude versie. ZCBS vergelijkt hem met de nieuwe waarden en bepaalt of er iets is gewijzigd. Behandel hem als een
  ondoorzichtige tekst en laat de tabs ongemoeid.
- Het stempel in `a34` is dat van het openen. Houdt de app het formulier lang vast, dan is dat het tijdstip van het openen, niet van het opslaan.

## 4. De zichtbare velden

De naam `aN` hoort bij veld `N` van het record. De namen komen uit de config van archief.

| Veld | Formuliernaam | Soort | Voorbeeld |
|---|---|---|---|
| 5 Titel | `a5` | tekst | `Waterhuishouding rondom Hoogovens` |
| 7 Auteur(s) | `a7` | tekst | leeg |
| 8 Datum | `a8` | tekst | `03-09-1970` |
| 6 Omschrijving | `a6` | tekstvak | een lange tekst |
| 22 Oorspronkelijk archief | `a22` en `a22b` | keuzelijst met Anders | `HKH Jan de Ruijter` |
| 24 Thema | `a24` en `a24b` | meervoudige keuzelijst met Anders | `Locale geschiedenis en bewoners` |
| 3 Type publicatie | `a3` en `a3b` | keuzelijst met Anders | Anders met `waterhuishouding` |
| 4 Trefwoord | `a4` | tekst | leeg |
| 9 Uitgever | `a9` en `a9b` | keuzelijst met Anders | `Gemeente Heemskerk` |
| 11 Totaal aantal pagina's | `a11` | tekst | `1` |
| 25 Plaats | `a25` en `a25b` | keuzelijst met Anders | `Heemskerk` |
| 26 Straatnaam | `a26` | tekst | leeg |
| 18 Aanvullend materiaal | `a18` | tekst | leeg |
| 10 Conditie | `a10` en `a10x` | tekst en opmerking | leeg |
| 12 Verwerving | `a12` | tekst | leeg |
| 13 Eigenaar | `a13` | tekst | leeg |
| 14 ZCBS inv.nr. | `a14` | tekst | leeg |
| 21 Plaatsing op internet | `a21` | keuzerondje | zie hieronder |
| 16 Doos nummer | `a16` | tekst | leeg |
| 15 Map nummer | `a15` en `a15x` | tekst en tijdelijk | `92` |
| 27 Locatie op PC | `a27` | tekst | leeg |
| 19 Interne opm. | `a19` | tekstvak | leeg |

- Niet te bewerken zijn het documentnummer en de stempels voor aangepast en aangemaakt. Veld 0, 1 en 2 komen alleen via `a0` mee.
- Voor de velden 23 en 28 tot en met 31 staat geen invoerveld in het formulier, omdat ze in de config geen naam hebben. Ze blijven
  alleen in `a0`. Of ze bij het opslaan behouden blijven, moet nog worden gecontroleerd.
- **Veld 27, Locatie op PC, is bewerkbaar.** De app kan daar de originele bestandsnaam in zetten, zoals het UX-ontwerp wil.

## 5. Keuzelijsten en subvelden

- **Keuzelijsten.** De waarde van een optie is dezelfde tekst als wat je ziet. De eerste optie, `==Maak uw keuze==`, is leeg.
- **Anders.** Kies je `== Anders ==`, dan komt de tekst uit `aNb`. ZCBS vervangt `== Anders ==` door die tekst als `aNb` gevuld is. Staat de
  opgeslagen waarde niet in de lijst, dan zet het formulier zelf `== Anders ==` aan en vult het het `b`-veld. In het voorbeeld is dat zo
  voor Type publicatie. De app moet dat nadoen, anders wist een gewone lijst die waarde.
- **Meervoudige keuze.** Bij `a24` stuur je een deel `a24` per gekozen optie. De pagina toont het resultaat als `waarde; waarde`. Dat
  ZCBS ze zo samenvoegt, is afgeleid uit de weergave en de data, en nog niet uitgeprobeerd.
- **Subvelden.** Bij conditie en map nummer voegt ZCBS de twee invoervelden samen als `hoofdveld|subveld`, alleen als het subveld gevuld is.
  Een verticale streep in die velden is dus het scheidingsteken. Schrijf er nooit zelf een in.
- **Plaatsing op internet, `a21`.** Neen heeft waarde `0`. Ja heeft een dagcode: het aantal dagen sinds 9 juli 1999, dus `9679` voor
  7 januari 2026. Staat een record al online, dan blijft die code staan. Anders levert het formulier zelf de code van vandaag. Neem
  de waarde dus uit het formulier en reken hem niet zelf uit.

## 6. Bijlagen

Het formulier heeft ook velden voor bijlagen:

| Naam | Betekenis |
|---|---|
| `add_copy` | Een eerder geüploade bijlage kiezen. Leeg is "nog niet eerder geüpload" |
| `add_file` | Het bestand zelf, met een lege bestandsnaam als er niets is gekozen |
| `add_ident` | De naam op de server, na het voorvoegsel `11387_`, met een toegestane extensie |
| `add_inter` | `0` is ja, `1` is neen op internet. Standaard `1` |
| `add_title` | De titel van de bijlage, verplicht bij een upload |

De pagina waarschuwt dat het aanpassen van een bijlage gelijktijdige wijzigingen in de metadata ongedaan maakt. Voor een gewone bewerking
stuur je deze velden mee zoals een browser dat zonder keuze doet, dus leeg met `add_inter=1`. Bijlagen zelf pakken we niet op in de eerste versie.

## 7. Wat je voor een bewerking verstuurt

1. Haal het formulier op en lees alle velden met hun waarden uit.
2. Vervang alleen de waarden die de gebruiker wijzigt, en laat de rest zoals het kwam. Dat geldt ook voor de verborgen velden en `a0`.
3. Stuur het als `multipart/form-data` in latin-1, met de cookies van de ingelogde gebruiker.
4. Lees het record daarna terug en vergelijk het met wat je wilde.

ZCBS vergelijkt `a0` alleen met de nieuwe waarden. Het controleert niet of een ander het record intussen heeft gewijzigd. De laatste gewijzigde
versie wint. De app moet dat zelf voor het opslaan controleren, bijvoorbeeld met de stempels uit het formulier.

## 8. Voorbeeld

Een aanpassing van alleen de titel, met de overige velden zoals ontvangen. Alle waarden zijn voorbeelden.

```bash
curl -b cookies.txt https://www.historischekringheemskerk.nl/cgi-bin/archief.pl \
  -F ident=11387 -F upd_form=edt -F write=1 -F ident_next=11387 -F misc=1 \
  -F search="VELD number 11387" -F bool=and -F inword=1 -F ocr=1 -F display=list \
  -F istart=1 -F sort=02 -F onload=11387 \
  -F a32="260107-10:00|system" -F a33="260107-11:47|<naam>" -F a34="261024-19:46|<naam>" \
  -F a0=$'HKH\t997\t11387\t...\t...' \
  -F a5="Waterhuishouding rondom Hoogovens, 1970" \
  -F a7= -F a8=03-09-1970 -F a6="..." -F a22="HKH Jan de Ruijter" -F a22b= \
  -F a24="Locale geschiedenis en bewoners" -F a24b= -F a3="== Anders ==" -F a3b=waterhuishouding \
  -F a4= -F a9="Gemeente Heemskerk" -F a9b= -F a11=1 -F a25=Heemskerk -F a25b= \
  -F a26= -F a18= -F a10= -F a10x= -F a12= -F a13= -F a14= -F a21=9679 \
  -F a16= -F a15=92 -F a15x= -F a27= -F a19= \
  -F add_copy= -F add_file=@/dev/null -F add_ident= -F add_inter=1 -F add_title=
```

De tekens in een terminal zijn UTF-8. Een echte client moet de waarden als latin-1 coderen.

## 9. Opmerkingen

- Het formulier stuurt geen enkel geheim of token mee. Wie het formulier kan versturen met een geldige inlog, kan alles bewerken.
- De hidden velden `a32`, `a33` en `a34` laten zien wie het record heeft aangemaakt, beschreven en als laatste heeft geopend. In het
  voorbeeld bevat `a34` de naam van de gebruiker die het formulier opende.
- De pagina geeft na het opslaan het gewijzigde record weer als `misc=1`. De mail naar de beheerder en de cumulatieve back-up
  volgen uit de code van `objecten.pl`, zie `docs/zcbs-analyse-en-wrapper.md`, A3.

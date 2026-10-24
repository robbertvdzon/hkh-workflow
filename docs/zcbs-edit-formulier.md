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
- De knop VERWERK heeft geen naam en stuurt dus zelf geen veld mee. De knop "+ edit volgende" (`write=2`) staat in de HTML van archief **uitgecommentarieerd**:
  de app gebruikt dus `write=1`.

## 2. Het formulier ophalen

Met een GET op de bewerkpagina, ingelogd:

`archief.pl?ident=<nummer>&upd_form=edt`

De pagina zelf gebruikt een langere vorm met extra zoekparameters. Of `ident` en `upd_form` genoeg zijn, is niet uitgeprobeerd.
Het openen van deze pagina **vergrendelt het record** tien minuten voor andere gebruikers (zie paragraaf 10). Open hem dus alleen als iemand echt gaat bewerken.

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
- Voor de velden 17, 20, 23 en 28 tot en met 31 staat geen invoerveld in het formulier, omdat ze in de config geen naam hebben. **ZCBS
  neemt ze bij het opslaan niet over uit `a0`, maar leest ze uit het formulier. Zonder invoerveld worden ze dus leeg opgeslagen.**
  Zie paragraaf 10: dat gaat bij ongeveer 23 records echt data kosten. De app moet deze velden zelf als `a17`, `a20`, `a23` en
  `a28` t/m `a31` meesturen, met de waarde uit `a0`.
- **Veld 27, Locatie op PC, is bewerkbaar.** De app kan daar de originele bestandsnaam in zetten, zoals het UX-ontwerp wil.

## 5. Keuzelijsten en subvelden

- **Keuzelijsten.** De waarde van een optie is dezelfde tekst als wat je ziet. De eerste optie, `==Maak uw keuze==`, is leeg.
- **Anders.** Kies je `== Anders ==`, dan komt de tekst uit `aNb`. ZCBS vervangt `== Anders ==` door die tekst als `aNb` gevuld is. Staat de
  opgeslagen waarde niet in de lijst, dan zet het formulier zelf `== Anders ==` aan en vult het het `b`-veld. In het voorbeeld is dat zo
  voor Type publicatie. De app moet dat nadoen, anders wist een gewone lijst die waarde.
- **Meervoudige keuze.** Bij `a24` stuur je een deel `a24` per gekozen optie. De pagina toont het resultaat als `waarde; waarde`. ZCBS
  voegt ze samen met een NUL-teken en maakt daar bij het opslaan `; ` van (`objecten.pl` regel 17323). Dat is uit de code gelezen
  en nog niet uitgeprobeerd.
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

Let op: volgens de code (`objecten.pl`, rond regel 280) weigert het script verzoeken met `curl` in de User-Agent met een lege pagina (`==Sorry:3==`).
Dat is uit de code gelezen en nog niet uitgeprobeerd. Daarom staat er een eigen User-Agent in dit voorbeeld, bijvoorbeeld `-A "hkh-workflow"`.

```bash
curl -b cookies.txt -A "hkh-workflow" https://www.historischekringheemskerk.nl/cgi-bin/archief.pl \
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

## 10. Wat de code zegt (onderzoek 24 oktober)

Uit `objecten.pl` (OUTPUT_UPDATE vanaf regel 16997, ZCBS_MENU vanaf 24582, LOCK_OBJECT vanaf 27940) en `archief/misc/cfg-cbs.pl`.

### Waar komen de keuzelijsten vandaan?
- Uit de configuratie van de collectie: `cgi-bin/archief/misc/cfg-cbs.pl`. Elke lijst is een variabele met één waarde per regel:
  `$VELD03` (Type publicatie), `$VELD09` (Uitgever), `$VELD22` (Oorspronkelijk archief), `$VELD24` (Thema) en `$VELD25` (Plaats).
- Dat zijn alle vijf de keuzelijsten van archief. De rest is een tekstveld. Staat er in de config een getal, dan is het veld een
  tekstveld van die breedte (bijvoorbeeld `$VELD15 = 10`).
- Speciale regels: `==Maak uw keuze==` is de lege eerste optie, `== Anders ==` toont het veld "namelijk", `MULTIPLE` (of `MULTIPLE8`) maakt
  er een meervoudige keuze van, en een regel als `== Koppen ==` is een tussenkopje (optgroup) en geen keuze. Een stuk na een `|` is een link en telt niet mee.
- ZCBS **controleert niet** of een opgeslagen waarde in de lijst staat. Elke tekst wordt geaccepteerd. De lijst stuurt alleen de invoer.
- Een waarde die niet in de lijst staat, krijgt in het formulier `== Anders ==` met de waarde in het "namelijk"-veld.
- Slaat iemand een nieuwe Anders-waarde op, dan mailt ZCBS de beheerder (`$EMAIL_BB`) met "Keuze ANDERS in listmenu", zodat die hem aan de lijst kan toevoegen.
- De config is UTF-8 en de pagina latin-1. ZCBS zet dat zelf om. Het formulier is dus de beste bron voor de lijst: lees de `<option>`-regels
  uit de HTML van het formulier en bewaar geen eigen kopie.

### Meervoudige keuze (Thema)
- Het formulier heeft een `<select multiple>` met `name="a24"`. Een browser stuurt dan één deel `a24` per gekozen optie.
- De formulierlezer van ZCBS (`cgi-lib.pl`, niet gedownload) voegt gelijknamige delen samen met een NUL-teken. Bij het opslaan wordt dat `; `.
- Bij het tonen wordt de opgeslagen waarde op `;` gesplitst en worden de passende opties geselecteerd. Waarden die niet in de lijst staan, komen in het "namelijk"-veld.
- In de app: toon een meerkeuzeveld, en stuur één `a24` per keuze. Staan er geen keuzes, dan stuur je er één lege (of `==Maak uw keuze==`; ZCBS haalt dat weg).
- Er is in de data nu weinig meervoudige keuze: het gaat om `a24` in archief. Test dit als eerste.

### Opslaan: wat ZCBS precies doet
1. Alleen met `write=1` (of 2) en rechten `1`. Een gebruiker "extern" kan niets.
2. Het recordnummer moet binnen het bereik van de gebruiker liggen (`cluster1` tot `cluster2` in `permit.lst`). Anders wordt `upd_form` leeggemaakt en gebeurt er niets.
3. `LOCK_OBJECT` draait **ook bij het opslaan**. Heeft een andere gebruiker het record de laatste 10 minuten geopend, dan komt de pagina
   "Tijdelijk geblokkeerd object" en wordt er niets opgeslagen. Dezelfde gebruiker blokkeert zichzelf niet. In de testversie (`archief2`) is er geen vergrendeling.
4. Alle velden komen uit het formulier (`$a3 = $query{"a3"}` enz.), niet uit `a0`.
5. `== Anders ==` wordt vervangen door `aNb`. Subvelden worden `veld|subveld`. Tabs worden spaties, regeleinden in tekstvakken worden `<br>`, `\r` een spatie, dubbele spaties één.
   Een kop `==Maak uw keuze==` en `== Nog onbekend ==` worden weggehaald.
6. Het nieuwe record wordt vergeleken met `a0` over de velden 0 tot en met 32. Zijn ze gelijk, dan wordt er **niets** opgeslagen (en ook geen mail).
7. Anders gaat de regel naar `updates.txt` (`updates2.txt` in de testversie). Alleen in productie ook naar `backup1/upd-cumm.txt` en per mail naar `$EMAIL_TO`.
8. Veranderen de velden samen meer dan 125 tekens in lengte, dan worden ook "beschreven" (`a33`) en "aangepast" (`a34`) gezet. Anders alleen `a34`.
9. `a21` wordt `0` voor records op het prikbord. Autocorrectie van hoofdletters staat uit.
10. Een succes of fout staat niet in een aparte code. Lees het record daarna terug.

### Valkuilen die het onderzoek opleverde
- **Een User-Agent met `curl` wordt volgens de code geweigerd** met een lege pagina (`==Sorry:3==`). Nog niet uitgeprobeerd. Gebruik een eigen naam.
- **Een filter op de inhoud van het verzoek.** Staat in de GET-regel of in het POST-lichaam een van `onerror`, `onload`, `onclick`, `onfocus`,
  `onselect`, `onsubmit`, `script` (niet direct na "java"), `style` of `curl`, dan antwoordt ZCBS met een lege pagina (`==Sorry:2==`).
  Omdat `a0` het hele record meestuurt, geldt dat ook voor tekst die er al staat. In archief gaat het om **2 van 1970 records**:
  10558 ("kandidaatsscriptie") en 11162 ("inscriptie"). Die zijn ook in ZCBS zelf niet te bewerken. De app moet dit vooraf controleren en melden.
  Ook nieuwe tekst met "description", "manuscript", "style" enzovoort faalt.
- **Velden zonder invoerveld worden gewist** (zie paragraaf 4). In de data zijn er zo'n 14 records met inhoud in veld 17 (zoals
  "|Document niet geheel gescand.") en zo'n 9 met een getal in veld 28. Bewerk je die via het ZCBS-formulier, dan verdwijnt die inhoud.
  De app moet ze meesturen.
- **De vergrendeling hoort bij de gebruikersnaam**, niet bij het tabblad. Twee mensen met dezelfde inlog blokkeren elkaar niet.
- **Geen controle op tussentijdse wijzigingen.** Een tweede bewerking nadat de vergrendeling is verlopen, overschrijft de eerste.
  Vergelijk dus zelf `a33`/`a34` en het record met wat je ophaalde.
- **De cookie.** Na inloggen zet ZCBS `zcbs-user=<naam>|<crypt(wachtwoord)>` (DES-crypt met vaste "salt" 99) en `zcbs-app-user` (24 uur).
  De cookie bevat dus een zwakke hash van het wachtwoord. Bewaar hem alleen in het geheugen van de app.
- **Testversie.** `archief2.pl` laadt `objecten2.pl`, `archief2.txt`, `updates2.txt` en eventueel `cfg-cbs2.pl`. Niets daarvan zit in de download.
  Alleen systeembeheer mag er schrijven, en er is geen vergrendeling.

### Wat nog moet worden uitgeprobeerd
1. Het inloggen: welke velden en welke cookies (op `archief2.pl`).
2. Dat `GET archief.pl?ident=N&upd_form=edt` het formulier geeft.
3. Een bewerking met alleen een titelwijziging, en het terugkijken van het record.
4. Een meervoudige keuze (Thema) met twee waarden.
5. Een record met veld 17 gevuld, om te zien dat het blijft staan als de app `a17` meestuurt.
6. Een record met een lange omschrijving en regeleinden, en tekens als `ë`, `"` en `&`.
7. Wat de respons is bij een geblokkeerd record en bij het filter.

## 11. Inloggen vanuit de app (Kotlin)

Uit `AUTORISATIE` in `objecten.pl` (vanaf regel 18570). Niets uitgeprobeerd.

ZCBS bepaalt wie je bent in deze volgorde:
1. **IP-adres** (eerste adres uit `X-Forwarded-For`, anders `REMOTE_ADDR`) staat in `permit.lst`: dan ben je de gebruiker van die regel, zonder wachtwoord. Staat er
   `<ROUTER>` bij, dan deelt het adres meerdere gebruikers en moet je met naam en wachtwoord kiezen (zie punt 3). Stuur dus **nooit zelf een `X-Forwarded-For`-header mee**.
2. **Cookie** `zcbs-user=<naam>|<hash>` (naast `zcbs-date` en `zcbs-rout`). Een cookie met een andere gebruiker dan de IP-regel gaat voor op het IP-adres.
3. **Inloggen** met `user` en `pswd`.

### Inloggen
- `POST /cgi-bin/archief.pl` met `application/x-www-form-urlencoded` en de velden `user` en `pswd` (het formulier van `archief.pl?user=reset`;
  wachtwoord alleen `A-Za-z0-9_`). Er is geen token.
- Klopt het, dan staat in het antwoord "Gebruikersnaam/wachtwoord combinatie herkend" en komen er `Set-Cookie`-regels:
  `zcbs-user=<naam>|<crypt(wachtwoord,99)>`, `zcbs-rout`, `zcbs-date=<minuten>` en `zcbs-app-user` (24 uur). Klopt het niet, dan ben je "extern" met
  "onjuist" in de tekst, en dan kun je niets bewerken.
- Achter een router-adres is het formulier anders: een keuzerondje `user` met de naam, `pswd`, en verborgen `veld=access`.

### Wat de app moet doen
- Eén `HttpClient` met een cookiebeheerder **per gebruiker**, alleen in het geheugen. Stuur bij elke aanroep de cookies mee en neem elke nieuwe `Set-Cookie` over,
  want `zcbs-date` wordt bij elk antwoord ververst.
- Bij een gewoon (niet-router) adres verloopt de sessie na **10 minuten stilstand** (`$SESSION_EXT`), ook bij opslaan. Voor router-adressen is dat 60 minuten
  en geldt een uitzondering voor opslaan. Wie langer dan tien minuten bewerkt, verliest zijn opslag en krijgt een inlogscherm.
- Log daarom vlak voor het opslaan opnieuw in met de naam en het wachtwoord in het geheugen, haal het formulier opnieuw op (dat ververst ook de vergrendeling),
  vergelijk `a0` met wat de gebruiker begon te bewerken, en post direct.
- Een eigen User-Agent (geen `curl`) en latin-1 voor de velden.
- Een herkenbare mislukking: het antwoord bevat "Uw sessie is verlopen", "niet herkend", "Tijdelijk geblokkeerd object" of `==Sorry`.

### Aanvulling: HKH zelf en de vergrendeling

- In `permit.lst` staat voor archief een regel `<ROUTER> HKH` met het vaste IPv4-adres van de kring (plus een regel voor een gebruiker die thuis een eigen
  adres heeft). Iedereen op het HKH-netwerk komt dus met hetzelfde adres binnen en moet zich per persoon aanmelden met het routerformulier
  (`user` + `pswd` + `veld=access`). Draait de app op het HKH-netwerk en gaat die via hetzelfde publieke adres naar buiten, dan geldt voor de app ook de routerweg
  (sessie 60 minuten stilstand, opslaan wordt niet geweigerd door een verlopen sessie). Draait de app elders, dan geldt de weg met 10 minuten.
- Het IP-adres telt voor het inloggen en voor het resetten van een vergrendeling (`upd_form=clr` geldt voor **alle** gebruikers van dat adres). Voor de
  vergrendeling zelf telt de gebruikersnaam.
- De vergrendeling wordt gezet bij elk verzoek met `upd_form=edt` en een `ident`: het openen van het formulier en ook het opslaan. Gewoon bekijken
  (`archief.pl?ident=N` zonder `upd_form`) vergrendelt niet. Elk nieuw verzoek verlengt de vergrendeling met tien minuten. Er is geen automatische
  vrijgave na het opslaan. De app moet `upd_form=clr` niet gebruiken, omdat dat ook de vergrendelingen van collega's op hetzelfde adres opheft.
- Tijdelijk geblokkeerd geldt dus alleen voor een andere naam. In de testversie `archief2` is er geen vergrendeling.

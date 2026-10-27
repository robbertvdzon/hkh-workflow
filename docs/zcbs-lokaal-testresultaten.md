# Lokale ZCBS: wat de echte code doet

Datum: 25 oktober 2026. Getest op een lokale kopie van de echte ZCBS-code (`objecten.pl` 4.18q, `archief.pl`, `cgi-lib.pl`) met de archiefdata van 21 oktober, een eigen `permit.lst` met verzonnen
testgebruikers, lege e-mailadressen en een nep `sendmail.pm` die mails in een bestand schrijft. Opzet en gebruik: [tools/zcbs-local/README.md](../oude-tests/zcbs-local/README.md).
**Er is niets naar de echte HKH-server gestuurd.** De uitkomsten gelden voor de code zoals gedownload; de echte server kan afwijken in configuratie, hostingregels, IP-adressen en versie.

## Uitkomsten

| # | Wat | Uitkomst |
|---|---|---|
| 1 | Inloggen: `POST archief.pl` met `user` en `pswd` | Goed: tekst "Gebruikersnaam/wachtwoord combinatie herkend!", cookies `zcbs-user`, `zcbs-rout` (leeg), `zcbs-date`, `zcbs-app-user`, links "Uitloggen" en "Reset blokkering" (recht 1). |
| 2 | Fout wachtwoord | Tekst "combinatie niet herkend!". **Er komt wel een `zcbs-user`-cookie met de naam** (andere hash). De cookie is dus geen signaal van succes; de tekst wel. Dit corrigeert een eerdere uitspraak van mij. |
| 3 | Gedeeltelijke naam en hoofdletters | "Bewerker" met het juiste wachtwoord werkt; "test bewerker" (kleine letters) geeft geen cookie en geen inlog. |
| 4 | Routerweg (`X-Forwarded-For` = router-IP) | Zonder login: "Wie bent u?" met namen. `user`, `pswd`, `veld=access` werkt; cookie `zcbs-rout=<routernaam>`. Fout wachtwoord: "Onjuist wachtwoord ingevuld!". |
| 5 | Formulier ophalen: `GET ?ident=<nr>&upd_form=edt` | Geeft het volledige bewerkformulier, met alle velden, `a0` en de keuzelijsten. `ident` en `upd_form` zijn genoeg. Het zet een regel in `lock_object.txt`. Het formulier van de lokale kopie komt overeen met de geplakte pagina van productie. |
| 6 | Nummer dat niet bestaat | Geen bewerkformulier (geen `a0`), alleen de pagina van een leeg record. **Een POST met dat nummer maakt het record aan** (getest met 99998). |
| 7 | POST zonder wijziging | Er wordt niets weggeschreven (`updates.txt` blijft gelijk). Antwoord is de recordpagina. |
| 8 | POST met een wijziging | Eén regel erbij in `updates.txt`, één in `backup1/upd-cumm.txt`, een logregel type 4, een lock-regel. Mail: geen (lokaal uitgeschakeld). |
| 9 | Vergrendeling door een ander | "Het object 11387 is nog voor 9 minuten door een andere gegevensbeheerder ( Test Bewerker ) voor bewerking in gebruik. U kunt dit object momenteel nog niet aanpassen." Geldt voor openen en voor opslaan; er wordt niets opgeslagen. |
| 10 | Sessie van een gewoon adres | 9 minuten stil: werkt. 11 minuten stil: "Uw sessie is verlopen", inlogscherm. |
| 11 | Meerkeuze `a24` | Twee delen `a24` worden `Geschiedenis; Markt`. |
| 12 | Speciale tekens | `ë é " ' & <b>` blijven staan zoals verstuurd. Een tab wordt een spatie. Een regeleinde wordt `<br>` (`\r` wordt een spatie). `&amp;` en `</textarea>` blijven letterlijk staan. |
| 13 | Velden die niet in het formulier staan | `a28` wordt leeg opgeslagen (record 10147 had `1`). `a17` staat wel in het formulier als het gevuld is (record 10016) en blijft dan behouden. |
| 14 | User-Agent met `curl` | `==Sorry:3==`. |
| 15 | Woorden in de URL | `search=style`, `script`, `manuscript`, `lifestyle`, `onclick`, `curlew` geven `==Sorry:1==`; `scriptie` niet. |
| 16 | Woorden in een veld (POST) | `script`, `style`, `curl` mogen (het ruwe POST-filter is dood: `$content_length` is leeg). Geweigerd met `==Sorry:4==` (niets opgeslagen): de hoofdletterwoorden `FROM`, `NULL`, `SELECT`, `UNION`, `CONCAT`, `CHAR(` en de hele woorden `onclick`, `onload`, enz. In de 1908 records komt dat nergens voor. `permit.lst`, `objecten.pl` of `cfg-cbs.pl` geeft `==Sorry:5==`. |
| 17 | Snelheidsbegrenzing | Zonder cookie: het 8e verzoek binnen 10 seconden geeft "Access ZCBS denied: using too many requests". Met een `zcbs-user`-cookie niet. |
| 18 | `EXPORT` | Geeft een zoekresultaatpagina met de melding dat er een exportbestand klaarstaat, geen ruwe tekst. Niet verder uitgezocht; het formulier levert alles wat we nodig hebben. |
| 19 | `LAST`, `LAST3`, `LAST10`, `LAST100` (ingelogd) | `?search=LAST` geeft het hoogste nummer **inclusief lege records** (nu 11933), met `ident=<nr>`-links en de regel `zcbs_archief = "0000 <nummers> 0000"`. `LAST3` geeft de drie hoogste, `LAST10` de tien, `LAST100` de honderd. Zonder inlog (publieke weergave) ontbreken de lege records: daar was het hoogste nummer 11901. De app moet `LAST` dus ingelogd gebruiken. |
| 20 | `NEWRECORD` | Zonder het derde woord `x` (`NEWRECORD 11902 11903`): "fout in opgegeven afbeeldingslocatie". Met `x` werkt het; voor bestaande nummers: "database record nr. 11902 bestaat reeds". |
| 21 | Weergave of formulier: lock | De gewone weergave `?ident=<nr>` zet **geen** lock; `&upd_form=edt` wel (ook voor een nummer dat niet bestaat). |
| 22 | Veld 21 bij een leeg record | Lege records (door `NEWRECORD` of images.exe) hebben veld 21 leeg, maar het formulier toont "neen" (`0`). De eerste bewerking verandert dus altijd ook veld 21 (leeg wordt `0`); dat is geen fout. |
| 23 | Controle tegen `a0` (de `RecordRebuilder` uit de spec) op 363 records | 250 willekeurige en 113 bewust gekozen (tekens `"` `&` `>` ë; gevulde velden 4, 7, 10, 16, 17, 18, 19 en 28): het uit het formulier opnieuw opgebouwde record is steeds gelijk aan `a0`, behalve veld 28 (geen invoerveld, 9 records) en veld 21 bij lege records (rij 22). Een door de app opgeslagen record voldoet daarna ook aan de controle. |
| 24 | Opties zonder `</option>` | Het formulier sluit de `<option>`-regels niet. Een parser die dat niet afhandelt, behoudt alleen de laatste optie (mijn eerste testparser deed dat). Jsoup en `html.parser` met een aanpassing werken. |
| 25 | Mail per opslag | Eén mail per opslag met het hele record en de twee links; een extra mail "Keuze ANDERS in listmenu" bij een nieuwe Anders-waarde. Lokaal in het nepbestand `mailbox.txt` (13 mails voor 12 opslagen en 1 Anders). Ook `backup1/upd-cumm.txt` krijgt een regel. |

## Wat dit verandert in de spec

- Het inlogsucces is de tekst, niet de cookie.
- `a0` aanwezig is de controle dat een record bestaat. Zonder `a0` niet posten, want ZCBS maakt dan een nieuw record aan.
- Velden die niet in het formulier staan, komen uit `a0`.
- Woordenfilters: vooraf controleren en een duidelijke melding geven.
- Altijd de cookies meesturen (snelheidsbegrenzing) en een eigen User-Agent.

# Nep-versies van images.exe en pdf2zcbs.exe

Om de testpagina op een Mac (of Linux) te proberen zonder Windows. Het zijn Perl-scripts die dezelfde
vragen stellen als de echte programma's (volgens `docs/042.pdf` en `docs/050.pdf`), maar niets uploaden.

Instellingen in de app (pas het pad aan naar jouw checkout):

| Instelling | Waarde |
|---|---|
| pdf2zcbs.exe | `…/hkh-workflow/tools/mock/pdf2zcbs/pdf2zcbs.pl` |
| images.exe | `…/hkh-workflow/tools/mock/images-werk/images.pl` |

Net als een gewoon Perl-programma bufferen ze hun uitvoer als ze niet in een terminal draaien. Zo zie
je thuis al hetzelfde probleem als met de echte exe's te verwachten is. Zet bij Instellingen
**PERLIO=:unix** aan of uit om het verschil te zien.

De scripts schrijven in hun eigen map (`output/`, `images/`, `small/` …); die mappen staan in `.gitignore`.

// Maakt alle screenshots voor de handleidingen in manual/afbeeldingen/ door de app lokaal helemaal door te lopen.
// Vereist: de app op APP_URL (standaard http://localhost:9010) met de test-ZCBS en de nep-programma's, scans in de eerste map met scans
// (met een submap Kerkplein), een leeg verwerkt-log en het beheerderswachtwoord in ADMIN_PASSWORD. Zie tools/docs/README.md.
const { open, login, shot, BASE } = require('./schermen.cjs');

const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'abc';

async function waitFor(page, text, ms = 90000) {
  await page.waitForFunction((t) => document.body.innerText.includes(t), text, { timeout: ms });
  await page.waitForLoadState('networkidle');
}

async function nieuw(page) {
  await page.goto(BASE + '/nieuw');
  await page.waitForLoadState('networkidle');
}

const KERKPLEIN = ['kerkplein_1950.pdf', 'kerkplein_1962.pdf', 'raadhuis_voorgevel.pdf', 'markt_1935.jpg'];
const SCHOOLFOTOS = ['school_001.pdf', 'school_002.pdf', 'klas_foto.jpg'];

async function chooseFiles(page, folder = 'Kerkplein', files = KERKPLEIN) {
  await page.click(`a:has-text("${folder}")`);
  await page.waitForLoadState('networkidle');
  for (const f of files) await page.check(`input[type=checkbox][value="${f}"]`);
}

(async () => {
  const { browser, page } = await open();
  page.on('dialog', (d) => d.accept());

  // ---------- Inloggen en kiezen ----------
  await page.goto(BASE + '/');
  await page.fill('input[name=naam]', 'Carla');
  await page.fill('input[name=wachtwoord]', 'geheim');
  await shot(page, 'inloggen', [{ sel: '#naam', n: 1 }, { sel: '#wachtwoord', n: 2 }, { text: 'Inloggen', n: 3 }]);
  await login(page);
  await shot(page, 'collectie', [{ sel: 'a[href$="/collectie/archief"]', n: 1 }]);
  await page.goto(BASE + '/collectie/archief');
  await shot(page, 'kies-actie-invoeren', [{ text: 'Nieuwe bestanden in ZCBS zetten', n: 1 }]);
  await shot(page, 'kies-actie-records', [{ text: 'ZCBS records aanpassen', n: 1 }]);

  // ---------- Nieuwe bestanden ----------
  await nieuw(page);
  if (await page.locator('button:has-text("Opnieuw beginnen")').count()) { await page.click('button:has-text("Opnieuw beginnen")'); await page.waitForLoadState('networkidle'); }
  if (await page.locator('button:has-text("Verplaats ze naar een map met resten")').count()) { await page.click('button:has-text("Verplaats ze naar een map met resten")'); await page.waitForLoadState('networkidle'); }
  await nieuw(page); // laadt opnieuw, zodat een melding over het opruimen van resten niet op het plaatje staat
  await shot(page, 'invoeren-1-controleren', [{ text: 'Alles in orde', n: 1 }]);
  await page.click('button:has-text("Alles in orde")');
  await page.waitForLoadState('networkidle');

  await shot(page, 'invoeren-2-map-kiezen', [{ text: 'Kerkplein', n: 1 }], { scrollTo: 'h2:has-text("Bestanden kiezen")', top: true });
  await chooseFiles(page);
  await shot(page, 'invoeren-2-bestanden-kiezen', [{ sel: 'input[type=checkbox][value="kerkplein_1950.pdf"]', n: 1 }, { text: 'Open', n: 2 }, { text: 'Verder', n: 3 }], { scrollTo: 'h2:has-text("Bestanden kiezen")', top: true });
  await page.locator('button:has-text("Open")').first().click();
  await page.waitForTimeout(900);
  await shot(page, 'invoeren-2-popup', []);
  await page.click('#viewer-close');
  await page.click('button:has-text("Verder")');
  await page.waitForLoadState('networkidle');

  await shot(page, 'invoeren-3-nummers', [{ sel: '#start', n: 1 }, { text: 'Geef de bestanden deze nummers', n: 2 }], { scrollTo: 'button:has-text("Geef de bestanden deze nummers")' });

  // Opnieuw beginnen nadat de nummers zijn gegeven laat kopieën achter: zo ontstaat het scherm "Eerst opruimen".
  await page.click('button:has-text("Geef de bestanden deze nummers en ga verder")');
  await page.waitForLoadState('networkidle');
  await page.click('button:has-text("Opnieuw beginnen")');
  await page.waitForLoadState('networkidle');
  await page.goto(BASE + '/nieuw');
  await page.waitForLoadState('networkidle');
  if (await page.locator('button:has-text("Verplaats ze naar een map met resten")').count()) {
    await shot(page, 'invoeren-1-opruimen', [{ text: 'Verplaats ze naar een map met resten', n: 1 }], { scrollTo: 'button:has-text("Verplaats ze naar een map met resten")' });
    await page.click('button:has-text("Verplaats ze naar een map met resten")');
    await page.waitForLoadState('networkidle');
  }
  if (await page.locator('button:has-text("Alles in orde")').count()) { await page.click('button:has-text("Alles in orde")'); await page.waitForLoadState('networkidle'); }
  await chooseFiles(page);
  await page.click('button:has-text("Verder")');
  await page.waitForLoadState('networkidle');
  await page.click('button:has-text("Geef de bestanden deze nummers en ga verder")');
  await page.waitForLoadState('networkidle');

  await shot(page, 'invoeren-4-pdf2zcbs', [{ text: 'Automatisch uitvoeren', n: 1 }]);
  await page.click('button:has-text("Automatisch uitvoeren")');
  await waitFor(page, 'Ja, goed: verder met images.exe');
  await shot(page, 'invoeren-4-controleer', [{ text: 'Open', n: 1 }], { scrollTo: 'h2:has-text("pdf2zcbs")', top: true });
  await shot(page, 'invoeren-4-bevestig', [{ text: 'Ja, goed: verder met images.exe', n: 1 }, { text: 'Niet goed: pdf2zcbs.exe opnieuw uitvoeren', n: 2 }], { scrollTo: 'button:has-text("Ja, goed: verder met images.exe")' });
  await page.locator('main button:has-text("Open")').nth(3).click();
  await page.waitForTimeout(900);
  await shot(page, 'invoeren-4-popup', []);
  await page.click('#viewer-close');
  await page.click('button:has-text("Ja, goed: verder met images.exe")');
  await page.waitForLoadState('networkidle');

  await shot(page, 'invoeren-5-images', [{ text: 'Automatisch uitvoeren', n: 1 }]);
  await page.click('button:has-text("Automatisch uitvoeren")');
  await waitFor(page, 'Ja, goed: verder met de upload');
  await shot(page, 'invoeren-5-controleer', [{ text: 'OP DE SERVER', n: 1 }], { scrollTo: 'h2:has-text("images.exe")', top: true });
  await shot(page, 'invoeren-5-bevestig', [{ text: 'Ja, goed: verder met de upload', n: 1 }, { text: 'Niet goed: images.exe opnieuw uitvoeren', n: 2 }], { scrollTo: 'button:has-text("Ja, goed: verder met de upload")' });
  await page.click('button:has-text("Ja, goed: verder met de upload")');
  await page.waitForLoadState('networkidle');

  await shot(page, 'invoeren-6-upload', [{ text: 'Uitvoeren', n: 1 }]);
  await page.locator('main button:has-text("Uitvoeren")').first().click();
  await waitFor(page, 'Maak een back-up');
  await shot(page, 'invoeren-7-backup', [{ text: 'Uitvoeren', n: 1 }]);
  await page.locator('main button:has-text("Uitvoeren")').first().click();
  await waitFor(page, 'staan nu op het archief');
  const range = (await page.locator('main').innerText()).match(/(\d+) t\/m (\d+)/g)?.pop()?.match(/(\d+) t\/m (\d+)/);
  await shot(page, 'invoeren-klaar', [{ text: 'Nieuwe bestanden verwerken', n: 1 }], { scrollTo: 'h2:has-text("Klaar")', top: true });
  await page.goto(BASE + '/verwerkt');
  await shot(page, 'verwerkt', []);

  // ---------- Records aanpassen ----------
  await page.goto(BASE + '/records');
  await page.fill('#nummers', range ? `${range[1]}-${range[2]}` : '99102-99105');
  await shot(page, 'records-1-kiezen', [{ sel: '#nummers', n: 1 }, { text: 'Toon deze records', n: 2 }]);
  await page.click('button:has-text("Toon deze records")');
  await page.waitForLoadState('networkidle');
  await shot(page, 'records-2-lijst', [{ text: 'Pdf', n: 1 }, { text: 'Open in ZCBS', n: 2 }, { text: 'Bewerk', n: 3 }], { scrollTo: 'main .table', top: false });
  await page.locator('main button:has-text("Pdf")').first().click();
  await page.waitForTimeout(900);
  await shot(page, 'records-2-popup', []);
  await page.click('#viewer-close').catch(() => {});
  await page.locator('a.btn:has-text("Bewerk")').first().click();
  await page.waitForLoadState('networkidle');
  await page.fill('input[name=a5]', 'Kerkplein met de oude kerk, 1950');
  await shot(page, 'records-3-formulier', [{ sel: 'input[name=a5]', n: 1 }], { scrollTo: 'h1', top: true });
  await shot(page, 'records-3-opslaan', [{ text: 'Opslaan en volgende', n: 1 }, { text: 'Opslaan', n: 2 }, { text: 'Volgende zonder opslaan', n: 3 }], { scrollTo: 'button:has-text("Opslaan en volgende")' });
  await page.locator('main button:has-text("Opslaan")').first().click();
  await page.waitForLoadState('networkidle');
  await shot(page, 'records-4-opgeslagen', [], { scrollTo: 'main .alert-ok', top: true });

  // ---------- Beheerder ----------
  await page.goto(BASE + '/beheer');
  await shot(page, 'beheer-inloggen', [{ sel: 'input[type=password]', n: 1 }]);
  await page.fill('input[type=password]', ADMIN_PASSWORD);
  await page.click('main button[type=submit]');
  await page.waitForLoadState('networkidle');
  await page.goto(BASE + '/instellingen');
  const sections = [
    ['instellingen-1-server', 'Bestanden van de server en ZCBS'],
    ['instellingen-2-resten', 'Resten van een eerdere run'],
    ['instellingen-3-logboek', 'Logboek'],
    ['instellingen-4-ftp', 'FTP-wachtwoord'],
    ['instellingen-5-collectie', 'Per collectie'],
    ['instellingen-6-test', 'Test verbinding'],
  ];
  for (const [name, legend] of sections) await shot(page, name, [], { element: `fieldset:has(legend:has-text("${legend}")), section:has(h2:has-text("${legend}"))` });
  await shot(page, 'instellingen-0-boven', [], { scrollTo: 'h1', top: true });

  // Sessies met iemand die bezig is: begin een nieuwe doorloop tot en met de nummers.
  await nieuw(page);
  await page.click('button:has-text("Alles in orde")').catch(() => {});
  await page.waitForLoadState('networkidle');
  if (await page.locator('a:has-text("Schoolfoto")').count()) {
    await chooseFiles(page, 'Schoolfoto', SCHOOLFOTOS);
    await page.click('button:has-text("Verder")');
    await page.waitForLoadState('networkidle');
    await page.click('button:has-text("Geef de bestanden deze nummers en ga verder")');
    await page.waitForLoadState('networkidle');
  }
  await page.goto(BASE + '/instellingen/sessies');
  await shot(page, 'sessies', [{ text: 'Stoppen', n: 1 }]);

  await browser.close();
})().catch((e) => { console.error('MISLUKT', e.message); process.exit(1); });

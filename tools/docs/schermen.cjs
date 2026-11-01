// Hulp voor de screenshots in de handleidingen: logt in op de lokale app, markeert knoppen met een genummerde rode rand en bewaart het scherm.
// Gebruik: zie tools/docs/README.md. Alleen tegen de lokale test-omgeving (fictieve gegevens).
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const BASE = process.env.APP_URL || 'http://localhost:9010';
const OUT = process.env.OUT_DIR || path.join(__dirname, '..', '..', 'manual', 'afbeeldingen');
const USER = process.env.ZCBS_USER || 'user1';
const PASSWORD = process.env.ZCBS_PASSWORD || 'abc';

async function open() {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL || 'chrome' });
  const ctx = await browser.newContext({ viewport: { width: 1180, height: 780 }, deviceScaleFactor: 1.5, locale: 'nl-NL' });
  const page = await ctx.newPage();
  return { browser, ctx, page };
}

async function login(page) {
  await page.goto(BASE + '/');
  if (await page.locator('input[name=naam]').count()) {
    await page.fill('input[name=naam]', USER);
    await page.fill('input[name=wachtwoord]', PASSWORD);
    await page.click('button:has-text("Inloggen")');
    await page.waitForLoadState('networkidle');
  }
}

/** Markeert elementen met een rode rand en een nummer. Een doel is een tekst (knop, link of label) of { sel, n }. */
async function mark(page, targets) {
  await page.evaluate((targets) => {
    document.querySelectorAll('.doc-mark, .doc-badge').forEach((e) => e.remove());
    const findByText = (text) => {
      const nodes = [...document.querySelectorAll('button, a, input[type=submit], label, summary, th, h2, h1, .chip, span, p')];
      return nodes.filter((n) => n.offsetParent !== null && (n.value || n.textContent || '').trim().startsWith(text))
        .sort((a, b) => (a.textContent.length - b.textContent.length))[0];
    };
    targets.forEach((t, i) => {
      const el = t.sel ? document.querySelector(t.sel) : findByText(t.text);
      if (!el) { console.log('NIET GEVONDEN: ' + (t.sel || t.text)); return; }
      const r = el.getBoundingClientRect();
      const box = document.createElement('div');
      box.className = 'doc-mark';
      Object.assign(box.style, { position: 'absolute', left: (r.left + scrollX - 4) + 'px', top: (r.top + scrollY - 4) + 'px', width: (r.width + 8) + 'px', height: (r.height + 8) + 'px',
        border: '3px solid #e11d48', borderRadius: '8px', pointerEvents: 'none', zIndex: 99999, boxShadow: '0 0 0 3px rgba(225,29,72,.18)' });
      const badge = document.createElement('div');
      badge.className = 'doc-badge';
      badge.textContent = String(t.n || i + 1);
      Object.assign(badge.style, { position: 'absolute', left: (r.left + scrollX - 18) + 'px', top: (r.top + scrollY - 18) + 'px', width: '26px', height: '26px', borderRadius: '13px',
        background: '#e11d48', color: '#fff', font: '700 15px/26px system-ui, sans-serif', textAlign: 'center', pointerEvents: 'none', zIndex: 100000 });
      document.body.append(box, badge);
    });
  }, targets);
}

/**
 * Maakt de testomgeving onherkenbaar lokaal: de tijdelijke mappen van de test worden K:- en servermappen, en de testgebruiker krijgt een
 * voornaam. Alleen de tekst op het scherm verandert, niet de app.
 */
async function rewrite(page) {
  await page.evaluate(() => {
    const fixPath = (p) => p
      .replace(/\/private\/tmp\/[^\s"]*?\/e2e3\/scans/g, 'K:\\scans jelly')
      .replace(/\/private\/tmp\/[^\s"]*?\/e2e2\/k\//g, 'K:\\')
      .replace(/\/private\/tmp\/[^\s"]*?\/e2e2\/invoer/g, '\\\\server\\werkmap')
      .replace(/\/private\/tmp\/[^\s"]*?\/e2e2\/(pdf2zcbs|images-werk)/g, 'C:\\zcbs\\$1');
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    const nodes = [];
    while (walker.nextNode()) nodes.push(walker.currentNode);
    for (const n of nodes) {
      let t = n.nodeValue;
      if (t.includes('/private/tmp/')) {
        t = t.replace(/(K:\\|\\\\server\\werkmap|C:\\zcbs\\)?\/private\/tmp\/\S+/g, (m) => {
          const fixed = fixPath(m.replace(/[.,;]+$/, ''));
          const tail = m.match(/[.,;]+$/)?.[0] || '';
          return (fixed.startsWith('K:') || fixed.startsWith('\\\\') || fixed.startsWith('C:') ? fixed.replace(/\//g, '\\') : fixed) + tail;
        });
      }
      t = t.replace(/\buser1\b/g, 'Carla');
      if (t !== n.nodeValue) n.nodeValue = t;
    }
  });
}

async function shot(page, name, targets = [], opts = {}) {
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.evaluate(() => document.querySelectorAll('.doc-mark, .doc-badge').forEach((e) => e.remove()));
  await rewrite(page);
  if (opts.scrollTo) await page.locator(opts.scrollTo).first().evaluate((el, top) => el.scrollIntoView({ block: top ? 'start' : 'center' }), !!opts.top);
  if (targets.length) await mark(page, targets);
  const file = path.join(OUT, name + '.jpg');
  if (opts.element) await page.locator(opts.element).first().screenshot({ path: file, type: 'jpeg', quality: 90 });
  else await page.screenshot({ path: file, type: 'jpeg', quality: 90, fullPage: !!opts.full });
  console.log('screenshot', path.relative(process.cwd(), file));
}

module.exports = { open, login, mark, shot, rewrite, BASE, OUT };

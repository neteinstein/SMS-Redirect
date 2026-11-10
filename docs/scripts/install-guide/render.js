// Renders the install-guide illustrations in screens.html to docs/images/install-apk/*.png.
//   NODE_PATH=$(npm root -g) node docs/scripts/install-guide/render.js
const { chromium } = require('playwright');
const path = require('path');

const OUT = path.resolve(__dirname, '..', '..', 'images', 'install-apk');

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 480, height: 940 }, deviceScaleFactor: 1.5 });
  page.on('pageerror', e => { console.error('PAGEERR', e.message); process.exit(1); });
  await page.goto('file://' + path.join(__dirname, 'screens.html'));
  const keys = await page.evaluate(() => window.SCREEN_KEYS);
  for (const key of keys) {
    await page.evaluate(k => window.renderScreen(k), key);
    await page.evaluate(() => document.fonts.ready);
    await page.waitForTimeout(100);
    const file = path.join(OUT, key + '.png');
    await page.screenshot({ path: file, clip: { x: 0, y: 0, width: 480, height: 940 } });
    console.log('Wrote', path.relative(process.cwd(), file));
  }
  await browser.close();
})();

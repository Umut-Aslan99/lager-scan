import { expect, test, devices, type Page } from '@playwright/test';

/** Types into the code field like a Bluetooth hand scanner does (text + Enter). */
async function scan(page: Page, code: string) {
  await page.getByTestId('code-input').fill(code);
  await page.getByTestId('code-input').press('Enter');
}

// Also guards the first-visit race: parallel cookie-less requests used to create several workspaces,
// so the QR code could belong to a different workspace than the browser's cookie.
test('phone paired via QR code books a transfer – the control station shows it live', async ({ browser, page }) => {
  await page.goto('/');
  await expect(page.getByTestId('live')).toHaveText('Live verbunden');
  await expect(page.getByTestId('pair-qr')).toBeVisible();
  await expect(page.locator('.tile', { hasText: 'Im Wareneingang' }).locator('.value')).toHaveText('3');
  const joinUrl = (await page.locator('.pair .mono').textContent())!.trim();
  expect(joinUrl).toMatch(/\/join\/[0-9a-f]{24}$/);

  // The phone opens the QR link and lands in the same warehouse.
  const phone = await browser.newContext({ ...devices['Pixel 7'], locale: 'de-DE', timezoneId: 'Europe/Berlin' });
  const p = await phone.newPage();
  await p.goto(joinUrl);
  await expect(p).toHaveURL(/\/scan\?joined=1$/);
  await expect(p.getByText('Mit dem Leitstand verbunden')).toBeVisible();

  // Receiving bin → article → transfer; the target bin comes from scanning its label.
  await scan(p, 'BIN:WE-01');
  const bin = p.getByTestId('bin-result');
  await expect(bin).toContainText('ET-1001');
  await bin.locator('li', { hasText: 'ET-1001' }).getByRole('button', { name: 'Öffnen' }).click();
  await expect(p.getByTestId('article-result')).toContainText('ET-1001');
  await p.getByRole('button', { name: 'Umlagern' }).click();
  await expect(p.getByTestId('from-bin')).toHaveValue('WE-01');
  await scan(p, 'BIN:A-01-01');
  await expect(p.getByTestId('to-bin')).toHaveValue('A-01-01');
  await p.getByTestId('quantity').fill('20');
  await p.getByTestId('book').click();
  await expect(p.getByTestId('toast')).toHaveText('Umlagerung gebucht');

  // Control station: new line highlighted, receiving count down by one.
  await expect(page.getByTestId('feed').locator('li.fresh')).toContainText('Umlagerung');
  await expect(page.getByTestId('feed').locator('li.fresh')).toContainText('WE-01 → A-01-01');
  await expect(page.locator('.tile', { hasText: 'Im Wareneingang' }).locator('.value')).toHaveText('2');
  await phone.close();
});

test('without network the booking waits on the device and is sent when the network returns', async ({ page, context }) => {
  const catalogLoaded = page.waitForResponse((r) => r.url().includes('/api/articles') && r.ok());
  await page.goto('/scan');
  await catalogLoaded; // the device now has articles and bins cached

  await context.setOffline(true);
  await expect(page.getByTestId('netpill')).toContainText('Offline');
  await scan(page, 'ART:ET-1014');
  await expect(page.getByText('Offline – Bestand aus dem letzten Abgleich')).toBeVisible();
  await expect(page.getByTestId('article-result')).toContainText('ET-1014');
  await page.getByRole('button', { name: 'Entnahme' }).click();
  await page.getByTestId('book').click();
  await expect(page.getByTestId('toast')).toContainText('offline gespeichert');
  await expect(page.getByTestId('netpill')).toContainText('1 wartend');

  await context.setOffline(false);
  await expect(page.getByTestId('netpill')).toHaveAttribute('data-pending', '0', { timeout: 20_000 });
  const bookings = await (await page.request.get('/api/bookings')).json();
  expect(bookings[0]).toMatchObject({ type: 'ISSUE', articleNumber: 'ET-1014', quantity: 1 });
});

test('a booking that breaks a rule is refused with the reason', async ({ page }) => {
  await page.goto('/scan?code=ART:ET-1001');
  await expect(page.getByTestId('article-result')).toContainText('ET-1001');
  await page.getByRole('button', { name: 'Entnahme' }).click();
  await page.getByTestId('quantity').fill('9999');
  await page.getByTestId('book').click();
  await expect(page.getByTestId('toast')).toContainText('liegen nur');
});

test('an inventory count books the difference', async ({ page }) => {
  await page.goto('/scan?code=ART:ET-1001');
  const stock = page.getByTestId('stock-lines').locator('li').first();
  const [binCode, qtyText] = [await stock.locator('.mono').textContent(), await stock.locator('b').textContent()];
  const before = Number(qtyText!.split(' ')[0].replace('.', '').replace(',', '.'));
  await page.getByRole('button', { name: 'Inventur' }).click();
  await expect(page.getByTestId('to-bin')).toHaveValue(binCode!.trim());
  await page.getByTestId('counted').fill(String(before + 2));
  await page.getByTestId('book').click();
  await expect(page.getByTestId('toast')).toHaveText('Inventur gebucht · Differenz +2');
});

test('control station lists and searches the stock', async ({ page }) => {
  await page.goto('/');
  const rows = page.getByTestId('stock-table').locator('tbody tr');
  await expect(rows).toHaveCount(36);
  await page.getByRole('searchbox', { name: 'Artikel suchen' }).fill('ET-1001');
  await expect(rows).toHaveCount(1);
  await rows.first().getByRole('link').click();
  await expect(page).toHaveURL(/\/artikel\/ET-1001$/);
  await expect(page.getByRole('heading', { level: 1 })).not.toBeEmpty();
});

test('labels page has a QR code for every bin and article', async ({ page }) => {
  await page.goto('/etiketten');
  await expect(page.locator('.label-card img')).toHaveCount(32 + 36);
  await expect(page.getByTestId('label-A-01-01')).toContainText('A-01-01');
});

test('security headers and camera permission for this origin only', async ({ request }) => {
  const res = await request.get('/scan');
  expect(res.headers()['content-security-policy']).toContain("script-src 'self'");
  expect(res.headers()['permissions-policy']).toContain('camera=(self)');
  expect(res.headers()['cache-control']).toBe('no-cache');
});

test('the scanner fits a small phone without sideways scrolling', async ({ browser }) => {
  const ctx = await browser.newContext({ viewport: { width: 360, height: 740 }, isMobile: true, hasTouch: true });
  const p = await ctx.newPage();
  await p.goto('/scan?code=ART:ET-1001');
  await expect(p.getByTestId('article-result')).toBeVisible();
  expect(await p.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(360);
  await ctx.close();
});

test('after the first visit the scanner opens without network (service worker)', async ({ page, context }) => {
  await page.goto('/scan');
  // Wait until the Angular service worker controls the page and has cached the app shell.
  await page.evaluate(async () => { await navigator.serviceWorker.ready; });
  await page.reload();
  await expect.poll(() => page.evaluate(async () => {
    for (const name of await caches.keys()) if (name.includes(':assets:app:cache') && (await (await caches.open(name)).keys()).length > 5) return true;
    return false;
  }), { timeout: 20_000 }).toBe(true);

  await context.setOffline(true);
  await page.goto('/scan');
  await expect(page.getByTestId('code-input')).toBeVisible();
  await expect(page.getByTestId('netpill')).toContainText('Offline');
});

test('a first visit creates exactly one workspace', async ({ page }) => {
  const seen: Promise<string | undefined>[] = [];
  page.on('response', (r) => seen.push(r.allHeaders().then((h) => h['set-cookie']?.match(/ls_ws=([0-9a-f-]+)/)?.[1])));
  await page.goto('/');
  await expect(page.getByTestId('stock-table').locator('tbody tr')).toHaveCount(36);
  await expect(page.getByTestId('live')).toHaveText('Live verbunden');
  const ids = new Set((await Promise.all(seen)).filter(Boolean));
  expect(ids.size).toBe(1);
});

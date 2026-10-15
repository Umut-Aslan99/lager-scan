import { expect, test, type Page } from '@playwright/test';

// The guided tours (site-tour.js): every step must find its element – a renamed or removed part of the UI fails here.
async function walk(page: Page, start: string, id: string): Promise<string[]> {
  const skipped: string[] = [];
  page.on('console', (m) => { if (m.text().includes('[site-tour]')) skipped.push(m.text()); });
  await page.goto(start);
  const tour = page.locator(`[data-site-tour="${id}"]`);
  const titles: string[] = [];
  while (titles.length < 30) {
    const bubble = tour.locator('section.bubble');
    await expect(bubble).toBeVisible();
    const step = await bubble.getAttribute('data-step');
    titles.push((await tour.locator('h2').textContent()) ?? '');
    await tour.getByTestId('tour-next').click();
    if ((await tour.count()) === 0) break;
    await expect(bubble).not.toHaveAttribute('data-step', step!, { timeout: 10_000 });
  }
  expect(skipped).toEqual([]);
  expect(await page.evaluate((k) => localStorage.getItem(k), `site-tour:${id}`)).toBe('done');
  return titles;
}

test('the control station tour walks through every part', async ({ page }) => {
  const titles = await walk(page, '/?tour=start', 'lagerscan');
  expect(titles).toHaveLength(9);
  await expect(page.getByTestId('tour-launcher')).toBeVisible();
});

test('the scanner has its own tour', async ({ page }) => {
  const titles = await walk(page, '/scan?tour=start', 'lagerscan-scanner');
  expect(titles).toHaveLength(6);
});

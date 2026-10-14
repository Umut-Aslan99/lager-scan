import { expect, test } from '@playwright/test';

// The fake camera shows the QR label of bin C-02-03 (global-setup.ts). Headless Chromium on Linux has no
// BarcodeDetector, so this exercises the jsQR fallback – the same path iPhones take.
test('camera scan of a bin label opens the bin', async ({ page }) => {
  await page.goto('/scan');
  await page.getByTestId('camera-start').click();
  await expect(page.getByText('Etikett in den Rahmen halten')).toBeVisible();
  await expect(page.getByTestId('bin-result')).toContainText('C-02-03', { timeout: 15_000 });
});

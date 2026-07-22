import { test } from '@playwright/test';
import { login } from '@nop-entropy/e2e-shared';

test('diag: table cell structure', async ({ page }) => {
  await login(page, { fast: true });
  await page.goto('#/NopAuthUser-main');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);

  // Dump first data row HTML
  const rows = page.locator('tr');
  const count = await rows.count();
  for (let i = 0; i < Math.min(3, count); i++) {
    const html = await rows.nth(i).innerHTML();
    if (html.length < 3000) {
      console.log(`=== ROW ${i} ===`);
      console.log(html);
    }
  }

  // Dump header cells with data-index
  const headers = page.locator('th[data-index]');
  const hCount = await headers.count();
  console.log(`\n=== HEADER CELLS (${hCount}) ===`);
  for (let i = 0; i < hCount; i++) {
    const idx = await headers.nth(i).getAttribute('data-index');
    const text = (await headers.nth(i).textContent())?.trim();
    console.log(`  th[${i}] data-index="${idx}" text="${text}"`);
  }
});

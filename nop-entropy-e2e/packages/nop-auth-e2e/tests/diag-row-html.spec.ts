import { test } from '@playwright/test';
import { login } from '@nop-entropy/e2e-shared';

test('diag: dump row HTML', async ({ page }) => {
  await login(page, { fast: true });
  await page.goto('#/NopAuthRole-main');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(3000);

  // Dump first 3 rows' innerHTML
  const rows = page.locator('tr, .cxd-Table-row');
  const count = await rows.count();
  console.log('ROW COUNT:', count);

  for (let i = 0; i < Math.min(3, count); i++) {
    const html = await rows.nth(i).innerHTML();
    console.log(`=== ROW ${i} HTML ===`);
    console.log(html.substring(0, 3000));
  }

  // Also check for any action buttons or dropdowns in the table
  const actionCells = page.locator('.cxd-Table-cell:last-child, td:last-child');
  const actionCount = await actionCells.count();
  console.log('ACTION CELL COUNT:', actionCount);
  for (let i = 0; i < Math.min(3, actionCount); i++) {
    const html = await actionCells.nth(i).innerHTML();
    console.log(`=== ACTION CELL ${i} ===`);
    console.log(html.substring(0, 2000));
  }
});

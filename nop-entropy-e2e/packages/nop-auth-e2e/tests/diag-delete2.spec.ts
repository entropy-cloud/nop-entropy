import { test } from '@playwright/test';
import { login } from '@nop-entropy/e2e-shared';

test('diag: delete confirm dialog', async ({ page }) => {
  await login(page, { fast: true });
  await page.goto('#/NopAuthRole-main');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(3000);

  // Click first data row's "更多" dropdown
  const firstDataRow = page.locator('tbody tr').first();
  const moreBtn = firstDataRow.locator('button').filter({ hasText: /更多/ }).first();
  await moreBtn.click();
  await page.waitForTimeout(800);

  // Click the 删除 item (use the same selector as rowAction)
  const deleteItem = page.locator('.cxd-DropDown-menuItem, .cxd-DropDown-menu > *').filter({ hasText: /删除/ }).first();
  console.log('Delete item count:', await deleteItem.count());
  await deleteItem.click();
  await page.waitForTimeout(2000);

  // Dump ALL modals (visible and hidden)
  const modals = page.locator('[class*="cxd-Modal"], [class*="cxd-Dialog"], [class*="cxd-Confirm"]');
  const modalCount = await modals.count();
  console.log(`=== ALL MODAL ELEMENTS: ${modalCount} ===`);
  for (let i = 0; i < modalCount; i++) {
    const visible = await modals.nth(i).isVisible().catch(() => false);
    const cls = await modals.nth(i).getAttribute('class');
    console.log(`  [${i}] visible=${visible} class="${cls}"`);
    if (visible) {
      const html = await modals.nth(i).innerHTML();
      console.log(`  HTML (first 2500): ${html.substring(0, 2500)}`);
    }
  }

  // Dump ALL visible buttons
  const allBtns = page.locator('button:visible');
  const btnCount = await allBtns.count();
  console.log(`=== ALL VISIBLE BUTTONS (${btnCount}) ===`);
  for (let i = 0; i < btnCount; i++) {
    const text = (await allBtns.nth(i).textContent())?.trim();
    const cls = await allBtns.nth(i).getAttribute('class');
    console.log(`  [${i}] text="${text}" class="${cls?.substring(0, 80)}"`);
  }
});

import { test } from '@playwright/test';
import { login } from '@nop-entropy/e2e-shared';

test('diag: delete confirm only', async ({ page }) => {
  await login(page, { fast: true });
  await page.goto('#/NopAuthRole-main');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(3000);

  // Click first data row's "更多" dropdown
  const firstDataRow = page.locator('tbody tr').first();
  const moreBtn = firstDataRow.locator('button').filter({ hasText: /更多/ }).first();
  await moreBtn.click();
  await page.waitForTimeout(800);

  // Dump dropdown menu
  const dropMenu = page.locator('.cxd-DropDown-menu, [role="menu"]').last();
  if (await dropMenu.isVisible().catch(() => false)) {
    console.log('=== DROPDOWN MENU HTML ===');
    console.log(await dropMenu.innerHTML());
  }

  // Click delete item
  const deleteItem = page.locator('.cxd-DropDown-menuItem, [role="menuitem"], .cxd-DropDown-menu > div > *').filter({ hasText: /删除/ }).first();
  console.log('Delete item count:', await deleteItem.count());
  if (await deleteItem.count().then(c => c > 0)) {
    await deleteItem.click();
    await page.waitForTimeout(2000);

    // Dump all visible modals
    const modals = page.locator('.cxd-Modal:visible, .cxd-Dialog:visible');
    const modalCount = await modals.count();
    console.log(`=== VISIBLE MODALS: ${modalCount} ===`);
    for (let i = 0; i < modalCount; i++) {
      const html = await modals.nth(i).innerHTML();
      console.log(`=== MODAL ${i} (first 3000) ===`);
      console.log(html.substring(0, 3000));
    }

    // Dump all visible buttons with their context
    const allBtns = page.locator('button:visible');
    const btnCount = await allBtns.count();
    console.log(`=== ALL VISIBLE BUTTONS (${btnCount}) ===`);
    for (let i = 0; i < btnCount; i++) {
      const text = (await allBtns.nth(i).textContent())?.trim();
      const cls = await allBtns.nth(i).getAttribute('class');
      console.log(`  [${i}] text="${text}" class="${cls}"`);
    }
  }
});

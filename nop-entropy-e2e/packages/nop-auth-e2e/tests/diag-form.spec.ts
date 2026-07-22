import { test } from '@playwright/test';
import { login } from '@nop-entropy/e2e-shared';

test('diag: form structure and dialogs', async ({ page }) => {
  await login(page, { fast: true });
  await page.goto('#/NopAuthRole-main');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(3000);

  // 1. Dump filter form structure
  const filterInputs = page.locator('input[name^="filter_"]');
  const filterCount = await filterInputs.count();
  console.log('=== FILTER INPUTS ===');
  console.log(`Count: ${filterCount}`);
  for (let i = 0; i < filterCount; i++) {
    const name = await filterInputs.nth(i).getAttribute('name');
    const placeholder = await filterInputs.nth(i).getAttribute('placeholder');
    const visible = await filterInputs.nth(i).isVisible().catch(() => false);
    console.log(`  input[${i}]: name="${name}" placeholder="${placeholder}" visible=${visible}`);
  }

  // Also check for any filter form
  const filterForm = page.locator('.cxd-Crud-filter, .cxd-Form--quickFilter').first();
  if (await filterForm.count().then(c => c > 0)) {
    console.log('=== FILTER FORM HTML (first 2000) ===');
    console.log((await filterForm.innerHTML()).substring(0, 2000));
  }

  // 2. Click first row's "查看" to see view dialog
  const firstDataRow = page.locator('tbody tr, .cxd-Table-row').first();
  const viewBtn = firstDataRow.locator('button').filter({ hasText: /查看/ }).first();
  if (await viewBtn.count().then(c => c > 0)) {
    await viewBtn.click();
    await page.waitForTimeout(1500);

    const dialog = page.locator('.cxd-Modal, .cxd-Dialog').first();
    if (await dialog.isVisible().catch(() => false)) {
      console.log('=== VIEW DIALOG HTML (first 5000) ===');
      const html = await dialog.innerHTML();
      console.log(html.substring(0, 5000));
    }
    await page.keyboard.press('Escape');
    await page.waitForTimeout(500);
  }

  // 3. Click first row's "更多" → "删除" to see confirm dialog
  const moreBtn = firstDataRow.locator('button').filter({ hasText: /更多/ }).first();
  if (await moreBtn.count().then(c => c > 0)) {
    await moreBtn.click();
    await page.waitForTimeout(500);

    const deleteItem = page.locator('.cxd-DropDown-menuItem, .cxd-DropDown-menu > *, [role="menuitem"]').filter({ hasText: /删除/ }).first();
    if (await deleteItem.count().then(c => c > 0)) {
      await deleteItem.click();
      await page.waitForTimeout(1500);

      // Dump all visible modals/dialogs
      const modals = page.locator('.cxd-Modal, .cxd-Dialog, .cxd-Confirm');
      const modalCount = await modals.count();
      console.log(`=== MODAL COUNT AFTER DELETE: ${modalCount} ===`);
      for (let i = 0; i < modalCount; i++) {
        if (await modals.nth(i).isVisible().catch(() => false)) {
          const html = await modals.nth(i).innerHTML();
          console.log(`=== MODAL ${i} HTML (first 2000) ===`);
          console.log(html.substring(0, 2000));
        }
      }

      // Dump ALL visible buttons
      const allButtons = page.locator('button:visible');
      const btnCount = await allButtons.count();
      console.log(`=== ALL VISIBLE BUTTONS (${btnCount}) ===`);
      for (let i = 0; i < btnCount; i++) {
        const text = (await allButtons.nth(i).textContent())?.trim();
        console.log(`  Button ${i}: "${text}"`);
      }
    }
  }
});

import { test } from '@playwright/test';
import { login, rpc } from '@nop-entropy/e2e-shared';

const TEST_ID = `diag_${Date.now()}`;

test('diag: delete flow and table read', async ({ page, request }) => {
  // Login for RPC
  await rpc(request, 'NopAuthRole__sysLogin', {
    data: { username: 'nop', password: '123' },
  }).catch(() => {});

  // Create user
  const userName = `${TEST_ID}_deltest`;
  const nickName = 'DiagDelete';
  const saveResp = await rpc<{ data: { id: string } }>(request, 'NopAuthUser__save', {
    data: { userName, nickName, password: 'Test@1234', status: 1, userType: 1, gender: 1 },
  });
  const userId = (saveResp as any)?.data?.id;
  console.log('Created user:', userId, 'resp ok:', (saveResp as any)?.ok);

  await login(page, { fast: true });
  await page.goto('#/NopAuthUser-main');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);

  // Search for user
  const filterInput = page.locator('input[name^="filter_userName"]').first();
  await filterInput.fill(userName);
  await page.locator('.cxd-Table-searchableForm button[type="submit"]').first().click();
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);

  // Check row
  const rows = page.locator('tbody tr, tr');
  const rowCount = await rows.count();
  console.log(`Rows after search: ${rowCount}`);
  for (let i = 0; i < Math.min(5, rowCount); i++) {
    const text = (await rows.nth(i).textContent())?.substring(0, 100);
    console.log(`  Row ${i}: ${text}`);
  }

  // Now try to delete
  console.log('\n=== DELETE FLOW ===');
  // Find the data row (not header)
  let dataRow = null;
  for (let i = 0; i < rowCount; i++) {
    const text = await rows.nth(i).textContent();
    if (text && text.includes(userName)) {
      dataRow = rows.nth(i);
      break;
    }
  }

  if (dataRow) {
    // Click 更多
    const moreBtn = dataRow.locator('button').filter({ hasText: /更多/ }).first();
    console.log('More button count:', await moreBtn.count());
    await moreBtn.click();
    await page.waitForTimeout(800);

    // Click 删除
    const deleteItem = page.locator('.cxd-DropDown-menu > *').filter({ hasText: /删除/ }).first();
    console.log('Delete item count:', await deleteItem.count());
    await deleteItem.click();
    await page.waitForTimeout(2000);

    // Check what dialogs are visible
    const allBtns = page.locator('button:visible');
    const btnCount = await allBtns.count();
    console.log(`\nVisible buttons after delete click (${btnCount}):`);
    for (let i = 0; i < btnCount; i++) {
      const text = (await allBtns.nth(i).textContent())?.trim();
      if (text && text.length < 30) {
        console.log(`  [${i}] "${text}"`);
      }
    }

    // Try clicking Confirm
    const confirmBtn = page.locator('button:has-text("Confirm")').last();
    console.log('\nConfirm button count:', await confirmBtn.count());
    if (await confirmBtn.count().then(c => c > 0)) {
      await confirmBtn.click({ force: true });
      console.log('Clicked Confirm');
      await page.waitForTimeout(3000);

      // Check if overlay is gone
      const overlay = page.locator('[data-slot="alert-dialog-overlay"]');
      console.log('Overlay count:', await overlay.count());
      const overlayVisible = await overlay.isVisible().catch(() => false);
      console.log('Overlay visible:', overlayVisible);
    }

    // Search again
    console.log('\n=== SEARCH AFTER DELETE ===');
    await page.waitForTimeout(1000);
    const filterInput2 = page.locator('input[name^="filter_userName"]').first();
    const filterVisible = await filterInput2.isVisible().catch(() => false);
    console.log('Filter input visible:', filterVisible);
    if (filterVisible) {
      await filterInput2.clear();
      await filterInput2.fill(userName);
      await page.locator('.cxd-Table-searchableForm button[type="submit"]').first().click({ force: true });
      await page.waitForTimeout(2000);

      const rows2 = page.locator('tr');
      const rowCount2 = await rows2.count();
      console.log(`Rows after delete+search: ${rowCount2}`);
      for (let i = 0; i < rowCount2; i++) {
        const text = (await rows2.nth(i).textContent())?.substring(0, 80);
        if (text && text.includes(userName)) {
          console.log(`  FOUND at row ${i}: ${text}`);
        }
      }
    }
  } else {
    console.log('Data row not found');
  }

  // Cleanup via RPC if still exists
  if (userId) {
    await rpc(request, 'NopAuthUser__delete', { id: userId }).catch(() => {});
  }
});

import { test } from '@playwright/test';
import { login, rpc } from '@nop-entropy/e2e-shared';

const TEST_ID = `diag_${Date.now()}`;

test('diag: filter and search', async ({ page, request }) => {
  const userName = `diag_filter_${TEST_ID}`;
  const userId = `diag_filter_${TEST_ID}`;
  await rpc(request, 'NopAuthUser__save', {
    data: { userId, userName, nickName: 'DiagFilter' },
  });

  await login(page, { fast: true });
  await page.goto('#/NopAuthUser-main');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);

  // Check the filter form structure
  const filterForm = page.locator('.cxd-Crud-filter, form').first();
  console.log('=== FILTER FORM AREA ===');
  const crudArea = page.locator('.cxd-Crud').first();
  console.log((await crudArea.innerHTML()).substring(0, 2000));

  // Now try the filter
  const filterInput = page.locator('input[name^="filter_userName"]').first();
  console.log(`Filter input visible: ${await filterInput.isVisible().catch(() => false)}`);
  await filterInput.fill(userName);
  await page.waitForTimeout(500);

  // Check for query button near filter
  const queryBtn = page.locator('button:has-text("查询"), button:has-text("搜索"), button:has-text("Search")');
  const queryCount = await queryBtn.count();
  console.log(`Query button count: ${queryCount}`);

  if (queryCount > 0) {
    await queryBtn.first().click();
  } else {
    // Try Enter
    await filterInput.press('Enter');
  }
  await page.waitForTimeout(2000);

  // Check rows
  const rows = page.locator('tbody tr');
  const rowCount = await rows.count();
  console.log(`Rows after search: ${rowCount}`);
  for (let i = 0; i < rowCount; i++) {
    const text = (await rows.nth(i).textContent())?.substring(0, 100);
    console.log(`  Row ${i}: ${text}`);
  }

  // Cleanup
  await rpc(request, 'NopAuthUser__delete', { data: { userId } });
});

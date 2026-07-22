import { test } from '@playwright/test';
import { login, rpc } from '@nop-entropy/e2e-shared';

const TEST_ID = `diag_${Date.now()}`;

test('diag: user list structure', async ({ page, request }) => {
  // Create a test user
  const userName = `diag_search_${TEST_ID}`;
  const userId = `diag_search_${TEST_ID}`;
  await rpc(request, 'NopAuthUser__save', {
    data: { userId, userName, nickName: 'DiagSearch' },
  });

  await login(page, { fast: true });
  await page.goto('#/NopAuthUser-main');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(3000);

  // Check pagination
  const pagination = page.locator('.cxd-Pagination, .cxd-Table-foot');
  if (await pagination.count().then(c => c > 0)) {
    console.log('=== PAGINATION HTML ===');
    console.log((await pagination.first().innerHTML()).substring(0, 1000));
  }

  // Check row count
  const rows = page.locator('tbody tr');
  const rowCount = await rows.count();
  console.log(`Visible data rows: ${rowCount}`);

  // Check if our user is in the list
  let found = false;
  for (let i = 0; i < rowCount; i++) {
    const text = await rows.nth(i).textContent();
    if (text && text.includes(userName)) {
      found = true;
      console.log(`User found at row ${i}`);
      break;
    }
  }
  console.log(`User "${userName}" found in visible rows: ${found}`);

  // Try clicking refresh button
  const refreshBtn = page.locator('[class*="fa-sync"]').first();
  if (await refreshBtn.isVisible().catch(() => false)) {
    console.log('Refresh button is visible, clicking...');
    await refreshBtn.click();
    await page.waitForTimeout(2000);
    console.log('After refresh, checking rows...');

    const rowsAfter = page.locator('tbody tr');
    const rowCountAfter = await rowsAfter.count();
    console.log(`Rows after refresh: ${rowCountAfter}`);

    for (let i = 0; i < rowCountAfter; i++) {
      const text = await rowsAfter.nth(i).textContent();
      if (text && text.includes(userName)) {
        console.log(`User found at row ${i} after refresh`);
        found = true;
        break;
      }
    }
  }

  // Check for any search/filter mechanism
  const allInputs = page.locator('.cxd-Crud input:visible');
  const inputCount = await allInputs.count();
  console.log(`=== VISIBLE INPUTS IN CRUD (${inputCount}) ===`);
  for (let i = 0; i < inputCount; i++) {
    const name = await allInputs.nth(i).getAttribute('name');
    const placeholder = await allInputs.nth(i).getAttribute('placeholder');
    const type = await allInputs.nth(i).getAttribute('type');
    console.log(`  input[${i}]: name="${name}" type="${type}" placeholder="${placeholder}"`);
  }

  // Cleanup
  await rpc(request, 'NopAuthUser__delete', { data: { userId } });
});

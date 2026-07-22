import { test } from '@playwright/test';
import { login, rpc } from '@nop-entropy/e2e-shared';

test('diag: verify search works', async ({ page, request }) => {
  // First create a user properly using the same pattern as the tests
  await rpc(request, 'dev__login', {
    data: { username: 'nop', password: '123' },
  });

  const TEST_ID = `diag_${Date.now()}`;
  const userName = `${TEST_ID}_test`;
  const saveResp = await rpc<{ id: string }>(request, 'NopAuthUser__save', {
    data: {
      userName,
      nickName: 'Diag Test',
      password: 'Test@1234',
      status: 1,
      userType: 1,
      gender: 1,
    },
  });
  console.log('Save response:', JSON.stringify(saveResp).substring(0, 200));
  const userId = (saveResp as any).data?.id;
  console.log('Created user ID:', userId);

  // Verify user exists via RPC
  const getResp = await rpc(request, 'NopAuthUser__get', { id: userId });
  console.log('Get response userName:', (getResp as any).data?.userName);

  await login(page, { fast: true });
  await page.goto('#/NopAuthUser-main');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);

  // Search for 'nop' (should definitely exist)
  const filterInput = page.locator('input[name^="filter_userName"]').first();
  await filterInput.fill('nop');

  // Listen for the query request
  const [response] = await Promise.all([
    page.waitForResponse(
      (r) => r.url().includes('/api') || r.url().includes('/r/') || r.url().includes('/graphql'),
      { timeout: 10_000 },
    ).catch(() => null),
    page.locator('.cxd-Table-searchableForm button[type="submit"]').first().click(),
  ]);

  if (response) {
    console.log('Search response URL:', response.url());
    console.log('Search response status:', response.status());
    const body = await response.text().catch(() => 'N/A');
    console.log('Search response body (first 500):', body.substring(0, 500));
  } else {
    console.log('No search request detected');
  }

  await page.waitForTimeout(2000);

  // Check rows
  const rows = page.locator('tbody tr');
  const rowCount = await rows.count();
  console.log(`Rows after searching 'nop': ${rowCount}`);
  for (let i = 0; i < rowCount; i++) {
    const text = (await rows.nth(i).textContent())?.substring(0, 150);
    console.log(`  Row ${i}: ${text}`);
  }

  // Now search for our specific user
  console.log(`\nNow searching for: ${userName}`);
  await filterInput.clear();
  await filterInput.fill(userName);
  await page.locator('.cxd-Table-searchableForm button[type="submit"]').first().click();
  await page.waitForTimeout(2000);

  const rows2 = page.locator('tbody tr');
  const rowCount2 = await rows2.count();
  console.log(`Rows after searching '${userName}': ${rowCount2}`);
  for (let i = 0; i < rowCount2; i++) {
    const text = (await rows2.nth(i).textContent())?.substring(0, 150);
    console.log(`  Row ${i}: ${text}`);
  }

  // Cleanup
  await rpc(request, 'NopAuthUser__delete', { id: userId }).catch(() => {});
});

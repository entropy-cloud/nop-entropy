import { test } from '@nop-entropy/e2e-shared';
const { login, rpc, loginRpc } = await import('@nop-entropy/e2e-shared');

test('debug submit', async ({ page, request }) => {
  const requests: string[] = [];
  page.on('request', req => {
    if (req.url().includes('/r/')) {
      requests.push(`REQ: ${req.method()} ${decodeURIComponent(req.url()).slice(0,200)} body=${req.postData()?.slice(0,300)}`);
    }
  });

  await login(page, { username: 'nop', password: '123' });
  await page.goto('http://localhost:4173/#/NopAuthUser-main');
  await page.waitForTimeout(2000);

  // Click add
  await page.locator('[data-slot="crud-toolbar-main"] button').filter({ hasText: /新增/ }).first().click();
  await page.waitForTimeout(1000);

  // Fill form
  const dialog = page.locator('[data-slot="dialog-surface"]').first();
  await dialog.locator('input[name="userName"]').fill('e2e_submit_test_' + Date.now());
  await dialog.locator('input[name="nickName"]').fill('SubmitTest');

  // Click submit
  const submitBtn = dialog.locator('button').filter({ hasText: /确定/ }).first();
  await submitBtn.click();
  await page.waitForTimeout(5000);

  console.log('All requests:', JSON.stringify(requests, null, 2));
});

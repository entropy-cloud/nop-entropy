import { test } from '@nop-entropy/e2e-shared';
const { login, rpc, loginRpc, FluxAdapter } = await import('@nop-entropy/e2e-shared');
import { FormDialog, CrudListPage } from '@nop-entropy/e2e-shared';

test('debug console', async ({ page, request }) => {
  // Capture all console messages
  const consoleMsgs: string[] = [];
  page.on('console', msg => consoleMsgs.push(`${msg.type()}: ${msg.text()}`));
  page.on('pageerror', err => consoleMsgs.push(`PAGEERROR: ${err.message}`));
  page.on('requestfailed', req => consoleMsgs.push(`REQFAIL: ${req.url()}`));

  await login(page, { username: 'nop', password: '123' });

  // Navigate to user page and open add dialog
  await page.goto('http://localhost:4173/#/NopAuthUser-main');
  await page.waitForTimeout(2000);

  // Click add button
  await page.locator('[data-slot="crud-toolbar-main"] button').filter({ hasText: /新增/ }).first().click();
  await page.waitForTimeout(2000);

  // Try to fill and submit
  const dialog = page.locator('[data-slot="dialog-surface"]').first();
  const fillResult = await dialog.locator('input[name="userName"]').fill('e2e_test_' + Date.now());
  console.log('Fill result:', fillResult);

  const submitBtn = dialog.locator('button').filter({ hasText: /确定|确认|保存/ }).first();
  await submitBtn.click();
  await page.waitForTimeout(3000);

  console.log('Console messages:', JSON.stringify(consoleMsgs, null, 2));
  console.log('Dialog still open:', await dialog.isVisible().catch(() => 'ERROR'));
});

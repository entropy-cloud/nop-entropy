import { test } from '@nop-entropy/e2e-shared';
import { expect } from '@playwright/test';
import { login } from '@nop-entropy/e2e-shared';

test('diagnose: capture console errors', async ({ page }) => {
  const errors: string[] = [];
  page.on('console', msg => {
    if (msg.type() === 'error' || msg.type() === 'warning') {
      errors.push(`[${msg.type()}] ${msg.text()}`);
    }
  });

  await login(page, { username: 'nop', password: '123' });
  await page.goto('#/NopAuthUser-main');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(3000);

  console.log('=== Console errors/warnings ===');
  for (const e of errors) {
    console.log(e);
  }
  if (errors.length === 0) console.log('(none)');

  expect(true).toBe(true);
});

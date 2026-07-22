import { test } from '@playwright/test';
import { login } from '@nop-entropy/e2e-shared';

test('diag: console errors during search', async ({ page }) => {
  // Capture console errors from the start
  const errors: string[] = [];
  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      errors.push(msg.text());
    }
  });
  page.on('pageerror', (err) => {
    errors.push(`PAGE ERROR: ${err.message}`);
  });

  await login(page, { fast: true });
  await page.goto('#/NopAuthUser-main');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(3000);

  console.log('=== BEFORE SEARCH ===');
  console.log('tr count:', await page.locator('tr').count());

  // Search
  const filterInput = page.locator('input[name^="filter_userName"]').first();
  await filterInput.fill('nop');
  await page.locator('.cxd-Table-searchableForm button[type="submit"]').first().click();
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(3000);

  console.log('\n=== AFTER SEARCH ===');
  console.log('tr count:', await page.locator('tr').count());

  console.log('\n=== CONSOLE ERRORS ===');
  if (errors.length === 0) {
    console.log('No console errors');
  } else {
    for (const e of errors) {
      console.log(e);
    }
  }

  // Also try: just refresh without search
  console.log('\n=== TRYING REFRESH ===');
  await page.goto('#/NopAuthUser-main');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);
  console.log('tr count after navigation:', await page.locator('tr').count());

  const refreshBtn = page.locator('[class*="fa-sync"]').first();
  if (await refreshBtn.isVisible().catch(() => false)) {
    await refreshBtn.click();
    await page.waitForTimeout(3000);
    console.log('tr count after refresh:', await page.locator('tr').count());
    console.log('Errors after refresh:', errors.length);
  }
});

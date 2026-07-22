import { test } from '@playwright/test';
import { login } from '@nop-entropy/e2e-shared';

test('diag: full page dump after search', async ({ page }) => {
  await login(page, { fast: true });
  await page.goto('#/NopAuthUser-main');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(3000);

  console.log('=== BEFORE SEARCH ===');
  console.log('tr count:', await page.locator('tr').count());

  // Search for 'nop'
  const filterInput = page.locator('input[name^="filter_userName"]').first();
  await filterInput.fill('nop');
  await page.locator('.cxd-Table-searchableForm button[type="submit"]').first().click();
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(3000);

  console.log('\n=== AFTER SEARCH ===');
  console.log('tr count:', await page.locator('tr').count());

  // Dump the entire #main-content or app root
  const mainContent = page.locator('#main-content, main, #root').first();
  const html = await mainContent.innerHTML();
  console.log('=== MAIN CONTENT HTML (first 5000) ===');
  console.log(html.substring(0, 5000));

  // Check for JS errors
  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      console.log('CONSOLE ERROR:', msg.text());
    }
  });
});

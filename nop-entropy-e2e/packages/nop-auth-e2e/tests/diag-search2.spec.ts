import { test } from '@playwright/test';
import { login } from '@nop-entropy/e2e-shared';

test('diag: search and row structure', async ({ page }) => {
  await login(page, { fast: true });
  await page.goto('#/NopAuthUser-main');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);

  // Search for 'nop'
  const filterInput = page.locator('input[name^="filter_userName"]').first();
  await filterInput.fill('nop');
  await page.locator('.cxd-Table-searchableForm button[type="submit"]').first().click();

  // Wait for network idle
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(3000);

  // Try multiple selectors
  const selectors = ['tr', 'tbody tr', '.cxd-Table-row', '.cxd-Table-body tr'];
  for (const sel of selectors) {
    const count = await page.locator(sel).count();
    console.log(`Selector "${sel}": ${count} matches`);
  }

  // Dump the table body HTML
  const tableBody = page.locator('.cxd-Table-body').first();
  if (await tableBody.count().then(c => c > 0)) {
    const html = await tableBody.innerHTML();
    console.log('=== TABLE BODY HTML (first 2000) ===');
    console.log(html.substring(0, 2000));
  } else {
    console.log('No .cxd-Table-body found');
    // Dump the entire CRUD body
    const crudBody = page.locator('.cxd-Crud-body').first();
    if (await crudBody.count().then(c => c > 0)) {
      console.log('=== CRUD BODY HTML (first 2000) ===');
      console.log((await crudBody.innerHTML()).substring(0, 2000));
    }
  }

  // Check if there's a loading spinner
  const spinner = page.locator('.cxd-Spinner, [class*="loading"], [class*="Spinner"]');
  console.log(`Spinner count: ${await spinner.count()}`);
  for (let i = 0; i < Math.min(3, await spinner.count()); i++) {
    const visible = await spinner.nth(i).isVisible().catch(() => false);
    console.log(`  Spinner ${i} visible: ${visible}`);
  }
});

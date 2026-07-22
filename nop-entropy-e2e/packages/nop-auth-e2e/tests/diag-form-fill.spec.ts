import { test, expect } from '@playwright/test';
import { login } from '@nop-entropy/e2e-shared';

const TEST_ID = `diag_${Date.now()}`;

test('diag: user form fill and submit', async ({ page }) => {
  await login(page, { fast: true });
  await page.goto('#/NopAuthUser-main');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);

  // Click add
  await page.locator('button:has(.fa-plus)').first().click();
  await page.waitForTimeout(1500);

  const dialog = page.locator('.cxd-Modal, .cxd-Dialog').first();
  console.log('Dialog visible:', await dialog.isVisible().catch(() => false));

  // Dump form structure
  const formItems = dialog.locator('[data-amis-name]');
  const itemCount = await formItems.count();
  console.log(`\n=== FORM ITEMS (${itemCount}) ===`);
  for (let i = 0; i < itemCount; i++) {
    const name = await formItems.nth(i).getAttribute('data-amis-name');
    const hasInput = await formItems.nth(i).locator('input, textarea, select').count().then(c => c > 0);
    const hasSelect = await formItems.nth(i).locator('.cxd-Select').count().then(c => c > 0);
    const hasCheckbox = await formItems.nth(i).locator('input[type="checkbox"]').count().then(c => c > 0);
    const required = await formItems.nth(i).getAttribute('class');
    const isRequired = required?.includes('is-required') ?? false;
    console.log(`  [${i}] name="${name}" input=${hasInput} select=${hasSelect} checkbox=${hasCheckbox} required=${isRequired}`);
  }

  // Fill basic fields
  const userName = `${TEST_ID}_create`;
  await dialog.locator('input[name="userName"]').fill(userName);
  await dialog.locator('input[name="nickName"]').fill('Diag User');
  await dialog.locator('input[name="password"]').fill('Test@1234');
  await dialog.locator('input[name="__password2"]').fill('Test@1234');

  // Try selectOption for gender
  console.log('\n=== TRYING GENDER SELECT ===');
  const genderItem = dialog.locator('[data-amis-name="gender"]').first();
  const genderSelect = genderItem.locator('.cxd-Select').first();
  if (await genderSelect.count().then(c => c > 0)) {
    console.log('Gender select found, clicking...');
    await genderSelect.click();
    await page.waitForTimeout(500);

    // Dump dropdown options
    const options = page.locator('.cxd-Select-menu, .cxd-DropDown-menu, [role="listbox"], [role="option"]');
    const optCount = await options.count();
    console.log(`Options found: ${optCount}`);
    for (let i = 0; i < optCount; i++) {
      const text = await options.nth(i).textContent();
      const cls = await options.nth(i).getAttribute('class');
      console.log(`  Option/Container [${i}]: text="${text?.trim().substring(0, 50)}" class="${cls}"`);
    }

    // Try to click option with text "男"
    const maleOption = page.locator('[role="option"], .cxd-Select-option, .cxd-DropDown-menuItem').filter({ hasText: '男' }).first();
    console.log(`Male option count: ${await maleOption.count()}`);
    if (await maleOption.count().then(c => c > 0)) {
      await maleOption.click();
      console.log('Clicked male option');
    } else {
      console.log('Could not find male option');
      // Try broader search
      const anyOption = page.locator('div, li, a').filter({ hasText: /^男$/ }).first();
      console.log(`Broader male option: ${await anyOption.count()}`);
    }
  } else {
    console.log('Gender select NOT found');
  }

  await page.waitForTimeout(500);

  // Now try to submit
  console.log('\n=== SUBMITTING FORM ===');
  const submitBtn = dialog.getByRole('button', { name: /确定|确认|保存|Confirm|Save/ }).first();
  await submitBtn.click();
  await page.waitForTimeout(2000);

  // Check if dialog is still visible (form errors)
  const dialogStillVisible = await dialog.isVisible().catch(() => false);
  console.log(`Dialog still visible after submit: ${dialogStillVisible}`);

  if (dialogStillVisible) {
    // Check for error messages
    const errors = dialog.locator('.cxd-Form-feedback, .has-error, .cxd-Form-item--error, [class*="error"]');
    const errorCount = await errors.count();
    console.log(`Error elements: ${errorCount}`);
    for (let i = 0; i < errorCount; i++) {
      const text = await errors.nth(i).textContent();
      console.log(`  Error [${i}]: "${text?.trim()}"`);
    }
  }
});

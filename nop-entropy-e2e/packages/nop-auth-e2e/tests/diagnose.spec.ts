import { test, login } from '@nop-entropy/e2e-shared';

test('check tabs', async ({ page, engine }) => {
  await login(page, { username: 'nop', password: '123' });
  await page.goto('http://localhost:4173/#/NopAuthUser-main');
  await page.waitForTimeout(3000);
  await engine.addButton(page).click();
  await page.waitForTimeout(3000);

  const modal = await page.evaluate(() => {
    const m = document.querySelector('.cxd-Modal, .cxd-Dialog');
    if (!m) return { exists: false };
    return {
      exists: true,
      formItems: m.querySelectorAll('.cxd-Form-item').length,
      inputs: m.querySelectorAll('input[name], textarea[name]').length,
    };
  });
  console.log(JSON.stringify(modal));
  if (!modal.exists || modal.formItems === 0) throw new Error('Tabs broken');
});

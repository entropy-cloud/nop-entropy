import { test, login } from '@nop-entropy/e2e-shared';
import { UserPO } from './page-objects/user.po.js';

test('verify form items render', async ({ page, engine }) => {
  await login(page, { username: 'nop', password: '123' });
  const userPO = new UserPO(page, engine);
  await userPO.goto();
  await engine.addButton(page).click();
  await page.waitForTimeout(3000);

  const state = await page.evaluate(() => {
    const modal = document.querySelector('.cxd-Modal, .cxd-Dialog');
    if (!modal) return { exists: false };
    return {
      exists: true,
      formItems: modal.querySelectorAll('.cxd-Form-item').length,
      inputs: modal.querySelectorAll('input[name], textarea[name], select[name]').length,
      inputNames: Array.from(modal.querySelectorAll('input[name], textarea[name], select[name]'))
        .map(el => el.getAttribute('name')).filter(Boolean),
      tabPanes: modal.querySelectorAll('.cxd-Tabs-pane').length,
      activePaneChildren: modal.querySelector('.cxd-Tabs-pane.is-active')?.children.length ?? 0,
    };
  });
  console.log('=== FORM STATE ===');
  console.log(JSON.stringify(state, null, 2));
});

import { test, expect } from '@playwright/test';
import { LoginPO } from './page-objects/login.po.js';
import { UserPO } from './page-objects/user.po.js';

test.describe('登录功能', () => {
  test('成功登录 - 离开登录页', async ({ page }) => {
    const loginPO = new LoginPO(page);

    await loginPO.goto();
    await loginPO.login('nop', '123');

    await expect(page).not.toHaveURL(/#\/login/);
  });

  test('登录失败 - 错误密码', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');
    await page.locator('input[placeholder="账号"]').fill('nop');
    await page.locator('input[placeholder="密码"]').fill('wrong_password');
    await page.getByRole('button', { name: '登 录' }).click();

    await page.waitForTimeout(2000);
    await expect(page).toHaveURL(/#\/login/);
  });

  test('登录后访问受保护页面', async ({ page }) => {
    const loginPO = new LoginPO(page);
    await loginPO.goto();
    await loginPO.login('nop', '123');

    const userPO = new UserPO(page);
    await userPO.goto();

    await expect(page).toHaveURL(/NopAuthUser-main/);
    await expect(
      page.locator('table, .ant-table, .crud-table-wrap').first(),
    ).toBeVisible({ timeout: 15_000 });
  });

  test('登出后无法访问受保护页面', async ({ page }) => {
    const loginPO = new LoginPO(page);

    await loginPO.goto();
    await loginPO.login('nop', '123');

    // Clear all storage to simulate logout
    await page.evaluate(() => {
      localStorage.clear();
      sessionStorage.clear();
    });

    const userPO = new UserPO(page);
    await userPO.goto();

    // After clearing tokens, reload to force auth check
    await page.reload();
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    // The app should redirect to login or not show data
    const currentUrl = page.url();
    const onLoginPage = currentUrl.includes('login') || currentUrl === new URL(currentUrl).origin + '/';
    if (!onLoginPage) {
      // If still on a protected page, verify no data loads
      const tableHasData = await page.locator('tbody > tr').first().isVisible().catch(() => false);
      expect(tableHasData).toBeFalsy();
    }
  });
});

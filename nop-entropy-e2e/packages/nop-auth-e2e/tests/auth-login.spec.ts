import { test } from '@nop-entropy/e2e-shared';
import { expect } from '@playwright/test';
import { login, forceLocale } from '@nop-entropy/e2e-shared';
import { UserPO } from './page-objects/user.po.js';

test.describe('登录功能', () => {
  test('成功登录 - 离开登录页', async ({ page }) => {
    await login(page, { username: 'nop', password: '123' });

    await expect(page).not.toHaveURL(/#\/login/);
  });

  test('登录失败 - 错误密码', async ({ page }) => {
    await page.goto('/');
    await forceLocale(page);
    await page.reload();
    await page.waitForLoadState('networkidle');
    const allInputs = await page.locator('input:visible').all();
    if (allInputs.length >= 2) {
      await allInputs[0].fill('nop');
      await allInputs[1].fill('wrong_password');
    }
    await page.getByRole('button', { name: /登\s*录/ }).click();

    await page.waitForTimeout(2000);
    await expect(page).toHaveURL(/login/);
  });

  test('登录后访问受保护页面', async ({ page, engine }) => {
    await login(page, { username: 'nop', password: '123' });

    const userPO = new UserPO(page, engine);
    await userPO.goto();

    await expect(page).toHaveURL(/NopAuthUser-main/);
    await expect(
      page.locator('table, .ant-table, .crud-table-wrap').first(),
    ).toBeVisible({ timeout: 15_000 });
  });

  test('登出后无法访问受保护页面', async ({ page, engine }) => {
    await login(page, { username: 'nop', password: '123' });

    await page.evaluate(() => {
      localStorage.clear();
      sessionStorage.clear();
    });

    const userPO = new UserPO(page, engine);
    await userPO.goto();

    await page.reload();
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    const currentUrl = page.url();
    const onLoginPage = currentUrl.includes('login') || currentUrl === new URL(currentUrl).origin + '/';
    if (!onLoginPage) {
      const tableHasData = await page.locator('tbody > tr').first().isVisible().catch(() => false);
      expect(tableHasData).toBeFalsy();
    }
  });
});

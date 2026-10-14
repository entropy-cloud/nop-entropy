import { test } from '@nop-entropy/e2e-shared';
import { expect } from '@playwright/test';
import { forceLocale, login, LoginPage } from '@nop-entropy/e2e-shared';
import { UserPO } from './page-objects/user.po.js';

const ADMIN = { username: 'nop', password: '123' } as const;

test.describe('登录功能', () => {
  test('成功登录 - 离开登录页', async ({ page }) => {
    await login(page, ADMIN);

    const loginPage = new LoginPage(page);
    await loginPage.assertSignedIn();
  });

  test('登录失败 - 错误密码', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.open();
    await forceLocale(page);

    await loginPage.submitCredentials({ username: 'nop', password: 'wrong_password' });

    await loginPage.assertStillOnLogin();
  });

  test('登录后访问受保护页面', async ({ page, engine }) => {
    await login(page, ADMIN);

    const userPO = new UserPO(page, engine);
    await userPO.goto();

    await expect(page).toHaveURL(/NopAuthUser-main/);
    await userPO.assertListVisible();
  });

  test('登出后无法访问受保护页面', async ({ page, engine }) => {
    await login(page, ADMIN);

    // 清除会话存储模拟登出
    await page.evaluate(() => {
      localStorage.clear();
      sessionStorage.clear();
    });

    const userPO = new UserPO(page, engine);

    // 受保护页面要么导航被登录守卫弹回，要么渲染不出任何数据行
    const reached = await userPO.tryGoto('NopAuthUser-main');
    await page.reload();
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    if (reached) {
      expect(await userPO.getTableRowCount()).toBe(0);
    } else {
      expect(page.url()).toMatch(/login|#\/?$/);
    }
  });
});

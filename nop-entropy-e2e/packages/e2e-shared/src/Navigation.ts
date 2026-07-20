import type { Page as PlaywrightPage } from '@playwright/test';
import { loginRpc } from './RpcClient';

const LOCALE_KEY = 'nop-language:v1';

export async function login(
  page: PlaywrightPage,
  options?: { username?: string; password?: string; baseUrl?: string },
): Promise<void> {
  const baseUrl = options?.baseUrl ?? process.env.E2E_BASE_URL ?? 'http://127.0.0.1:4175';
  const username = options?.username ?? process.env.E2E_USER ?? 'nop';
  const password = options?.password ?? process.env.E2E_PASSWORD ?? '123';

  await page.goto(baseUrl);
  await page.evaluate((key) => localStorage.setItem(key, 'zh-CN'), LOCALE_KEY);
  await page.reload();
  await page.waitForLoadState('networkidle');

  if (process.env.E2E_AUTH_MODE === 'rpc') {
    const token = await loginRpc({ url: baseUrl }, username, password);
    await page.evaluate((t) => {
      localStorage.setItem('accessToken', t);
    }, token);
    await page.reload();
    await page.waitForLoadState('networkidle');
    return;
  }

  const usernameInput = page.locator('input[name="username"], input[type="text"]').first();
  const passwordInput = page.locator('input[name="password"], input[type="password"]').first();

  if (await usernameInput.isVisible()) {
    await usernameInput.fill(username);
    await passwordInput.fill(password);
    await page.locator('button[type="submit"], button:has-text("登录")').click();
    await page.waitForLoadState('networkidle');
  }
}

export async function navigateTo(page: PlaywrightPage, hashRoute: string): Promise<void> {
  await page.goto(`#/${hashRoute}`);
  await page.waitForLoadState('networkidle');
}

export async function loginAndNavigate(
  page: PlaywrightPage,
  hashRoute: string,
  options?: { username?: string; password?: string; baseUrl?: string },
): Promise<void> {
  await login(page, options);
  await navigateTo(page, hashRoute);
}

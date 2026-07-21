import type { Page as PlaywrightPage } from '@playwright/test';
import { loginRpc } from './RpcClient';

const LOCALE_KEY = 'nop-language:v1';

export interface LoginOptions {
  username?: string;
  password?: string;
  baseUrl?: string;
}

/**
 * Resolve the base URL from (in priority order):
 * 1. Explicit option
 * 2. E2E_BASE_URL env var
 * 3. Default fallback
 */
function resolveBaseUrl(explicit?: string): string {
  return explicit ?? process.env.E2E_BASE_URL ?? 'http://localhost:4173';
}

/**
 * Log in to the application via the browser form.
 *
 * After login, the React auth store will be populated with user info + token,
 * and subsequent page navigations will be authenticated.
 *
 * If `E2E_AUTH_MODE=rpc`, performs login via direct RPC and injects the token
 * into localStorage — faster and avoids form-render timing issues.
 */
export async function login(
  page: PlaywrightPage,
  options?: LoginOptions | string,
): Promise<void> {
  const opts: LoginOptions =
    typeof options === 'string' ? { baseUrl: options } : (options ?? {});

  const baseUrl = resolveBaseUrl(opts.baseUrl);
  const username = opts.username ?? process.env.E2E_USER ?? 'nop';
  const password = opts.password ?? process.env.E2E_PASSWORD ?? '123';

  await page.goto(baseUrl);
  await page.evaluate((key) => localStorage.setItem(key, 'zh-CN'), LOCALE_KEY);
  await page.reload();
  await page.waitForLoadState('networkidle');

  if (process.env.E2E_AUTH_MODE === 'rpc') {
    const token = await loginRpc({ url: baseUrl }, username, password);
    await page.evaluate((t) => localStorage.setItem('accessToken', t), token);
    await page.reload();
    await page.waitForLoadState('networkidle');
    await waitForAuthenticated(page);
    await waitForMenuLoaded(page);
    return;
  }

  const usernameInput = page.locator('input[name="username"], input[type="text"]').first();
  const passwordInput = page.locator('input[name="password"], input[type="password"]').first();

  if (await usernameInput.isVisible()) {
    await usernameInput.fill(username);
    await passwordInput.fill(password);
    await page.locator('button[type="submit"], button:has-text("登录")').click();
    await waitForAuthenticated(page);
    await waitForMenuLoaded(page);
  }
}

/**
 * Wait until the React auth store (sessionStorage 'auth:v2') reports
 * isAuthenticated === true. More reliable than `waitForLoadState('networkidle')`
 * because it directly observes the application state rather than network timing.
 */
async function waitForAuthenticated(page: PlaywrightPage, timeoutMs = 30_000): Promise<void> {
  await page.waitForFunction(
    () => {
      try {
        const raw = sessionStorage.getItem('auth:v2');
        if (!raw) return false;
        const parsed = JSON.parse(raw);
        return parsed?.state?.isAuthenticated === true;
      } catch {
        return false;
      }
    },
    undefined,
    { timeout: timeoutMs },
  );
}

/**
 * Wait until the sidebar/menu has populated with items, indicating that the
 * menu query has resolved and route registration is complete. Without this,
 * direct hash navigation (e.g. #/NopAuthUser-main) may hit the catch-all route
 * because the menu-driven routes haven't been registered yet.
 */
async function waitForMenuLoaded(page: PlaywrightPage, timeoutMs = 15_000): Promise<void> {
  await page.waitForFunction(
    () => document.querySelectorAll('nav button[class*="flex-1"]').length >= 2,
    undefined,
    { timeout: timeoutMs },
  );
}

export async function navigateTo(page: PlaywrightPage, hashRoute: string): Promise<void> {
  await page.goto(`#/${hashRoute}`);
  await page.waitForLoadState('networkidle');
}

/**
 * Force the nop-chaos-next locale by writing to localStorage.
 * Useful in tests that need a specific language without going through login.
 */
export async function forceLocale(
  page: PlaywrightPage,
  locale = 'zh-CN',
): Promise<void> {
  await page.evaluate(
    ([key, value]) => localStorage.setItem(key, value),
    [LOCALE_KEY, locale] as const,
  );
}

export async function loginAndNavigate(
  page: PlaywrightPage,
  hashRoute: string,
  options?: LoginOptions | string,
): Promise<void> {
  await login(page, options);
  await navigateTo(page, hashRoute);
}

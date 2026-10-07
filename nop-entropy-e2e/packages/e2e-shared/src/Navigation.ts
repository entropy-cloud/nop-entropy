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
 * 2. E2E_BASE_URL / BASE_URL / PLAYWRIGHT_BASE_URL env var
 *    （与 playwright.config 的 baseURL 解析一致，保证与测试 baseURL 同 origin，
 *      避免 localhost/127.0.0.1 差异导致 sessionStorage 认证状态隔离丢失）
 * 3. Default fallback '/' —— 相对路径按 playwright.config 的 use.baseURL 解析
 *    （FRONTEND_DEV_MODE=true 时是 vite 4173，默认部署形态是后端托管的 SPA 静态资源）
 */
function resolveBaseUrl(_page: PlaywrightPage, explicit?: string): string {
  if (explicit) return explicit;
  return (
    process.env.E2E_BASE_URL ??
    process.env.BASE_URL ??
    process.env.PLAYWRIGHT_BASE_URL ??
    '/'
  );
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

  const baseUrl = resolveBaseUrl(page, opts.baseUrl);
  const username = opts.username ?? process.env.E2E_USER ?? 'nop';
  const password = opts.password ?? process.env.E2E_PASSWORD ?? '123';

  await page.goto(baseUrl);
  // locale 持久化到 localStorage 但不在登录前 reload——reload 会重新引导应用，
  // 引导完成前填写的登录表单会被随后的重渲染清空（提交空值、必填校验报错）。
  // 壳层缺省语言即 zh-CN，本次会话文本断言不受影响；持久化值在后续整页加载时生效。
  await page.evaluate((key) => localStorage.setItem(key, 'zh-CN'), LOCALE_KEY);

  if (process.env.E2E_AUTH_MODE === 'rpc') {
    // baseUrl 可能是相对路径 '/'，loginRpc 在 Node 侧 fetch，需要绝对 origin
    const origin = new URL(page.url()).origin;
    const token = await loginRpc({ url: origin }, username, password);
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
    // 提交按钮必须与输入框同表单：页面可能存在多套（移动端/桌面端）登录表单，
    // 裸 first() 会填 A 表单点 B 表单，B 的空值校验直接报错
    const loginForm = page
      .locator('form:has(input[name="username"]), form:has(input[type="password"])')
      .first();
    const submitBtn = loginForm
      .locator('button[type="submit"], button:has-text("登录")')
      .first();

    // 表单可能在水合完成前被填写（受控组件重渲染会清空值）——填后校验、整体重试
    for (let attempt = 0; attempt < 3; attempt++) {
      await usernameInput.fill(username);
      await passwordInput.fill(password);
      const filled =
        (await usernameInput.inputValue()) === username &&
        (await passwordInput.inputValue()) === password;
      if (!filled) {
        await page.waitForTimeout(800);
        continue;
      }
      await submitBtn.click();
      const ok = await waitForAuthenticated(page, 15_000).then(() => true).catch(() => false);
      if (ok) {
        await waitForMenuLoaded(page);
        return;
      }
      await page.waitForTimeout(800);
    }
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
        // 壳层可能把会话写在 sessionStorage 或 localStorage（不同版本/渲染路径）
        const raw = sessionStorage.getItem('auth:v2') ?? localStorage.getItem('auth:v2');
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
 * Click a sidebar menu button by exact title and wait for navigation.
 * Handles exact matching to avoid ambiguity (e.g. "Dashboard" vs "Flux Dashboard").
 */
export async function openMenuRoute(
  page: PlaywrightPage,
  menuTitle: string,
  urlPattern?: RegExp,
): Promise<void> {
  const btn = page.locator('aside, nav').getByRole('button', { name: menuTitle, exact: true }).first();
  await btn.click();
  if (urlPattern) {
    await page.waitForURL(urlPattern, { timeout: 10_000 });
  }
  await page.waitForLoadState('networkidle');
}

/**
 * Wait for the sidebar to be visible and contain at least the Dashboard entry.
 * Use after login to confirm the app shell has rendered.
 */
export async function waitForSidebar(page: PlaywrightPage, timeoutMs = 15_000): Promise<void> {
  await page.locator('aside, nav').first().waitFor({ state: 'visible', timeout: timeoutMs });
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

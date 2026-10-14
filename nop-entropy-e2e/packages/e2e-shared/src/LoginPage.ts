import { expect } from '@playwright/test';
import type { Page as PlaywrightPage } from '@playwright/test';
import { navigateTo } from './Navigation';

export interface Credentials {
  username: string;
  password: string;
}

/**
 * 登录页页面对象。
 *
 * spec 只表达"用某账号登录 / 断言仍在登录页"这类业务意图，
 * 输入框、提交按钮、路由判定等 UI 细节全部收口在此。
 */
export class LoginPage {
  constructor(private page: PlaywrightPage) {}

  /** 打开应用入口（未登录时应落在登录页）。 */
  async open(baseUrl?: string): Promise<void> {
    await this.page.goto(baseUrl ?? '/');
    await this.page.waitForLoadState('networkidle').catch(() => {});
  }

  private get usernameInput() {
    return this.page.locator('input[name="username"], input[type="text"]').first();
  }

  private get passwordInput() {
    return this.page.locator('input[name="password"], input[type="password"]').first();
  }

  /** 等待登录表单渲染完成（壳层 bootstrapPending 阶段只有空白占位）。 */
  async waitForForm(timeoutMs = 20_000): Promise<void> {
    await this.usernameInput.waitFor({ state: 'visible', timeout: timeoutMs });
  }

  /** 填写凭据并提交，**不**对登录结果做任何断言（成功/失败用例共用）。 */
  async submitCredentials(credentials: Credentials): Promise<void> {
    await this.waitForForm();
    // 提交按钮必须与输入框同表单：页面可能存在多套（移动端/桌面端）登录表单，
    // 裸 first() 会填 A 表单点 B 表单，B 的空值校验直接报错
    const form = this.page
      .locator('form:has(input[name="username"]), form:has(input[type="password"])')
      .first();
    await this.usernameInput.fill(credentials.username);
    await this.passwordInput.fill(credentials.password);
    await form
      .locator('button[type="submit"], button:has-text("登录")')
      .first()
      .click();
  }

  /** 断言已进入应用（离开登录路由）。 */
  async assertSignedIn(timeoutMs = 15_000): Promise<void> {
    await expect(this.page).not.toHaveURL(/#\/login/, { timeout: timeoutMs });
  }

  /** 断言仍停留在登录路由（凭据错误等失败场景）。 */
  async assertStillOnLogin(timeoutMs = 15_000): Promise<void> {
    await expect(this.page).toHaveURL(/login/, { timeout: timeoutMs });
  }

  /** 登录后直接进入指定 hash 路由。 */
  async signInAndOpen(credentials: Credentials, hashRoute: string): Promise<void> {
    await navigateTo(this.page, hashRoute);
  }
}

import { expect, type Page } from '@playwright/test';

export class LoginPage {
  constructor(readonly page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/');
    await this.page.waitForLoadState('networkidle');
  }

  async login(username = 'nop', password = '123'): Promise<void> {
    await this.page.locator('input[placeholder="账号"]').fill(username);
    await this.page.locator('input[placeholder="密码"]').fill(password);
    await this.page.getByRole('button', { name: '登 录' }).click();
    await this.page.waitForURL(/#\/(?!login)/, { timeout: 15_000 }).catch(() => {
      return this.page.waitForURL(/#/, { timeout: 5_000 });
    });
  }

  async assertLoggedIn(): Promise<void> {
    await expect(this.page).not.toHaveURL(/#\/login/);
  }
}

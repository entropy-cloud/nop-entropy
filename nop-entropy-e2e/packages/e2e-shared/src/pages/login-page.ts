import { expect, type Page } from '@playwright/test';

const LOCALE_KEY = 'nop-language:v1';

export class LoginPage {
  constructor(readonly page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/');
    await this.page.evaluate((key) => localStorage.setItem(key, 'zh-CN'), LOCALE_KEY);
    await this.page.reload();
    await this.page.waitForLoadState('networkidle');
  }

  async login(username = 'nop', password = '123'): Promise<void> {
    const inputs = this.page.locator('input:visible');
    await inputs.first().waitFor({ state: 'visible', timeout: 10_000 });

    const allInputs = await inputs.all();
    if (allInputs.length >= 2) {
      await allInputs[0].fill(username);
      await allInputs[1].fill(password);
    }

    await this.page.getByRole('button', { name: /登\s*录/ }).click();
    await this.page.waitForURL(/#\/(?!login)/, { timeout: 15_000 }).catch(() => {
      return this.page.waitForURL(/#/, { timeout: 5_000 });
    });
  }

  async assertLoggedIn(): Promise<void> {
    await expect(this.page).not.toHaveURL(/#\/login/);
  }
}

export async function forceLocale(page: Page): Promise<void> {
  await page.evaluate((key) => localStorage.setItem(key, 'zh-CN'), LOCALE_KEY);
}

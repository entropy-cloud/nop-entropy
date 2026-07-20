import type { Page as PlaywrightPage } from '@playwright/test';
import type { EngineAdapter } from './types';

export abstract class BasePage {
  constructor(
    protected page: PlaywrightPage,
    protected engine: EngineAdapter,
  ) {}

  async goto(hashRoute: string): Promise<void> {
    await this.page.goto(`#/${hashRoute}`);
    await this.page.waitForLoadState('networkidle');
  }
}

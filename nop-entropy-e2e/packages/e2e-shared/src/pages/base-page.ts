import type { Page } from '@playwright/test';
import { navigateToEntity } from '../helpers/table-helper.js';

export abstract class BasePage {
  constructor(readonly page: Page) {}

  abstract get entityName(): string;

  async goto(): Promise<void> {
    await navigateToEntity(this.page, this.entityName);
  }
}

import { type Page } from '@playwright/test';
import {
  AmisCrudPage,
  readModalField,
  waitForTableLoad,
  getTableRowCount,
} from '@nop-entropy/e2e-shared';

export class TaskPO extends AmisCrudPage {
  override get entityName(): string {
    return 'NopJobTask';
  }

  constructor(page: Page) {
    super(page);
  }

  async searchTask(jobFireId: string): Promise<void> {
    await this.search('jobFireId', jobFireId);
  }

  async readField(fieldName: string): Promise<string> {
    return readModalField(this.page, fieldName);
  }

  async getRowCount(): Promise<number> {
    return getTableRowCount(this.page);
  }

  async waitForRow(rowIdentifier: string): Promise<void> {
    const row = this.page.locator('tbody > tr').filter({ hasText: rowIdentifier }).first();
    await row.waitFor({ state: 'visible', timeout: 15_000 });
  }
}

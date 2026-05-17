import { type Page } from '@playwright/test';
import {
  AmisCrudPage,
  readModalField,
  clickInRow,
  confirmDialog,
  waitForTableLoad,
  getTableRowCount,
} from '@nop-entropy/e2e-shared';

export class FirePO extends AmisCrudPage {
  override get entityName(): string {
    return 'NopJobFire';
  }

  constructor(page: Page) {
    super(page);
  }

  async searchFire(jobName: string): Promise<void> {
    await this.search('jobName', jobName);
  }

  async readField(fieldName: string): Promise<string> {
    return readModalField(this.page, fieldName);
  }

  async cancelFire(rowIdentifier: string): Promise<void> {
    await clickInRow(this.page, rowIdentifier, '取消触发');
    await confirmDialog(this.page);
    await waitForTableLoad(this.page);
  }

  async rerunFire(rowIdentifier: string): Promise<void> {
    await clickInRow(this.page, rowIdentifier, '重跑触发');
    await confirmDialog(this.page);
    await waitForTableLoad(this.page);
  }

  async getRowCount(): Promise<number> {
    return getTableRowCount(this.page);
  }

  async waitForRow(rowIdentifier: string): Promise<void> {
    const row = this.page.locator('tbody > tr').filter({ hasText: rowIdentifier }).first();
    await row.waitFor({ state: 'visible', timeout: 15_000 });
  }
}

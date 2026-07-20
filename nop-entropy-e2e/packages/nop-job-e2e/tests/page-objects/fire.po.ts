import { type Page } from '@playwright/test';
import { CrudListPage, FormDialog } from '@nop-entropy/e2e-shared';
import type { EngineAdapter } from '@nop-entropy/e2e-shared';

export class FirePO extends CrudListPage {
  constructor(page: Page, engine: EngineAdapter) {
    super(page, engine, { entityRoute: 'NopJobFire-main', entityName: 'NopJobFire' });
  }

  async goto(): Promise<void> {
    await this.page.goto(`#/NopJobFire-main`);
    await this.page.waitForLoadState('networkidle');
    await this.waitForList();
  }

  async clickSave(): Promise<void> {
    const dialog = new FormDialog(this.page, this.engine);
    await dialog.submit();
    await this.waitForList();
  }

  async clickView(rowId: string): Promise<void> {
    const row = await this.findRowByText(rowId);
    if (row) {
      await this.engine.rowAction(row, /查看/);
    }
    await this.engine.dialog(this.page).waitFor({ state: 'visible' });
  }

  async clickEdit(rowId: string): Promise<void> {
    const row = await this.findRowByText(rowId);
    if (row) {
      await this.engine.rowAction(row, /编辑/);
    }
    await this.engine.dialog(this.page).waitFor({ state: 'visible' });
  }

  async clickDelete(rowId: string): Promise<void> {
    const row = await this.findRowByText(rowId);
    if (row) {
      await this.deleteRow(row);
    }
    await this.waitForList();
  }

  async searchFire(jobName: string): Promise<void> {
    const filterInput = this.page.locator('input[name^="filter_jobName"]').first();
    const visible = await filterInput.isVisible().catch(() => false);
    if (visible) {
      await filterInput.clear();
      await filterInput.fill(jobName);
    }
    await this.engine.addButton(this.page).click();
    await this.engine.table(this.page).waitFor({ state: 'visible' });
  }

  async readField(fieldName: string): Promise<string> {
    const dialog = new FormDialog(this.page, this.engine);
    return dialog.getField(fieldName);
  }

  async cancelFire(rowIdentifier: string): Promise<void> {
    const row = await this.findRowByText(rowIdentifier);
    if (row) {
      await this.engine.rowAction(row, /取消触发/);
    }
    const confirmBtn = this.page.locator('button:has-text("确定"), button:has-text("确认")').first();
    await confirmBtn.click();
    await this.waitForList();
  }

  async rerunFire(rowIdentifier: string): Promise<void> {
    const row = await this.findRowByText(rowIdentifier);
    if (row) {
      await this.engine.rowAction(row, /重跑触发/);
    }
    const confirmBtn = this.page.locator('button:has-text("确定"), button:has-text("确认")').first();
    await confirmBtn.click();
    await this.waitForList();
  }

  async getRowCount(): Promise<number> {
    return this.engine.rows(this.page).count();
  }

  async waitForRow(rowIdentifier: string): Promise<void> {
    const row = await this.findRowByText(rowIdentifier);
    if (row) {
      await row.waitFor({ state: 'visible', timeout: 15_000 });
    }
  }
}

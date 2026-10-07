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
    await this.waitForListWithReload();
  }

  async clickSave(): Promise<void> {
    const dialog = new FormDialog(this.page, this.engine);
    await dialog.submit();
    await this.waitForList();
  }

  async clickView(rowId: string): Promise<void> {
    // 触发批次/任务列表默认升序分页，新记录常在末页——跨页定位
    const row = (await this.findRowByText(rowId)) ?? (await this.findRowDeep(rowId, '作业名'));
    if (!row) {
      throw new Error('未找到行: ' + rowId);
    }
    await this.engine.rowAction(row, /查看/);
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
    const row = await this.findRowDeep(jobName, '作业名');
    if (!row) {
      throw new Error('搜索后未找到行: ' + jobName);
    }
  }

  async readField(fieldName: string): Promise<string> {
    const dialog = new FormDialog(this.page, this.engine);
    return dialog.getField(fieldName);
  }

  async cancelFire(rowIdentifier: string): Promise<void> {
    await this.rowActionWithConfirm(rowIdentifier, /取消触发/);
  }

  async rerunFire(rowIdentifier: string): Promise<void> {
    await this.rowActionWithConfirm(rowIdentifier, /重跑触发/);
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

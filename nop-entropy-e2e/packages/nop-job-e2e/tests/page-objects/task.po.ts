import { type Page } from '@playwright/test';
import { CrudListPage, FormDialog } from '@nop-entropy/e2e-shared';
import type { EngineAdapter } from '@nop-entropy/e2e-shared';

export class TaskPO extends CrudListPage {
  constructor(page: Page, engine: EngineAdapter) {
    super(page, engine, { entityRoute: 'NopJobTask-main', entityName: 'NopJobTask' });
  }

  async goto(): Promise<void> {
    await super.goto('NopJobTask-main');
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

  async clickViewByText(text: string): Promise<void> {
    const row = (await this.findRowByText(text)) ?? (await this.findRowDeep(text, '作业名'));
    if (!row) {
      throw new Error('未找到行: ' + text);
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

  async searchTask(jobFireId: string): Promise<void> {
    // 任务表无过滤表单，且按 jobScheduleId 升序分页——跨页定位目标批次ID的行
    const row = (await this.findRowByText(jobFireId)) ?? (await this.findRowDeep(jobFireId));
    if (!row) {
      throw new Error('搜索后未找到行: ' + jobFireId);
    }
  }

  async readField(fieldName: string): Promise<string> {
    const dialog = new FormDialog(this.page, this.engine);
    return dialog.getField(fieldName);
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

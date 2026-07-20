import { type Page } from '@playwright/test';
import { CrudListPage, FormDialog } from '@nop-entropy/e2e-shared';
import type { EngineAdapter } from '@nop-entropy/e2e-shared';

export class TaskPO extends CrudListPage {
  constructor(page: Page, engine: EngineAdapter) {
    super(page, engine, { entityRoute: 'NopJobTask-main', entityName: 'NopJobTask' });
  }

  async goto(): Promise<void> {
    await this.page.goto(`#/NopJobTask-main`);
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

  async searchTask(jobFireId: string): Promise<void> {
    const filterInput = this.page.locator('input[name^="filter_jobFireId"]').first();
    const visible = await filterInput.isVisible().catch(() => false);
    if (visible) {
      await filterInput.clear();
      await filterInput.fill(jobFireId);
    }
    await this.engine.addButton(this.page).click();
    await this.engine.table(this.page).waitFor({ state: 'visible' });
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

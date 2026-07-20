import type { Page, Locator } from '@playwright/test';
import { BasePage } from './base-page.js';
import { createEngine } from '../engine.js';
import type { EngineAdapter } from '../types.js';
import { FormDialog } from '../FormDialog.js';

export abstract class AmisCrudPage extends BasePage {
  protected readonly engine: EngineAdapter;

  constructor(page: Page) {
    super(page);
    this.engine = createEngine();
  }

  async search(fieldName: string, value: string): Promise<void> {
    const filterInput = this.page.locator(`input[name^="filter_${fieldName}"]`).first();
    const visible = await filterInput.isVisible().catch(() => false);
    if (visible) {
      await filterInput.clear();
      await filterInput.fill(value);
    }
    await this.engine.addButton(this.page).click();
    await this.engine.table(this.page).waitFor({ state: 'visible' });
  }

  async clickAdd(): Promise<void> {
    const btn = await this.engine.addButton(this.page);
    await btn.click();
    await this.engine.dialog(this.page).waitFor({ state: 'visible' });
  }

  async clickSave(): Promise<void> {
    const dialog = new FormDialog(this.page, this.engine);
    const modal = this.engine.dialog(this.page);
    await dialog.submit();
    await modal.waitFor({ state: 'hidden', timeout: 10_000 }).catch(async () => {
      await this.page.keyboard.press('Escape');
      await this.page.waitForTimeout(500);
      await modal.waitFor({ state: 'hidden', timeout: 5_000 }).catch(() => {});
    });
    await this.engine.table(this.page).waitFor({ state: 'visible' });
  }

  async clickView(rowId: string): Promise<void> {
    const row = await this.findRow(rowId);
    if (row) {
      await this.engine.rowAction(row, /查看/);
    }
    await this.engine.dialog(this.page).waitFor({ state: 'visible' });
  }

  async clickEdit(rowId: string): Promise<void> {
    const row = await this.findRow(rowId);
    if (row) {
      await this.engine.rowAction(row, /编辑/);
    }
    await this.engine.dialog(this.page).waitFor({ state: 'visible' });
  }

  async clickDelete(rowId: string): Promise<void> {
    const row = await this.findRow(rowId);
    if (row) {
      await this.engine.rowAction(row, /删除/);
    }
    await this.engine.submitButton(this.engine.dialog(this.page)).click();
    await this.engine.table(this.page).waitFor({ state: 'visible' });
  }

  private async findRow(rowId: string): Promise<Locator | null> {
    const rows = this.engine.rows(this.page);
    const count = await rows.count();
    for (let i = 0; i < count; i++) {
      const row = rows.nth(i);
      const text = (await row.textContent()) ?? '';
      if (text.includes(rowId)) return row;
    }
    return null;
  }
}

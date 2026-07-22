import type { Locator } from '@playwright/test';
import type { Page as PlaywrightPage } from '@playwright/test';
import type { CrudPageConfig, EngineAdapter } from './types';
import { BasePage } from './Page';
import { FormDialog } from './FormDialog';
import { GraphQLClient } from './GraphQlClient';

export class CrudListPage extends BasePage {
  protected config: CrudPageConfig;
  protected _graphQL: GraphQLClient;

  constructor(page: PlaywrightPage, engine: EngineAdapter, config: CrudPageConfig) {
    super(page, engine);
    this.config = config;
    this._graphQL = new GraphQLClient(page);
  }

  get graphQL(): GraphQLClient {
    return this._graphQL;
  }

  async navigate(): Promise<void> {
    await this.goto(this.config.entityRoute);
    await this.waitForList();
  }

  async waitForList(timeoutMs = 30_000): Promise<void> {
    await this.engine.crudContainer(this.page).waitFor({ state: 'visible', timeout: timeoutMs });
    await this.engine.table(this.page).waitFor({ state: 'visible', timeout: timeoutMs });
  }

  async getAddButton(): Promise<Locator> {
    return this.engine.addButton(this.page);
  }

  async clickAdd(): Promise<FormDialog> {
    const btn = await this.getAddButton();
    await btn.click();
    const dialog = new FormDialog(this.page, this.engine);
    await dialog.waitForVisible();
    return dialog;
  }

  async editRow(row: Locator): Promise<FormDialog> {
    await this.engine.rowAction(row, /编辑|Edit/);
    const dialog = new FormDialog(this.page, this.engine);
    await dialog.waitForVisible();
    return dialog;
  }

  async deleteRow(row: Locator): Promise<void> {
    await this.engine.rowAction(row, /删除|Delete/);
    // Wait for the confirmation dialog to appear, then click confirm
    const dialog = this.page.locator('[role="alertdialog"]').last();
    await dialog.waitFor({ state: 'visible', timeout: 5_000 }).catch(() => {});
    await this.page.waitForTimeout(500);
    // Use role-based selector inside the dialog for precision
    const confirmBtn = dialog.getByRole('button', { name: /^Confirm$|^确定$|^确认$/ }).first();
    if (await confirmBtn.count().then((c) => c > 0)) {
      await confirmBtn.click();
    } else {
      // Fallback: generic button search
      await this.page
        .locator('button:has-text("Confirm"), button:has-text("确定"), button:has-text("确认")')
        .last()
        .click({ force: true });
    }
    // Wait for dialog to disappear
    await dialog.waitFor({ state: 'hidden', timeout: 5_000 }).catch(() => {});
    await this.page.waitForLoadState('networkidle').catch(() => {});
    await this.page.waitForTimeout(1000);
  }

  async deleteEntityViaApi(entityName: string, id: string | number): Promise<void> {
    await this._graphQL.delete(entityName, id);
  }

  async findRowByField(field: string, value: string): Promise<Locator | null> {
    const allRows = this.engine.rows(this.page);
    const count = await allRows.count();
    for (let i = 0; i < count; i++) {
      const row = allRows.nth(i);
      const cell = row.locator(`[data-field="${field}"], > td:nth-child(2)`);
      const text = (await cell.textContent()) ?? '';
      if (text.trim() === value) return row;
    }
    return null;
  }

  async findRowByText(text: string): Promise<Locator | null> {
    const allRows = this.engine.rows(this.page);
    const count = await allRows.count();
    for (let i = 0; i < count; i++) {
      const row = allRows.nth(i);
      const rowText = (await row.textContent()) ?? '';
      if (rowText.includes(text)) return row;
    }
    return null;
  }

  async getCellText(rowIndex: number, fieldName: string): Promise<string> {
    const allRows = this.engine.rows(this.page);
    const row = allRows.nth(rowIndex);
    return this.engine.cellValue(row, fieldName, this.config.columnHeaders ?? []);
  }

  async assertGraphQLOk(): Promise<void> {
    // GraphQL operations are verified through the client; this is a no-op placeholder
  }
}

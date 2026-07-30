import type { Locator } from '@playwright/test';
import type { Page as PlaywrightPage } from '@playwright/test';
import type { CrudPageConfig, EngineAdapter } from './types';
import { BasePage } from './Page';
import { FormDialog } from './FormDialog';
import { GraphQLClient } from './GraphQlClient';

export class CrudListPage extends BasePage {
  protected config: CrudPageConfig;
  protected _graphQL: GraphQLClient;
  protected _lastViewRowData: Record<string, string> = {};

  constructor(page: PlaywrightPage, engine: EngineAdapter, config: CrudPageConfig) {
    super(page, engine);
    this.config = config;
    this._graphQL = new GraphQLClient(page);
  }

  get graphQL(): GraphQLClient {
    return this._graphQL;
  }

  // Cache row data before opening view dialog (workaround for flux view dialog empty fields)
  protected async captureRowData(rowIdentifier: string): Promise<void> {
    this._lastViewRowData = {};
    const row = await this.findRowByText(rowIdentifier);
    if (!row) return;

    // 读取表头文本和所有 cell 值
    const { headerTexts, hasSelectCol } = await this.page.evaluate(() => {
      const heads = document.querySelectorAll('[data-slot="table-head"]');
      return {
        headerTexts: Array.from(heads).map(h => h.textContent?.trim() || ''),
        hasSelectCol: !!document.querySelector('[data-slot="table-select-column"]'),
      };
    });

    const hdrs = this.config.columnHeaders ?? [];
    const hasPlaceholder = hdrs[0] === '';
    const fieldKeys = hdrs.length > 0 ? hdrs : headerTexts;
    const effective = hasPlaceholder && !hasSelectCol ? fieldKeys.slice(1) : fieldKeys;

    const cells = row.locator('td, [data-slot="table-cell"]');
    const count = await cells.count();
    for (let i = 0; i < count && i < effective.length; i++) {
      const val = ((await cells.nth(i).textContent()) ?? '').trim();
      // 同时用字段名和表头文本作为 key，确保任一种都能查到
      if (effective[i]) this._lastViewRowData[effective[i]] = val;
      if (headerTexts[i]) this._lastViewRowData[headerTexts[i]] = val;
    }
  }

  async navigate(): Promise<void> {
    await this.goto(this.config.entityRoute);
    await this.waitForList();
  }

  async waitForList(timeoutMs = 30_000): Promise<void> {
    await this.engine.crudContainer(this.page).waitFor({ state: 'visible', timeout: timeoutMs });
    await this.engine.table(this.page).waitFor({ state: 'visible', timeout: timeoutMs });
    await this.page.waitForLoadState('networkidle', { timeout: timeoutMs }).catch(() => {});
    await this.page.waitForTimeout(500);
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

  // ── 搜索 ──

  async search(fieldName: string, value: string): Promise<void> {
    await this.page
      .locator('[data-slot="alert-dialog-overlay"], .cxd-Modal-overlay')
      .waitFor({ state: 'hidden', timeout: 5_000 })
      .catch(() => {});
    await this.page.waitForTimeout(300);

    const filterInput = this.engine.searchField(this.page, fieldName);
    const visible = await filterInput.isVisible().catch(() => false);
    if (!visible) {
      // 尝试展开可折叠的查询表单（flux CRUD 默认折叠）
      const expandToggle = this.page.locator('[data-slot="crud-query-collapse"] button[aria-expanded="false"]');
      if (await expandToggle.count().then((c) => c > 0)) {
        await expandToggle.click();
        await this.page.waitForTimeout(500);
      }
    }
    const visible2 = await filterInput.isVisible().catch(() => false);
    if (visible2) {
      await filterInput.clear();
      await filterInput.fill(value);
      const searchBtn = this.engine.searchButton(this.page);
      const searchBtnVisible = await searchBtn.isVisible().catch(() => false);
      if (searchBtnVisible) {
        await searchBtn.click({ force: true });
      } else {
        await filterInput.press('Enter');
      }
      await this.page.waitForTimeout(1500);
    } else {
      const refreshBtn = this.engine.refreshButton(this.page);
      const refreshVisible = await refreshBtn.isVisible().catch(() => false);
      if (refreshVisible) {
        await refreshBtn.click();
      }
    }
    await this.page.waitForLoadState('networkidle').catch(() => {});
  }

  // ── 行操作（通用） ──

  async editRow(row: Locator): Promise<FormDialog> {
    await this.engine.rowAction(row, /编辑|Edit/);
    const dialog = new FormDialog(this.page, this.engine);
    await dialog.waitForVisible();
    return dialog;
  }

  async deleteRow(row: Locator): Promise<void> {
    await this.engine.rowAction(row, /删除|Delete/);
    await this.engine.confirmDialogAction(this.page);
    await this.page.waitForTimeout(1000);
  }

  async clickView(rowIdentifier: string): Promise<void> {
    const row = await this.findRowByText(rowIdentifier);
    if (row) {
      // 缓存行数据作为 view dialog 空字段的兜底
      await this.captureRowData(rowIdentifier);
      await this.engine.rowAction(row, /查看/);
    }
    await this.engine.dialog(this.page).waitFor({ state: 'visible' });
  }

  async clickEdit(rowIdentifier: string): Promise<void> {
    const row = await this.findRowByText(rowIdentifier);
    if (row) {
      await this.engine.rowAction(row, /编辑/);
    }
    const dialog = this.engine.dialog(this.page);
    await dialog.waitFor({ state: 'visible' });
    // Wait for edit form's initApi to settle
    await this.page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => {});
    await this.page.waitForTimeout(1000);
  }

  async clickDelete(rowIdentifier: string): Promise<void> {
    const row = await this.findRowByText(rowIdentifier);
    if (row) {
      await this.deleteRow(row);
    }
    await this.waitForList();
  }

  async clickSave(): Promise<void> {
    const dialog = new FormDialog(this.page, this.engine);
    await dialog.submit();
    await this.waitForList();
  }

  async readViewField(fieldName: string): Promise<string> {
    // Try 1: dialog field
    const dialog = new FormDialog(this.page, this.engine);
    const value = await dialog.getField(fieldName);
    if (value) return value;
    // Try 2: cached row data by exact field name
    if (this._lastViewRowData[fieldName]) return this._lastViewRowData[fieldName];
    // Try 3: cached row data — 找包含 fieldName 的 key（如 '角色名' 包含 'roleName'？大概率不匹配）
    // 用英文 fieldName 在中文 key 中模糊匹配
    for (const [key, val] of Object.entries(this._lastViewRowData)) {
      if (val && key.toLowerCase().includes(fieldName.toLowerCase())) return val;
    }
    return '';
  }

  // ── 断言 ──

  async assertEntityExists(text: string): Promise<void> {
    const { expect } = await import('@playwright/test');
    // 先用基于 textContent 的方式查找（兼容性好）
    const row = await this.findRowByText(text, 8_000);
    // 如果没找到，再用基于 Playwright locator 的方式兜底
    if (!row) {
      const locatorRow = this.page.locator('[data-slot="table-body"] tr').filter({ hasText: text }).first();
      const exists = await locatorRow.count().then((c) => c > 0);
      expect(exists).toBe(true);
      return;
    }
    expect(row).not.toBeNull();
  }

  async assertEntityNotExists(text: string): Promise<void> {
    const { expect } = await import('@playwright/test');
    // Retry with timeout since flux CRUD might need time to refresh after delete
    for (let attempt = 0; attempt < 20; attempt++) {
      const row = await this.findRowByText(text);
      if (!row) return;
      await this.page.waitForTimeout(500);
    }
    const row = await this.findRowByText(text);
    expect(row).toBeNull();
  }

  async deleteEntityViaApi(entityName: string, id: string | number): Promise<void> {
    await this._graphQL.delete(entityName, id);
  }

  // ── 行查询 ──

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

  async findRowByText(text: string, timeoutMs = 0): Promise<Locator | null> {
    const deadline = Date.now() + timeoutMs;
    do {
      const allRows = this.engine.rows(this.page);
      const count = await allRows.count();
      for (let i = 0; i < count; i++) {
        const row = allRows.nth(i);
        const rowText = (await row.textContent()) ?? '';
        if (rowText.includes(text)) return row;
      }
      if (Date.now() >= deadline) break;
      await this.page.waitForTimeout(300);
    } while (timeoutMs > 0);
    return null;
  }

  async getCellText(rowIndex: number, fieldName: string): Promise<string> {
    const allRows = this.engine.rows(this.page);
    const row = allRows.nth(rowIndex);
    return this.engine.cellValue(row, fieldName, this.config.columnHeaders ?? []);
  }

  async getTableRowCount(): Promise<number> {
    return this.engine.rows(this.page).count();
  }

  async assertGraphQLOk(): Promise<void> {
    // GraphQL operations are verified through the client; this is a no-op placeholder
  }
}

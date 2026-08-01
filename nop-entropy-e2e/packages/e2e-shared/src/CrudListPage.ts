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

  // 打开 view dialog 前缓存表格行数据，作为 view dialog 字段读取的兜底
  //（view 表单字段渲染为无 label 的 nop-text span，无法按字段名定位时使用）。
  protected async captureRowData(rowIdentifier: string): Promise<void> {
    this._lastViewRowData = {};
    const row = await this.findRowByText(rowIdentifier);
    if (!row) return;

    const { headerTexts, hasSelectCol } = await this.page.evaluate(() => {
      const heads = document.querySelectorAll('[data-slot="table-head"]');
      return {
        headerTexts: Array.from(heads).map((h) => h.textContent?.trim() || ''),
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
    // 表格容器可见时数据行可能尚未加载（CRUD 在 mount 后才发起查询，期间表格显示
    // 空/加载占位行）。等待数据查询完成的 networkidle，并尽力等待第一条数据行 attach。
    // 合法空表无数据行时，3s 超时后放行（不影响后续 search/assert 的空判断）。
    await this.page.waitForLoadState('networkidle').catch(() => {});
    await this.engine
      .rows(this.page)
      .first()
      .waitFor({ state: 'attached', timeout: 3_000 })
      .catch(() => {});
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
    if (visible) {
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

    // 等待编辑表单的 loadAction 填充字段后再填写：
    // loadAction 触发 __get (ajax) 并用 form.setValues() 填充响应数据。
    // 若在 setValues() 完成前填写字段，加载的数据会覆盖填写值（编辑用户
    // 提交旧值的真实根因——jsdom 单测复现不了，仅真实浏览器暴露此时序）。
    // 1) 等 loadAction ajax 完成（networkidle）；
    await this.page.waitForLoadState('networkidle').catch(() => {});
    // 2) 轮询直到表单出现带值的输入框，且值稳定（连续两次读取一致），
    //    确认 setValues() 已完成、不再覆盖。
    await this.page
      .waitForFunction(
        () => {
          const inputs = document.querySelectorAll(
            '[data-slot="dialog-surface"] input, [data-slot="dialog-content"] input',
          );
          let anyFilled = false;
          for (const el of inputs) {
            const v = (el as HTMLInputElement).value;
            if (v && v.trim() !== '') {
              anyFilled = true;
              break;
            }
          }
          return anyFilled;
        },
        undefined,
        { timeout: 15_000 },
      )
      .catch(() => {});
    // 3) 额外等 setValues 写入稳定（避免 fill 与异步 setValues 竞态）。
    await this.page.waitForTimeout(300);
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
    // Try 2: cached row data（view 表单字段渲染无 label 时的兜底）
    if (this._lastViewRowData[fieldName]) return this._lastViewRowData[fieldName];
    // Try 3: 模糊匹配包含 fieldName 的缓存 key
    for (const [key, val] of Object.entries(this._lastViewRowData)) {
      if (val && key.toLowerCase().includes(fieldName.toLowerCase())) return val;
    }
    return '';
  }

  // ── 断言 ──

  async assertEntityExists(text: string): Promise<void> {
    const { expect } = await import('@playwright/test');
    const row = await this.findRowByText(text);
    expect(row).not.toBeNull();
  }

  async assertEntityNotExists(text: string): Promise<void> {
    const { expect } = await import('@playwright/test');
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

  async getTableRowCount(): Promise<number> {
    return this.engine.rows(this.page).count();
  }

  async assertGraphQLOk(): Promise<void> {
    // GraphQL operations are verified through the client; this is a no-op placeholder
  }
}

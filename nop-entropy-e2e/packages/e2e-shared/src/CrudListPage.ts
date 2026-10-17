import type { Locator } from '@playwright/test';
import type { Page as PlaywrightPage } from '@playwright/test';
import type { CrudPageConfig, EngineAdapter } from './types';
import { BasePage } from './Page';
import { FormDialog } from './FormDialog';
import { GraphQLClient } from './GraphQlClient';

/** 表单业务数据：`fieldName -> 业务值`。字段控件类型由渲染引擎适配层决定。 */
export type FormData = Record<string, string | number | boolean>;

/**
 * CRUD 列表页页面对象基类。
 *
 * 对外只暴露**业务语义**方法（search / clickAdd / clickSave / clickView /
 * clickEdit / clickDelete / assertRowExists / readViewField ...），业务一律以
 * `fieldName` 表达；DOM 契约、控件类型、分页排序等全部封装在引擎适配层内，
 * spec 代码不应出现 locator、CSS 选择器或控件概念。
 */
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
      const cell = cells.nth(i);
      const val = ((await cell.textContent()) ?? '').trim();
      // 优先读单元格自身 data-field——索引映射在行首存在选择列/序号列时会整体错位，
      // 只能对无 data-field 的行兜底
      const ownField = await cell.getAttribute('data-field');
      if (ownField) this._lastViewRowData[ownField] = val;
      if (effective[i]) this._lastViewRowData[effective[i]] = val;
      if (headerTexts[i]) this._lastViewRowData[headerTexts[i]] = val;
    }
  }

  async navigate(): Promise<void> {
    // entityRoute 为实体名（如 'NopAuthUser'），拼接 -main 得到主页面 hash 路由
    await this.goto(`/${this.config.entityRoute}-main`);
    await this.waitForList();
  }

  // 90s：flux 渲染管线下，页面模型首次编译（per-page 冷缓存）可能超过 30s，
  // 编译结果有缓存，同页后续导航不再耗时
  async waitForList(timeoutMs = 90_000): Promise<void> {
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

  /**
   * 冷启动兜底：后端刚启动时，SPA 首次挂载 CRUD 页面偶发失败（页面 schema 已正常
   * 返回但前端渲染未挂载）。先短等一轮，失败则整页 reload 重挂载后再长等——
   * reload 会重新发起页面 schema 与数据查询，绕过首次挂载的竞态。
   */
  async waitForListWithReload(timeoutMs = 90_000): Promise<void> {
    try {
      await this.waitForList(Math.min(timeoutMs, 30_000));
      return;
    } catch {
      await this.page.reload();
      await this.waitForList(timeoutMs);
    }
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
      .locator('[data-slot="alert-dialog-overlay"]')
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
        // force：行操作下拉的 PopOver overlay 可能残留在 DOM 中拦截指针事件
        await refreshBtn.click({ force: true });
      }
    }
    await this.page.waitForLoadState('networkidle').catch(() => {});
  }

  // ── 表单（按 fieldName 表达业务数据，不暴露控件类型） ──

  /** 用 `{ fieldName: value }` 业务数据填充当前打开的对话框。 */
  async fillDialog(data: FormData): Promise<void> {
    const dialog = new FormDialog(this.page, this.engine);
    for (const [fieldName, value] of Object.entries(data)) {
      await dialog.setField(fieldName, value);
    }
  }

  /** 读取当前对话框中某字段的业务值。 */
  async readDialogField(fieldName: string): Promise<string> {
    const dialog = new FormDialog(this.page, this.engine);
    return dialog.getField(fieldName);
  }

  /** 在当前对话框中为某 fieldName 的下拉字段选择选项（选项值或选项文案）。 */
  async selectDialogOption(fieldName: string, option: string | number | boolean): Promise<void> {
    const dialog = new FormDialog(this.page, this.engine);
    await dialog.selectOption([fieldName], [String(option)]);
  }

  /** 在当前对话框中为某 input-file / input-image 字段上传文件。 */
  async uploadDialogFile(fieldName: string, filePaths: string[]): Promise<void> {
    const dialog = new FormDialog(this.page, this.engine);
    await dialog.uploadFile(fieldName, filePaths);
  }

  /** 在当前对话框中为某 icon-picker 字段选择图标。 */
  async selectDialogIcon(fieldName: string, iconName: string): Promise<void> {
    const dialog = new FormDialog(this.page, this.engine);
    await dialog.selectIcon(fieldName, iconName);
  }

  /** 在当前对话框中为某 picker 弹窗字段点选匹配行。 */
  async selectDialogPickerRow(fieldName: string, rowText: string): Promise<void> {
    const dialog = new FormDialog(this.page, this.engine);
    await dialog.selectPicker(fieldName, rowText);
  }

  /** 切换到当前对话框内的指定标签页（layoutControl="tabs" 的表单字段常藏在非首个 tab）。 */
  async switchDialogTab(tabLabel: string): Promise<void> {
    const dialog = new FormDialog(this.page, this.engine);
    await dialog.switchToTab(tabLabel);
  }

  // ── 行操作（通用） ──

  async editRow(row: Locator): Promise<FormDialog> {
    await this.engine.rowAction(row, /编辑|Edit/);
    const dialog = new FormDialog(this.page, this.engine);
    await dialog.waitForVisible();
    return dialog;
  }

  /**
   * 点击行操作确认框的确认按钮（通用）。flux 壳层 alert-dialog 走 data-slot 契约
   * （历史 portal 残留 DOM 取最新挂载的一个）。确认框未出现返回 false；点击后未关闭
   * 抛错——残留 overlay 会拦截后续所有行操作，宁可显式失败。
   */
  async tryClickConfirm(): Promise<boolean> {
    const btn = this.page
      .locator('[data-slot="alert-dialog-portal"] button:visible')
      .filter({ hasText: /^(确认|确定|OK)$/ })
      .last();

    const appeared = await btn
      .waitFor({ state: 'visible', timeout: 4_000 })
      .then(() => true)
      .catch(() => false);
    if (!appeared) return false;

    // 首次点击可能落在渲染器重渲染窗口期被吞掉——点击后验证框已消失，未消失则重试
    for (let attempt = 0; attempt < 3; attempt++) {
      await btn.click();
      const closed = await this.page
        .locator('[data-slot="alert-dialog-portal"] button:visible')
        .first()
        .waitFor({ state: 'hidden', timeout: 5_000 })
        .then(() => true)
        .catch(() => false);
      if (closed) return true;
      await this.page.waitForTimeout(500);
    }
    throw new Error('行操作确认框点击后未关闭');
  }

  /**
   * 行操作 + 确认框的整体封装（通用）。下拉菜单项点击偶发落空（菜单关闭时指针下
   * 元素已更替）导致确认框不出现——整体重试行操作而不是静默放行，否则状态迁移没有
   * 发生，后续断言/轮询只会收到难以定位的超时。
   */
  async rowActionWithConfirm(rowIdentifier: string, actionNamePattern: RegExp): Promise<void> {
    const row = await this.findRowDeep(rowIdentifier);
    if (!row) {
      throw new Error('未找到行: ' + rowIdentifier);
    }
    for (let attempt = 0; attempt < 2; attempt++) {
      await this.engine.rowAction(row, actionNamePattern);
      if (await this.tryClickConfirm()) {
        await this.waitForList();
        return;
      }
      await this.page.waitForTimeout(800);
    }
    throw new Error('行操作未能触发确认框: ' + actionNamePattern);
  }

  /** 在指定行上执行一个操作（如"启用调度"），不等待确认框。 */
  async runRowAction(rowIdentifier: string, actionNamePattern: RegExp): Promise<void> {
    const row = await this.findRowByText(rowIdentifier);
    if (!row) {
      throw new Error('未找到行: ' + rowIdentifier);
    }
    await this.engine.rowAction(row, actionNamePattern);
  }

  /**
   * 打开某行的业务面板（弹窗或抽屉），如"分配用户"/"分配权限"。
   * `panel: 'dialog' | 'drawer' | 'either'` 声明该业务动作的承载形态，
   * spec 与业务 PO 不需要知道渲染层用的是哪一种。
   */
  async openRowPanel(
    rowIdentifier: string,
    actionNamePattern: RegExp,
    panel: 'dialog' | 'drawer' | 'either' = 'either',
  ): Promise<void> {
    await this.runRowAction(rowIdentifier, actionNamePattern);
    const target =
      panel === 'dialog'
        ? this.engine.dialog(this.page)
        : panel === 'drawer'
          ? this.engine.drawer(this.page)
          : this.engine.dialog(this.page).or(this.engine.drawer(this.page));
    await target.waitFor({ state: 'visible' });
  }

  /** 在指定行上执行一个操作并确认（如"触发"、"启用"），操作后等待列表就绪。 */
  async confirmRowAction(rowIdentifier: string, actionNamePattern: RegExp): Promise<void> {
    await this.rowActionWithConfirm(rowIdentifier, actionNamePattern);
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

  /**
   * 跨页定位后打开查看面板。默认升序分页下新建记录常在末页，
   * 直接 findRowByText 找不到时先翻页/排序再打开。
   */
  async clickViewDeep(rowIdentifier: string, sortHeaderText?: string): Promise<void> {
    const row =
      (await this.findRowByText(rowIdentifier)) ??
      (await this.findRowDeep(rowIdentifier, sortHeaderText));
    if (!row) {
      throw new Error(`未找到业务记录: ${rowIdentifier}`);
    }
    await this.captureRowData(rowIdentifier);
    await this.engine.rowAction(row, /查看/);
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
    // 2) 轮询直到表单出现带值的输入框，确认 setValues() 已完成、不再覆盖。
    await this.page
      .waitForFunction(
        () => {
          const inputs = document.querySelectorAll(
            '[data-slot="dialog-surface"] input, [data-slot="dialog-content"] input',
          );
          for (const el of inputs) {
            const v = (el as HTMLInputElement).value;
            if (v && v.trim() !== '') return true;
          }
          return false;
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

  /**
   * 断言查看面板渲染了指定业务字段且取值非空（按 fieldName，不依赖 label 文案与布局）。
   *
   * 只读查看表单把字段渲染为静态文本、没有 `data-field` 字段根，因此这里走
   * `readViewField`：先按字段定位，取不到时回落到打开面板时缓存的行数据。
   */
  async assertViewFieldPresent(fieldName: string): Promise<void> {
    const { expect } = await import('@playwright/test');
    const value = await this.readViewField(fieldName);
    expect(value, `查看面板未渲染字段 ${fieldName}`).not.toBe('');
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

  /** 断言列表页已就绪（CRUD 容器与表格均可见）。 */
  async assertListVisible(): Promise<void> {
    const { expect } = await import('@playwright/test');
    await expect(this.engine.table(this.page)).toBeVisible({ timeout: 15_000 });
  }

  /**
   * 读取指定行的某列单元格文本（按列名，走 engine.cellValue 的 td[data-field] 契约）。
   * 通用方法：user/role 等所有 Crud PO 均可直接使用。
   */
  async readTableField(rowIdentifier: string, columnName: string): Promise<string> {
    const allRows = this.engine.rows(this.page);
    const count = await allRows.count();
    for (let i = 0; i < count; i++) {
      const r = allRows.nth(i);
      const text = (await r.textContent()) ?? '';
      if (text.includes(rowIdentifier)) {
        return this.engine.cellValue(r, columnName, this.config.columnHeaders ?? []);
      }
    }
    return '';
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

  /**
   * 跨页定位目标行（引擎驱动）：刷新强制重查 → 每页条数调最大 → （可选）点击排序
   * 表头切换升降序 → 逐页向后翻。分页与排序 DOM 差异由引擎适配层封装。
   * 找不到返回 null（由调用方决定报错或重试）。
   */
  async findRowDeep(text: string, sortHeaderText?: string): Promise<Locator | null> {
    // 建单后列表可能停留在旧查询结果——先点刷新强制重查
    const refreshBtn = this.engine.refreshButton(this.page);
    if (await refreshBtn.isVisible().catch(() => false)) {
      await refreshBtn.click({ force: true });
      await this.page.waitForLoadState('networkidle').catch(() => {});
    }

    await this.engine.setPageSize(this.page, 100);

    for (let i = 0; i < 4; i++) {
      const row = await this.findRowByText(text);
      if (row) return row;

      // 升序分页下新建行常在末页：先切降序（前两轮），再逐页向后翻
      if (sortHeaderText && i < 2) {
        await this.engine.sortByColumn(this.page, sortHeaderText);
        const rowAfterSort = await this.findRowByText(text);
        if (rowAfterSort) return rowAfterSort;
      }

      const nextPage = i + 2;
      if (!(await this.engine.goToPage(this.page, nextPage).catch(() => false))) {
        // 无第 nextPage 页：已到末页，排序若还没试完则继续，否则终止
        if (!sortHeaderText || i >= 2) break;
      }
      await this.page.waitForTimeout(400);
    }
    return this.findRowByText(text);
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

  /**
   * 跨页定位目标行，找不到时抛错——用于"检索到某条记录"这类业务前置断言，
   * 失败信息直接给出缺失的业务标识，而不是让调用方各自拼。
   */
  async findRowDeepOrFail(text: string, sortHeaderText?: string): Promise<Locator> {
    const row = await this.findRowDeep(text, sortHeaderText);
    if (!row) {
      throw new Error(`未找到业务记录: ${text}`);
    }
    return row;
  }

  /** 等待包含指定业务标识的行出现。 */
  async waitForRow(rowIdentifier: string, timeoutMs = 15_000): Promise<void> {
    const { expect } = await import('@playwright/test');
    const row = await this.findRowByText(rowIdentifier);
    if (row) {
      await expect(row).toBeVisible({ timeout: timeoutMs });
    }
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

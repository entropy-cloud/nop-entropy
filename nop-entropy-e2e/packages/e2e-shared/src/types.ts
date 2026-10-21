import type { Locator, Page } from '@playwright/test';

export const DEFAULT_LIST_TIMEOUT = 15_000;
export const DEFAULT_DIALOG_TIMEOUT = 15_000;
export const DEFAULT_NAV_TIMEOUT = 30_000;

/** host 为 flux 单引擎；类型保留仅为下游兼容，恒为 'flux'。 */
export type EngineType = 'flux';

export interface EngineAdapter {
  engineName: string;

  // CRUD 列表
  crudContainer(page: Page): Locator;
  table(page: Page): Locator;
  rows(page: Page): Locator;
  cellValue(row: Locator, fieldName: string, columnHeaders: string[]): Promise<string>;
  addButton(page: Page): Locator;
  queryButton(page: Page): Locator;
  rowAction(row: Locator, actionNamePattern: RegExp): Promise<void>;

  // CRUD 搜索
  searchField(page: Page, fieldName: string): Locator;
  searchButton(page: Page): Locator;
  refreshButton(page: Page): Locator;

  /**
   * 按 fieldName 定位任意作用域内的字段输入控件（不限 CRUD 查询表单）——
   * 独立表单页（queryForm 等）与弹窗表单共用同一字段定位契约。
   */
  fieldInput(scope: Page | Locator, fieldName: string): Locator;

  /** 把每页条数设为指定值（flux 原生 select） */
  setPageSize(page: Page, size: number): Promise<void>;
  /** 点击列头排序触发器（每次点击切换升/降序） */
  sortByColumn(page: Page, headerText: string): Promise<void>;
  /** 跳到第 n 页（1-based）；目标页不存在或控件不可用时返回 false */
  goToPage(page: Page, n: number): Promise<boolean>;

  // 对话框 / 表单
  dialog(page: Page): Locator;
  drawer(page: Page): Locator;
  formField(dialog: Locator, fieldName: string): Locator;
  setFieldValue(dialog: Locator, fieldName: string, value: string | boolean | number): Promise<void>;
  /**
   * 上传文件到 `input-file` / `input-image` 字段（按 fieldName 定位隐藏的
   * `input[type="file"]`，用 setInputFiles 注入，不触发原生文件对话框）。
   */
  setFieldFiles(dialog: Locator, fieldName: string, filePaths: string[]): Promise<void>;
  /** 在 `icon-picker` 字段中选择图标（先按搜索框过滤，再点 `[role="option"][aria-label]`）。 */
  selectIcon(dialog: Locator, fieldName: string, iconName: string): Promise<void>;
  /** 在 `picker` 弹窗字段（CRUD 内嵌弹层）中点选匹配行并确认。 */
  selectPickerRow(dialog: Locator, fieldName: string, rowText: string): Promise<void>;
  submitButton(dialog: Locator): Locator;
  /** 对话框取消/关闭按钮（字典加载失败等场景需要关窗重开让字段重挂载）。 */
  cancelButton(dialog: Locator): Locator;
  selectOption(dialog: Locator, fieldLabels: string[], optionText: string[]): Promise<void>;
  dateInputByLabel(page: Page, labelText: string): Locator;
  staticFieldValue(dialog: Locator, fieldName: string): Promise<string>;

  // 确认对话框
  confirmDialogAction(page: Page): Promise<void>;

  // ── Tab 支持 ──
  /** 按标签文本切换到指定标签页，返回标签面板的 Locator（作用域上下文） */
  switchToTab(scope: Page | Locator, tabLabel: string): Promise<Locator>;
  /** 获取当前激活的标签面板 Locator */
  activeTabPanel(scope: Page | Locator): Locator;

  // ── Sub-Form 支持（内联子表单 / Combo / ObjectField） ──
  /** 获取指定字段名的子表单容器 Locator */
  subForm(scope: Page | Locator, fieldName: string): Locator;
  /** 获取数组型子表单中指定索引的条目 Locator */
  subFormItem(scope: Page | Locator, fieldName: string, index: number): Locator;

  // ── Sub-Table / 嵌套 CRUD 支持 ──
  /** 获取 scope 范围内第 index 个嵌套 CRUD 表格容器（0 起始） */
  subTable(scope: Page | Locator, index?: number): Locator;
}

export interface CrudPageConfig {
  entityRoute: string;
  entityName?: string;
  domain?: string;
  engine?: EngineAdapter;
  columnHeaders?: string[];
}

export const ENGINE_TYPES: EngineType[] = ['flux'];

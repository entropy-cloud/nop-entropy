import type { Locator, Page } from '@playwright/test';

export type EngineType = 'amis' | 'flux';

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

  // 对话框 / 表单
  dialog(page: Page): Locator;
  drawer(page: Page): Locator;
  formField(dialog: Locator, fieldName: string): Locator;
  setFieldValue(dialog: Locator, fieldName: string, value: string | boolean | number): Promise<void>;
  submitButton(dialog: Locator): Locator;
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

export const ENGINE_TYPES: EngineType[] = ['amis', 'flux'];

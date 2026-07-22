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
}

export interface CrudPageConfig {
  entityRoute: string;
  entityName?: string;
  domain?: string;
  engine?: EngineAdapter;
  columnHeaders?: string[];
}

export const ENGINE_TYPES: EngineType[] = ['amis', 'flux'];

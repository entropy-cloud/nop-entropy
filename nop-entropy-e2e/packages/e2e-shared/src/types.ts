import type { Locator, Page } from '@playwright/test';

export type EngineType = 'amis' | 'flux';

export interface EngineAdapter {
  engineName: string;

  crudContainer(page: Page): Locator;
  table(page: Page): Locator;
  rows(page: Page): Locator;
  cellValue(row: Locator, fieldName: string, columnHeaders: string[]): Promise<string>;
  addButton(page: Page): Locator;
  rowAction(row: Locator, actionNamePattern: RegExp): Promise<void>;

  dialog(page: Page): Locator;
  formField(dialog: Locator, fieldName: string): Locator;
  submitButton(dialog: Locator): Locator;
  selectOption(dialog: Locator, fieldLabels: string[], optionText: string[]): Promise<void>;
  dateInputByLabel(page: Page, labelText: string): Locator;
}

export interface CrudPageConfig {
  entityRoute: string;
  entityName?: string;
  domain?: string;
  engine?: EngineAdapter;
  columnHeaders?: string[];
}

export const ENGINE_TYPES: EngineType[] = ['amis', 'flux'];

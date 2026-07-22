import type { Page as PlaywrightPage, Locator } from '@playwright/test';
import type { EngineAdapter } from './types';

export class FormDialog {
  private _currentScope: Locator;

  constructor(
    private page: PlaywrightPage,
    private engine: EngineAdapter,
  ) {
    this._currentScope = this.engine.dialog(this.page);
  }

  get dialog() {
    return this._currentScope;
  }

  /** 重设作用域为对话框本身（清空 tab/sub-form 定位状态） */
  resetScope(): void {
    this._currentScope = this.engine.dialog(this.page);
  }

  /** 切换到对话框内的指定标签页，后续操作局限在该标签面板内 */
  async switchToTab(tabLabel: string): Promise<void> {
    this._currentScope = await this.engine.switchToTab(this.engine.dialog(this.page), tabLabel);
  }

  /** 获取对话框范围内的子表单容器 */
  subForm(fieldName: string): Locator {
    return this.engine.subForm(this._currentScope, fieldName);
  }

  /** 获取对话框范围内的数组子表单条目 */
  subFormItem(fieldName: string, index: number): Locator {
    return this.engine.subFormItem(this._currentScope, fieldName, index);
  }

  async waitForVisible(): Promise<void> {
    await this.dialog.waitFor({ state: 'visible' });
  }

  async waitForHidden(): Promise<void> {
    await this.dialog.waitFor({ state: 'hidden' });
  }

  async setField(name: string, value: string | boolean | number): Promise<void> {
    await this.engine.setFieldValue(this.dialog, name, value);
  }

  async getField(name: string): Promise<string> {
    // Try 1: native input/textarea/select with matching name
    const field = this.engine.formField(this.dialog, name);
    const count = await field.count();
    if (count > 0) {
      try {
        return (await field.first().inputValue()) ?? '';
      } catch {
        return (await field.first().textContent()) ?? '';
      }
    }
    // Try 2: engine-specific static field reader
    return this.engine.staticFieldValue(this.dialog, name);
  }

  async selectOption(fieldLabels: string[], optionTexts: string[]): Promise<void> {
    await this.engine.selectOption(this.dialog, fieldLabels, optionTexts);
  }

  async submit(): Promise<void> {
    await this.engine.submitButton(this.dialog).click();
    await this.waitForHidden();
  }
}

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

  /** 上传文件到 input-file / input-image 字段。 */
  async uploadFile(fieldName: string, filePaths: string[]): Promise<void> {
    await this.engine.setFieldFiles(this.dialog, fieldName, filePaths);
  }

  /** 在 icon-picker 字段中选择图标。 */
  async selectIcon(fieldName: string, iconName: string): Promise<void> {
    await this.engine.selectIcon(this.dialog, fieldName, iconName);
  }

  /** 在 picker 弹窗字段中点选匹配行并确认。 */
  async selectPicker(fieldName: string, rowText: string): Promise<void> {
    await this.engine.selectPickerRow(this.dialog, fieldName, rowText);
  }

  async submit(): Promise<void> {
    await this.engine.submitButton(this.dialog).click();
    await this.waitForHidden();
  }

  /**
   * 取消并关闭当前对话框。
   *
   * 用途：字典选项加载失败（errorMessage 置位、effect 不再重跑）时，
   * 只有让表单字段走完整卸载/重挂载才能触发新的 dict 拉取。
   */
  async cancel(): Promise<void> {
    // 先收掉可能残留的组合框弹层，避免 Escape 误关弹层而非对话框
    await this.page.keyboard.press('Escape').catch(() => {});
    await this.page.waitForTimeout(200);
    const btn = this.engine.cancelButton(this.dialog);
    if (await btn.isVisible().catch(() => false)) {
      await btn.click();
    } else {
      await this.page.keyboard.press('Escape');
    }
    await this.waitForHidden();
  }
}

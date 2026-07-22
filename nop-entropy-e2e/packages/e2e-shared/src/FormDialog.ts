import type { Page as PlaywrightPage, Locator } from '@playwright/test';
import type { EngineAdapter } from './types';

export class FormDialog {
  constructor(
    private page: PlaywrightPage,
    private engine: EngineAdapter,
  ) {}

  get dialog() {
    return this.engine.dialog(this.page);
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

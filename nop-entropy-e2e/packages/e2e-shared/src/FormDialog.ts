import type { Page as PlaywrightPage } from '@playwright/test';
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

  async setField(name: string, value: string): Promise<void> {
    const field = this.engine.formField(this.dialog, name);
    await field.fill(value);
  }

  async getField(name: string): Promise<string> {
    const field = this.engine.formField(this.dialog, name);
    return (await field.inputValue()) ?? '';
  }

  async selectOption(fieldLabels: string[], optionTexts: string[]): Promise<void> {
    await this.engine.selectOption(this.dialog, fieldLabels, optionTexts);
  }

  async submit(): Promise<void> {
    await this.engine.submitButton(this.dialog).click();
    await this.waitForHidden();
  }
}

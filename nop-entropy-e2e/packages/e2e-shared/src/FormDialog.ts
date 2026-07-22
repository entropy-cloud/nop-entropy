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

  async setField(name: string, value: string): Promise<void> {
    const field = this.engine.formField(this.dialog, name);
    await field.fill(value);
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
    // Try 2: AMIS static field identified by data-amis-name attribute
    const amisField = this.dialog.locator(`[data-amis-name="${name}"]`).first();
    if (await amisField.count().then((c) => c > 0)) {
      const staticEl = amisField.locator('.cxd-Form-static, .cxd-PlainField, .cxd-MappingField').first();
      if (await staticEl.count().then((c) => c > 0)) {
        return ((await staticEl.textContent()) ?? '').trim();
      }
      // Fallback: read the value column
      const valueEl = amisField.locator('.cxd-Form-value').first();
      if (await valueEl.count().then((c) => c > 0)) {
        return ((await valueEl.textContent()) ?? '').trim();
      }
    }
    return '';
  }

  async selectOption(fieldLabels: string[], optionTexts: string[]): Promise<void> {
    await this.engine.selectOption(this.dialog, fieldLabels, optionTexts);
  }

  async submit(): Promise<void> {
    await this.engine.submitButton(this.dialog).click();
    await this.waitForHidden();
  }
}

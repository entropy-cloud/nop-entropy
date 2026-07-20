import type { Locator, Page } from '@playwright/test';
import type { EngineAdapter } from './types';

export class FluxAdapter implements EngineAdapter {
  engineName = 'flux';

  crudContainer(page: Page): Locator {
    return page.locator('[data-slot="crud-table"]').first();
  }

  table(page: Page): Locator {
    return page.locator('[data-slot="crud-table"] .nop-table').first();
  }

  rows(page: Page): Locator {
    return page.locator('tbody tr[data-slot="table-row"]');
  }

  async cellValue(row: Locator, fieldName: string, _columnHeaders: string[]): Promise<string> {
    const cell = row.locator(`[data-field="${fieldName}"]`);
    return (await cell.textContent()) ?? '';
  }

  addButton(page: Page): Locator {
    return page.locator('[data-testid="btn-add"]').first();
  }

  async rowAction(row: Locator, _actionNamePattern: RegExp): Promise<void> {
    const button = row.getByRole('button').filter({ hasText: _actionNamePattern }).first();
    await button.click();
  }

  dialog(page: Page): Locator {
    return page.locator('[data-slot="dialog-surface"]').first();
  }

  formField(dialog: Locator, fieldName: string): Locator {
    return dialog.getByLabel(fieldName);
  }

  submitButton(dialog: Locator): Locator {
    return dialog.getByRole('button', {
      name: /确定|确认|保存|Confirm|Save/,
    }).first();
  }

  async selectOption(dialog: Locator, _fieldLabels: string[], _optionText: string[]): Promise<void> {
    const label = dialog.getByLabel(_fieldLabels[0]);
    await label.click();
    const option = dialog.getByRole('option').filter({ hasText: _optionText[0] }).first();
    await option.click();
  }

  dateInputByLabel(page: Page, labelText: string): Locator {
    return page.getByLabel(labelText);
  }
}

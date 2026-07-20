import type { Locator, Page } from '@playwright/test';
import type { EngineAdapter } from './types';

export class AmisAdapter implements EngineAdapter {
  engineName = 'amis';

  crudContainer(page: Page): Locator {
    return page.locator('#main-content, main, .cxd-Page').first();
  }

  table(page: Page): Locator {
    return page.locator('.cxd-Crud, .cxd-Table').first();
  }

  rows(page: Page): Locator {
    return page.locator('tr, .cxd-Table-row');
  }

  async cellValue(row: Locator, fieldName: string, _columnHeaders: string[]): Promise<string> {
    const index = _columnHeaders.indexOf(fieldName);
    if (index === -1) return '';
    const cell = row.locator(`> td:nth-child(${index + 1})`);
    return (await cell.textContent()) ?? '';
  }

  addButton(page: Page): Locator {
    return page.locator('button:has(.fa-plus)').first();
  }

  async rowAction(row: Locator, actionNamePattern: RegExp): Promise<void> {
    const button = row.locator('button').filter({ hasText: actionNamePattern }).first();
    await button.click();
  }

  dialog(page: Page): Locator {
    return page.locator('.cxd-Modal, .cxd-Dialog').first();
  }

  formField(dialog: Locator, fieldName: string): Locator {
    return dialog.locator(`input[name="${fieldName}"]`);
  }

  submitButton(dialog: Locator): Locator {
    return dialog.getByRole('button', {
      name: /确定|确认|保存|Confirm|Save/,
    }).first();
  }

  async selectOption(_dialog: Locator, _fieldLabels: string[], _optionText: string[]): Promise<void> {
    const dialog = _dialog;
    const fieldLabel = _fieldLabels[0];
    const optionText = _optionText[0];
    const input = dialog.locator(`input[name="${fieldLabel}"]`);
    await input.click();
    const option = dialog.locator('.cxd-DropDown-menuItem').filter({ hasText: optionText }).first();
    await option.click();
  }

  dateInputByLabel(page: Page, labelText: string): Locator {
    return page.locator('.cxd-Form-item').filter({ hasText: labelText }).locator('input').first();
  }
}

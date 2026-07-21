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

  drawer(page: Page): Locator {
    return page.locator('[data-slot="dialog-surface"], [data-slot="drawer-surface"]').first();
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
    const page = dialog.page();

    for (let i = 0; i < _fieldLabels.length; i++) {
      const labelText = _fieldLabels[i];
      const optionText = _optionText[i] ?? _optionText[_optionText.length - 1];

      const field = dialog.getByLabel(labelText);
      await field.scrollIntoViewIfNeeded();
      await field.click();

      // Searchable dropdown: type option text to filter options
      const tagName = await field.evaluate((el: Element) => el.tagName);
      if (tagName === 'INPUT' || tagName === 'TEXTAREA') {
        await field.fill(optionText);
        await page.waitForTimeout(400);
      }

      // Primary strategy: getByRole('option')
      try {
        const option = page.getByRole('option').filter({ hasText: optionText }).first();
        await option.waitFor({ state: 'visible', timeout: 2000 });
        await option.click();
      } catch {
        // Fallback: data-testid, li, or explicit role="option"
        const option = dialog.locator('[data-testid*="option"], li, [role="option"]').filter({ hasText: optionText }).first();
        await option.click();
      }

      // Cascading: wait for child options to populate before next field
      if (i < _fieldLabels.length - 1) {
        await page.waitForTimeout(500);
      }
    }
  }

  dateInputByLabel(page: Page, labelText: string): Locator {
    return page.getByLabel(labelText);
  }

  async datePickerSelect(page: Page, labelText: string, dateStr: string): Promise<void> {
    const field = page.getByLabel(labelText);
    await field.click();

    // Native <input type="date"> fast path
    const inputType = await field.evaluate((el: Element) => el.getAttribute('type'));
    if (inputType === 'date') {
      await field.fill(dateStr);
      return;
    }

    const calendar = page.locator('[data-slot="datepicker"], .datepicker, [role="dialog"]')
      .filter({ has: page.getByText(/选择日期|请选择/) }).first();

    try {
      await calendar.waitFor({ state: 'visible', timeout: 2000 });
    } catch {
      await field.click();
      try {
        await calendar.waitFor({ state: 'visible', timeout: 2000 });
      } catch {
        await field.fill(dateStr);
        return;
      }
    }

    const parts = dateStr.split('-');
    const targetDay = parseInt(parts[2], 10).toString();

    // Direct date cell selection
    const dayCell = calendar.locator(`[data-date="${dateStr}"], td:has-text("${targetDay}")`).first();
    if (await dayCell.isVisible().catch(() => false)) {
      await dayCell.click();
      return;
    }

    // Navigate to target month/year
    const targetYear = parseInt(parts[0], 10);
    const targetMonth = parseInt(parts[1], 10);

    const nextBtn = calendar.getByRole('button', { name: /next|下一步|›|>/ }).first();
    const prevBtn = calendar.getByRole('button', { name: /prev|上一步|‹|</ }).first();

    for (let attempt = 0; attempt < 12; attempt++) {
      const headerText = await calendar.locator('[data-month], .datepicker-header, .rdp-caption_label').first().textContent() || '';
      const yearMatch = headerText.match(/(\d{4})/);
      const monthMatch = headerText.match(/\b(\d{1,2})\b/);

      const shownYear = yearMatch ? parseInt(yearMatch[1], 10) : 0;
      const shownMonth = monthMatch ? parseInt(monthMatch[1], 10) : 0;

      if (shownYear === targetYear && shownMonth === targetMonth) break;
      if (shownYear > targetYear || (shownYear === targetYear && shownMonth > targetMonth)) {
        await prevBtn.click();
      } else {
        await nextBtn.click();
      }
      await page.waitForTimeout(300);
    }

    const finalCell = calendar.locator(`[data-date="${dateStr}"], td:has-text("${targetDay}")`).first();
    await finalCell.click();
  }

  async confirmDialog(page: Page): Promise<void> {
    const dialog = page.locator('[data-slot="dialog-surface"], [role="alertdialog"]')
      .filter({ has: page.getByText(/确认|确认操作|确定/) }).first();
    await dialog.waitFor({ state: 'visible', timeout: 3000 });
    const confirmBtn = dialog.getByRole('button', { name: /确定|确认|Confirm|Yes/ }).first();
    await confirmBtn.click();
    await dialog.waitFor({ state: 'hidden', timeout: 3000 });
  }

  async alertDialog(page: Page): Promise<void> {
    const dialog = page.locator('[data-slot="dialog-surface"], [role="alertdialog"]')
      .filter({ has: page.getByText(/提示|警告|Alert/) }).first();
    await dialog.waitFor({ state: 'visible', timeout: 3000 });
    const okBtn = dialog.getByRole('button', { name: /确定|确认|OK/ }).first();
    await okBtn.click();
    await dialog.waitFor({ state: 'hidden', timeout: 3000 });
  }
}

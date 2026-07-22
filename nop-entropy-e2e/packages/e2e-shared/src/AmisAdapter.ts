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

  queryButton(page: Page): Locator {
    return page.locator('button:has-text("查询"), button:has-text("搜索"), button:has-text("Query")').first();
  }

  async rowAction(row: Locator, actionNamePattern: RegExp): Promise<void> {
    // Try 1: direct button in the row matching the pattern (e.g. "查看")
    const directButton = row.locator('button').filter({ hasText: actionNamePattern }).first();
    if (await directButton.count().then((c) => c > 0)) {
      await directButton.click();
      return;
    }

    // Try 2: AMIS "更多" (More) dropdown — click to expand, then select menu item
    const page = row.page();
    const moreButton = row.locator('button').filter({ hasText: /更多|More/ }).first();
    if (await moreButton.count().then((c) => c > 0)) {
      await moreButton.click();
      await page.waitForTimeout(300);
      // Menu items appear in a portal overlay outside the row
      const menuItem = page
        .locator('.cxd-DropDown-menuItem, .cxd-DropDown-menu > *')
        .filter({ hasText: actionNamePattern })
        .first();
      await menuItem.click();
      return;
    }

    // Fallback: click any matching link/button
    const fallback = row.locator('a, button').filter({ hasText: actionNamePattern }).first();
    await fallback.click();
  }

  dialog(page: Page): Locator {
    return page.locator('.cxd-Modal, .cxd-Dialog').first();
  }

  drawer(page: Page): Locator {
    return page.locator('.cxd-Drawer, .cxd-Modal, .cxd-Dialog').first();
  }

  formField(dialog: Locator, fieldName: string): Locator {
    return dialog.locator(`input[name="${fieldName}"], textarea[name="${fieldName}"], select[name="${fieldName}"]`);
  }

  submitButton(dialog: Locator): Locator {
    return dialog.getByRole('button', {
      name: /确定|确认|保存|Confirm|Save/,
    }).first();
  }

  async selectOption(_dialog: Locator, _fieldLabels: string[], _optionText: string[]): Promise<void> {
    const dialog = _dialog;
    const page = dialog.page();
    const fieldKey = _fieldLabels[0];
    const optionText = _optionText[0];

    // Strategy: AMIS Select form control, located by data-amis-name attribute
    // AMIS form items have: <div data-amis-name="gender" class="cxd-Form-item">
    // The Select trigger is: .cxd-Select inside that form item
    // Options popup uses: .cxd-Select-option (NOT .cxd-DropDown-menuItem which doesn't exist)
    const formItem = dialog.locator(`[data-amis-name="${fieldKey}"]`).first();
    if (await formItem.count().then((c) => c > 0)) {
      const selectTrigger = formItem.locator('.cxd-Select').first();
      if (await selectTrigger.count().then((c) => c > 0)) {
        await selectTrigger.click();
        await page.waitForTimeout(300);
        const option = page.locator('.cxd-Select-option').filter({ hasText: optionText }).first();
        await option.click();
        return;
      }
      // Could be a native <select> inside the form item
      const nativeSelect = formItem.locator('select').first();
      if (await nativeSelect.count().then((c) => c > 0)) {
        await nativeSelect.selectOption({ label: optionText });
        return;
      }
    }

    // Fallback: native <select> by name attribute (for non-AMIS forms)
    const nativeSelect = dialog.locator(`select[name="${fieldKey}"]`).first();
    if (await nativeSelect.count().then((c) => c > 0)) {
      await nativeSelect.selectOption({ label: optionText });
    }
  }

  dateInputByLabel(page: Page, labelText: string): Locator {
    return page.locator('.cxd-Form-item').filter({ hasText: labelText }).locator('input').first();
  }
}

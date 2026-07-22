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

  async setFieldValue(dialog: Locator, fieldName: string, value: string | boolean | number): Promise<void> {
    const page = dialog.page();
    const strValue = String(value);

    // 1. Boolean → checkbox / switch
    if (typeof value === 'boolean') {
      const checkbox = dialog.locator(`[name="${fieldName}"] input[type="checkbox"]`).first();
      if (await checkbox.count().then((c) => c > 0)) {
        const isChecked = await checkbox.isChecked();
        if (isChecked !== value) await checkbox.click();
        return;
      }
      // AMIS Switch: [data-role="switch"] inside [data-amis-name]
      const switchEl = dialog.locator(`[data-amis-name="${fieldName}"] [data-role="switch"]`).first();
      if (await switchEl.count().then((c) => c > 0)) {
        const ariaChecked = await switchEl.getAttribute('aria-checked');
        const isOn = ariaChecked === 'true';
        if (isOn !== value) await switchEl.click();
        return;
      }
    }

    // 2. Native text input / textarea
    const nativeField = this.formField(dialog, fieldName);
    if (await nativeField.count().then((c) => c > 0)) {
      await nativeField.first().fill(strValue);
      return;
    }

    // 3. AMIS Select (input-text without name, but inside data-amis-name)
    const formItem = dialog.locator(`[data-amis-name="${fieldName}"]`).first();
    if (await formItem.count().then((c) => c > 0)) {
      const selectTrigger = formItem.locator('.cxd-Select').first();
      if (await selectTrigger.count().then((c) => c > 0)) {
        await selectTrigger.click();
        await page.waitForTimeout(300);
        const option = page.locator('.cxd-Select-option').filter({ hasText: strValue }).first();
        await option.click();
        return;
      }
      // Native <select> inside form item
      const nativeSelect = formItem.locator('select').first();
      if (await nativeSelect.count().then((c) => c > 0)) {
        await nativeSelect.selectOption({ label: strValue });
        return;
      }
      // Text input inside form item (data-amis-name wrapper)
      const innerInput = formItem.locator('input, textarea').first();
      if (await innerInput.count().then((c) => c > 0)) {
        await innerInput.fill(strValue);
        return;
      }
    }

    // 4. Fallback: try native <select> by name
    const nativeSelect = dialog.locator(`select[name="${fieldName}"]`).first();
    if (await nativeSelect.count().then((c) => c > 0)) {
      await nativeSelect.selectOption({ label: strValue });
    }
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

  // ── CRUD 搜索 ──

  searchField(page: Page, fieldName: string): Locator {
    return page.locator(`input[name^="filter_${fieldName}"]`).first();
  }

  searchButton(page: Page): Locator {
    return page.locator('.cxd-Table-searchableForm button[type="submit"]').first();
  }

  refreshButton(page: Page): Locator {
    return page.locator('[class*="fa-sync"]').first();
  }

  // ── 只读字段 ──

  async staticFieldValue(dialog: Locator, fieldName: string): Promise<string> {
    const amisField = dialog.locator(`[data-amis-name="${fieldName}"]`).first();
    if (await amisField.count().then((c) => c > 0)) {
      const staticEl = amisField
        .locator('.cxd-Form-static, .cxd-PlainField, .cxd-MappingField')
        .first();
      if (await staticEl.count().then((c) => c > 0)) {
        return ((await staticEl.textContent()) ?? '').trim();
      }
      const valueEl = amisField.locator('.cxd-Form-value').first();
      if (await valueEl.count().then((c) => c > 0)) {
        return ((await valueEl.textContent()) ?? '').trim();
      }
    }
    return '';
  }

  // ── 确认对话框 ──

  async confirmDialogAction(page: Page): Promise<void> {
    await page
      .locator('[role="alertdialog"]')
      .waitFor({ state: 'visible', timeout: 10_000 })
      .catch(() => {});
    await page.waitForTimeout(500);

    const clicked = await page.evaluate(() => {
      const dlg = document.querySelector('[role="alertdialog"]');
      if (!dlg) return false;
      const btns = dlg.querySelectorAll('button, [role="button"]');
      for (const btn of btns) {
        const el = btn as HTMLElement;
        const text = el.textContent?.trim() || '';
        const cs = window.getComputedStyle(el);
        if (
          /^(confirm|确定|确认|ok|删除)$/i.test(text) &&
          cs.display !== 'none' &&
          cs.visibility !== 'hidden'
        ) {
          el.click();
          return true;
        }
      }
      return false;
    });

    if (!clicked) {
      await page.evaluate(() => {
        const btn = document.querySelector(
          '[data-slot="alert-dialog-action"]',
        ) as HTMLElement | null;
        btn?.click();
      });
    }

    await page
      .locator('[role="alertdialog"]')
      .waitFor({ state: 'hidden', timeout: 10_000 })
      .catch(() => {});
    await page.waitForLoadState('networkidle').catch(() => {});
  }
}

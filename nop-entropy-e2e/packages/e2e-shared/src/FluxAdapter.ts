import type { Locator, Page } from '@playwright/test';
import type { EngineAdapter } from './types';

export class FluxAdapter implements EngineAdapter {
  engineName = 'flux';

  // ── CRUD 容器 ──

  crudContainer(page: Page): Locator {
    return page.locator('.nop-crud').first();
  }

  table(page: Page): Locator {
    return page.locator('.nop-table').first();
  }

  rows(page: Page): Locator {
    return page.locator('[data-slot="table-body"] tr[data-slot="table-row"]');
  }

  async cellValue(row: Locator, fieldName: string, columnHeaders: string[]): Promise<string> {
    const index = columnHeaders.indexOf(fieldName);
    if (index === -1) return '';
    const cell = row.locator(`td:nth-child(${index + 1})`);
    return ((await cell.textContent()) ?? '').trim();
  }

  addButton(page: Page): Locator {
    return page
      .locator('[data-slot="crud-toolbar-main"] button')
      .filter({ hasText: /新增|Add|添加/ })
      .first();
  }

  queryButton(page: Page): Locator {
    return page
      .locator('[data-slot="crud-query"] button')
      .filter({ hasText: /查询|搜索|Search/ })
      .first();
  }

  async rowAction(row: Locator, actionNamePattern: RegExp): Promise<void> {
    const actionContainer = row.locator('[data-slot="table-actions"]').first();
    const button = actionContainer.getByRole('button').filter({ hasText: actionNamePattern }).first();
    if (await button.count().then((c) => c > 0)) {
      await button.click();
      return;
    }
    const fallback = row.getByRole('button').filter({ hasText: actionNamePattern }).first();
    await fallback.click();
  }

  // ── CRUD 搜索 ──

  searchField(page: Page, fieldName: string): Locator {
    return page
      .locator('[data-slot="crud-query"]')
      .locator(`input[name="${fieldName}"], #${fieldName}-control`)
      .first();
  }

  searchButton(page: Page): Locator {
    return page
      .locator('[data-slot="crud-query"] button[type="submit"]')
      .or(page.locator('[data-slot="crud-query"] button').filter({ hasText: /搜索|查询|Search/ }))
      .first();
  }

  refreshButton(page: Page): Locator {
    return page
      .locator('[data-slot="crud-toolbar-main"] button')
      .filter({ hasText: /刷新|Refresh/ })
      .first();
  }

  // ── 对话框 ──

  dialog(page: Page): Locator {
    return page.locator('[data-slot="dialog-surface"], [data-slot="dialog-content"]').first();
  }

  drawer(page: Page): Locator {
    return page.locator('[data-slot="drawer-surface"], [data-slot="drawer-content"]').first();
  }

  formField(dialog: Locator, fieldName: string): Locator {
    return dialog.locator(
      `input[name="${fieldName}"], textarea[name="${fieldName}"], #${fieldName}-control`,
    );
  }

  async setFieldValue(
    dialog: Locator,
    fieldName: string,
    value: string | boolean | number,
  ): Promise<void> {
    const page = dialog.page();
    const strValue = String(value);

    // 1. Boolean → Checkbox / Switch
    if (typeof value === 'boolean') {
      // Flux checkbox: button[data-slot="checkbox"] with id
      const checkbox = dialog.locator(
        `button[data-slot="checkbox"][id="${fieldName}-control"]`,
      ).first();
      if (await checkbox.count().then((c) => c > 0)) {
        const ariaChecked = await checkbox.getAttribute('aria-checked');
        if ((ariaChecked === 'true') !== value) await checkbox.click();
        return;
      }
      // Flux switch: Base UI renders <span role="switch"> + hidden <input type="checkbox" id="name-control">
      // The span has aria-checked; click toggles via synthetic event on hidden input
      const switchEl = dialog
        .locator(`[data-slot="switch-wrapper"]:has(#${fieldName}-control) [role="switch"]`)
        .first();
      if (await switchEl.count().then((c) => c > 0)) {
        const ariaChecked = await switchEl.getAttribute('aria-checked');
        if ((ariaChecked === 'true') !== value) {
          await switchEl.click();
          await page.waitForTimeout(300);
        }
        return;
      }
    }

    // 2. Native input / textarea (skip buttons and checkboxes)
    const nativeField = this.formField(dialog, fieldName);
    if (await nativeField.count().then((c) => c > 0)) {
      const tagName = await nativeField.evaluate((el: Element) => el.tagName);
      const inputType = await nativeField
        .evaluate((el: Element) => (el as HTMLInputElement).type)
        .catch(() => '');
      if (tagName === 'INPUT' && inputType !== 'checkbox' && inputType !== 'radio') {
        await nativeField.first().fill(strValue);
        return;
      }
      if (tagName === 'TEXTAREA') {
        await nativeField.first().fill(strValue);
        return;
      }
      if (tagName === 'SELECT') {
        await nativeField.selectOption({ label: strValue });
        return;
      }
      // tagName === 'BUTTON' or input[type=checkbox/radio] → fall through to combobox
    }

    // 3. Combobox (Flux Select)
    const selectWrapper = dialog.locator(
      `[data-slot="select-wrapper"]`,
    ).filter({ has: page.locator(`#${fieldName}-control, [name="${fieldName}"]`) }).first();
    const comboboxTrigger = selectWrapper.locator('[data-slot="combobox-trigger"]').first();
    if (await comboboxTrigger.count().then((c) => c > 0)) {
      await comboboxTrigger.click();
      await page.waitForTimeout(300);
      const option = page.locator('[data-slot="combobox-item"]').filter({ hasText: strValue }).first();
      await option.click();
      return;
    }

    // 4. Fallback: try fill on getByLabel
    const labelField = dialog.getByLabel(fieldName);
    if (await labelField.count().then((c) => c > 0)) {
      await labelField.fill(strValue).catch(() => {});
    }
  }

  submitButton(dialog: Locator): Locator {
    return dialog
      .getByRole('button', { name: /确定|确认|保存|Submit|Save|提交/ })
      .first();
  }

  async selectOption(
    dialog: Locator,
    fieldLabels: string[],
    optionTexts: string[],
  ): Promise<void> {
    const page = dialog.page();

    for (let i = 0; i < fieldLabels.length; i++) {
      const fieldKey = fieldLabels[i];
      const optionText = optionTexts[i] ?? optionTexts[optionTexts.length - 1];

      const selectWrapper = dialog
        .locator('[data-slot="select-wrapper"]')
        .filter({ has: page.locator(`#${fieldKey}-control, [name="${fieldKey}"]`) })
        .first();
      const trigger = selectWrapper.locator('[data-slot="combobox-trigger"]').first();
      await trigger.click();
      await page.waitForTimeout(300);

      const option = page
        .locator('[data-slot="combobox-item"]')
        .filter({ hasText: optionText })
        .first();
      await option.waitFor({ state: 'visible', timeout: 3000 });
      await option.click();

      if (i < fieldLabels.length - 1) {
        await page.waitForTimeout(500);
      }
    }
  }

  dateInputByLabel(page: Page, labelText: string): Locator {
    return page.locator(`[aria-label="${labelText}"]`).first();
  }

  // ── 只读字段 ──

  async staticFieldValue(dialog: Locator, fieldName: string): Promise<string> {
    const field = dialog.locator(`#${fieldName}-control`).first();
    if (await field.count().then((c) => c > 0)) {
      return ((await field.textContent()) ?? '').trim();
    }
    return '';
  }

  // ── Tab 支持 ──

  async switchToTab(scope: Page | Locator, tabLabel: string): Promise<Locator> {
    const s = scope as Locator;
    const page = 'url' in scope ? (scope as Page) : s.page();
    const tabBtn = s.locator('[data-slot="tabs-trigger"]').filter({ hasText: tabLabel }).first();
    await tabBtn.click();
    await page.waitForTimeout(300);
    // Base UI uses `hidden` attribute (not `data-active`) on inactive panels
    return s.locator('[data-slot="tabs-content"]:not([hidden])').first();
  }

  activeTabPanel(scope: Page | Locator): Locator {
    const s = scope as Locator;
    return s.locator('[data-slot="tabs-content"]:not([hidden])').first();
  }

  // ── Sub-Form 支持 ──

  subForm(scope: Page | Locator, fieldName: string): Locator {
    const s = scope as Locator;
    // Flux object-field: find by child label text (case-insensitive)
    return s
      .locator('[data-slot="field-control"].nop-object-field')
      .filter({ has: s.locator('[data-slot="field-label"]').filter({ hasText: new RegExp(fieldName, 'i') }) })
      .first();
  }

  subFormItem(scope: Page | Locator, fieldName: string, index: number): Locator {
    const s = scope as Locator;
    // Flux array-field: find by label, then locate nth array-field-item
    const container = s
      .locator('[data-slot="field-control"].nop-array-field')
      .filter({ has: s.locator('[data-slot="field-label"]').filter({ hasText: new RegExp(fieldName, 'i') }) })
      .first();
    return container.locator('[data-slot="array-field-item"]').nth(index);
  }

  // ── Sub-Table / 嵌套 CRUD ──

  subTable(scope: Page | Locator, index = 0): Locator {
    const s = scope as Locator;
    // Flux nested CRUD: div.nop-crud inside the scope
    return s.locator('div.nop-crud').nth(index);
  }

  // ── 确认对话框 ──

  async confirmDialogAction(page: Page): Promise<void> {
    const alertDialog = page.locator('[data-slot="alert-dialog-content"]').first();
    const dialogSurface = page.locator('[data-slot="dialog-surface"]').first();

    let confirmBtn: Locator;
    if (await alertDialog.isVisible().catch(() => false)) {
      confirmBtn = alertDialog.locator('[data-slot="alert-dialog-action"]').first();
    } else if (await dialogSurface.isVisible().catch(() => false)) {
      confirmBtn = dialogSurface.locator('[data-slot="surface-confirm-submit"]').first();
    } else {
      await page
        .locator('[data-slot="alert-dialog-content"], [data-slot="dialog-surface"]')
        .first()
        .waitFor({ state: 'visible', timeout: 10_000 });
      confirmBtn = page
        .locator(
          '[data-slot="alert-dialog-action"], [data-slot="surface-confirm-submit"]',
        )
        .first();
    }

    await confirmBtn.click();

    await page
      .locator('[data-slot="alert-dialog-content"], [data-slot="dialog-surface"]')
      .first()
      .waitFor({ state: 'hidden', timeout: 10_000 })
      .catch(() => {});
    await page.waitForLoadState('networkidle').catch(() => {});
  }

  // ── Flux 特有方法（不在 EngineAdapter 接口中，供 PO 直接调用） ──

  async datePickerSelect(page: Page, labelText: string, dateStr: string): Promise<void> {
    const field = page.getByLabel(labelText);
    await field.click();

    const inputType = await field.evaluate((el: Element) => el.getAttribute('type'));
    if (inputType === 'date') {
      await field.fill(dateStr);
      return;
    }

    const calendar = page
      .locator('[data-slot="datepicker"], [role="dialog"]')
      .filter({ has: page.getByText(/选择日期|请选择/) })
      .first();

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
    const dayCell = calendar
      .locator(`[data-date="${dateStr}"], td:has-text("${targetDay}")`)
      .first();
    if (await dayCell.isVisible().catch(() => false)) {
      await dayCell.click();
      return;
    }

    const targetYear = parseInt(parts[0], 10);
    const targetMonth = parseInt(parts[1], 10);
    const nextBtn = calendar.getByRole('button', { name: /next|下一步|›|>/ }).first();
    const prevBtn = calendar.getByRole('button', { name: /prev|上一步|‹|</ }).first();

    for (let attempt = 0; attempt < 12; attempt++) {
      const headerText =
        (await calendar.locator('[data-month], .datepicker-header').first().textContent()) ?? '';
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

    const finalCell = calendar
      .locator(`[data-date="${dateStr}"], td:has-text("${targetDay}")`)
      .first();
    await finalCell.click();
  }

  async alertDialog(page: Page): Promise<void> {
    const dialog = page.locator('[data-slot="alert-dialog-content"]').first();
    await dialog.waitFor({ state: 'visible', timeout: 3000 });
    const okBtn = dialog.getByRole('button', { name: /确定|确认|OK/ }).first();
    await okBtn.click();
    await dialog.waitFor({ state: 'hidden', timeout: 3000 });
  }
}

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
    // 动态检测表格是否有 checkbox 选择列
    const page = row.page();
    const hasSelectCol = await page.evaluate(() =>
      !!document.querySelector('[data-slot="table-select-column"]'),
    );

    const rawIndex = columnHeaders.indexOf(fieldName);
    if (rawIndex === -1) return '';

    // columnHeaders 包含 checkbox 占位时（'' 开头），如果实际表格没有 checkbox 列，需要减 1
    const hasPlaceholder = columnHeaders[0] === '';
    const actualIndex = hasPlaceholder && !hasSelectCol ? rawIndex - 1 : rawIndex;
    if (actualIndex < 0) return '';

    const cell = row.locator(`td:nth-child(${actualIndex + 1})`);
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
    const page = row.page();
    const actionContainer = row.locator('[data-slot="table-actions"]').first();
    const button = actionContainer.getByRole('button').filter({ hasText: actionNamePattern }).first();
    if (await button.count().then((c) => c > 0)) {
      await button.click();
      return;
    }

    // 操作在"更多"下拉菜单中 → 展开 dropdown，点击菜单项
    const moreButton = row.getByRole('button').filter({ hasText: /更多|More/ }).first();
    if (await moreButton.count().then((c) => c > 0)) {
      await moreButton.click();
      await page.waitForTimeout(300);
      const menuItem = page
        .locator('[data-slot="dropdown-menu-item"], [role="menuitem"]')
        .filter({ hasText: actionNamePattern })
        .first();
      if (await menuItem.count().then((c) => c > 0)) {
        await menuItem.click();
        return;
      }
    }

    const fallback = row.getByRole('button').filter({ hasText: actionNamePattern }).first();
    await fallback.click();
  }

  // ── CRUD 搜索 ──

  searchField(page: Page, fieldName: string): Locator {
    return page
      .locator('[data-slot="crud-query"]')
      .locator(`input[name="filter_${fieldName}"], input[name^="filter_${fieldName}__"], #${fieldName}-control`)
      .first();
  }

  searchButton(page: Page): Locator {
    return page
      .locator('[data-slot="crud-query"] [data-slot="form-actions"] button')
      .or(page.locator('[data-slot="crud-query-controls"] button'))
      .or(page.locator('[data-slot="crud-query"] button[type="submit"]'))
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
      const checkbox = dialog.locator(
        `button[data-slot="checkbox"][id="${fieldName}-control"]`,
      ).first();
      if (await checkbox.count().then((c) => c > 0)) {
        const ariaChecked = await checkbox.getAttribute('aria-checked');
        if ((ariaChecked === 'true') !== value) await checkbox.click();
        return;
      }
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

    // 2. Native input / textarea
    const nativeField = this.formField(dialog, fieldName);
    if (await nativeField.count().then((c) => c > 0)) {
      const tagName = await nativeField.evaluate((el: Element) => el.tagName);
      const inputType = await nativeField
        .evaluate((el: Element) => (el as HTMLInputElement).type)
        .catch(() => '');
      if (tagName === 'INPUT' && inputType !== 'checkbox' && inputType !== 'radio') {
        const isInsideCombobox = await nativeField.evaluate(
          (el: Element) => !!el.closest('[role="combobox"]')
        ).catch(() => false);
        if (!isInsideCombobox) {
          const disabled = await nativeField.evaluate(
            (el: Element) => (el as HTMLInputElement).disabled || (el as HTMLInputElement).readOnly
          ).catch(() => false);
          if (!disabled) {
            const input = nativeField.first();
            await input.click();
            await page.keyboard.press('Control+a');
            await page.keyboard.press('Meta+a');
            await page.keyboard.press('Backspace');
            await page.keyboard.type(strValue, { delay: 10 });
            return;
          }
        }
      }
      if (tagName === 'TEXTAREA') {
        const disabled = await nativeField.evaluate(
          (el: Element) => (el as HTMLTextAreaElement).disabled || (el as HTMLTextAreaElement).readOnly
        ).catch(() => false);
        if (!disabled) {
          await nativeField.first().fill(strValue);
          return;
        }
      }
    }

    // 3. Combobox (Flux Select)
    const comboInput = dialog.locator(`#${fieldName}-control`);
    if (await comboInput.count().then((c) => c > 0)) {
      await comboInput.first().click();
      await page.waitForTimeout(200);
      // Try matching by text first, then fall back to first option
      const matchingOption = page.locator(`[role="option"]:has-text("${strValue}")`).first();
      if (await matchingOption.count().then((c) => c > 0)) {
        await matchingOption.click();
        return;
      }
      // Fallback: press ArrowDown and select first option
      await page.keyboard.press('ArrowDown');
      await page.waitForTimeout(500);
      const firstOption = page.getByRole('option').first();
      try {
        await firstOption.waitFor({ state: 'visible', timeout: 5000 });
        await firstOption.click();
        await page.waitForTimeout(300);
        return;
      } catch {
        // Option didn't appear
      }
    }

    // 4. Last resort: getByLabel fill
    const labelField = dialog.getByLabel(fieldName);
    if (await labelField.count().then((c) => c > 0)) {
      await labelField.fill(strValue).catch(() => {});
    }
  }

  submitButton(dialog: Locator): Locator {
    return dialog.locator('button').filter({ hasText: /确定|确认|保存|Submit|Save|提交/ }).first();
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
    // Try 1: direct id lookup
    const field = dialog.locator(`#${fieldName}-control`).first();
    if (await field.count().then((c) => c > 0)) {
      return ((await field.textContent()) ?? '').trim();
    }
    // Try 2: find by label text in view dialog (renders as nop-text spans)
    // View dialog: <div class="nop-flex"><span class="nop-text">Label</span><span class="nop-text">value</span></div>
    const viewField = dialog.locator('.nop-form .nop-flex').filter({
      has: dialog.locator('.nop-text').first(),
    });
    const viewCount = await viewField.count();
    for (let i = 0; i < viewCount; i++) {
      const pair = viewField.nth(i);
      const texts = await pair.locator('.nop-text').allTextContents();
      const label = texts[0]?.trim() ?? '';
      const value = texts[1]?.trim() ?? '';
      if (label.toLowerCase().includes(fieldName.toLowerCase())) {
        return value;
      }
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
    // Narrow to alert-dialog only — a stale hidden [data-slot="dialog-surface"]
    // can linger in the DOM after a previous dialog closed, causing .first()
    // to resolve to the wrong element and silently time out without clicking.
    const container = page.locator('[data-slot="alert-dialog-content"]').first();
    try {
      await container.waitFor({ state: 'visible', timeout: 10_000 });
    } catch {
      // No alert-dialog appeared; maybe the action was confirmed inline
      // or uses a regular dialog surface instead.
      const surface = page.locator('[data-slot="dialog-surface"]').first();
      try {
        await surface.waitFor({ state: 'visible', timeout: 2_000 });
      } catch {
        return;
      }
    }

    // Give the action button a moment to mount after the dialog becomes visible
    await page.waitForTimeout(200);

    const clicked = await page.evaluate(() => {
      const dlg = document.querySelector('[data-slot="alert-dialog-content"]');
      if (dlg) {
        const btn = dlg.querySelector<HTMLElement>('[data-slot="alert-dialog-action"]');
        if (btn) { btn.click(); return true; }
      }
      const surface = document.querySelector('[data-slot="dialog-surface"]');
      if (surface) {
        const btn = surface.querySelector<HTMLElement>('[data-slot="surface-confirm-submit"]');
        if (btn) { btn.click(); return true; }
      }
      const allBtns = document.querySelectorAll('button');
      for (const btn of allBtns) {
        const text = btn.textContent?.trim() || '';
        if (/^(confirm|确定|确认|删除|ok)$/i.test(text)) {
          btn.click();
          return true;
        }
      }
      return false;
    });

    if (!clicked) {
      const fallback = page.locator('[data-slot="alert-dialog-action"], [data-slot="surface-confirm-submit"]').first();
      await fallback.click({ force: true }).catch(() => {});
    }

    await page.locator('[data-slot="alert-dialog-content"]').first().waitFor({ state: 'hidden', timeout: 10_000 }).catch(() => {});
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

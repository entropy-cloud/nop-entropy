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

  async cellValue(row: Locator, fieldName: string, _columnHeaders: string[]): Promise<string> {
    // 契约（flux-guide 13-testing.md "字段定位契约"）：表格单元格 td[data-field="colName"]。
    // 按列名直读，替代 td:nth-child(N) + 占位空表头的列索引偏移修正。
    const cell = row.locator(`td[data-field="${fieldName}"]`).first();
    if (await cell.count().then((c) => c > 0)) {
      return ((await cell.textContent()) ?? '').trim();
    }
    return '';
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
    // 契约：查询表单字段也是 wrap 字段，带 data-field；回退旧结构
    return page
      .locator('[data-slot="crud-query"]')
      .locator(
        `[data-field="${fieldName}"] input, [data-field="${fieldName}"] textarea, input[name="${fieldName}"], #${fieldName}-control`,
      )
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
    // 契约（flux-guide 13-testing.md）：字段根 .nop-field[data-field="name"]，
    // 含 checkbox/switch（它们的 ${name}-control id 不在 interactive 元素上）。
    return dialog.locator(`[data-field="${fieldName}"]`).first();
  }

  /** 按字段根 data-renderer 读取控件类型（契约属性，确定性分派）。 */
  private async fieldRendererType(field: Locator): Promise<string | undefined> {
    if (await field.count().then((c) => c === 0)) return undefined;
    const type = await field.getAttribute('data-renderer').catch(() => undefined);
    return type ?? undefined;
  }

  async setFieldValue(
    dialog: Locator,
    fieldName: string,
    value: string | boolean | number,
  ): Promise<void> {
    const page = dialog.page();
    const strValue = String(value);

    // ── 主路径：字段根 [data-field] + data-renderer 分派（契约） ──
    const field = this.formField(dialog, fieldName);
    const rendererType = await this.fieldRendererType(field);

    if (rendererType !== undefined) {
      if (typeof value === 'boolean') {
        // checkbox / switch：点击 interactive 元素切换 aria-checked
        const toggle = field
          .locator('[data-slot="checkbox"], [data-slot="switch"]')
          .first();
        if (await toggle.count().then((c) => c > 0)) {
          const checked = await toggle.getAttribute('aria-checked');
          if ((checked === 'true') !== value) {
            await toggle.click();
            await page.waitForTimeout(300);
          }
          return;
        }
      }

      if (rendererType === 'select') {
        // select：点击 trigger 展开，按 data-value 精确选择选项
        await this.clickSelectOption(page, field, strValue);
        return;
      }

      // 其余 input 类控件（input-text / textarea / input-number / ...）：
      // fill 触发 React onChange → form store 更新
      const input = field.locator('input:not([type="checkbox"]):not([type="radio"]), textarea').first();
      if (await input.count().then((c) => c > 0)) {
        await input.fill(strValue);
        return;
      }

      // 无 input/textarea（如只读视图）→ 尝试 textContent 写入
      await field.click().catch(() => {});
      return;
    }

    // ── 回退路径：无 data-field（旧 bundle / 非 wrap 字段） ──
    await this.setFieldValueLegacy(dialog, fieldName, value);
  }

  /** 旧 DOM 结构回退（data-slot / ${name}-control / combobox label 匹配）。 */
  private async setFieldValueLegacy(
    dialog: Locator,
    fieldName: string,
    value: string | boolean | number,
  ): Promise<void> {
    const page = dialog.page();
    const strValue = String(value);

    if (typeof value === 'boolean') {
      const checkbox = dialog
        .locator(`#${fieldName}-control-label [data-slot="checkbox"][role="checkbox"]`)
        .first();
      if (await checkbox.count().then((c) => c > 0)) {
        const ariaChecked = await checkbox.getAttribute('aria-checked');
        if ((ariaChecked === 'true') !== value) await checkbox.click();
        return;
      }
      const switchEl = dialog
        .locator(`#${fieldName}-control-label [data-slot="switch"][role="switch"]`)
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

    const nativeField = dialog
      .locator(`input[name="${fieldName}"], textarea[name="${fieldName}"], #${fieldName}-control`)
      .first();
    if (await nativeField.count().then((c) => c > 0)) {
      const tagName = await nativeField.evaluate((el: Element) => el.tagName);
      const inputType = await nativeField
        .evaluate((el: Element) => (el as HTMLInputElement).type)
        .catch(() => '');
      const isInsideCombobox = await nativeField
        .evaluate((el: Element) => !!el.closest('[role="combobox"]'))
        .catch(() => false);
      if (tagName === 'INPUT' && inputType !== 'checkbox' && inputType !== 'radio' && !isInsideCombobox) {
        await nativeField.fill(strValue);
        return;
      }
      if (tagName === 'TEXTAREA') {
        await nativeField.fill(strValue);
        return;
      }
      if (tagName === 'SELECT') {
        await nativeField.selectOption({ label: strValue });
        return;
      }
    }

    const comboTrigger = dialog.locator(`#${fieldName}-control`).first();
    if (await comboTrigger.count().then((c) => c > 0)) {
      await comboTrigger.click();
      await page.waitForTimeout(400);
      if (await this.clickVisibleComboboxItem(page, strValue)) {
        await page.waitForTimeout(300);
        return;
      }
      await comboTrigger.click();
      await page.waitForTimeout(400);
      if (await this.clickVisibleComboboxItem(page, strValue)) {
        await page.waitForTimeout(300);
        return;
      }
    }

    const labelField = dialog.getByLabel(fieldName);
    if (await labelField.count().then((c) => c > 0)) {
      await labelField.fill(strValue).catch(() => {});
    }
  }

  /**
   * 契约：select 选项 [data-slot="combobox-item"][data-value="..."]] 按 value 精确选择。
   * 用原生 DOM click 绕过 Playwright 在 Base UI combobox-item 上的 actionability 超时。
   */
  private async clickSelectOption(page: Page, field: Locator, value: string): Promise<void> {
    const trigger = field
      .locator('[data-slot="combobox-trigger"], [role="combobox"]')
      .first();
    if (await trigger.count().then((c) => c === 0)) return;
    await trigger.click();
    await page.waitForTimeout(400);

    const clicked = await page.evaluate((val: string) => {
      const items = Array.from(
        document.querySelectorAll('[data-slot="combobox-item"][data-value]'),
      );
      for (const it of items) {
        const r = it.getBoundingClientRect();
        if (r.width <= 0 || r.height <= 0) continue;
        if (it.getAttribute('data-value') === val) {
          (it as HTMLElement).click();
          return true;
        }
      }
      return false;
    }, value);

    if (!clicked) {
      // 回退：按文本匹配（value-label 字典格式）
      await this.clickVisibleComboboxItem(page, value);
      await page.waitForTimeout(300);
    }
  }

  /**
   * 在已展开的 combobox 弹出层中，按 value 匹配**可见**的选项并原生 click。
   *
   * 真相（契约）：combobox-item 文本 = option.label（非 value），无 data-value。
   * dict 选项文本常为 `value-label` 格式（如 "1-男"）。必须只点击真正可见（有非零尺寸）
   * 的选项，跳过隐藏的 selected-value tracker。
   *
   * 匹配优先级：文本完全等于 value > 以 `${value}-` 开头（value-label 字典格式）> 包含 value。
   * 用原生 DOM click 绕过 Playwright 在 Base UI combobox-item 上的 actionability 超时。
   */
  async clickVisibleComboboxItem(page: Page, value: string): Promise<boolean> {
    return page.evaluate((val: string) => {
      const items = Array.from(document.querySelectorAll('[data-slot="combobox-item"]'));
      for (const it of items) {
        const r = it.getBoundingClientRect();
        if (r.width <= 0 || r.height <= 0) continue;
        const text = (it.textContent || '').trim();
        if (text === val || text.startsWith(`${val}-`) || text.includes(val)) {
          (it as HTMLElement).click();
          return true;
        }
      }
      return false;
    }, value);
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

      // 契约：按字段根 [data-field] + combobox-item 文本/值选择
      const field = this.formField(dialog, fieldKey);
      if (await field.count().then((c) => c > 0)) {
        const trigger = field
          .locator('[data-slot="combobox-trigger"], [role="combobox"]')
          .first();
        await trigger.click();
        await page.waitForTimeout(300);

        // 先按 data-value 精确匹配（契约），失败回退文本匹配
        const clicked = await page.evaluate((val: string) => {
          const items = Array.from(
            document.querySelectorAll('[data-slot="combobox-item"][data-value]'),
          );
          for (const it of items) {
            const r = it.getBoundingClientRect();
            if (r.width <= 0 || r.height <= 0) continue;
            if (it.getAttribute('data-value') === val) {
              (it as HTMLElement).click();
              return true;
            }
          }
          return false;
        }, optionText);

        if (!clicked) {
          const option = page
            .locator('[data-slot="combobox-item"]')
            .filter({ hasText: optionText })
            .first();
          await option.waitFor({ state: 'visible', timeout: 3000 });
          await option.click();
        }

        if (i < fieldLabels.length - 1) {
          await page.waitForTimeout(500);
        }
        continue;
      }

      // 回退：select-wrapper + #key-control
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
    // 契约：字段根 [data-field]，只读视图字段渲染为静态文本
    const field = dialog.locator(`[data-field="${fieldName}"]`).first();
    if (await field.count().then((c) => c > 0)) {
      return ((await field.textContent()) ?? '').trim();
    }
    const legacy = dialog.locator(`#${fieldName}-control`).first();
    if (await legacy.count().then((c) => c > 0)) {
      return ((await legacy.textContent()) ?? '').trim();
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

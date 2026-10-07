import type { Locator, Page } from '@playwright/test';
import { AmisAdapter } from './AmisAdapter';

/**
 * flux 引擎适配器（双模）：flux 渲染管线与 amis 渲染管线可能在同一个被测应用里
 * 按页面粒度混用（render-mode=flux 下，meta 驱动页面与 GenPage 页面的输出不同），
 * 因此每个定位器都是 "flux data-slot 契约优先、amis cxd 结构兜底" 的联合选择器，
 * 与具体页面用哪种渲染器无关。交互动作（确认框、排序、分页）优先走 flux 契约，
 * flux 结构不存在时回落 super 的 amis 实现。
 */
export class FluxAdapter extends AmisAdapter {
  engineName = 'flux';

  crudContainer(page: Page): Locator {
    return page.locator('.nop-crud, .cxd-Crud').first();
  }

  table(page: Page): Locator {
    return page.locator('.nop-table, .cxd-Crud, .cxd-Table').first();
  }

  rows(page: Page): Locator {
    return page.locator(
      '[data-slot="table-body"] tr[data-slot="table-row"], .cxd-Table tbody tr',
    );
  }

  addButton(page: Page): Locator {
    return page
      .locator(
        '[data-slot="crud-toolbar-main"] button:has-text("新增"), button:has-text("新增"), button:has(.fa-plus)',
      )
      .first();
  }

  refreshButton(page: Page): Locator {
    return page
      .locator('[data-slot="crud-toolbar-main"] button:has-text("刷新"), [class*="fa-sync"]')
      .first();
  }

  dialog(page: Page): Locator {
    return page
      .locator('[data-slot="dialog-surface"], [data-slot="dialog-content"], .cxd-Modal, .cxd-Dialog')
      .first();
  }

  drawer(page: Page): Locator {
    return page
      .locator('[data-slot="drawer-surface"], [data-slot="drawer-content"], .cxd-Drawer')
      .first();
  }

  formField(dialog: Locator, fieldName: string): Locator {
    return dialog
      .locator(`[data-field="${fieldName}"], input[name="${fieldName}"], textarea[name="${fieldName}"]`)
      .first();
  }

  // ── flux 组合框契约（探针实证）：字段根 label.nop-field[data-field][data-renderer]，
  // 触发器 [data-slot="input-group"]，选项 [data-slot="combobox-item"] ──

  private fluxFieldRoot(dialog: Locator, fieldName: string): Locator {
    return dialog.locator(`[data-field="${fieldName}"]`).first();
  }

  /** 在字段根上执行 flux 组合框选择；字段不存在或非组合框结构时返回 false */
  private async fluxComboboxSelect(root: Locator, optionText: string): Promise<boolean> {
    if (!(await root.count().then((c) => c > 0))) return false;
    const trigger = root.locator('[data-slot="input-group"]').first();
    if (!(await trigger.count().then((c) => c > 0))) return false;

    const page = root.page();
    await trigger.click();
    const item = page.locator('[data-slot="combobox-item"]')
      .filter({ hasText: optionText })
      .first();
    try {
      await item.waitFor({ state: 'visible', timeout: 5_000 });
      await item.click();
      return true;
    } catch {
      await page.keyboard.press('Escape').catch(() => {});
      return false;
    }
  }

  override async selectOption(dialog: Locator, fieldLabels: string[], optionTexts: string[]): Promise<void> {
    for (let i = 0; i < fieldLabels.length; i++) {
      const key = fieldLabels[i];
      const optionText = optionTexts[i] ?? optionTexts[optionTexts.length - 1];
      if (await this.fluxComboboxSelect(this.fluxFieldRoot(dialog, key), optionText)) continue;
      await super.selectOption(dialog, [key], [optionText]);
    }
  }

  override async setFieldValue(dialog: Locator, fieldName: string, value: string | boolean | number): Promise<void> {
    const root = this.fluxFieldRoot(dialog, fieldName);
    const renderer = await root.getAttribute('data-renderer').catch(() => null);
    if (renderer === 'select' || renderer === 'combobox') {
      await this.fluxComboboxSelect(root, String(value));
      return;
    }
    await super.setFieldValue(dialog, fieldName, value);
  }

  async setPageSize(page: Page, size: number): Promise<void> {
    // flux：原生 select；amis：下拉自绘。联合探测，哪种存在用哪种
    const native = page.locator('[data-slot="table-pagination"] select').first();
    if (await native.isVisible().catch(() => false)) {
      await native.selectOption(String(size));
      await page.waitForLoadState('networkidle').catch(() => {});
      return;
    }
    await super.setPageSize(page, size);
  }

  async sortByColumn(page: Page, headerText: string): Promise<void> {
    const fluxTh = page.locator('th[data-slot="table-head"]').filter({ hasText: headerText }).first();
    if (await fluxTh.isVisible().catch(() => false)) {
      const trigger = fluxTh.locator('[data-slot="table-sort"], button, [class*="sort"]').first();
      if (await trigger.count().then((c) => c > 0)) {
        await trigger.click();
      } else {
        await fluxTh.click();
      }
      await page.waitForLoadState('networkidle').catch(() => {});
      await page.waitForTimeout(600);
      return;
    }
    await super.sortByColumn(page, headerText);
  }

  async goToPage(page: Page, n: number): Promise<boolean> {
    const fluxLink = page.locator('[data-slot="pagination"] [data-slot="pagination-link"]')
      .filter({ hasText: new RegExp(`^${n}$`) }).first();
    if (await fluxLink.isVisible().catch(() => false)) {
      const cls = (await fluxLink.getAttribute('class')) ?? '';
      if (/active|disabled|selected/.test(cls)) return false;
      await fluxLink.click();
      await page.waitForLoadState('networkidle').catch(() => {});
      await page.waitForTimeout(500);
      return true;
    }
    return super.goToPage(page, n);
  }

  async rowAction(row: Locator, actionNamePattern: RegExp): Promise<void> {
    // flux：行内操作区 / dropdown-menu-item；amis：更多下拉 + cxd 菜单项——双模
    const fluxItem = row.page().locator('[data-slot="dropdown-menu-item"], [role="menuitem"]')
      .filter({ hasText: actionNamePattern }).first();
    const more = row.getByRole('button').filter({ hasText: /更多|More/ }).first();
    if (await more.count().then((c) => c > 0)) {
      await more.click();
      try {
        await fluxItem.waitFor({ state: 'visible', timeout: 4_000 });
        await fluxItem.click();
        return;
      } catch {
        await row.page().keyboard.press('Escape').catch(() => {});
      }
    }
    await super.rowAction(row, actionNamePattern);
  }

  /**
   * 确认框点击：flux 壳层 alert-dialog 用 data-slot 结构渲染，优先按契约定位；
   * 壳层未命中（amis 自绘确认框）时回退 super 的 role=alertdialog 文本匹配。
   */
  override async confirmDialogAction(page: Page): Promise<void> {
    const slotBtn = page
      .locator('[data-slot="alert-dialog-action"], [data-slot="surface-confirm-submit"], [data-slot="alert-dialog-portal"] button:visible')
      .filter({ hasText: /^(确认|确定|OK|删除)$/ })
      .last();
    if (await slotBtn.isVisible().catch(() => false)) {
      await slotBtn.click();
      return;
    }
    await super.confirmDialogAction(page);
  }

  /** 日期选择 helper（Flux 特有，不在 EngineAdapter 接口中，供 PO 直接调用）。 */
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

  /** shell alert-dialog 的确认点击 helper（Flux 特有）。 */
  async alertDialog(page: Page): Promise<void> {
    const dialog = page.locator('[data-slot="alert-dialog-content"]').first();
    await dialog.waitFor({ state: 'visible', timeout: 3000 });
    const okBtn = dialog.getByRole('button', { name: /确定|确认|OK/ }).first();
    await okBtn.click();
    await dialog.waitFor({ state: 'hidden', timeout: 3000 });
  }
}

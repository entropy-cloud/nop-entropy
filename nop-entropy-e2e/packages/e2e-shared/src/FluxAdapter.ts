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
    // 必须落到真实输入控件：字段根本身是 label[data-field]，fill/inputValue 对 label
    // 无效；先找字段根内的 input/textarea，再按 name 兜底
    return dialog
      .locator(
        `[data-field="${fieldName}"] input, [data-field="${fieldName}"] textarea, input[name="${fieldName}"], textarea[name="${fieldName}"]`,
      )
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
    // 已关闭字段的弹层 DOM 会残留（不可见但仍在文档中）——必须限定可见弹层内的选项，
    // 否则 first() 命中前一个字段的隐藏选项，点击静默失败（字段留空、提交时必填校验报错）
    const visibleItems = page.locator('[data-slot="combobox-item"]:visible');
    const esc = optionText.replace(/"/g, '\\"');
    const displayInput = root.locator('input').first();

    for (let attempt = 0; attempt < 2; attempt++) {
      await trigger.click();
      // 等待弹层真正打开：连续填多个组合框时，前一弹层的关闭清理可能吞掉首次 toggle
      const opened = await visibleItems
        .first()
        .waitFor({ state: 'visible', timeout: 3_000 })
        .then(() => true)
        .catch(() => false);
      if (!opened) continue;

      const byValue = page
        .locator(`[data-slot="combobox-item"][data-value="${esc}"]:visible`)
        .first();
      const item = (await byValue.isVisible().catch(() => false))
        ? byValue
        : visibleItems.filter({ hasText: optionText }).first();
      try {
        await item.click({ timeout: 3_000 });
      } catch {
        await page.keyboard.press('Escape').catch(() => {});
        continue;
      }
      // 校验选中已登记（显示输入框出现值），未登记视为本次点击被吞，重试
      const registered = await displayInput
        .evaluate((el) => !!(el as HTMLInputElement).value)
        .catch(() => false);
      if (registered) return true;
    }
    await page.keyboard.press('Escape').catch(() => {});
    return false;
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
      // 带 data-renderer 根的字段是 flux 渲染——组合框选择失败时不能回落 amis 的
      // input fill（会把裸值写进显示框产生脏表单状态），显式失败
      const ok = await this.fluxComboboxSelect(root, String(value));
      if (!ok) {
        throw new Error(`flux 组合框选择失败: ${fieldName}="${value}"（弹层未打开或选项未命中）`);
      }
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
    const page = row.page();
    // 上一行操作遗留的 amis PopOver 遮罩会拦截后续所有行点击——点击遮罩本身
    // 即 amis 的 outside-click 关闭机制，先清理再继续
    const popoverOverlay = page.locator('.cxd-PopOver-overlay').first();
    const dismissPopovers = async () => {
      if (await popoverOverlay.isVisible().catch(() => false)) {
        await popoverOverlay.click({ force: true }).catch(() => {});
        await popoverOverlay.waitFor({ state: 'hidden', timeout: 2_000 }).catch(() => {});
      }
    };
    await dismissPopovers();

    // 行内直接按钮（无需下拉）
    const directButton = row.locator('button').filter({ hasText: actionNamePattern }).first();
    if ((await directButton.count().then((c) => c > 0)) && (await directButton.isVisible().catch(() => false))) {
      await directButton.click();
      return;
    }

    // 更多下拉：只点一次「更多」，菜单项用 flux + amis 联合选择器匹配——
    // 先点开后回落 super 再点一次「更多」会把下拉 toggle 关闭并遗留遮罩
    const more = row.getByRole('button').filter({ hasText: /更多|More/ }).first();
    if (await more.count().then((c) => c > 0)) {
      await more.click();
      const menuItem = page
        .locator(
          '[data-slot="dropdown-menu-item"], [role="menuitem"], .cxd-DropDown-menuItem, .cxd-DropDown-menu > li',
        )
        .filter({ hasText: actionNamePattern })
        .first();
      try {
        await menuItem.waitFor({ state: 'visible', timeout: 4_000 });
        await menuItem.click();
        return;
      } catch {
        // amis 弹层不响应 Escape——用遮罩 outside-click 关闭，避免拦截后续操作
        await page.keyboard.press('Escape').catch(() => {});
        await dismissPopovers();
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

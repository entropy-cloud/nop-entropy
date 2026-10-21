import type { Locator, Page } from '@playwright/test';
import type { EngineAdapter } from './types';

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

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

  fieldInput(scope: Page | Locator, fieldName: string): Locator {
    const s = scope as Locator;
    // 契约：字段根 [data-field] 内的交互控件；非 wrap 字段回退 name / id
    return s
      .locator(
        `[data-field="${fieldName}"] input, [data-field="${fieldName}"] textarea, input[name="${fieldName}"], textarea[name="${fieldName}"], #${fieldName}-control`,
      )
      .first();
  }

  // ── 分页 / 排序 ──

  async setPageSize(page: Page, size: number): Promise<void> {
    const native = page.locator('[data-slot="table-pagination"] select').first();
    if (await native.isVisible().catch(() => false)) {
      await native.selectOption(String(size));
      await page.waitForLoadState('networkidle').catch(() => {});
      return;
    }
    // 分页控件未渲染（无分页或单页放得下）——按调用方语义视为已生效
  }

  async sortByColumn(page: Page, headerText: string): Promise<void> {
    const th = page.locator('th[data-slot="table-head"]').filter({ hasText: headerText }).first();
    if (!(await th.isVisible().catch(() => false))) return;
    const trigger = th.locator('[data-slot="table-sort"], button, [class*="sort"]').first();
    if (await trigger.count().then((c) => c > 0)) {
      await trigger.click();
    } else {
      await th.click();
    }
    await page.waitForLoadState('networkidle').catch(() => {});
    await page.waitForTimeout(600);
  }

  async goToPage(page: Page, n: number): Promise<boolean> {
    const link = page
      .locator('[data-slot="pagination"] [data-slot="pagination-link"]')
      .filter({ hasText: new RegExp(`^${n}$`) })
      .first();
    if (!(await link.isVisible().catch(() => false))) return false;
    const cls = (await link.getAttribute('class')) ?? '';
    if (/active|disabled|selected/.test(cls)) return false;
    await link.click();
    await page.waitForLoadState('networkidle').catch(() => {});
    await page.waitForTimeout(500);
    return true;
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

      if (rendererType === 'select' || rendererType === 'tree-select') {
        // select / tree-select：点击 trigger 展开，按 data-value 精确选择选项
        await (rendererType === 'tree-select'
          ? this.selectTreeOption(page, field, strValue)
          : this.clickSelectOption(page, field, strValue));
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
   * 契约：select 的选项是 `[data-slot="combobox-item"][data-value="<value>"]`。
   *
   * 三个坑（都实测踩过，改动前请先读）：
   *
   * 1. **原生 `el.click()` 是有效的**——Base UI `ComboboxItem` 的 `onClick` 直接接到
   *    `commitSelection`，不需要 pointerdown/mousedown 事件链。用原生 click 还能绕开
   *    Playwright 在 Base UI 上的 actionability 超时（弹层动画期间尤其明显）。
   * 2. **"已打开"的唯一正确判据是 `[data-slot="combobox-content"][data-open]`**——
   *    关闭的弹层 DOM 不卸载（退出动画需要）。三种错法都实测踩过：
   *    `.first()` 会取到别的字段已关闭弹层的隐藏项，把"已开"误判成"未开"；
   *    `:not([data-closed])` 更糟——**从未打开过**的弹层既无 `data-closed` 也无
   *    `data-open`，会被误判成"已打开"；
   *    `:visible` 结果正确但依赖几何与动画时序，`data-open` 是语义判据，更稳。
   * 3. **触发器是 `[data-slot="input-group"]`**，本渲染路径没有 `combobox-trigger`
   *    （下拉按钮是 `input-group-button`，输入框带 `role="combobox"`）。候选按序探测。
   */
  private async clickSelectOption(page: Page, field: Locator, value: string): Promise<void> {
    for (let attempt = 0; attempt < 5; attempt++) {
      if (!(await this.openCombobox(field))) {
        await page.waitForTimeout(400);
        continue;
      }
      // 弹层可先于字典异步到达而打开：先等本弹层出现可见选项再点。
      // 在途加载未完成时轮询等待；字典请求失败则关层重开（新一轮 effect 重拉）。
      if (!(await this.waitForOpenComboboxItems(page, 4_000))) {
        await page.keyboard.press('Escape').catch(() => {});
        await page.waitForTimeout(400);
        continue;
      }
      if (!(await this.clickVisibleComboboxItem(page, value))) {
        await page.waitForTimeout(300);
        continue;
      }

      // 校验选中已登记（显示框出现值），未登记视为本次点击被吞掉，重开重试
      const registered = await field
        .locator('input')
        .first()
        .evaluate((el) => !!(el as HTMLInputElement).value)
        .catch(() => true);
      if (registered) {
        await page.waitForTimeout(300);
        return;
      }
    }
    throw new Error(
      `flux 组合框选择失败: "${value}"（弹层未打开或选项未命中）\n${await this.describeCombobox(field)}`,
    );
  }

  /**
   * 轮询等待**当前打开**的弹层内出现几何可见的选项。
   *
   * 字典 hook（Qv）在 effect 内异步拉取 options，弹层的 data-open 与选项挂载之间存在窗口；
   * 拉取失败时 errorMessage 置位、选项恒空——由调用方关层重开恢复（重开不重跑 dict effect，
   * 仅当 effect 仍在途时有效；彻底失败需上层关闭重开对话框让字段重挂载）。
   */
  private async waitForOpenComboboxItems(page: Page, timeout: number): Promise<boolean> {
    const deadline = Date.now() + timeout;
    while (Date.now() < deadline) {
      const count = await page.evaluate(() => {
        const open = document.querySelector('[data-slot="combobox-content"][data-open]');
        if (!open) return 0;
        return Array.from(open.querySelectorAll('[data-slot="combobox-item"]')).filter((it) => {
          const r = it.getBoundingClientRect();
          return r.width > 0 && r.height > 0;
        }).length;
      });
      if (count > 0) return true;
      await page.waitForTimeout(150);
    }
    return false;
  }

  /** 失败诊断：字段根 DOM + 全部选项的可见性/取值快照，快速定位契约漂移。 */
  private async describeCombobox(field: Locator): Promise<string> {
    const page = field.page();
    const html = await field
      .first()
      .evaluate((el) => el.outerHTML.slice(0, 600))
      .catch(() => '(field html unavailable)');
    const items = await page
      .evaluate(() =>
        Array.from(document.querySelectorAll('[data-slot="combobox-item"]')).map((it) => {
          const r = it.getBoundingClientRect();
          return {
            visible: r.width > 0 && r.height > 0,
            value: it.getAttribute('data-value'),
            text: (it.textContent || '').trim().slice(0, 24),
          };
        }),
      )
      .catch(() => []);
    return `field=${html}\nitems=${JSON.stringify(items).slice(0, 800)}`;
  }

  /**
   * 确保字段内的组合框弹层处于展开态。
   *
   * "已展开"判据 = 存在带 `data-open` 的弹层（见 `clickSelectOption` 坑 2）。已展开时直接
   * 返回，避免再点一次触发器把刚打开的弹层 toggle 关掉。
   *
   * 但该判据是**页面级**的：连续填多个组合框时，上一字段的弹层可能仍处于关闭动画
   * （`data-open` 未摘除），此时会把别人的弹层误判成本字段已打开，随后点选落到隐藏的
   * 旧弹层里——实测同一表单连选 4 个下拉时第 4 个必挂（选项全部 visible:false）。
   * 因此先按 Escape 关掉任何残留弹层，确认全局无弹层打开后再点本字段触发器。
   */
  private async openCombobox(field: Locator): Promise<boolean> {
    const page = field.page();
    const openPopup = page.locator('[data-slot="combobox-content"][data-open]');

    if (await openPopup.count().then((c) => c > 0)) {
      await page.keyboard.press('Escape').catch(() => {});
      await openPopup
        .first()
        .waitFor({ state: 'hidden', timeout: 1_500 })
        .catch(() => {});
      if (await openPopup.count().then((c) => c > 0)) return false;
    }

    for (const sel of [
      '[data-slot="input-group"]',
      '[data-slot="combobox-trigger"]',
      '[role="combobox"]',
    ]) {
      const trigger = field.locator(sel).first();
      if (!(await trigger.count().then((c) => c > 0))) continue;
      await trigger.click().catch(() => {});
      // 连续填多个组合框时，前一弹层的关闭清理可能吞掉首次 toggle
      const opened = await openPopup
        .first()
        .waitFor({ state: 'attached', timeout: 2_000 })
        .then(() => true)
        .catch(() => false);
      if (opened) return true;
    }
    return false;
  }

  /**
   * 在当前打开的弹层内点选一个选项（原生 click）。
   *
   * 先按 `data-open` 把作用域收敛到当前弹层（关闭弹层的残留选项不参与），再按几何尺寸
   * 过滤，最后先按 `data-value` 精确命中、未命中回退按选项文案匹配——字典选项文本常为
   * `value-label` 格式（如 "1-男"），前缀锚定避免 "1" 误命中 "10-..."。
   */
  private async clickVisibleComboboxItem(page: Page, value: string): Promise<boolean> {
    return page.evaluate((val: string) => {
      const openPopup = document.querySelector('[data-slot="combobox-content"][data-open]');
      const scope: ParentNode = openPopup ?? document;
      const live = Array.from(scope.querySelectorAll('[data-slot="combobox-item"]')).filter((it) => {
        const r = it.getBoundingClientRect();
        return r.width > 0 && r.height > 0;
      });

      const byValue = live.find((it) => it.getAttribute('data-value') === val);
      if (byValue) {
        (byValue as HTMLElement).click();
        return true;
      }
      const byText = live.find((it) => {
        const text = (it.textContent || '').trim();
        return text === val || text.startsWith(`${val}-`);
      });
      if (byText) {
        (byText as HTMLElement).click();
        return true;
      }
      return false;
    }, value);
  }

  submitButton(dialog: Locator): Locator {
    return dialog.locator('button').filter({ hasText: /确定|确认|保存|Submit|Save|提交/ }).first();
  }

  cancelButton(dialog: Locator): Locator {
    return dialog.locator('button').filter({ hasText: /取消|关闭|Cancel|Close/ }).first();
  }

  async selectOption(
    dialog: Locator,
    fieldLabels: string[],
    optionTexts: string[],
  ): Promise<void> {
    const page = dialog.page();

    for (let i = 0; i < fieldLabels.length; i++) {
      const field = this.formField(dialog, fieldLabels[i]);
      const optionText = optionTexts[i] ?? optionTexts[optionTexts.length - 1];

      const rendererType = await this.fieldRendererType(field);
      if (rendererType === 'tree-select') {
        await this.selectTreeOption(page, field, optionText);
      } else {
        await this.clickSelectOption(page, field, optionText);
      }

      if (i < fieldLabels.length - 1) {
        await page.waitForTimeout(500);
      }
    }
  }

  /**
   * 契约（flux-renderers-form-advanced/tree-controls.tsx + tree-option-list.tsx）：
   * `tree-select` 字段根内 trigger 为 `[data-slot="tree-select-control"]`/`tree-select-trigger-row` 的
   * button；弹层为 `[data-slot="tree-select-popover-options"]`，选项为 `[role="treeitem"]`（按文本命中）。
   */
  async selectTreeOption(page: Page, field: Locator, value: string): Promise<void> {
    const trigger = field
      .locator(
        '[data-slot="tree-select-control"] button, [data-slot="tree-select-trigger-row"] button, button',
      )
      .first();
    await trigger.click();

    const popup = page.locator('[data-slot="tree-select-popover-options"]').first();
    await popup.waitFor({ state: 'visible', timeout: 8_000 });

    const items = popup.locator('[role="treeitem"]');
    await items.first().waitFor({ state: 'visible', timeout: 8_000 });
    // 精确文本优先，未命中回退包含匹配
    const exact = items.filter({ hasText: new RegExp(`^\\s*${escapeRegExp(value)}\\s*$`) }).first();
    const item = (await exact.count().then((c) => c > 0))
      ? exact
      : items.filter({ hasText: value }).first();
    if (item) {
      await item.click();
    }
    await page.waitForTimeout(300);
  }

  dateInputByLabel(page: Page, labelText: string): Locator {
    return page.locator(`[aria-label="${labelText}"]`).first();
  }

  // ── 文件上传 / 图标选择 / 弹窗选择 ──

  /**
   * 契约（nop-chaos-flux flux-renderers-form-advanced/upload-field.tsx）：
   * `input-file`/`input-image` 字段内有一个视觉隐藏的 `input[type="file"]`
   * （sr-only，Playwright setInputFiles 允许隐藏 input）。用 setInputFiles 注入，
   * 不触发原生文件对话框。
   */
  async setFieldFiles(dialog: Locator, fieldName: string, filePaths: string[]): Promise<void> {
    const page = dialog.page();
    const field = this.formField(dialog, fieldName);
    const fileInput = field.locator('input[type="file"]').first();
    if (await fileInput.count().then((c) => c > 0)) {
      await fileInput.setInputFiles(filePaths);
    } else {
      // 无 input[type=file]（旧结构）→ 走原生 filechooser 拦截
      const [chooser] = await Promise.all([
        page.waitForEvent('filechooser'),
        field.getByRole('button').first().click(),
      ]);
      await chooser.setFiles(filePaths);
    }
    // 等 pending 上传结束并落库（upload action 可能异步）
    await page
      .locator('[data-slot="upload-field-control"][data-pending]')
      .first()
      .waitFor({ state: 'detached', timeout: 15_000 })
      .catch(() => {});
    await page.waitForLoadState('networkidle').catch(() => {});
    await page.waitForTimeout(300);
  }

  /**
   * 契约（flux-renderers-form-advanced/icon-picker.tsx）：字段内有
   * `[data-slot="icon-picker-trigger"]`，点击弹出 `[role="listbox"]`，
   * 选项为 `[role="option"][aria-label="<lucide图标名>"]`；可先用搜索框过滤。
   */
  async selectIcon(dialog: Locator, fieldName: string, iconName: string): Promise<void> {
    const page = dialog.page();
    const field = this.formField(dialog, fieldName);
    const trigger = field.locator('[data-slot="icon-picker-trigger"]').first();
    await trigger.click();

    // 可选搜索框过滤（searchable 时 PopoverContent 顶部有 input[type=search]）
    const searchInput = page.locator('input[type="search"]').last();
    if (await searchInput.isVisible().catch(() => false)) {
      await searchInput.fill(iconName).catch(() => {});
      await page.waitForTimeout(300);
    }

    const option = page
      .locator(`[role="listbox"] [role="option"][aria-label="${iconName}"]`)
      .first();
    await option.waitFor({ state: 'visible', timeout: 5_000 });
    await option.click();
    await page.waitForTimeout(300);
  }

  /**
   * 契约（flux-renderers-form-advanced/picker-renderer.tsx + picker-dropdown.tsx）：
   * 字段内有 `[data-slot="picker-trigger"]`，点击弹出 `[data-slot="picker-dialog-content"]`
   * （或 drawer/popover），内容为内嵌 CRUD 表格；选中行后点 `[data-slot="picker-confirm"]`。
   */
  async selectPickerRow(dialog: Locator, fieldName: string, rowText: string): Promise<void> {
    const page = dialog.page();
    const field = this.formField(dialog, fieldName);
    const trigger = field.locator('[data-slot="picker-trigger"]').first();
    await trigger.click();

    const surface = page
      .locator(
        '[data-slot="picker-dialog-content"], [data-slot="picker-drawer-content"], [data-slot="picker-popover-content"]',
      )
      .first();
    await surface.waitFor({ state: 'visible', timeout: 10_000 });

    const row = surface
      .locator('[data-slot="table-body"] tr[data-slot="table-row"]')
      .filter({ hasText: rowText })
      .first();
    await row.waitFor({ state: 'visible', timeout: 10_000 });
    // flux CRUD 行选区：可见控件是 [data-slot="checkbox"] 的 span（原生 input 被 sr-only 隐藏）。
    const checkbox = row
      .locator(
        '[data-slot="row-selection-checkbox"], [data-slot="checkbox"], [role="checkbox"], input[type="checkbox"]',
      )
      .first();
    if (await checkbox.count().then((c) => c > 0)) {
      await checkbox.click({ force: true });
    } else {
      await row.click();
    }

    await surface.locator('[data-slot="picker-confirm"]').first().click();
    await surface.waitFor({ state: 'hidden', timeout: 10_000 }).catch(() => {});
    await page.waitForTimeout(300);
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

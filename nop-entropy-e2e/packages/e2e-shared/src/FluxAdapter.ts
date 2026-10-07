import type { Page } from '@playwright/test';
import { AmisAdapter } from './AmisAdapter';

/**
 * flux 引擎适配器：继承 AmisAdapter 作为兜底层。
 *
 * 背景：平台缺省渲染模式为 flux（WebConfigs.CFG_WEB_RENDER_MODE），但 flux 渲染
 * 仅在页面存在同名 flux.yaml 变体时生效（PageModelLoaderFactory 优先加载 flux.yaml，
 * 否则回退标准管线）。当前各模块页面尚无 flux.yaml，页面内容仍由 amis 兼容渲染器
 * 输出，因此交互选择器复用 AmisAdapter 的全套实现。
 *
 * 当某页面落地 flux.yaml（DOM 变为 data-slot/data-field 契约结构，见
 * flux-guide 13-testing.md）后，在此按页面对应方法覆盖 flux 选择器，未覆盖的
 * 方法继续走 AmisAdapter 兜底。
 */
export class FluxAdapter extends AmisAdapter {
  engineName = 'flux';

  /**
   * 确认框点击：flux 壳层的 alert-dialog 用 data-slot 结构渲染，优先按契约定位；
   * 壳层未命中（amis 自绘确认框）时回退 super 的 role=alertdialog 文本匹配。
   */
  override async confirmDialogAction(page: Page): Promise<void> {
    const slotBtn = page
      .locator('[data-slot="alert-dialog-action"], [data-slot="surface-confirm-submit"]')
      .first();
    if (await slotBtn.isVisible().catch(() => false)) {
      await slotBtn.click();
      return;
    }
    await super.confirmDialogAction(page);
  }

  /**
   * 日期选择 helper（Flux 特有，不在 EngineAdapter 接口中，供 PO 直接调用）。
   */
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

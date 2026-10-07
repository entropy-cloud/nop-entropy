import { expect, type Page } from '@playwright/test';
import { BasePage } from '@nop-entropy/e2e-shared';
import type { EngineAdapter } from '@nop-entropy/e2e-shared';

export class SymbolSearchPO extends BasePage {
  private readonly indexId: string;

  constructor(page: Page, engine: EngineAdapter, indexId: string) {
    super(page, engine);
    this.indexId = indexId;
  }

  async open(): Promise<void> {
    // location.hash 赋值原语（page.goto(hash) 会被 SPA 壳层导航踩掉或忽略）
    await this.goto(`NopCodeSymbol-main?indexId=${this.indexId}`);
    await this.page.waitForLoadState('networkidle');
    await expect(this.page.locator('text=名称关键词').first()).toBeVisible({ timeout: 10_000 });
  }

  async searchSymbol(query: string): Promise<void> {
    const queryInput = this.engine.searchField(this.page, 'query');
    await queryInput.fill(query);
    await this.clickSearch(queryInput);
  }

  async searchSymbolAndWait(query: string): Promise<{
    total: number;
    items: { name: string }[];
  }> {
    const queryInput = this.engine.searchField(this.page, 'query');
    await queryInput.fill(query);

    // flux 页面的 CRUD 查询可能走 /graphql 也可能走 /r/<opName>——按操作名匹配，不限端点
    const responsePromise = this.page
      .waitForResponse(
        (resp) =>
          !!resp.request().postData()?.includes('findPage_symbols') ||
          resp.url().includes('findPage_symbols'),
        { timeout: 15_000 },
      )
      .catch(async () => {
        // 首次提交（按钮点击）可能未触发查询——回车兜底重提交一次
        const retry = this.page.waitForResponse(
          (resp) =>
            !!resp.request().postData()?.includes('findPage_symbols') ||
            resp.url().includes('findPage_symbols'),
          { timeout: 15_000 },
        );
        await queryInput.press('Enter');
        return retry;
      });
    await this.clickSearch(queryInput);
    const response = await responsePromise;

    const json = await response.json();
    return json?.data?.NopCodeSymbol__findPage_symbols;
  }

  /** 搜索按钮按引擎契约定位（flux 过滤表单按钮文本为「搜索」，限定主内容区），兜底回车提交 */
  private async clickSearch(queryInput: ReturnType<EngineAdapter['searchField']>): Promise<void> {
    const scopedBtn = this.page
      .locator('.nop-crud, .cxd-Crud, main')
      .getByRole('button', { name: /^搜索$|^查询$/ })
      .first();
    if (await scopedBtn.isVisible().catch(() => false)) {
      await scopedBtn.click();
    } else {
      await queryInput.press('Enter');
    }
  }

  async viewFirstDetail(): Promise<void> {
    const viewButton = this.page.locator('button:has-text("查看详情"), a:has-text("查看详情")').first();
    await viewButton.click();
    await expect(this.page.locator('text=符号详情').first()).toBeVisible({ timeout: 10_000 });
  }

  async assertTableHasData(): Promise<void> {
    await expect(this.page.locator('td').first()).toBeVisible({ timeout: 5_000 });
  }
}

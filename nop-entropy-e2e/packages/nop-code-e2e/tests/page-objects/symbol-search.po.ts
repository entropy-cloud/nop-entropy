import type { Page } from '@playwright/test';
import { BasePage, expect } from '@nop-entropy/e2e-shared';
import type { EngineAdapter } from '@nop-entropy/e2e-shared';

/** 符号搜索的查询字段（queryForm 上的字段名）。 */
const QUERY_FIELD = 'filter_query';

/** 符号搜索结果（分页结果本体）。 */
export interface SymbolSearchResult {
  total: number;
  items: { name: string }[];
}

export class SymbolSearchPO extends BasePage {
  private readonly indexId: string;

  constructor(page: Page, engine: EngineAdapter, indexId: string) {
    super(page, engine);
    this.indexId = indexId;
  }

  async open(): Promise<void> {
    await this.goto(`NopCodeSymbol-main?indexId=${this.indexId}`);
    await this.assertOpened();
  }

  /** 页面已就绪（符号检索表单渲染完成）。 */
  async assertOpened(): Promise<void> {
    await expect(this.fieldInput(QUERY_FIELD)).toBeVisible({ timeout: 20_000 });
  }

  /** 按关键词检索符号并等待后端返回分页结果。 */
  async searchSymbolAndWait(query: string): Promise<SymbolSearchResult> {
    await this.fillField(QUERY_FIELD, query);

    // flux 页面的 CRUD 查询可能走 /graphql 也可能走 /r/<opName>——按操作名匹配，不限端点
    const matchesSearch = (resp: { request(): { postData(): string | null }; url(): string }) =>
      !!resp.request().postData()?.includes('searchSymbols') || resp.url().includes('searchSymbols');

    const responsePromise = this.page
      .waitForResponse(matchesSearch, { timeout: 15_000 })
      .catch(async () => {
        // 首次提交（按钮点击）可能未触发查询——回车兜底重提交一次
        const retry = this.page.waitForResponse(matchesSearch, { timeout: 15_000 });
        await this.fieldInput(QUERY_FIELD).press('Enter');
        return retry;
      });
    await this.submitSearch();
    const response = await responsePromise;

    const json = await response.json();
    // /r/ REST 信封 {status, code, msg, data: PageBean}——data 即分页结果本体
    return json?.data as SymbolSearchResult;
  }

  /** 提交检索（按钮优先，回车兜底）。 */
  private async submitSearch(): Promise<void> {
    const scopedBtn = this.page
      .locator('.nop-crud, main')
      .getByRole('button', { name: /^搜索$|^查询$/ })
      .first();
    if (await scopedBtn.isVisible().catch(() => false)) {
      await scopedBtn.click();
      return;
    }
    await this.fieldInput(QUERY_FIELD).press('Enter');
  }

  /** 打开首条检索结果的详情面板。 */
  async viewFirstDetail(): Promise<void> {
    await this.page
      .locator('button:has-text("查看详情"), a:has-text("查看详情")')
      .first()
      .click();
    await expect(this.page.locator('text=符号详情').first()).toBeVisible({ timeout: 10_000 });
  }

  /** 结果表格已渲染数据行。 */
  async assertTableHasData(): Promise<void> {
    await expect(this.engine.rows(this.page).first()).toBeVisible({ timeout: 10_000 });
  }
}

export { SymbolSearchPO as default };

import { expect, type Page } from '@playwright/test';
import { BasePage } from '@nop-entropy/e2e-shared';
import type { EngineAdapter } from '@nop-entropy/e2e-shared';

export class SymbolSearchPO extends BasePage {
  private readonly indexId: string;
  private engine: EngineAdapter;

  override get entityName(): string {
    return 'NopCodeSymbol';
  }

  constructor(page: Page, engine: EngineAdapter, indexId: string) {
    super(page);
    this.engine = engine;
    this.indexId = indexId;
  }

  override async goto(): Promise<void> {
    await this.page.goto(`/#/NopCodeSymbol-main?indexId=${this.indexId}`);
    await this.page.waitForLoadState('networkidle');
    await expect(this.page.locator('text=名称关键词').first()).toBeVisible({ timeout: 10_000 });
  }

  async searchSymbol(query: string): Promise<void> {
    const queryInput = this.page.locator('input[name="filter_query"]');
    await queryInput.fill(query);
    await this.page.getByRole('button', { name: '查询' }).click();
  }

  async searchSymbolAndWait(query: string): Promise<{
    total: number;
    items: { name: string }[];
  }> {
    const queryInput = this.page.locator('input[name="filter_query"]');
    await queryInput.fill(query);

    const responsePromise = this.page.waitForResponse(
      (resp) =>
        resp.url().includes('/graphql') &&
        !!resp.request().postData()?.includes('findPage_symbols'),
    );
    await this.page.getByRole('button', { name: '查询' }).click();
    const response = await responsePromise;

    const json = await response.json();
    return json?.data?.NopCodeSymbol__findPage_symbols;
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

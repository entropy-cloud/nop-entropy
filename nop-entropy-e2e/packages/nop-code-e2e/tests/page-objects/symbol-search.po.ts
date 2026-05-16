/**
 * Page Object for the NopCodeSymbol search page.
 *
 * URL pattern: /#/NopCodeSymbol-main?indexId={indexId}
 *
 * Provides methods for searching symbols via the UI and verifying results.
 */
import { expect, type Page } from '@playwright/test';
import { BasePage, fillField, clickButton, waitForTableLoad } from '@nop-entropy/e2e-shared';

export class SymbolSearchPO extends BasePage {
  private readonly indexId: string;

  override get entityName(): string {
    return 'NopCodeSymbol';
  }

  constructor(page: Page, indexId: string) {
    super(page);
    this.indexId = indexId;
  }

  /** Navigate to the symbol search page with the configured indexId. */
  override async goto(): Promise<void> {
    await this.page.goto(`/#/NopCodeSymbol-main?indexId=${this.indexId}`);
    await this.page.waitForLoadState('networkidle');
    await expect(this.page.locator('text=名称关键词').first()).toBeVisible({ timeout: 10_000 });
  }

  /** Search symbols by query keyword. */
  async searchSymbol(query: string): Promise<void> {
    const queryInput = this.page.locator('input[name="filter_query"]');
    await queryInput.fill(query);
    await clickButton(this.page, '查询');
  }

  /** Search and wait for the GraphQL response, returning parsed result. */
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
    await clickButton(this.page, '查询');
    const response = await responsePromise;

    const json = await response.json();
    return json?.data?.NopCodeSymbol__findPage_symbols;
  }

  /** Click the first "查看详情" button in the table. */
  async viewFirstDetail(): Promise<void> {
    const viewButton = this.page.locator('button:has-text("查看详情"), a:has-text("查看详情")').first();
    await viewButton.click();
    await expect(this.page.locator('text=符号详情').first()).toBeVisible({ timeout: 10_000 });
  }

  /** Assert that the table has at least one row visible. */
  async assertTableHasData(): Promise<void> {
    await expect(this.page.locator('td').first()).toBeVisible({ timeout: 5_000 });
  }
}

import { expect, type Page } from '@playwright/test';
import { BasePage } from '@nop-entropy/e2e-shared';
import type { EngineAdapter } from '@nop-entropy/e2e-shared';

export interface TypeHierarchyFormInput {
  indexId: string;
  qualifiedName: string;
  direction: string;
  maxDepth: string;
}

export class TypeHierarchyPO extends BasePage {
  constructor(page: Page, engine: EngineAdapter) {
    super(page, engine);
  }

  async open(): Promise<void> {
    await this.page.goto('/#/type-hierarchy-main');
    await this.page.waitForLoadState('networkidle');
    await expect(this.page.locator('text=类型层级').first()).toBeVisible({ timeout: 10_000 });
  }

  async fillForm(input: TypeHierarchyFormInput): Promise<void> {
    await this.page.locator('input[name="indexId"]').fill(input.indexId);
    await this.page.locator('input[name="qualifiedName"]').fill(input.qualifiedName);
    await this.page.locator('input[name="direction"]').fill(input.direction);
    await this.page.locator('input[name="maxDepth"]').fill(input.maxDepth);
  }

  async submitAndWait(): Promise<void> {
    const responsePromise = this.page.waitForResponse(
      (resp) => resp.url().includes('/graphql') && !!resp.request().postData()?.includes('NopCodeSymbol'),
    );
    await this.page.getByRole('button', { name: '提交' }).click();
    await responsePromise;
  }
}

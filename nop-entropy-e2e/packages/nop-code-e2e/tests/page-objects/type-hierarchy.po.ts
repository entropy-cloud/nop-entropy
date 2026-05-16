/**
 * Page Object for the Type Hierarchy query page.
 *
 * URL pattern: /#/type-hierarchy-main
 *
 * Provides methods for filling the type hierarchy form and submitting queries.
 */
import { expect, type Page } from '@playwright/test';
import { BasePage } from '@nop-entropy/e2e-shared';

export interface TypeHierarchyFormInput {
  indexId: string;
  qualifiedName: string;
  direction: string;
  maxDepth: string;
}

export class TypeHierarchyPO extends BasePage {
  override get entityName(): string {
    return 'type-hierarchy';
  }

  constructor(page: Page) {
    super(page);
  }

  /** Navigate to the type hierarchy page. */
  override async goto(): Promise<void> {
    await this.page.goto('/#/type-hierarchy-main');
    await this.page.waitForLoadState('networkidle');
    await expect(this.page.locator('text=类型层级').first()).toBeVisible({ timeout: 10_000 });
  }

  /** Fill the type hierarchy query form fields. */
  async fillForm(input: TypeHierarchyFormInput): Promise<void> {
    await this.page.locator('input[name="indexId"]').fill(input.indexId);
    await this.page.locator('input[name="qualifiedName"]').fill(input.qualifiedName);
    await this.page.locator('input[name="direction"]').fill(input.direction);
    await this.page.locator('input[name="maxDepth"]').fill(input.maxDepth);
  }

  /** Submit the form and wait for the GraphQL response. */
  async submitAndWait(): Promise<void> {
    const responsePromise = this.page.waitForResponse(
      (resp) => resp.url().includes('/graphql') && !!resp.request().postData()?.includes('NopCodeSymbol'),
    );
    await this.page.getByRole('button', { name: '提交' }).click();
    await responsePromise;
  }
}

import type { Page } from '@playwright/test';
import { BasePage, expect } from '@nop-entropy/e2e-shared';
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
    await this.goto('type-hierarchy-main');
    await this.assertOpened();
  }

  /** 页面已就绪（层级查询表单渲染完成）。 */
  async assertOpened(): Promise<void> {
    await expect(this.fieldInput('qualifiedName')).toBeVisible({ timeout: 20_000 });
  }

  /** 按 fieldName 填写查询条件。 */
  async fillForm(input: TypeHierarchyFormInput): Promise<void> {
    for (const [fieldName, value] of Object.entries(input)) {
      await this.fillField(fieldName, value);
    }
  }

  /** 提交查询并等待层级接口返回。 */
  async submitAndWait(): Promise<void> {
    const responsePromise = this.page.waitForResponse(
      (resp) => resp.url().includes('/graphql') && !!resp.request().postData()?.includes('NopCodeSymbol'),
    );
    await this.page.getByRole('button', { name: '提交' }).click();
    await responsePromise;
  }
}

export { TypeHierarchyPO as default };

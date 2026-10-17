import type { Page } from '@playwright/test';
import { CrudListPage } from '@nop-entropy/e2e-shared';
import type { EngineAdapter } from '@nop-entropy/e2e-shared';

/**
 * 部门页面（标签页形态）：菜单路由 `/pages/NopAuthDept/tabsView`，含"树形视图/列表视图"两个标签。
 */
export class DeptPO extends CrudListPage {
  constructor(page: Page, engine: EngineAdapter) {
    super(page, engine, { entityRoute: 'NopAuthDept', entityName: 'NopAuthDept' });
  }

  /** 打开"部门-标签页"页面并等待渲染。 */
  async openTabs(): Promise<void> {
    await this.goto('pages/NopAuthDept/tabsView');
    await this.page.waitForTimeout(4000);
  }

  /** 切换到指定标签（树形视图 / 列表视图）。 */
  async switchTab(label: string): Promise<void> {
    await this.engine.switchToTab(this.page, label);
  }

  /** 在当前激活标签内新建部门，可选指定父部门（tree-select，按名称选择）。 */
  async createDept(name: string, parentName?: string): Promise<void> {
    await this.page
      .locator('[data-slot="tabs-content"]:not([hidden]) [data-slot="crud-toolbar-main"] button')
      .filter({ hasText: /新增|Add/ })
      .first()
      .click();
    await this.fillDialog({ deptName: name });
    if (parentName) {
      await this.selectDialogOption('parentId', parentName);
    }
    await this.clickSave();
  }
}

export { DeptPO as default };

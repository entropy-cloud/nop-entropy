import { expect, type Page } from '@playwright/test';
import { CrudListPage } from '@nop-entropy/e2e-shared';
import type { EngineAdapter, FormData } from '@nop-entropy/e2e-shared';

export type RoleFormData = FormData;

export class RolePO extends CrudListPage {
  constructor(page: Page, engine: EngineAdapter) {
    super(page, engine, {
      entityRoute: 'NopAuthRole-main',
      entityName: 'NopAuthRole',
      columnHeaders: ['roleId', 'roleName', 'isPrimary', 'createdBy', 'createTime', 'updatedBy', 'updateTime', 'remark'],
    });
  }

  async goto(): Promise<void> {
    await super.goto('NopAuthRole-main');
    await this.waitForListWithReload();
  }

  async searchRole(roleName: string): Promise<void> {
    await this.search('roleName', roleName);
  }

  async createRole(data: RoleFormData): Promise<void> {
    await this.clickAdd();
    await this.fillDialog(data);
    await this.clickSave();
    await this.goto();
  }

  async editRole(rowIdentifier: string, changes: RoleFormData): Promise<void> {
    await this.clickEdit(rowIdentifier);
    await this.fillDialog(changes);
    await this.clickSave();
  }

  async viewRole(rowIdentifier: string): Promise<void> {
    await this.clickView(rowIdentifier);
  }

  async deleteRole(rowIdentifier: string): Promise<void> {
    await this.clickDelete(rowIdentifier);
  }

  /** 为角色分配用户。 */
  async assignUsers(roleId: string): Promise<void> {
    await this.openRowPanel(roleId, /用户/, 'drawer');
  }

  /** 为角色分配功能权限。 */
  async assignPermissions(roleId: string): Promise<void> {
    await this.openRowPanel(roleId, /授权/);
  }

  /** 断言角色管理面板（弹窗或抽屉）已打开。 */
  async assertPanelVisible(): Promise<void> {
    const panel = this.engine.dialog(this.page).or(this.engine.drawer(this.page));
    await expect(panel).toBeVisible({ timeout: 10_000 });
  }

  async assertRoleExists(roleId: string): Promise<void> {
    await this.assertEntityExists(roleId);
  }

  async assertRoleNotExists(roleId: string): Promise<void> {
    await this.assertEntityNotExists(roleId);
  }
}

export { RolePO as default };

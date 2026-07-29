import { expect, type Page } from '@playwright/test';
import { CrudListPage, FormDialog } from '@nop-entropy/e2e-shared';
import type { EngineAdapter } from '@nop-entropy/e2e-shared';

export interface RoleFormData {
  roleId?: string;
  roleName: string;
  isPrimary?: boolean;
  remark?: string;
}

export class RolePO extends CrudListPage {
  constructor(page: Page, engine: EngineAdapter) {
    super(page, engine, {
      entityRoute: 'NopAuthRole-main',
      entityName: 'NopAuthRole',
      columnHeaders: ['roleId', 'roleName', 'isPrimary', 'createdBy', 'createTime', 'updatedBy', 'updateTime', 'remark'],
    });
  }

  async goto(): Promise<void> {
    await this.page.goto(`#/NopAuthRole-main`);
    await this.page.waitForLoadState('networkidle');
    await this.waitForList();
  }

  async searchRole(roleName: string): Promise<void> {
    await this.search('roleName', roleName);
  }

  async fillForm(data: RoleFormData): Promise<void> {
    const dialog = new FormDialog(this.page, this.engine);
    if (data.roleId !== undefined) await dialog.setField('roleId', data.roleId);
    await dialog.setField('roleName', data.roleName);
    if (data.isPrimary !== undefined) await dialog.setField('isPrimary', data.isPrimary);
    if (data.remark !== undefined) await dialog.setField('remark', data.remark);
  }

  async clickRoleUsers(roleId: string): Promise<void> {
    const row = await this.findRowByText(roleId);
    if (row) {
      await this.engine.rowAction(row, /用户/);
    }
    await this.engine.drawer(this.page).waitFor({ state: 'visible' });
  }

  async clickAssignAuth(roleId: string): Promise<void> {
    const row = await this.findRowByText(roleId);
    if (row) {
      await this.engine.rowAction(row, /授权/);
    }
    // 授权可能打开 dialog 或 drawer，两者都支持
    const dialogOrDrawer = this.engine.dialog(this.page).or(this.engine.drawer(this.page));
    await dialogOrDrawer.waitFor({ state: 'visible' });
  }

  async assertDrawerVisible(): Promise<void> {
    const dialogOrDrawer = this.engine.dialog(this.page).or(this.engine.drawer(this.page));
    await expect(dialogOrDrawer).toBeVisible({ timeout: 10_000 });
  }

  async assertRoleExists(roleId: string): Promise<void> {
    await this.assertEntityExists(roleId);
  }

  async assertRoleNotExists(roleId: string): Promise<void> {
    await this.assertEntityNotExists(roleId);
  }

  async createRole(data: RoleFormData): Promise<void> {
    await this.clickAdd();
    await this.fillForm(data);
    await this.clickSave();
    if (this.engine.engineName === 'flux') {
      await this.goto();
    }
    await this.waitForList();
  }
}

export { RolePO as default };

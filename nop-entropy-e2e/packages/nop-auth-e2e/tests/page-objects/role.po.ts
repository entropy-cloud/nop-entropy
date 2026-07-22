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
    super(page, engine, { entityRoute: 'NopAuthRole-main', entityName: 'NopAuthRole' });
  }

  async goto(): Promise<void> {
    await this.page.goto(`#/NopAuthRole-main`);
    await this.page.waitForLoadState('networkidle');
    await this.waitForList();
  }

  async searchRole(roleName: string): Promise<void> {
    // Wait for any leftover dialog overlays to disappear
    await this.page
      .locator('[data-slot="alert-dialog-overlay"]')
      .waitFor({ state: 'hidden', timeout: 5_000 })
      .catch(() => {});

    // AMIS CRUD filter form: fill input and click 搜索 button
    const filterInput = this.page.locator('input[name^="filter_roleName"]').first();
    const visible = await filterInput.isVisible().catch(() => false);
    if (visible) {
      await filterInput.clear();
      await filterInput.fill(roleName);
      const searchBtn = this.page.locator('.cxd-Table-searchableForm button[type="submit"]').first();
      const searchBtnVisible = await searchBtn.isVisible().catch(() => false);
      if (searchBtnVisible) {
        await searchBtn.click({ force: true });
      } else {
        await filterInput.press('Enter');
      }
      await this.page.waitForTimeout(1500);
    } else {
      // Fallback: refresh the list
      const refreshBtn = this.engine
        .crudContainer(this.page)
        .locator('[class*="fa-sync"]')
        .first();
      const refreshVisible = await refreshBtn.isVisible().catch(() => false);
      if (refreshVisible) {
        await refreshBtn.click();
      }
    }
    await this.page.waitForLoadState('networkidle').catch(() => {});
  }

  async fillForm(data: RoleFormData): Promise<void> {
    const dialog = new FormDialog(this.page, this.engine);
    if (data.roleId !== undefined) {
      await dialog.setField('roleId', data.roleId);
    }
    await dialog.setField('roleName', data.roleName);
    if (data.isPrimary !== undefined) {
      await this.setCheckbox('isPrimary', data.isPrimary);
    }
    if (data.remark !== undefined) {
      await dialog.setField('remark', data.remark);
    }
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
    await this.engine.drawer(this.page).waitFor({ state: 'visible' });
  }

  async assertDrawerVisible(): Promise<void> {
    await expect(this.engine.drawer(this.page)).toBeVisible({ timeout: 10_000 });
  }

  async readViewField(fieldName: string): Promise<string> {
    const dialog = new FormDialog(this.page, this.engine);
    return dialog.getField(fieldName);
  }

  async clickSave(): Promise<void> {
    const dialog = new FormDialog(this.page, this.engine);
    await dialog.submit();
    await this.waitForList();
  }

  async clickView(rowId: string): Promise<void> {
    const row = await this.findRowByText(rowId);
    if (row) {
      await this.engine.rowAction(row, /查看/);
    }
    await this.engine.dialog(this.page).waitFor({ state: 'visible' });
  }

  async clickEdit(rowId: string): Promise<void> {
    const row = await this.findRowByText(rowId);
    if (row) {
      await this.engine.rowAction(row, /编辑/);
    }
    await this.engine.dialog(this.page).waitFor({ state: 'visible' });
  }

  async clickDelete(rowId: string): Promise<void> {
    const row = await this.findRowByText(rowId);
    if (row) {
      await this.deleteRow(row);
    }
    await this.waitForList();
  }

  async assertRoleExists(roleId: string): Promise<void> {
    const row = await this.findRowByText(roleId);
    expect(row).not.toBeNull();
  }

  async assertRoleNotExists(roleId: string): Promise<void> {
    const row = await this.findRowByText(roleId);
    expect(row).toBeNull();
  }

  async createRole(data: RoleFormData): Promise<void> {
    await this.clickAdd();
    await this.fillForm(data);
    await this.clickSave();
  }

  async getTableRowCount(): Promise<number> {
    return this.engine.rows(this.page).count();
  }

  private async setCheckbox(fieldName: string, checked: boolean): Promise<void> {
    const dialog = new FormDialog(this.page, this.engine);
    const container = dialog.dialog;
    const checkbox = container
      .locator(`[name="${fieldName}"] input[type="checkbox"]`)
      .first();
    const isChecked = await checkbox.isChecked();
    if (isChecked !== checked) {
      await checkbox.click();
    }
  }
}

export { RolePO as default };

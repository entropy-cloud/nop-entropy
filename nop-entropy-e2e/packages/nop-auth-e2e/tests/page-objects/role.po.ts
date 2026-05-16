/**
 * Page Object for the NopAuthRole entity CRUD page.
 *
 * URL pattern: /#/NopAuthRole-main
 *
 * Grid columns: roleId, roleName, isPrimary, createdBy, createTime,
 *               updatedBy, updateTime, remark
 *
 * Row actions: "用户" (role-users drawer), "授权" (assign-auth drawer)
 */
import { expect, type Page } from '@playwright/test';
import {
  AmisCrudPage,
  fillModalField,
  readModalField,
  clickRowAction,
  waitForDrawer,
  getTableRowCount,
} from '@nop-entropy/e2e-shared';

/** Shape of the NopAuthRole form data used for create/edit. */
export interface RoleFormData {
  roleId?: string;
  roleName: string;
  isPrimary?: boolean;
  remark?: string;
}

export class RolePO extends AmisCrudPage {
  override get entityName(): string {
    return 'NopAuthRole';
  }

  constructor(page: Page) {
    super(page);
  }

  // ── Search / Filter ───────────────────────────────────────────────────

  /** Search roles by roleName using the query form. */
  async searchRole(roleName: string): Promise<void> {
    await this.search('roleName', roleName);
  }

  // ── CRUD operations ───────────────────────────────────────────────────

  /**
   * Fill the add/edit form fields.
   *
   * Only fills fields that are present in `data`. Booleans are handled
   * via checkbox interactions.
   */
  async fillForm(data: RoleFormData): Promise<void> {
    if (data.roleId !== undefined) {
      await fillModalField(this.page, 'roleId', data.roleId);
    }
    await fillModalField(this.page, 'roleName', data.roleName);
    if (data.isPrimary !== undefined) {
      await this.setCheckbox('isPrimary', data.isPrimary);
    }
    if (data.remark !== undefined) {
      await fillModalField(this.page, 'remark', data.remark);
    }
  }

  // ── Row-level custom actions ──────────────────────────────────────────

  /**
   * Open the "用户" (role-users) drawer for a specific role.
   *
   * This is a row-level action that opens a drawer showing the users
   * associated with the given role.
   */
  async clickRoleUsers(roleId: string): Promise<void> {
    await clickRowAction(this.page, roleId, '用户');
    await waitForDrawer(this.page);
  }

  /**
   * Open the "授权" (assign-auth) drawer for a specific role.
   *
   * This is a row-level action that opens a drawer for managing
   * authorization/resource assignments for the role.
   */
  async clickAssignAuth(roleId: string): Promise<void> {
    await clickRowAction(this.page, roleId, '授权');
    await waitForDrawer(this.page);
  }

  // ── View modal helpers ────────────────────────────────────────────────

  /**
   * Read the display value of a field from the currently open view modal.
   *
   * @param fieldName - The AMIS field name (e.g. `'roleId'`, `'roleName'`).
   * @returns The field's current display value.
   */
  async readViewField(fieldName: string): Promise<string> {
    return readModalField(this.page, fieldName);
  }

  // ── Assertions ────────────────────────────────────────────────────────

  /** Assert that a role row with the given roleId exists in the table. */
  async assertRoleExists(roleId: string): Promise<void> {
    const row = this.page
      .locator('tr')
      .filter({ hasText: roleId })
      .first();
    await expect(row).toBeVisible();
  }

  /** Assert that no role row with the given roleId exists in the table. */
  async assertRoleNotExists(roleId: string): Promise<void> {
    const row = this.page
      .locator('tr')
      .filter({ hasText: roleId })
      .first();
    await expect(row).toHaveCount(0);
  }

  // ── Composite / end-to-end helpers ────────────────────────────────────

  /**
   * Create a role end-to-end: click add, fill the form, and save.
   *
   * If `data.roleId` is omitted it will not be filled (the server may
   * auto-generate it).
   */
  async createRole(data: RoleFormData): Promise<void> {
    await this.clickAdd();
    await this.fillForm(data);
    await this.clickSave();
  }

  /** Get the number of data rows currently visible in the table. */
  async getTableRowCount(): Promise<number> {
    return getTableRowCount(this.page);
  }

  // ── Internal helpers ──────────────────────────────────────────────────

  /**
   * Toggle a checkbox field inside the modal/drawer.
   *
   * @param fieldName - The AMIS field name of the checkbox.
   * @param checked - Whether the checkbox should be checked.
   */
  private async setCheckbox(
    fieldName: string,
    checked: boolean,
  ): Promise<void> {
    const container = this.page.locator('.cxd-Modal, .cxd-Drawer').last();
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

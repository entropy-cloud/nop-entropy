/**
 * Page Object for the NopAuthUser entity CRUD page.
 *
 * URL pattern: /#/NopAuthUser-main
 *
 * Grid columns: userId, userName, nickName, gender, email, phone,
 *               userType, status, tenantId, createdBy, createTime,
 *               updatedBy, updateTime
 */
import { expect, type Page } from '@playwright/test';
import {
  AmisCrudPage,
  fillModalField,
  readModalField,
  selectOption,
  waitForTableLoad,
  readTableCell,
  getTableRowCount,
} from '@nop-entropy/e2e-shared';

/** Form data matching NopAuthUser entity fields. */
export interface UserFormData {
  userId?: string;
  userName: string;
  nickName: string;
  password?: string;
  deptId?: string;
  openId?: string;
  gender?: number;
  email?: string;
  phone?: string;
  userType?: number;
  status?: number;
  tenantId?: string;
  remark?: string;
}

export class UserPO extends AmisCrudPage {
  override get entityName(): string {
    return 'NopAuthUser';
  }

  constructor(page: Page) {
    super(page);
  }

  // ── Search / Filter ───────────────────────────────────────────────────

  /** Type a userName into the query form and trigger the search. */
  async searchUser(userName: string): Promise<void> {
    await this.search('userName', userName);
  }

  // ── Add / Create ──────────────────────────────────────────────────────

  /** Fill all fields in the add modal (userId is writable in add mode). */
  async fillAddForm(data: UserFormData): Promise<void> {
    if (data.userId) {
      await fillModalField(this.page, 'userId', data.userId);
    }
    await fillModalField(this.page, 'userName', data.userName);
    await fillModalField(this.page, 'nickName', data.nickName);
    if (data.password) {
      await fillModalField(this.page, 'password', data.password);
      await fillModalField(this.page, '__password2', data.password);
    }
    if (data.deptId) {
      await fillModalField(this.page, 'deptId', data.deptId);
    }
    if (data.openId) {
      await fillModalField(this.page, 'openId', data.openId);
    }
    if (data.gender !== undefined) {
      await selectOption(this.page, 'gender', data.gender);
    }
    if (data.email) {
      await fillModalField(this.page, 'email', data.email);
    }
    if (data.phone) {
      await fillModalField(this.page, 'phone', data.phone);
    }
    if (data.userType !== undefined) {
      await selectOption(this.page, 'userType', data.userType);
    }
    if (data.status !== undefined) {
      await selectOption(this.page, 'status', data.status);
    }
    if (data.tenantId) {
      await fillModalField(this.page, 'tenantId', data.tenantId);
    }
    if (data.remark) {
      await fillModalField(this.page, 'remark', data.remark);
    }
  }

  // ── Edit ──────────────────────────────────────────────────────────────

  /** Fill edit-form fields. userId is readonly in edit mode so it is omitted. */
  async fillEditForm(data: Partial<UserFormData>): Promise<void> {
    if (data.userName !== undefined) {
      await fillModalField(this.page, 'userName', data.userName);
    }
    if (data.nickName !== undefined) {
      await fillModalField(this.page, 'nickName', data.nickName);
    }
    if (data.deptId !== undefined) {
      await fillModalField(this.page, 'deptId', data.deptId);
    }
    if (data.openId !== undefined) {
      await fillModalField(this.page, 'openId', data.openId);
    }
    if (data.gender !== undefined) {
      await selectOption(this.page, 'gender', data.gender);
    }
    if (data.email !== undefined) {
      await fillModalField(this.page, 'email', data.email);
    }
    if (data.phone !== undefined) {
      await fillModalField(this.page, 'phone', data.phone);
    }
    if (data.userType !== undefined) {
      await selectOption(this.page, 'userType', data.userType);
    }
    if (data.status !== undefined) {
      await selectOption(this.page, 'status', data.status);
    }
    if (data.tenantId !== undefined) {
      await fillModalField(this.page, 'tenantId', data.tenantId);
    }
    if (data.remark !== undefined) {
      await fillModalField(this.page, 'remark', data.remark);
    }
  }

  // ── View ──────────────────────────────────────────────────────────────

  /**
   * Read a field value from the currently open view modal.
   * Returns the displayed text for the given fieldName.
   */
  async readViewField(fieldName: string): Promise<string> {
    return readModalField(this.page, fieldName);
  }

  // ── Table reads ───────────────────────────────────────────────────────

  /**
   * Read a cell value from the user list table.
   * @param rowIdentifier  - the row identifier (first column value)
   * @param columnName - the column header name
   */
  async readTableField(rowIdentifier: string, columnName: string): Promise<string> {
    return readTableCell(this.page, rowIdentifier, columnName);
  }

  /** Return the number of data rows currently visible in the grid. */
  async getTableRowCount(): Promise<number> {
    return getTableRowCount(this.page);
  }

  // ── Assertions ────────────────────────────────────────────────────────

  /** Assert that a row with the given userId exists in the grid. */
  async assertUserExists(rowIdentifier: string): Promise<void> {
    const cellText = await readTableCell(this.page, rowIdentifier, 'userName');
    expect(cellText).toBeTruthy();
  }

  /** Assert that no row for the given userId is present in the grid. */
  async assertUserNotExists(rowIdentifier: string): Promise<void> {
    const rowLocator = this.page.locator('tbody > tr');
    const count = await rowLocator.count();
    for (let i = 0; i < count; i++) {
      const text = await rowLocator.nth(i).textContent();
      expect(text).not.toContain(rowIdentifier);
    }
  }

  // ── Composite action ──────────────────────────────────────────────────

  /**
   * End-to-end convenience: navigate to the user page, open the add dialog,
   * fill in the form, and save.
   */
  async createUser(data: UserFormData): Promise<void> {
    await this.goto();
    await this.clickAdd();
    await this.fillAddForm(data);
    await this.clickSave();
    await waitForTableLoad(this.page);
  }
}

export { UserPO as default };

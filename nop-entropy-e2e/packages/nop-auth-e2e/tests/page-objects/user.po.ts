import { expect, type Page } from '@playwright/test';
import { CrudListPage, FormDialog } from '@nop-entropy/e2e-shared';
import type { EngineAdapter } from '@nop-entropy/e2e-shared';

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

export class UserPO extends CrudListPage {
  constructor(page: Page, engine: EngineAdapter) {
    super(page, engine, { entityRoute: 'NopAuthUser-main', entityName: 'NopAuthUser' });
  }

  async goto(): Promise<void> {
    await this.page.goto(`#/NopAuthUser-main`);
    await this.page.waitForLoadState('networkidle');
    await this.waitForList();
  }

  async searchUser(userName: string): Promise<void> {
    const filterInput = this.page.locator('input[name^="filter_userName"]').first();
    const visible = await filterInput.isVisible().catch(() => false);
    if (visible) {
      await filterInput.clear();
      await filterInput.fill(userName);
    }
    await this.engine.addButton(this.page).click();
    await this.engine.table(this.page).waitFor({ state: 'visible' });
  }

  async fillAddForm(data: UserFormData): Promise<void> {
    const dialog = new FormDialog(this.page, this.engine);
    if (data.userId) {
      await dialog.setField('userId', data.userId);
    }
    await dialog.setField('userName', data.userName);
    await dialog.setField('nickName', data.nickName);
    if (data.password) {
      await dialog.setField('password', data.password);
      await dialog.setField('__password2', data.password);
    }
    if (data.deptId) {
      await dialog.setField('deptId', data.deptId);
    }
    if (data.openId) {
      await dialog.setField('openId', data.openId);
    }
    if (data.gender !== undefined) {
      await dialog.selectOption(['gender'], [String(data.gender)]);
    }
    if (data.email) {
      await dialog.setField('email', data.email);
    }
    if (data.phone) {
      await dialog.setField('phone', data.phone);
    }
    if (data.userType !== undefined) {
      await dialog.selectOption(['userType'], [String(data.userType)]);
    }
    if (data.status !== undefined) {
      await dialog.selectOption(['status'], [String(data.status)]);
    }
    if (data.tenantId) {
      await dialog.setField('tenantId', data.tenantId);
    }
    if (data.remark) {
      await dialog.setField('remark', data.remark);
    }
  }

  async fillEditForm(data: Partial<UserFormData>): Promise<void> {
    const dialog = new FormDialog(this.page, this.engine);
    if (data.userName !== undefined) {
      await dialog.setField('userName', data.userName);
    }
    if (data.nickName !== undefined) {
      await dialog.setField('nickName', data.nickName);
    }
    if (data.deptId !== undefined) {
      await dialog.setField('deptId', data.deptId);
    }
    if (data.openId !== undefined) {
      await dialog.setField('openId', data.openId);
    }
    if (data.gender !== undefined) {
      await dialog.selectOption(['gender'], [String(data.gender)]);
    }
    if (data.email !== undefined) {
      await dialog.setField('email', data.email);
    }
    if (data.phone !== undefined) {
      await dialog.setField('phone', data.phone);
    }
    if (data.userType !== undefined) {
      await dialog.selectOption(['userType'], [String(data.userType)]);
    }
    if (data.status !== undefined) {
      await dialog.selectOption(['status'], [String(data.status)]);
    }
    if (data.tenantId !== undefined) {
      await dialog.setField('tenantId', data.tenantId);
    }
    if (data.remark !== undefined) {
      await dialog.setField('remark', data.remark);
    }
  }

  async readViewField(fieldName: string): Promise<string> {
    const dialog = new FormDialog(this.page, this.engine);
    return dialog.getField(fieldName);
  }

  async readTableField(rowIdentifier: string, columnName: string): Promise<string> {
    const row = await this.findRowByText(rowIdentifier);
    if (!row) return '';
    const allRows = this.engine.rows(this.page);
    const count = await allRows.count();
    for (let i = 0; i < count; i++) {
      const r = allRows.nth(i);
      const text = (await r.textContent()) ?? '';
      if (text.includes(rowIdentifier)) {
        return this.engine.cellValue(r, columnName, this.config.columnHeaders ?? []);
      }
    }
    return '';
  }

  async getTableRowCount(): Promise<number> {
    return this.engine.rows(this.page).count();
  }

  async assertUserExists(rowIdentifier: string): Promise<void> {
    const row = await this.findRowByText(rowIdentifier);
    expect(row).not.toBeNull();
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

  async assertUserNotExists(rowIdentifier: string): Promise<void> {
    const row = await this.findRowByText(rowIdentifier);
    expect(row).toBeNull();
  }

  async createUser(data: UserFormData): Promise<void> {
    await this.goto();
    await this.clickAdd();
    await this.fillAddForm(data);
    await this.clickSave();
    await this.waitForList();
  }
}

export { UserPO as default };

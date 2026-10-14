import type { Page } from '@playwright/test';
import { CrudListPage } from '@nop-entropy/e2e-shared';
import type { EngineAdapter, FormData } from '@nop-entropy/e2e-shared';

export interface UserFormData {
  userId?: string;
  userName?: string;
  nickName?: string;
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
    super(page, engine, {
      entityRoute: 'NopAuthUser-main',
      entityName: 'NopAuthUser',
      columnHeaders: ['', 'userName', 'nickName', 'deptId', 'gender', 'phone', 'userType', 'status'],
    });
  }

  async goto(): Promise<void> {
    await super.goto('NopAuthUser-main');
    await this.waitForListWithReload();
  }

  /** 按用户名检索用户。 */
  async searchUser(userName: string): Promise<void> {
    await this.search('userName', userName);
  }

  /** 新增用户（密码确认字段由 PO 内部维护，spec 不需要知道）。 */
  async createUser(data: UserFormData): Promise<void> {
    await this.goto();
    await this.clickAdd();
    await this.fillDialog(withPasswordConfirmation(data));
    await this.clickSave();
  }

  /** 修改用户资料。 */
  async editUser(rowIdentifier: string, changes: UserFormData): Promise<void> {
    await this.clickEdit(rowIdentifier);
    await this.fillDialog(definedFields(changes));
    await this.clickSave();
  }

  /** 查看用户详情（打开查看面板）。 */
  async viewUser(rowIdentifier: string): Promise<void> {
    await this.clickView(rowIdentifier);
  }

  /** 删除用户。 */
  async deleteUser(rowIdentifier: string): Promise<void> {
    await this.clickDelete(rowIdentifier);
  }

  async assertUserExists(rowIdentifier: string): Promise<void> {
    await this.assertEntityExists(rowIdentifier);
  }

  async assertUserNotExists(rowIdentifier: string): Promise<void> {
    await this.assertEntityNotExists(rowIdentifier);
  }
}

/** 新增表单有独立的"确认密码"输入，spec 不需要知道它——由 PO 补齐。 */
function withPasswordConfirmation(data: UserFormData): FormData {
  const fields = definedFields(data);
  if (fields.password) fields.__password2 = fields.password;
  return fields;
}

/** 只保留业务上显式给出的字段（跳过 undefined）。 */
function definedFields(data: UserFormData): FormData {
  const fields: FormData = {};
  for (const [fieldName, value] of Object.entries(data)) {
    if (value !== undefined) fields[fieldName] = value;
  }
  return fields;
}

export { UserPO as default };

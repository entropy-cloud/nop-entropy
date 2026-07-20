import { expect, type Page } from '@playwright/test';
import { CrudListPage, FormDialog } from '@nop-entropy/e2e-shared';
import type { EngineAdapter } from '@nop-entropy/e2e-shared';

export interface ResourceFormData {
  resourceId?: string;
  siteId?: string;
  displayName: string;
  orderNo?: number;
  resourceType: string;
  parentId?: string;
  icon?: string;
  routePath?: string;
  url?: string;
  component?: string;
  hidden?: boolean;
  keepAlive?: boolean;
  permissions?: string;
  noAuth?: boolean;
  status?: number;
  remark?: string;
}

export class ResourcePO extends CrudListPage {
  constructor(page: Page, engine: EngineAdapter) {
    super(page, engine, { entityRoute: 'NopAuthResource-main', entityName: 'NopAuthResource' });
  }

  async goto(): Promise<void> {
    await this.page.goto(`#/NopAuthResource-main`);
    await this.page.waitForLoadState('networkidle');
    await this.waitForList();
  }

  async searchBySite(siteId: string): Promise<void> {
    const filterInput = this.page.locator('input[name^="filter_siteId"]').first();
    const visible = await filterInput.isVisible().catch(() => false);
    if (visible) {
      await filterInput.clear();
      await filterInput.fill(siteId);
    }
    await this.engine.addButton(this.page).click();
    await this.engine.table(this.page).waitFor({ state: 'visible' });
  }

  async selectSite(siteId: string): Promise<void> {
    const dialog = new FormDialog(this.page, this.engine);
    await dialog.selectOption(['siteId'], [siteId]);
    await this.engine.addButton(this.page).click();
    await this.engine.table(this.page).waitFor({ state: 'visible' });
  }

  async fillForm(data: ResourceFormData): Promise<void> {
    const dialog = new FormDialog(this.page, this.engine);
    if (data.resourceId !== undefined) {
      await dialog.setField('resourceId', data.resourceId);
    }
    if (data.siteId !== undefined) {
      await dialog.selectOption(['siteId'], [data.siteId]);
    }
    await dialog.setField('displayName', data.displayName);
    if (data.orderNo !== undefined) {
      await dialog.setField('orderNo', String(data.orderNo));
    }
    await dialog.selectOption(['resourceType'], [data.resourceType]);
    if (data.parentId !== undefined) {
      await dialog.setField('parentId', data.parentId);
    }
    if (data.icon !== undefined) {
      await dialog.setField('icon', data.icon);
    }
    if (data.routePath !== undefined) {
      await dialog.setField('routePath', data.routePath);
    }
    if (data.url !== undefined) {
      await dialog.setField('url', data.url);
    }
    if (data.component !== undefined) {
      await dialog.setField('component', data.component);
    }
    if (data.hidden !== undefined) {
      await this.setCheckbox('hidden', data.hidden);
    }
    if (data.keepAlive !== undefined) {
      await this.setCheckbox('keepAlive', data.keepAlive);
    }
    if (data.permissions !== undefined) {
      await dialog.setField('permissions', data.permissions);
    }
    if (data.noAuth !== undefined) {
      await this.setCheckbox('noAuth', data.noAuth);
    }
    if (data.status !== undefined) {
      await dialog.selectOption(['status'], [String(data.status)]);
    }
    if (data.remark !== undefined) {
      await dialog.setField('remark', data.remark);
    }
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

  async assertResourceExists(resourceId: string): Promise<void> {
    const row = await this.findRowByText(resourceId);
    expect(row).not.toBeNull();
  }

  async assertResourceNotExists(resourceId: string): Promise<void> {
    const row = await this.findRowByText(resourceId);
    expect(row).toBeNull();
  }

  async createResource(data: ResourceFormData): Promise<void> {
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

export { ResourcePO as default };

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
    await this.search('siteId', siteId);
  }

  async selectSite(siteId: string): Promise<void> {
    const dialog = new FormDialog(this.page, this.engine);
    await dialog.setField('siteId', siteId);
    await this.engine.refreshButton(this.page).click();
    await this.page.keyboard.press('Enter');
    await this.engine.table(this.page).waitFor({ state: 'visible' });
  }

  async fillForm(data: ResourceFormData): Promise<void> {
    const dialog = new FormDialog(this.page, this.engine);
    if (data.resourceId !== undefined) await dialog.setField('resourceId', data.resourceId);
    if (data.siteId !== undefined) await dialog.setField('siteId', data.siteId);
    await dialog.setField('displayName', data.displayName);
    if (data.orderNo !== undefined) await dialog.setField('orderNo', String(data.orderNo));
    await dialog.setField('resourceType', data.resourceType);
    if (data.parentId !== undefined) await dialog.setField('parentId', data.parentId);
    if (data.icon !== undefined) await dialog.setField('icon', data.icon);
    if (data.routePath !== undefined) await dialog.setField('routePath', data.routePath);
    if (data.url !== undefined) await dialog.setField('url', data.url);
    if (data.component !== undefined) await dialog.setField('component', data.component);
    if (data.hidden !== undefined) await dialog.setField('hidden', data.hidden);
    if (data.keepAlive !== undefined) await dialog.setField('keepAlive', data.keepAlive);
    if (data.permissions !== undefined) await dialog.setField('permissions', data.permissions);
    if (data.noAuth !== undefined) await dialog.setField('noAuth', data.noAuth);
    if (data.status !== undefined) await dialog.setField('status', String(data.status));
    if (data.remark !== undefined) await dialog.setField('remark', data.remark);
  }

  async assertResourceExists(resourceId: string): Promise<void> {
    await this.assertEntityExists(resourceId);
  }

  async assertResourceNotExists(resourceId: string): Promise<void> {
    await this.assertEntityNotExists(resourceId);
  }

  async createResource(data: ResourceFormData): Promise<void> {
    await this.clickAdd();
    await this.fillForm(data);
    await this.clickSave();
  }
}

export { ResourcePO as default };

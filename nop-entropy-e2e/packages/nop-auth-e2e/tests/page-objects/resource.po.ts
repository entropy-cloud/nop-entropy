import { type Page } from '@playwright/test';
import { CrudListPage } from '@nop-entropy/e2e-shared';
import type { EngineAdapter, FormData } from '@nop-entropy/e2e-shared';

export type ResourceFormData = FormData;

export class ResourcePO extends CrudListPage {
  constructor(page: Page, engine: EngineAdapter) {
    super(page, engine, {
      entityRoute: 'NopAuthResource-main',
      entityName: 'NopAuthResource',
      columnHeaders: [
        'resourceId',
        'displayName',
        'resourceType',
        'icon',
        'routePath',
        'url',
        'component',
        'target',
        'hidden',
        'keepAlive',
        'noAuth',
        'status',
        'authCascadeUp',
        'remark',
      ],
    });
  }

  async goto(): Promise<void> {
    await super.goto('NopAuthResource-main');
    await this.waitForListWithReload();
  }

  async searchBySite(siteId: string): Promise<void> {
    await this.search('siteId', siteId);
  }

  async createResource(data: ResourceFormData): Promise<void> {
    await this.clickAdd();
    await this.fillDialog(data);
    await this.clickSave();
  }

  async editResource(rowIdentifier: string, changes: ResourceFormData): Promise<void> {
    await this.clickEdit(rowIdentifier);
    await this.fillDialog(changes);
    await this.clickSave();
  }

  async viewResource(rowIdentifier: string): Promise<void> {
    await this.clickView(rowIdentifier);
  }

  async deleteResource(rowIdentifier: string): Promise<void> {
    await this.clickDelete(rowIdentifier);
  }

  async assertResourceExists(resourceId: string): Promise<void> {
    await this.assertEntityExists(resourceId);
  }

  async assertResourceNotExists(resourceId: string): Promise<void> {
    await this.assertEntityNotExists(resourceId);
  }
}

export { ResourcePO as default };

/**
 * Page Object for the NopAuthResource entity CRUD page (tree table).
 *
 * URL pattern: /#/NopAuthResource-main
 *
 * NopAuthResource is a tree-structured entity displayed as a tree table.
 * Grid columns: resourceId, siteId, displayName, orderNo, resourceType,
 *               parentId, icon, routePath, url, component, status
 */
import { expect, type Page } from '@playwright/test';
import {
  AmisCrudPage,
  fillModalField,
  readModalField,
  selectOption,
  clickButton,
  waitForTableLoad,
  getTableRowCount,
} from '@nop-entropy/e2e-shared';

/** Shape of the NopAuthResource form data used for create/edit. */
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

export class ResourcePO extends AmisCrudPage {
  override get entityName(): string {
    return 'NopAuthResource';
  }

  constructor(page: Page) {
    super(page);
  }

  // ── Query / search ────────────────────────────────────────────────────

  /**
   * Search resources by siteId using the query form.
   *
   * The siteId field uses a button-group-select or dropdown control
   * linked to the `obj/NopAuthSite` dictionary.
   */
  async searchBySite(siteId: string): Promise<void> {
    await this.search('siteId', siteId);
  }

  /**
   * Select a site in the query form's button-group-select control.
   *
   * This targets the siteId filter as a select-type control rather than
   * a plain text input.
   */
  async selectSite(siteId: string): Promise<void> {
    await selectOption(this.page, 'siteId', siteId);
    await clickButton(this.page, '搜索');
    await waitForTableLoad(this.page);
  }

  // ── CRUD operations ───────────────────────────────────────────────────

  /**
   * Fill the add/edit form fields for a resource.
   *
   * Only fills fields present in `data`. Dict-backed fields (siteId,
   * resourceType, status) use select controls. Boolean fields use
   * checkbox interactions.
   */
  async fillForm(data: ResourceFormData): Promise<void> {
    if (data.resourceId !== undefined) {
      await fillModalField(this.page, 'resourceId', data.resourceId);
    }
    if (data.siteId !== undefined) {
      await selectOption(this.page, 'siteId', data.siteId);
    }
    await fillModalField(this.page, 'displayName', data.displayName);
    if (data.orderNo !== undefined) {
      await fillModalField(this.page, 'orderNo', String(data.orderNo));
    }
    await selectOption(this.page, 'resourceType', data.resourceType);
    if (data.parentId !== undefined) {
      await fillModalField(this.page, 'parentId', data.parentId);
    }
    if (data.icon !== undefined) {
      await fillModalField(this.page, 'icon', data.icon);
    }
    if (data.routePath !== undefined) {
      await fillModalField(this.page, 'routePath', data.routePath);
    }
    if (data.url !== undefined) {
      await fillModalField(this.page, 'url', data.url);
    }
    if (data.component !== undefined) {
      await fillModalField(this.page, 'component', data.component);
    }
    if (data.hidden !== undefined) {
      await this.setCheckbox('hidden', data.hidden);
    }
    if (data.keepAlive !== undefined) {
      await this.setCheckbox('keepAlive', data.keepAlive);
    }
    if (data.permissions !== undefined) {
      await fillModalField(this.page, 'permissions', data.permissions);
    }
    if (data.noAuth !== undefined) {
      await this.setCheckbox('noAuth', data.noAuth);
    }
    if (data.status !== undefined) {
      await selectOption(this.page, 'status', data.status);
    }
    if (data.remark !== undefined) {
      await fillModalField(this.page, 'remark', data.remark);
    }
  }

  // ── View modal helpers ────────────────────────────────────────────────

  /**
   * Read the display value of a field from the currently open view modal.
   *
   * @param fieldName - The AMIS field name (e.g. `'resourceId'`, `'displayName'`).
   * @returns The field's current display value.
   */
  async readViewField(fieldName: string): Promise<string> {
    return readModalField(this.page, fieldName);
  }

  // ── Assertions ────────────────────────────────────────────────────────

  /**
   * Assert that a resource row with the given resourceId exists in the
   * tree table.
   */
  async assertResourceExists(resourceId: string): Promise<void> {
    const row = this.page
      .locator('tr')
      .filter({ hasText: resourceId })
      .first();
    await expect(row).toBeVisible();
  }

  /**
   * Assert that no resource row with the given resourceId exists in the
   * tree table.
   */
  async assertResourceNotExists(resourceId: string): Promise<void> {
    const row = this.page
      .locator('tr')
      .filter({ hasText: resourceId })
      .first();
    await expect(row).toHaveCount(0);
  }

  // ── Composite / end-to-end helpers ────────────────────────────────────

  /**
   * Create a resource end-to-end: click add, fill the form, and save.
   *
   * Only the mandatory fields (`displayName`, `resourceType`) must be
   * provided; all other fields are optional.
   */
  async createResource(data: ResourceFormData): Promise<void> {
    await this.clickAdd();
    await this.fillForm(data);
    await this.clickSave();
  }

  /** Get the number of data rows currently visible in the table. */
  async getTableRowCount(): Promise<number> {
    return getTableRowCount(this.page);
  }

  // ── Internal helpers ──────────────────────────────────────────────────

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

export { ResourcePO as default };

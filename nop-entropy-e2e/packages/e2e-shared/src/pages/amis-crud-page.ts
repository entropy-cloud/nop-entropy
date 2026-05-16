import type { Page } from '@playwright/test';
import { BasePage } from './base-page.js';
import { fillField } from '../helpers/form-helper.js';
import { clickButton, confirmDialog, clickRowAction } from '../helpers/button-helper.js';
import { waitForModal } from '../helpers/modal-helper.js';
import { waitForTableLoad } from '../helpers/table-helper.js';
import { AMIS } from '../helpers/amis-selectors.js';

export abstract class AmisCrudPage extends BasePage {
  constructor(page: Page) {
    super(page);
  }

  async search(fieldName: string, value: string): Promise<void> {
    await fillField(this.page, fieldName, value, { inFilter: true });
    await clickButton(this.page, '搜索');
    await waitForTableLoad(this.page);
  }

  async clickAdd(): Promise<void> {
    await clickButton(this.page, '新增');
    await waitForModal(this.page);
  }

  async clickSave(): Promise<void> {
    await clickButton(this.page, '确认');
    const modal = this.page.locator(AMIS.MODAL).first();
    await modal.waitFor({ state: 'hidden', timeout: 10_000 }).catch(async () => {
      // fallback: dismiss via Escape
      await this.page.keyboard.press('Escape');
      await this.page.waitForTimeout(500);
      await modal.waitFor({ state: 'hidden', timeout: 5_000 }).catch(() => {});
    });
    await waitForTableLoad(this.page);
  }

  async clickView(rowId: string): Promise<void> {
    await clickRowAction(this.page, rowId, '查看');
    await waitForModal(this.page);
  }

  async clickEdit(rowId: string): Promise<void> {
    await clickRowAction(this.page, rowId, '编辑');
    await waitForModal(this.page);
  }

  async clickDelete(rowId: string): Promise<void> {
    await clickRowAction(this.page, rowId, '删除');
    await confirmDialog(this.page);
    await waitForTableLoad(this.page);
  }
}

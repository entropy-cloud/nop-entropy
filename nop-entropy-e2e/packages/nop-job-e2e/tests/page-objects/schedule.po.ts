import { type Page } from '@playwright/test';
import { CrudListPage, FormDialog } from '@nop-entropy/e2e-shared';
import type { EngineAdapter } from '@nop-entropy/e2e-shared';

export const E2E_EXECUTOR_REF = 'nopE2eTestInvoker';
export const E2E_JOB_PREFIX = 'e2e_job_';

export interface ScheduleFormData {
  jobName: string;
  displayName: string;
  executorKind?: number;
  executorRef: string;
  triggerType?: number;
  cronExpr?: string;
  repeatIntervalMs?: number;
  scheduleStatus?: number;
  blockStrategy?: number;
  timeoutSeconds?: number;
  partitionIndex?: number;
  retryPolicyId?: string;
  namespaceId?: string;
  groupId?: string;
  remark?: string;
}

export class SchedulePO extends CrudListPage {
  constructor(page: Page, engine: EngineAdapter) {
    super(page, engine, { entityRoute: 'NopJobSchedule-main', entityName: 'NopJobSchedule' });
  }

  async goto(): Promise<void> {
    await this.page.goto(`#/NopJobSchedule-main`);
    await this.page.waitForLoadState('networkidle');
    await this.waitForList();
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

  async searchSchedule(jobName: string): Promise<void> {
    const filterInput = this.page.locator('input[name^="filter_jobName"]').first();
    const visible = await filterInput.isVisible().catch(() => false);
    if (visible) {
      await filterInput.clear();
      await filterInput.fill(jobName);
    }
    await this.engine.addButton(this.page).click();
    await this.engine.table(this.page).waitFor({ state: 'visible' });
  }

  async fillAddForm(data: ScheduleFormData): Promise<void> {
    const dialog = new FormDialog(this.page, this.engine);
    await dialog.setField('jobName', data.jobName);
    await dialog.setField('displayName', data.displayName);
    if (data.executorKind !== undefined) {
      await dialog.selectOption(['executorKind'], [String(data.executorKind)]);
    }
    await dialog.setField('executorRef', data.executorRef);
    if (data.triggerType !== undefined) {
      await dialog.selectOption(['triggerType'], [String(data.triggerType)]);
    }
    if (data.cronExpr) {
      await dialog.setField('cronExpr', data.cronExpr);
    }
    if (data.scheduleStatus !== undefined) {
      await dialog.selectOption(['scheduleStatus'], [String(data.scheduleStatus)]);
    }
    if (data.blockStrategy !== undefined) {
      await dialog.selectOption(['blockStrategy'], [String(data.blockStrategy)]);
    }
    if (data.timeoutSeconds !== undefined) {
      await dialog.setField('timeoutSeconds', String(data.timeoutSeconds));
    }
    if (data.retryPolicyId) {
      await dialog.setField('retryPolicyId', data.retryPolicyId);
    }
    if (data.namespaceId) {
      await dialog.setField('namespaceId', data.namespaceId);
    }
    if (data.groupId) {
      await dialog.setField('groupId', data.groupId);
    }
    if (data.remark) {
      await dialog.setField('remark', data.remark);
    }
  }

  async readField(fieldName: string): Promise<string> {
    const dialog = new FormDialog(this.page, this.engine);
    return dialog.getField(fieldName);
  }

  async triggerNow(rowIdentifier: string): Promise<void> {
    const row = await this.findRowByText(rowIdentifier);
    if (row) {
      await this.engine.rowAction(row, /立即触发/);
    }
    const confirmBtn = this.page.locator('button:has-text("确定"), button:has-text("确认")').first();
    await confirmBtn.click();
    await this.waitForList();
  }

  async enableSchedule(rowIdentifier: string): Promise<void> {
    const row = await this.findRowByText(rowIdentifier);
    if (row) {
      await this.engine.rowAction(row, /启用调度/);
    }
    const confirmBtn = this.page.locator('button:has-text("确定"), button:has-text("确认")').first();
    await confirmBtn.click();
    await this.waitForList();
  }

  async disableSchedule(rowIdentifier: string): Promise<void> {
    const row = await this.findRowByText(rowIdentifier);
    if (row) {
      await this.engine.rowAction(row, /禁用调度/);
    }
    const confirmBtn = this.page.locator('button:has-text("确定"), button:has-text("确认")').first();
    await confirmBtn.click();
    await this.waitForList();
  }

  async pauseSchedule(rowIdentifier: string): Promise<void> {
    const row = await this.findRowByText(rowIdentifier);
    if (row) {
      await this.engine.rowAction(row, /暂停调度/);
    }
    const confirmBtn = this.page.locator('button:has-text("确定"), button:has-text("确认")').first();
    await confirmBtn.click();
    await this.waitForList();
  }

  async resumeSchedule(rowIdentifier: string): Promise<void> {
    const row = await this.findRowByText(rowIdentifier);
    if (row) {
      await this.engine.rowAction(row, /恢复调度/);
    }
    const confirmBtn = this.page.locator('button:has-text("确定"), button:has-text("确认")').first();
    await confirmBtn.click();
    await this.waitForList();
  }

  async archiveSchedule(rowIdentifier: string): Promise<void> {
    const row = await this.findRowByText(rowIdentifier);
    if (row) {
      await this.engine.rowAction(row, /归档调度/);
    }
    const confirmBtn = this.page.locator('button:has-text("确定"), button:has-text("确认")').first();
    await confirmBtn.click();
    await this.waitForList();
  }

  async getRowCount(): Promise<number> {
    return this.engine.rows(this.page).count();
  }

  async waitForRow(rowIdentifier: string): Promise<void> {
    const row = await this.findRowByText(rowIdentifier);
    if (row) {
      await row.waitFor({ state: 'visible', timeout: 15_000 });
    }
  }
}

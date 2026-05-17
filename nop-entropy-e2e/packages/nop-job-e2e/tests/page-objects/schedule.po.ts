import { type Page } from '@playwright/test';
import {
  AmisCrudPage,
  fillModalField,
  readModalField,
  selectOption,
  waitForTableLoad,
  getTableRowCount,
  clickInRow,
  confirmDialog,
} from '@nop-entropy/e2e-shared';

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
  remark?: string;
}

export class SchedulePO extends AmisCrudPage {
  override get entityName(): string {
    return 'NopJobSchedule';
  }

  constructor(page: Page) {
    super(page);
  }

  async searchSchedule(jobName: string): Promise<void> {
    await this.search('jobName', jobName);
  }

  async fillAddForm(data: ScheduleFormData): Promise<void> {
    await fillModalField(this.page, 'jobName', data.jobName);
    await fillModalField(this.page, 'displayName', data.displayName);
    if (data.executorKind !== undefined) {
      await selectOption(this.page, 'executorKind', data.executorKind);
    }
    await fillModalField(this.page, 'executorRef', data.executorRef);
    if (data.triggerType !== undefined) {
      await selectOption(this.page, 'triggerType', data.triggerType);
    }
    if (data.cronExpr) {
      await fillModalField(this.page, 'cronExpr', data.cronExpr);
    }
    if (data.scheduleStatus !== undefined) {
      await selectOption(this.page, 'scheduleStatus', data.scheduleStatus);
    }
    if (data.blockStrategy !== undefined) {
      await selectOption(this.page, 'blockStrategy', data.blockStrategy);
    }
    if (data.timeoutSeconds !== undefined) {
      await fillModalField(this.page, 'timeoutSeconds', String(data.timeoutSeconds));
    }
    if (data.remark) {
      await fillModalField(this.page, 'remark', data.remark);
    }
  }

  async readField(fieldName: string): Promise<string> {
    return readModalField(this.page, fieldName);
  }

  async triggerNow(rowIdentifier: string): Promise<void> {
    await clickInRow(this.page, rowIdentifier, '立即触发');
    await confirmDialog(this.page);
    await waitForTableLoad(this.page);
  }

  async enableSchedule(rowIdentifier: string): Promise<void> {
    await clickInRow(this.page, rowIdentifier, '启用调度');
    await confirmDialog(this.page);
    await waitForTableLoad(this.page);
  }

  async disableSchedule(rowIdentifier: string): Promise<void> {
    await clickInRow(this.page, rowIdentifier, '禁用调度');
    await confirmDialog(this.page);
    await waitForTableLoad(this.page);
  }

  async pauseSchedule(rowIdentifier: string): Promise<void> {
    await clickInRow(this.page, rowIdentifier, '暂停调度');
    await confirmDialog(this.page);
    await waitForTableLoad(this.page);
  }

  async resumeSchedule(rowIdentifier: string): Promise<void> {
    await clickInRow(this.page, rowIdentifier, '恢复调度');
    await confirmDialog(this.page);
    await waitForTableLoad(this.page);
  }

  async archiveSchedule(rowIdentifier: string): Promise<void> {
    await clickInRow(this.page, rowIdentifier, '归档调度');
    await confirmDialog(this.page);
    await waitForTableLoad(this.page);
  }

  async getRowCount(): Promise<number> {
    return getTableRowCount(this.page);
  }

  async waitForRow(rowIdentifier: string): Promise<void> {
    const row = this.page.locator('tbody > tr').filter({ hasText: rowIdentifier }).first();
    await row.waitFor({ state: 'visible', timeout: 15_000 });
  }
}

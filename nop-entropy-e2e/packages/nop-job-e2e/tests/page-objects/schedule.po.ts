import { type Page } from '@playwright/test';
import { CrudListPage, FormDialog } from '@nop-entropy/e2e-shared';
import type { EngineAdapter } from '@nop-entropy/e2e-shared';

// NopJobSchedule 按 executorKind（dict job/executor-kind：test/rpc/rpcPoll）解析 invoker
// （nopJobInvoker_test 等），模型上没有 executorRef 属性
export const E2E_EXECUTOR_KIND = 'test';
export const E2E_JOB_PREFIX = 'e2e_job_';

export interface ScheduleFormData {
  jobName: string;
  displayName: string;
  executorKind?: string;
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
    await super.goto('NopJobSchedule-main');
    await this.page.waitForLoadState('networkidle');
    await this.waitForListWithReload();
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
    const row = await this.findRowDeep(jobName, '作业名');
    if (!row) {
      throw new Error('搜索后未找到行: ' + jobName);
    }
  }

  async fillAddForm(data: ScheduleFormData): Promise<void> {
    const dialog = new FormDialog(this.page, this.engine);
    await dialog.setField('jobName', data.jobName);
    await dialog.setField('displayName', data.displayName);
    if (data.executorKind !== undefined) {
      await dialog.selectOption(['executorKind'], [data.executorKind]);
    }
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
    if (data.partitionIndex !== undefined) {
      // 分区索引是必填项，不填会被 amis 校验挡住、对话框无法提交
      await dialog.setField('partitionIndex', String(data.partitionIndex));
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
    await this.rowActionWithConfirm(rowIdentifier, /立即触发/);
  }

  async enableSchedule(rowIdentifier: string): Promise<void> {
    await this.rowActionWithConfirm(rowIdentifier, /启用调度/);
  }

  async disableSchedule(rowIdentifier: string): Promise<void> {
    await this.rowActionWithConfirm(rowIdentifier, /禁用调度/);
  }

  async pauseSchedule(rowIdentifier: string): Promise<void> {
    await this.rowActionWithConfirm(rowIdentifier, /暂停调度/);
  }

  async resumeSchedule(rowIdentifier: string): Promise<void> {
    await this.rowActionWithConfirm(rowIdentifier, /恢复调度/);
  }

  async archiveSchedule(rowIdentifier: string): Promise<void> {
    await this.rowActionWithConfirm(rowIdentifier, /归档调度/);
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

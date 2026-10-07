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


  /**
   * 尝试点击行操作确认框的确认按钮。确认框由宿主 shell 的 alert-dialog 渲染（按钮为
   * 取消/确认），portal 无状态标记且历史 portal 会残留 DOM——定位 portal 内"可见且
   * 文本恰为确认/确定"的按钮；点击后未随框消失则报错。
   * @returns 确认框是否出现过（未出现 = 行操作没有生效，由调用方决定重试）
   */
  private async tryClickConfirm(): Promise<boolean> {
    // 历史确认框 portal 会残留 DOM 且 DOM 顺序在前——取最后一个（最新挂载）的确认按钮
    const btn = this.page
      .locator('[data-slot="alert-dialog-portal"] button:visible')
      .filter({ hasText: /^(确认|确定)$/ })
      .last();
    const appeared = await btn.waitFor({ state: 'visible', timeout: 5_000 })
      .then(() => true)
      .catch(() => false);
    if (!appeared) return false;

    // 首次点击可能落在 amis 重渲染窗口期被吞掉——点击后验证框已消失，未消失则重试
    for (let attempt = 0; attempt < 3; attempt++) {
      await btn.click();
      const closed = await btn.waitFor({ state: 'hidden', timeout: 5_000 })
        .then(() => true)
        .catch(() => false);
      if (closed) return true;
      await this.page.waitForTimeout(500);
    }
    throw new Error('行操作确认框点击后未关闭');
  }

  /**
   * 行操作 + 确认框的整体封装。下拉菜单项点击偶发落空（菜单关闭时指针下的元素已
   * 更替），此时确认框不会出现——整体重试行操作而不是静默放行，否则状态迁移没有
   * 发生，后续断言/轮询只会收到难以定位的超时。
   */
  private async rowActionWithConfirm(rowIdentifier: string, actionNamePattern: RegExp): Promise<void> {
    const row = await this.findRowByText(rowIdentifier);
    if (!row) {
      throw new Error('未找到行: ' + rowIdentifier);
    }
    for (let attempt = 0; attempt < 2; attempt++) {
      await this.engine.rowAction(row, actionNamePattern);
      if (await this.tryClickConfirm()) {
        await this.waitForList();
        return;
      }
      await this.page.waitForTimeout(800);
    }
    throw new Error('行操作未能触发确认框: ' + actionNamePattern);
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

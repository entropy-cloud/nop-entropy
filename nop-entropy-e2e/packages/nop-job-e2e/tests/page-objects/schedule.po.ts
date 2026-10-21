import { type Page } from '@playwright/test';
import { CrudListPage, FormDialog } from '@nop-entropy/e2e-shared';
import type { EngineAdapter } from '@nop-entropy/e2e-shared';

// NopJobSchedule 按 executorKind（dict job/executor-kind：test/rpc/rpcPoll）解析 invoker
// （nopJobInvoker_test 等），模型上没有 executorRef 属性
export const E2E_EXECUTOR_KIND = 'test';
export const E2E_JOB_PREFIX = 'e2e_job_';

/** 调度状态（模型 fieldName 语义）。 */
export const SCHEDULE_STATUS = {
  DISABLED: 0,
  ENABLED: 10,
  PAUSED: 20,
  ARCHIVED: 40,
} as const;

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
    await this.waitForListWithReload();
  }

  async clickSave(): Promise<void> {
    await super.clickSave();
  }

  /**
   * 新建调度。
   *
   * 字典选项偶发「加载选项失败」（dict effect abort 竞态，见
   * ai-dev/bugs/2026-10/2026-10-18-readonly-guard-npe-and-flux-crud-guard-e2e-flakes.md C）
   * 时字段上的 errorMessage 不会自愈——关窗重开让表单重挂载再拉一次。
   */
  async createSchedule(data: ScheduleFormData): Promise<void> {
    let lastError: unknown;
    for (let attempt = 0; attempt < 3; attempt++) {
      try {
        await this.clickAdd();
        await this.fillAddForm(data);
        await this.clickSave();
        return;
      } catch (e) {
        lastError = e;
        const msg = e instanceof Error ? e.message : String(e);
        // 仅对「选项没进表单」类失败重开重试；业务校验等其它错误直接抛出
        if (!/组合框选择失败|加载选项失败/.test(msg)) throw e;
        await new FormDialog(this.page, this.engine)
          .cancel()
          .catch(() => {});
        await this.page.waitForTimeout(500);
      }
    }
    throw lastError;
  }

  /** 跨页定位调度（刷新 → 每页条数调最大 → 排序 → 逐页翻）。 */
  async searchSchedule(jobName: string): Promise<void> {
    await this.findRowDeepOrFail(jobName, '作业名');
  }

  async triggerNow(jobName: string): Promise<void> {
    await this.confirmRowAction(jobName, /立即触发/);
  }

  async enableSchedule(jobName: string): Promise<void> {
    await this.confirmRowAction(jobName, /启用调度/);
  }

  async disableSchedule(jobName: string): Promise<void> {
    await this.confirmRowAction(jobName, /禁用调度/);
  }

  async pauseSchedule(jobName: string): Promise<void> {
    await this.confirmRowAction(jobName, /暂停调度/);
  }

  async resumeSchedule(jobName: string): Promise<void> {
    await this.confirmRowAction(jobName, /恢复调度/);
  }

  async archiveSchedule(jobName: string): Promise<void> {
    await this.confirmRowAction(jobName, /归档调度/);
  }

  async getRowCount(): Promise<number> {
    return this.getTableRowCount();
  }

  async waitForRow(rowIdentifier: string): Promise<void> {
    await super.waitForRow(rowIdentifier);
  }

  /**
   * 新增/编辑调度表单的字段填写。分区索引是必填项，不填会被校验挡住、
   * 对话框无法提交，因此默认值由 PO 兜底。
   */
  async fillAddForm(data: ScheduleFormData): Promise<void> {
    await this.fillDialog({
      jobName: data.jobName,
      displayName: data.displayName,
      partitionIndex: data.partitionIndex ?? 0,
    });

    if (data.executorKind !== undefined) {
      await this.selectDialogOption('executorKind', data.executorKind);
    }
    if (data.triggerType !== undefined) {
      await this.selectDialogOption('triggerType', String(data.triggerType));
    }
    if (data.cronExpr) await this.fillDialog({ cronExpr: data.cronExpr });
    if (data.scheduleStatus !== undefined) {
      await this.selectDialogOption('scheduleStatus', String(data.scheduleStatus));
    }
    if (data.blockStrategy !== undefined) {
      await this.selectDialogOption('blockStrategy', String(data.blockStrategy));
    }

    await this.fillDialog({
      ...(data.timeoutSeconds !== undefined ? { timeoutSeconds: String(data.timeoutSeconds) } : {}),
      ...(data.retryPolicyId ? { retryPolicyId: data.retryPolicyId } : {}),
      ...(data.namespaceId ? { namespaceId: data.namespaceId } : {}),
      ...(data.groupId ? { groupId: data.groupId } : {}),
      ...(data.remark ? { remark: data.remark } : {}),
    });
  }
}

export { SchedulePO as default };

import { type Page } from '@playwright/test';
import { CrudListPage } from '@nop-entropy/e2e-shared';
import type { EngineAdapter } from '@nop-entropy/e2e-shared';

export class TaskPO extends CrudListPage {
  constructor(page: Page, engine: EngineAdapter) {
    super(page, engine, {
      entityRoute: 'NopJobTask-main',
      entityName: 'NopJobTask',
      columnHeaders: ['', 'jobFireId', 'taskStatus_label', 'executorInstanceId', 'startTime', 'endTime'],
    });
  }

  async goto(): Promise<void> {
    await super.goto('NopJobTask-main');
    await this.waitForListWithReload();
  }

  /** 跨页定位任务（任务表无过滤表单，按 jobFireId 升序分页）。 */
  async searchTask(jobFireId: string): Promise<void> {
    await this.findRowDeepOrFail(jobFireId);
  }

  /** 打开任务详情（任务表首列即批次ID）。 */
  async viewTask(jobFireId: string): Promise<void> {
    await this.clickViewDeep(jobFireId);
  }

  async getRowCount(): Promise<number> {
    return this.getTableRowCount();
  }
}

export { TaskPO as default };

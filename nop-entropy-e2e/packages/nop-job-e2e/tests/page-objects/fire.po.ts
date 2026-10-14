import { type Page } from '@playwright/test';
import { CrudListPage } from '@nop-entropy/e2e-shared';
import type { EngineAdapter } from '@nop-entropy/e2e-shared';

export class FirePO extends CrudListPage {
  constructor(page: Page, engine: EngineAdapter) {
    super(page, engine, {
      entityRoute: 'NopJobFire-main',
      entityName: 'NopJobFire',
      columnHeaders: [
        '',
        'fireStatus_label',
        'plannerInstanceId',
        'dispatchInstanceId',
        'startTime',
        'endTime',
        'durationMs',
        'errorCode',
        'errorMessage',
      ],
    });
  }

  async goto(): Promise<void> {
    await super.goto('NopJobFire-main');
    await this.waitForListWithReload();
  }

  /** 跨页定位触发批次（默认升序分页，新批次常在末页）。 */
  async searchFire(jobName: string): Promise<void> {
    await this.findRowDeepOrFail(jobName, '作业名');
  }

  /** 打开批次详情（先按作业名跨页定位）。 */
  async viewFire(jobName: string): Promise<void> {
    await this.clickViewDeep(jobName, '作业名');
  }

  async cancelFire(rowIdentifier: string): Promise<void> {
    await this.confirmRowAction(rowIdentifier, /取消触发/);
  }

  async rerunFire(rowIdentifier: string): Promise<void> {
    await this.confirmRowAction(rowIdentifier, /重跑触发/);
  }

  async getRowCount(): Promise<number> {
    return this.getTableRowCount();
  }
}

export { FirePO as default };

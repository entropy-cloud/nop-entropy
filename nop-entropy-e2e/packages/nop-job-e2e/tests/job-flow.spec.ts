import { test } from '@nop-entropy/e2e-shared';
import { expect } from '@playwright/test';
import { login, loginRpc, rpc } from '@nop-entropy/e2e-shared';
import { SchedulePO, E2E_EXECUTOR_KIND, E2E_JOB_PREFIX } from './page-objects/schedule.po.js';
import { seedE2ePermissions, SCHEDULE_PERMISSIONS } from './helpers/perm-seed.js';
import { FirePO } from './page-objects/fire.po.js';
import { TaskPO } from './page-objects/task.po.js';

const TEST_ID = `${E2E_JOB_PREFIX}${Date.now()}`;

const FIRE_STATUS_SUCCESS = 30;
const FIRE_STATUS_FAILED = 40;

const TASK_STATUS_WAITING = 0;
const TASK_STATUS_SUCCESS = 30;

const SCHEDULE_STATUS_DISABLED = 0;
const SCHEDULE_STATUS_ENABLED = 10;
const SCHEDULE_STATUS_PAUSED = 20;
const SCHEDULE_STATUS_ARCHIVED = 40;

interface FireItem { jobFireId: string; jobName: string; fireStatus: number; jobScheduleId: string }
interface TaskItem { jobTaskId: string; jobFireId: string; taskStatus: number }
interface FindPageResult<T> { total: number; items: T[] }

const createdScheduleIds: string[] = [];

// 轮询到 fire 进入终态（>= SUCCESS=30）。"非 WAITING 即返回"会在 RUNNING(20) 提前返回——
// invoker 已返回但完成处理器要等下个扫描周期才落终态（dev 环境 ~15-20s）
async function pollUntilFireTerminal(
  request: import('@playwright/test').APIRequestContext,
  jobScheduleId: string,
  maxAttempts = 30,
  intervalMs = 1500,
): Promise<FireItem | null> {
  for (let i = 0; i < maxAttempts; i++) {
    const resp = await rpc<FindPageResult<FireItem>>(request, 'NopJobFire__findPage', {
      query: {
        offset: 0, limit: 10,
        filter: { $type: 'eq', name: 'jobScheduleId', value: jobScheduleId },
      },
    });
    if (resp.ok && resp.data.total > 0) {
      const fire = resp.data.items[0];
      if (fire.fireStatus >= FIRE_STATUS_SUCCESS) return fire;
    }
    await new Promise((r) => setTimeout(r, intervalMs));
  }
  return null;
}

async function cleanupTestSchedules(
  request: import('@playwright/test').APIRequestContext,
): Promise<void> {
  const resp = await rpc<FindPageResult<{ id: string; jobName: string }>>(
    request, 'NopJobSchedule__findPage', { query: { offset: 0, limit: 200 } },
  );
  if (!resp.ok) return;
  for (const item of resp.data.items) {
    if (item.jobName.startsWith(E2E_JOB_PREFIX)) {
      await rpc(request, 'NopJobSchedule__delete', { id: item.id }).catch(() => {});
    }
  }
}

test.describe('Job 完整生命周期流程', () => {
  test.beforeAll(async ({ request }) => {
    await loginRpc(request);
    // 必须先于任何 NopJobSchedule 页面加载：见 perm-seed.ts 冻结说明
    await seedE2ePermissions(request, SCHEDULE_PERMISSIONS, ['/nop/job/pages/NopJobSchedule/main.page.yaml']);
    await cleanupTestSchedules(request);
  });

  test.afterAll(async ({ request }) => {
    for (const id of createdScheduleIds) {
      await rpc(request, 'NopJobSchedule__delete', { id }).catch(() => {});
    }
    createdScheduleIds.length = 0;
  });

  test('完整流程: 新建调度 → 启用 → 触发 → 查看Fire → 查看Task', async ({ page, request, engine }) => {
    // 建单/启用的确认框交互 + fire 终态轮询（dev 协调器扫描周期 ~15-20s）远超默认 60s
    test.setTimeout(180_000);
    const jobName = `${TEST_ID}_lifecycle`;

    await login(page);

    const schedulePO = new SchedulePO(page, engine);
    await schedulePO.goto();

    await schedulePO.clickAdd();
    await schedulePO.fillAddForm({
      jobName,
      displayName: `E2E生命周期测试`,
      executorKind: E2E_EXECUTOR_KIND,
      triggerType: 4,
      scheduleStatus: SCHEDULE_STATUS_DISABLED,
      blockStrategy: 1,
      partitionIndex: 0,
    });
    await schedulePO.clickSave();

    await schedulePO.searchSchedule(jobName);
    await schedulePO.waitForRow(jobName);
    const rowCount = await schedulePO.getRowCount();
    expect(rowCount).toBeGreaterThanOrEqual(1);

    await schedulePO.enableSchedule(jobName);

    await schedulePO.triggerNow(jobName);

    await loginRpc(request);

    const scheduleResp = await rpc<FindPageResult<{ id: string; jobScheduleId: string; jobName: string }>>(
      request, 'NopJobSchedule__findPage', {
        query: {
          offset: 0, limit: 10,
          filter: { $type: 'eq', name: 'jobName', value: jobName },
        },
      },
    );
    expect(scheduleResp.ok).toBeTruthy();
    expect(scheduleResp.data.total).toBeGreaterThanOrEqual(1);
    const schedule = scheduleResp.data.items[0];
    createdScheduleIds.push(schedule.id);

    const fire = await pollUntilFireTerminal(request, schedule.jobScheduleId);
    expect(fire).not.toBeNull();
    expect([FIRE_STATUS_SUCCESS, FIRE_STATUS_FAILED]).toContain(fire!.fireStatus);

    const firePO = new FirePO(page, engine);
    await firePO.goto();
    await firePO.searchFire(jobName);

    await firePO.waitForRow(jobName);
    // 表格刷新存在时序抖动，轮询等待行数稳定
    await expect
      .poll(() => firePO.getRowCount(), { timeout: 15_000 })
      .toBeGreaterThanOrEqual(1);

    // fire 表格不含 jobFireId 列，按本 run 唯一的 jobName 定位行
    await firePO.clickView(jobName);
    // 混合渲染下查看抽屉的字段为 label/value 静态对——断言状态标签真实渲染
    await expect(page.locator('text=批次状态').first()).toBeVisible();

    const taskListResp = await rpc<FindPageResult<TaskItem>>(request, 'NopJobTask__findPage', {
      query: {
        offset: 0, limit: 10,
        filter: { $type: 'eq', name: 'jobFireId', value: fire!.jobFireId },
      },
    });

    if (taskListResp.ok && taskListResp.data.total > 0) {
      const task = taskListResp.data.items[0];

      const taskPO = new TaskPO(page, engine);
      await taskPO.goto();
      await taskPO.searchTask(fire!.jobFireId);

      await taskPO.waitForRow(fire!.jobFireId);
      const taskRowCount = await taskPO.getRowCount();
      expect(taskRowCount).toBeGreaterThanOrEqual(1);

      // 任务表首列即批次ID（jobFireId），按其定位
      await taskPO.clickView(task.jobFireId);
      await expect(page.locator('text=任务状态').first()).toBeVisible();
    }
  });
});

test.describe('Schedule 状态转换 - UI', () => {
  test.beforeAll(async ({ request }) => {
    await loginRpc(request);
    // 必须先于任何 NopJobSchedule 页面加载：见 perm-seed.ts 冻结说明
    await seedE2ePermissions(request, SCHEDULE_PERMISSIONS, ['/nop/job/pages/NopJobSchedule/main.page.yaml']);
    await cleanupTestSchedules(request);
  });

  test.afterAll(async ({ request }) => {
    for (const id of createdScheduleIds) {
      await rpc(request, 'NopJobSchedule__delete', { id }).catch(() => {});
    }
    createdScheduleIds.length = 0;
  });

  test('状态切换: DISABLED → ENABLED → PAUSED → ENABLED → DISABLED', async ({ page, request, engine }) => {
    const jobName = `${TEST_ID}_status`;

    await login(page);

    const schedulePO = new SchedulePO(page, engine);
    await schedulePO.goto();

    await schedulePO.clickAdd();
    await schedulePO.fillAddForm({
      jobName,
      displayName: `E2E状态切换`,
      executorKind: E2E_EXECUTOR_KIND,
      triggerType: 4,
      scheduleStatus: SCHEDULE_STATUS_DISABLED,
      blockStrategy: 1,
      partitionIndex: 0,
    });
    await schedulePO.clickSave();
    await schedulePO.searchSchedule(jobName);
    await schedulePO.waitForRow(jobName);

    await loginRpc(request);
    const schedResp = await rpc<FindPageResult<{ id: string }>>(request, 'NopJobSchedule__findPage', {
      query: {
        offset: 0, limit: 1,
        filter: { $type: 'eq', name: 'jobName', value: jobName },
      },
    });
    if (schedResp.ok) createdScheduleIds.push(schedResp.data.items[0].id);

    await schedulePO.enableSchedule(jobName);

    await schedulePO.pauseSchedule(jobName);

    await schedulePO.resumeSchedule(jobName);

    await schedulePO.disableSchedule(jobName);
  });
});

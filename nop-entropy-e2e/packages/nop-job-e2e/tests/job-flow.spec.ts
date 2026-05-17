import { test, expect } from '@playwright/test';
import { loginRpc, rpc } from '@nop-entropy/e2e-shared';
import { LoginPO } from './page-objects/login.po.js';
import { SchedulePO, E2E_EXECUTOR_REF, E2E_JOB_PREFIX } from './page-objects/schedule.po.js';
import { FirePO } from './page-objects/fire.po.js';
import { TaskPO } from './page-objects/task.po.js';

const TEST_ID = `${E2E_JOB_PREFIX}${Date.now()}`;

const FIRE_STATUS_WAITING = 0;
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

async function pollUntilFireNotWaiting(
  request: import('@playwright/test').APIRequestContext,
  jobScheduleId: string,
  maxAttempts = 30,
  intervalMs = 2000,
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
      if (fire.fireStatus !== FIRE_STATUS_WAITING) return fire;
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
    await cleanupTestSchedules(request);
  });

  test.afterAll(async ({ request }) => {
    for (const id of createdScheduleIds) {
      await rpc(request, 'NopJobSchedule__delete', { id }).catch(() => {});
    }
    createdScheduleIds.length = 0;
  });

  test('完整流程: 新建调度 → 启用 → 触发 → 查看Fire → 查看Task', async ({ page, request }) => {
    const jobName = `${TEST_ID}_lifecycle`;

    // ===== Step 1: Login via UI =====
    const loginPO = new LoginPO(page);
    await loginPO.goto();
    await loginPO.login();

    // ===== Step 2: Create schedule via UI =====
    const schedulePO = new SchedulePO(page);
    await schedulePO.goto();

    await schedulePO.clickAdd();
    await schedulePO.fillAddForm({
      jobName,
      displayName: `E2E生命周期测试`,
      executorRef: E2E_EXECUTOR_REF,
      executorKind: 1,
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

    // ===== Step 3: Enable schedule via UI (row action) =====
    await schedulePO.enableSchedule(jobName);

    // ===== Step 4: Trigger via UI (row action) =====
    await schedulePO.triggerNow(jobName);

    // ===== Step 5: Poll fire via RPC until not WAITING =====
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

    const fire = await pollUntilFireNotWaiting(request, schedule.jobScheduleId);
    expect(fire).not.toBeNull();
    expect([FIRE_STATUS_SUCCESS, FIRE_STATUS_FAILED]).toContain(fire!.fireStatus);

    // ===== Step 6: Verify Fire via UI =====
    const firePO = new FirePO(page);
    await firePO.goto();
    await firePO.searchFire(jobName);

    await firePO.waitForRow(jobName);
    const fireRowCount = await firePO.getRowCount();
    expect(fireRowCount).toBeGreaterThanOrEqual(1);

    await firePO.clickView(fire!.jobFireId);
    const fireStatusText = await firePO.readField('fireStatus');
    expect(fireStatusText).toBeTruthy();

    // ===== Step 7: Verify Task via UI =====
    const taskListResp = await rpc<FindPageResult<TaskItem>>(request, 'NopJobTask__findPage', {
      query: {
        offset: 0, limit: 10,
        filter: { $type: 'eq', name: 'jobFireId', value: fire!.jobFireId },
      },
    });

    if (taskListResp.ok && taskListResp.data.total > 0) {
      const task = taskListResp.data.items[0];

      const taskPO = new TaskPO(page);
      await taskPO.goto();
      await taskPO.searchTask(fire!.jobFireId);

      await taskPO.waitForRow(fire!.jobFireId);
      const taskRowCount = await taskPO.getRowCount();
      expect(taskRowCount).toBeGreaterThanOrEqual(1);

      await taskPO.clickView(task.jobTaskId);
      const taskStatusText = await taskPO.readField('taskStatus');
      expect(taskStatusText).toBeTruthy();
    }
  });
});

test.describe('Schedule 状态转换 - UI', () => {
  test.beforeAll(async ({ request }) => {
    await loginRpc(request);
    await cleanupTestSchedules(request);
  });

  test.afterAll(async ({ request }) => {
    for (const id of createdScheduleIds) {
      await rpc(request, 'NopJobSchedule__delete', { id }).catch(() => {});
    }
    createdScheduleIds.length = 0;
  });

  test('状态切换: DISABLED → ENABLED → PAUSED → ENABLED → DISABLED', async ({ page, request }) => {
    const jobName = `${TEST_ID}_status`;

    const loginPO = new LoginPO(page);
    await loginPO.goto();
    await loginPO.login();

    const schedulePO = new SchedulePO(page);
    await schedulePO.goto();

    await schedulePO.clickAdd();
    await schedulePO.fillAddForm({
      jobName,
      displayName: `E2E状态切换`,
      executorRef: E2E_EXECUTOR_REF,
      executorKind: 1,
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

    // ENABLED
    await schedulePO.enableSchedule(jobName);

    // PAUSED
    await schedulePO.pauseSchedule(jobName);

    // back to ENABLED
    await schedulePO.resumeSchedule(jobName);

    // back to DISABLED
    await schedulePO.disableSchedule(jobName);
  });
});

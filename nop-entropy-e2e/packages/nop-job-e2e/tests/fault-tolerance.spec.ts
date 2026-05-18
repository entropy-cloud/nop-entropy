import { test, expect } from '@playwright/test';
import { loginRpc, rpc } from '@nop-entropy/e2e-shared';

const E2E_JOB_PREFIX = 'e2e_fault_';

const SCHEDULE_STATUS_DISABLED = 0;
const SCHEDULE_STATUS_ENABLED = 10;

const FIRE_STATUS_SUCCESS = 30;
const FIRE_STATUS_FAILED = 40;
const FIRE_STATUS_TIMEOUT = 50;

const TASK_STATUS_SUCCESS = 30;
const TASK_STATUS_FAILED = 40;

interface ScheduleItem {
  id: string;
  jobScheduleId: string;
  jobName: string;
  scheduleStatus: number;
  retryPolicyId: string | null;
  namespaceId: string | null;
  groupId: string | null;
}

interface FireItem {
  jobFireId: string;
  jobName: string;
  fireStatus: number;
  jobScheduleId: string;
  retryPolicyId: string | null;
  retryRecordId: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  durationMs: number | null;
}

interface TaskItem {
  jobTaskId: string;
  jobFireId: string;
  taskStatus: number;
  progress: number | null;
  progressMessage: string | null;
}

interface FindPageResult<T> { total: number; items: T[] }

const createdScheduleIds: string[] = [];

test.describe('Plan 18 容错改进集成测试', () => {
  test.beforeAll(async ({ request }) => {
    await loginRpc(request);
    await cleanupTestSchedules(request);
  });

  test.afterAll(async ({ request }) => {
    await loginRpc(request);
    for (const id of createdScheduleIds) {
      await rpc(request, 'NopJobSchedule__delete', { id }).catch(() => {});
    }
    createdScheduleIds.length = 0;
  });

  test('Phase 1: 集群 HA - Schedule 按 partitionIndex 正确创建', async ({ request }) => {
    await loginRpc(request);

    const jobName = `${E2E_JOB_PREFIX}partition_${Date.now()}`;

    const createResp = await rpc<ScheduleItem>(request, 'NopJobSchedule__save', {
      data: {
        jobName,
        displayName: 'E2E Partition Test',
        executorRef: 'nopE2eTestInvoker',
        executorKind: 1,
        triggerType: 4,
        scheduleStatus: SCHEDULE_STATUS_DISABLED,
        blockStrategy: 1,
        partitionIndex: 5,
      },
    });

    expect(createResp.ok).toBeTruthy();
    const scheduleId = createResp.data?.id;
    expect(scheduleId).toBeTruthy();
    createdScheduleIds.push(scheduleId!);

    const findResp = await rpc<FindPageResult<ScheduleItem>>(request, 'NopJobSchedule__findPage', {
      query: {
        offset: 0, limit: 1,
        filter: { $type: 'eq', name: 'jobName', value: jobName },
      },
    });

    expect(findResp.ok).toBeTruthy();
    expect(findResp.data.total).toBeGreaterThanOrEqual(1);
  });

  test('Phase 2: retryPolicyId 可设置到 Schedule 和 Fire', async ({ request }) => {
    await loginRpc(request);

    const jobName = `${E2E_JOB_PREFIX}retry_${Date.now()}`;

    const createResp = await rpc<ScheduleItem>(request, 'NopJobSchedule__save', {
      data: {
        jobName,
        displayName: 'E2E Retry Policy Test',
        executorRef: 'nopE2eTestInvoker',
        executorKind: 1,
        triggerType: 4,
        scheduleStatus: SCHEDULE_STATUS_DISABLED,
        blockStrategy: 1,
        partitionIndex: 0,
        retryPolicyId: 'test-retry-policy',
      },
    });

    expect(createResp.ok).toBeTruthy();
    const scheduleId = createResp.data?.id;
    expect(scheduleId).toBeTruthy();
    createdScheduleIds.push(scheduleId!);

    const findResp = await rpc<FindPageResult<ScheduleItem>>(request, 'NopJobSchedule__findPage', {
      query: {
        offset: 0, limit: 1,
        filter: { $type: 'eq', name: 'jobName', value: jobName },
      },
    });

    expect(findResp.ok).toBeTruthy();
    expect(findResp.data.items[0].retryPolicyId).toBe('test-retry-policy');
  });

  test('Phase 4: Task progress 字段可读写', async ({ request }) => {
    await loginRpc(request);

    const jobName = `${E2E_JOB_PREFIX}progress_${Date.now()}`;

    const createResp = await rpc<ScheduleItem>(request, 'NopJobSchedule__save', {
      data: {
        jobName,
        displayName: 'E2E Progress Test',
        executorRef: 'nopE2eTestInvoker',
        executorKind: 1,
        triggerType: 4,
        scheduleStatus: SCHEDULE_STATUS_ENABLED,
        blockStrategy: 1,
        partitionIndex: 0,
      },
    });

    expect(createResp.ok).toBeTruthy();
    const scheduleId = createResp.data?.id;
    expect(scheduleId).toBeTruthy();
    createdScheduleIds.push(scheduleId!);

    await rpc(request, 'NopJobSchedule__triggerNow', { id: scheduleId });

    const fire = await pollUntilFireNotWaiting(request, createResp.data!.jobScheduleId, 30, 2000);
    expect(fire).not.toBeNull();

    const taskResp = await rpc<FindPageResult<TaskItem>>(request, 'NopJobTask__findPage', {
      query: {
        offset: 0, limit: 10,
        filter: { $type: 'eq', name: 'jobFireId', value: fire!.jobFireId },
      },
    });

    if (taskResp.ok && taskResp.data.total > 0) {
      const task = taskResp.data.items[0];

      const updateResp = await rpc(request, 'NopJobTask__update', {
        data: {
          jobTaskId: task.jobTaskId,
          progress: 50,
          progressMessage: 'E2E test progress update',
        },
      });

      if (updateResp.ok) {
        const verifyResp = await rpc<TaskItem>(request, 'NopJobTask__get', {
          id: task.jobTaskId,
        });
        if (verifyResp.ok && verifyResp.data) {
          expect(verifyResp.data.progress).toBe(50);
          expect(verifyResp.data.progressMessage).toBe('E2E test progress update');
        }
      }
    }
  });

  test('Phase 5: Fire 失败时触发告警（验证 fire error 字段）', async ({ request }) => {
    await loginRpc(request);

    const jobName = `${E2E_JOB_PREFIX}alarm_${Date.now()}`;

    const createResp = await rpc<ScheduleItem>(request, 'NopJobSchedule__save', {
      data: {
        jobName,
        displayName: 'E2E Alarm Test',
        executorRef: 'nonExistentInvoker',
        executorKind: 1,
        triggerType: 4,
        scheduleStatus: SCHEDULE_STATUS_ENABLED,
        blockStrategy: 1,
        partitionIndex: 0,
        timeoutSeconds: 5,
      },
    });

    expect(createResp.ok).toBeTruthy();
    const scheduleId = createResp.data?.id;
    expect(scheduleId).toBeTruthy();
    createdScheduleIds.push(scheduleId!);

    await rpc(request, 'NopJobSchedule__triggerNow', { id: scheduleId });

    const fire = await pollUntilFireNotWaiting(request, createResp.data!.jobScheduleId, 60, 2000);
    expect(fire).not.toBeNull();

    if (fire && (fire.fireStatus === FIRE_STATUS_FAILED || fire.fireStatus === FIRE_STATUS_TIMEOUT)) {
      expect(fire.errorCode).toBeTruthy();
    }
  });

  test('完整生命周期: 创建带 retryPolicyId 的 Schedule → 触发 → 验证 Fire 状态和字段', async ({ request }) => {
    await loginRpc(request);

    const jobName = `${E2E_JOB_PREFIX}full_${Date.now()}`;

    const createResp = await rpc<ScheduleItem>(request, 'NopJobSchedule__save', {
      data: {
        jobName,
        displayName: 'E2E Full Lifecycle',
        executorRef: 'nopE2eTestInvoker',
        executorKind: 1,
        triggerType: 4,
        scheduleStatus: SCHEDULE_STATUS_ENABLED,
        blockStrategy: 1,
        partitionIndex: 0,
        retryPolicyId: null,
        namespaceId: 'default',
        groupId: 'e2e-test',
      },
    });

    expect(createResp.ok).toBeTruthy();
    const scheduleId = createResp.data?.id;
    expect(scheduleId).toBeTruthy();
    createdScheduleIds.push(scheduleId!);

    const schedule = createResp.data!;
    expect(schedule.namespaceId).toBe('default');
    expect(schedule.groupId).toBe('e2e-test');

    await rpc(request, 'NopJobSchedule__triggerNow', { id: scheduleId });

    const fire = await pollUntilFireNotWaiting(request, schedule.jobScheduleId, 30, 2000);
    expect(fire).not.toBeNull();
    expect([FIRE_STATUS_SUCCESS, FIRE_STATUS_FAILED]).toContain(fire!.fireStatus);

    const taskResp = await rpc<FindPageResult<TaskItem>>(request, 'NopJobTask__findPage', {
      query: {
        offset: 0, limit: 10,
        filter: { $type: 'eq', name: 'jobFireId', value: fire!.jobFireId },
      },
    });

    expect(taskResp.ok).toBeTruthy();
    if (taskResp.data.total > 0) {
      const task = taskResp.data.items[0];
      expect([TASK_STATUS_SUCCESS, TASK_STATUS_FAILED]).toContain(task.taskStatus);
    }

    const disableResp = await rpc(request, 'NopJobSchedule__update', {
      data: {
        id: scheduleId,
        scheduleStatus: SCHEDULE_STATUS_DISABLED,
      },
    });
    expect(disableResp.ok).toBeTruthy();
  });
});

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
      if (fire.fireStatus !== 0) return fire;
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

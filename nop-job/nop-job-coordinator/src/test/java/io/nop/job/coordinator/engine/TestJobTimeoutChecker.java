package io.nop.job.coordinator.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.cluster.naming.INamingService;
import io.nop.job.api.alarm.IJobAlarmHandler;
import io.nop.job.api.alarm.JobAlarmEvent;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.core.JobCoreErrors;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.FireScheduleOutcome;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import io.nop.job.dao.store.IJobTaskStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestJobTimeoutChecker {

    private JobTimeoutCheckerImpl checker;
    private MockTaskStore taskStore;
    private MockFireStore fireStore;
    private MockScheduleStore scheduleStore;
    private MockNamingService namingService;
    private MockAlarmHandler alarmHandler;
    private long currentTime;

    @BeforeEach
    void setUp() {
        checker = new JobTimeoutCheckerImpl();
        taskStore = new MockTaskStore();
        fireStore = new MockFireStore();
        scheduleStore = new MockScheduleStore();
        namingService = new MockNamingService();
        alarmHandler = new MockAlarmHandler();

        checker.setTaskStore(taskStore);
        checker.setFireStore(fireStore);
        checker.setScheduleStore(scheduleStore);
        checker.setNamingService(namingService);
        checker.setAlarmHandler(alarmHandler);
        checker.setDispatchTimeoutMs(5000);

        currentTime = System.currentTimeMillis();
    }

    @Test
    void testDispatchTimeout_marksTimedOutFire() {
        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_DISPATCHING,
                new Timestamp(currentTime - 10000));
        fireStore.addDispatchingFire(fire);

        NopJobSchedule schedule = createSchedule("s1", "testJob");
        scheduleStore.addSchedule("s1", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT, fire.getFireStatus());
        assertNotNull(fire.getEndTime());
    }

    @Test
    void testDispatchTimeout_notYetExpired() {
        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_DISPATCHING,
                new Timestamp(currentTime - 2000));
        fireStore.addDispatchingFire(fire);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.FIRE_STATUS_DISPATCHING, fire.getFireStatus());
    }

    @Test
    void testDispatchTimeout_firesAlarm() {
        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_DISPATCHING,
                new Timestamp(currentTime - 10000));
        fireStore.addDispatchingFire(fire);

        NopJobSchedule schedule = createSchedule("s1", "testJob");
        scheduleStore.addSchedule("s1", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(1, alarmHandler.getAlarmCount());
        JobAlarmEvent event = alarmHandler.getLastEvent();
        assertEquals("f1", event.getJobFireId());
        assertEquals("s1", event.getJobScheduleId());
    }

    @Test
    void testDispatchTimeout_updatesScheduleStats() {
        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_DISPATCHING,
                new Timestamp(currentTime - 10000));
        fireStore.addDispatchingFire(fire);

        NopJobSchedule schedule = createSchedule("s1", "testJob");
        schedule.setActiveFireCount(1);
        schedule.setTotalFireCount(5L);
        schedule.setFailFireCount(1L);
        scheduleStore.addSchedule("s1", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(0, schedule.getActiveFireCount());
        assertEquals(6L, schedule.getTotalFireCount());
        assertEquals(2L, schedule.getFailFireCount());
        assertEquals(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT, schedule.getLastFireStatus());
    }

    @Test
    void testDispatchTimeout_disabledWhenZero() {
        checker.setDispatchTimeoutMs(0);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_DISPATCHING,
                new Timestamp(currentTime - 100000));
        fireStore.addDispatchingFire(fire);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.FIRE_STATUS_DISPATCHING, fire.getFireStatus());
    }

    @Test
    void testDispatchTimeout_skipsTaskCancelWhenFireUpdateFails() {
        // Bug C protection: fire version conflict (bothFailed) must skip task cancellation
        // to avoid ending up with RUNNING/TIMEOUT fire + CANCELED tasks inconsistency.
        namingService.setAliveInstances(List.of("worker-a"));

        NopJobFire fire = createFire("f-fire-conflict", "s-fire-conflict",
                _NopJobCoreConstants.FIRE_STATUS_DISPATCHING, new Timestamp(currentTime - 10000));
        fireStore.addDispatchingFire(fire);

        NopJobSchedule schedule = createSchedule("s-fire-conflict", "testJob");
        scheduleStore.addSchedule("s-fire-conflict", schedule);

        NopJobTask task = createTask("t-fire-conflict", "f-fire-conflict",
                _NopJobCoreConstants.TASK_STATUS_WAITING);
        taskStore.addTaskForFire("f-fire-conflict", task);

        fireStore.setCompleteOutcome(FireScheduleOutcome.bothFailed());
        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_WAITING, task.getTaskStatus(),
                "Bug C: task cancellation must be skipped when fire update failed (bothFailed)");
    }

    @Test
    void testDispatchTimeout_cancelsTasksWhenOnlyScheduleUpdateFails() {
        // fire succeeded but schedule failed (fireOnly): tasks must still be canceled
        // (fire is terminal), and the schedule-counter miss is observable via WARN.
        namingService.setAliveInstances(List.of("worker-a"));

        NopJobFire fire = createFire("f-sched-conflict", "s-sched-conflict",
                _NopJobCoreConstants.FIRE_STATUS_DISPATCHING, new Timestamp(currentTime - 10000));
        fireStore.addDispatchingFire(fire);

        NopJobSchedule schedule = createSchedule("s-sched-conflict", "testJob");
        scheduleStore.addSchedule("s-sched-conflict", schedule);

        NopJobTask task = createTask("t-sched-conflict", "f-sched-conflict",
                _NopJobCoreConstants.TASK_STATUS_WAITING);
        taskStore.addTaskForFire("f-sched-conflict", task);

        fireStore.setCompleteOutcome(FireScheduleOutcome.fireOnly());
        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_CANCELED, task.getTaskStatus(),
                "fire succeeded (fireOnly) → task cancellation must proceed despite schedule update failure");
    }

    @Test
    void testWorkerLiveness_marksSuspiciousThenTimeoutWhenWorkerGone() {
        namingService.setAliveInstances(List.of("worker-a"));

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task.setWorkerInstanceId("worker-b");
        task.setStartTime(new Timestamp(currentTime - 10000));
        taskStore.addRunningTask(task);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f1", fire);

        NopJobSchedule schedule = createSchedule("s1", "testJob");
        schedule.setTimeoutSeconds(60);
        scheduleStore.addSchedule("s1", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_SUSPICIOUS, task.getTaskStatus());

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_TIMEOUT, task.getTaskStatus());
    }

    @Test
    void testWorkerLiveness_notMarkedWhenWorkerAlive() {
        namingService.setAliveInstances(List.of("worker-a"));

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task.setWorkerInstanceId("worker-a");
        task.setStartTime(new Timestamp(currentTime - 10000));
        taskStore.addRunningTask(task);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f1", fire);

        NopJobSchedule schedule = createSchedule("s1", "testJob");
        schedule.setTimeoutSeconds(60);
        scheduleStore.addSchedule("s1", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_RUNNING, task.getTaskStatus());
    }

    /**
     * plan 2254（最终裁定）：不存在 dispatchMode=remote 概念——远程执行（executorKind=rpcPoll）
     * 的认领者 workerInstanceId=coordinator hostId，纳入统一 liveness 链判定；
     * workerInstanceId 不在存活集合时与普通任务一样标记 SUSPICIOUS。
     */
    @Test
    void testWorkerLiveness_remoteTaskMarkedSuspicious() {
        namingService.setAliveInstances(List.of("coordinator-1"));

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task.setWorkerInstanceId("remote-worker-1");
        task.setStartTime(new Timestamp(currentTime - 10000));
        taskStore.addRunningTask(task);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fire.setDispatchMode("remote");
        fireStore.addFire("f1", fire);

        NopJobSchedule schedule = createSchedule("s1", "testJob");
        schedule.setTimeoutSeconds(60);
        scheduleStore.addSchedule("s1", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_SUSPICIOUS, task.getTaskStatus(),
                "remote task must join worker-liveness chain like normal tasks");
    }

    /**
     * plan 2254（最终裁定）：远程任务与普通任务一致——liveness 链（SUSPICIOUS 判定）优先于
     * 墙钟超时（本批标记 suspicious 后不再 tryMarkTimeout）。
     */
    @Test
    void testWorkerLiveness_remoteTaskSuspiciousBeforeWallClockTimeout() {
        namingService.setAliveInstances(List.of("coordinator-1"));

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task.setWorkerInstanceId("remote-worker-1");
        task.setStartTime(new Timestamp(currentTime - 10_000));
        taskStore.addRunningTask(task);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fire.setDispatchMode("remote");
        fireStore.addFire("f1", fire);

        NopJobSchedule schedule = createSchedule("s1", "testJob");
        schedule.setTimeoutSeconds(1); // 已超时
        scheduleStore.addSchedule("s1", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_SUSPICIOUS, task.getTaskStatus(),
                "liveness chain wins over wall-clock timeout for remote tasks");
    }

    @Test
    void testClaimedTask_reclaimedWhenWorkerGone() {
        namingService.setAliveInstances(List.of("worker-a"));

        NopJobTask task = createTask("t-claimed", "f-claimed", _NopJobCoreConstants.TASK_STATUS_CLAIMED);
        task.setWorkerInstanceId("worker-gone");
        taskStore.addRunningTask(task);

        NopJobFire fire = createFire("f-claimed", "s-claimed", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f-claimed", fire);

        NopJobSchedule schedule = createSchedule("s-claimed", "testJob");
        schedule.setTimeoutSeconds(60);
        scheduleStore.addSchedule("s-claimed", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_SUSPICIOUS, task.getTaskStatus(),
                "CLAIMED task whose worker has disappeared must be marked SUSPICIOUS on first scan");

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_TIMEOUT, task.getTaskStatus(),
                "SUSPICIOUS task must progress to TIMEOUT on second scan via markSuspiciousAsTimeout");
    }

    @Test
    void testClaimedTask_notMarkedWhenWorkerAlive() {
        namingService.setAliveInstances(List.of("worker-a"));

        NopJobTask task = createTask("t-claimed-alive", "f-claimed-alive", _NopJobCoreConstants.TASK_STATUS_CLAIMED);
        task.setWorkerInstanceId("worker-a");
        taskStore.addRunningTask(task);

        NopJobFire fire = createFire("f-claimed-alive", "s-claimed-alive",
                _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f-claimed-alive", fire);

        NopJobSchedule schedule = createSchedule("s-claimed-alive", "testJob");
        schedule.setTimeoutSeconds(60);
        scheduleStore.addSchedule("s-claimed-alive", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_CLAIMED, task.getTaskStatus(),
                "CLAIMED task whose worker is still alive must not be touched");
    }

    @Test
    void testSuspiciousToTimeout_conversion() {
        namingService.setAliveInstances(List.of("worker-a"));

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS);
        task.setWorkerInstanceId("worker-gone");
        task.setStartTime(new Timestamp(currentTime - 60000));
        taskStore.addRunningTask(task);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f1", fire);

        NopJobSchedule schedule = createSchedule("s1", "testJob");
        schedule.setTimeoutSeconds(60);
        scheduleStore.addSchedule("s1", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_TIMEOUT, task.getTaskStatus());
        assertNotNull(task.getEndTime());
    }

    @Test
    void testNoNamingService_skipsWorkerLivenessCheck() {
        checker.setNamingService(null);

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task.setWorkerInstanceId("worker-gone");
        task.setStartTime(new Timestamp(currentTime - 10000));
        taskStore.addRunningTask(task);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f1", fire);

        NopJobSchedule schedule = createSchedule("s1", "testJob");
        schedule.setTimeoutSeconds(60);
        scheduleStore.addSchedule("s1", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_RUNNING, task.getTaskStatus());
    }

    @Test
    void testExistingTaskTimeout_unchanged() {
        namingService.setAliveInstances(List.of("worker-a"));

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task.setWorkerInstanceId("worker-a");
        task.setStartTime(new Timestamp(currentTime - 120000));
        taskStore.addRunningTask(task);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f1", fire);

        NopJobSchedule schedule = createSchedule("s1", "testJob");
        schedule.setTimeoutSeconds(60);
        scheduleStore.addSchedule("s1", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_TIMEOUT, task.getTaskStatus());
    }

    @Test
    void testEmptyTasks_noop() {
        scheduleStore.setCurrentTime(currentTime);
        checker.scanOnce();
        assertEquals(0, alarmHandler.getAlarmCount());
    }

    @Test
    void testWorkerLiveness_nullWorkerIdNotMarked() {
        namingService.setAliveInstances(List.of("worker-a"));

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task.setWorkerInstanceId(null);
        task.setStartTime(new Timestamp(currentTime - 10000));
        taskStore.addRunningTask(task);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f1", fire);

        NopJobSchedule schedule = createSchedule("s1", "testJob");
        schedule.setTimeoutSeconds(60);
        scheduleStore.addSchedule("s1", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_RUNNING, task.getTaskStatus());
    }

    @Test
    void testBatchResilience_taskTimeout_singleFailureDoesNotAbortBatch() {
        NopJobTask task1 = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task1.setWorkerInstanceId("worker-a");
        task1.setStartTime(new Timestamp(currentTime - 120000));

        NopJobTask task2 = createTask("t2", "f2", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task2.setWorkerInstanceId("worker-a");
        task2.setStartTime(new Timestamp(currentTime - 120000));

        MockTaskStore explodingStore = new MockTaskStore() {
            @Override
            public boolean updateTask(NopJobTask task) {
                if ("t2".equals(task.getJobTaskId())) {
                    throw new NopException(JobCoreErrors.ERR_JOB_TIMEOUT);
                }
                return super.updateTask(task);
            }
        };
        explodingStore.addRunningTask(task1);
        explodingStore.addRunningTask(task2);
        checker.setTaskStore(explodingStore);

        namingService.setAliveInstances(List.of("worker-a"));

        NopJobFire fire1 = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        NopJobFire fire2 = createFire("f2", "s2", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f1", fire1);
        fireStore.addFire("f2", fire2);

        NopJobSchedule schedule1 = createSchedule("s1", "job1");
        schedule1.setTimeoutSeconds(60);
        NopJobSchedule schedule2 = createSchedule("s2", "job2");
        schedule2.setTimeoutSeconds(60);
        scheduleStore.addSchedule("s1", schedule1);
        scheduleStore.addSchedule("s2", schedule2);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_TIMEOUT, task1.getTaskStatus());
    }

    @Test
    void testBatchResilience_dispatchTimeout_singleFailureDoesNotAbortBatch() {
        NopJobFire fire1 = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_DISPATCHING,
                new Timestamp(currentTime - 10000));
        NopJobFire fire2 = createFire("f2", "s2", _NopJobCoreConstants.FIRE_STATUS_DISPATCHING,
                new Timestamp(currentTime - 10000));

        fireStore.addDispatchingFire(fire1);
        fireStore.addDispatchingFire(fire2);

        NopJobSchedule schedule1 = createSchedule("s1", "job1");
        scheduleStore.addSchedule("s1", schedule1);

        MockScheduleStore explodingScheduleStore = new MockScheduleStore() {
            @Override
            public NopJobSchedule tryLoadSchedule(String scheduleId) {
                if ("s2".equals(scheduleId)) {
                    throw new NopException(JobCoreErrors.ERR_JOB_TIMEOUT);
                }
                return super.tryLoadSchedule(scheduleId);
            }
        };
        explodingScheduleStore.addSchedule("s1", schedule1);
        NopJobSchedule schedule2 = createSchedule("s2", "job2");
        explodingScheduleStore.addSchedule("s2", schedule2);
        explodingScheduleStore.setCurrentTime(currentTime);
        checker.setScheduleStore(explodingScheduleStore);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT, fire1.getFireStatus());
        assertEquals(_NopJobCoreConstants.FIRE_STATUS_DISPATCHING, fire2.getFireStatus());
    }

    @Test
    void test_dispatchTimeoutScheduleDeleted() {
        NopJobFire fire = createFire("f-deleted", "s-deleted", _NopJobCoreConstants.FIRE_STATUS_DISPATCHING,
                new Timestamp(currentTime - 10000));
        fireStore.addDispatchingFire(fire);

        NopJobTask task = createTask("t-deleted", "f-deleted", _NopJobCoreConstants.TASK_STATUS_WAITING);
        taskStore.addTaskForFire("f-deleted", task);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals("f-deleted", fireStore.getFailedFireId(),
                "deleted schedule should trigger failFireWithoutSchedule");
        assertNotNull(fireStore.getFailedErrorCode());
        // check2 [P3-12]: 任务行错误码必须与 fire 层一致（SCHEDULE_DELETED），不得误写 JOB_TIMEOUT
        assertEquals(JobCoreErrors.ERR_JOB_SCHEDULE_DELETED.getErrorCode(), task.getErrorCode(),
                "task rows under a schedule-deleted fire must carry ERR_JOB_SCHEDULE_DELETED, not ERR_JOB_TIMEOUT");
        assertEquals(_NopJobCoreConstants.TASK_STATUS_CANCELED, task.getTaskStatus());
    }

    @Test
    void test_timeoutUpdateTaskVersionConflictLogsWarn() {
        MockTaskStore versionStore = new MockTaskStore() {
            @Override
            public boolean updateTask(NopJobTask task) {
                return false;
            }
        };
        checker.setTaskStore(versionStore);

        NopJobTask task = createTask("t-ver", "f-ver", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task.setWorkerInstanceId("worker-a");
        task.setStartTime(new Timestamp(currentTime - 120000));
        versionStore.addRunningTask(task);

        NopJobFire fire = createFire("f-ver", "s-ver", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f-ver", fire);

        NopJobSchedule schedule = createSchedule("s-ver", "job1");
        schedule.setTimeoutSeconds(60);
        scheduleStore.addSchedule("s-ver", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_TIMEOUT, task.getTaskStatus(),
                "Task should still be set to TIMEOUT locally even when updateTask returns false");
    }

    @Test
    void testExecutionTimeoutUsedWhenScheduleHasNoTimeoutSeconds() {
        checker.setExecutionTimeoutMs(3000);

        NopJobTask task = createTask("t-exec", "f-exec", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task.setWorkerInstanceId("worker-a");
        task.setStartTime(new Timestamp(currentTime - 5000));
        taskStore.addRunningTask(task);

        NopJobFire fire = createFire("f-exec", "s-exec", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f-exec", fire);

        NopJobSchedule schedule = createSchedule("s-exec", "job1");
        schedule.setTimeoutSeconds(null);
        scheduleStore.addSchedule("s-exec", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_TIMEOUT, task.getTaskStatus(),
                "Should use executionTimeoutMs when schedule has no timeoutSeconds");
    }

    @Test
    void testLongRunningTaskWithoutTimeoutNotKilled() {
        // plan 340 §2.1 (P1-1): a long-running task with NO schedule.timeoutSeconds and
        // executionTimeoutMs disabled must NOT fall back to dispatchTimeoutMs.
        checker.setExecutionTimeoutMs(-1);
        checker.setDispatchTimeoutMs(5000);

        NopJobTask task = createTask("t-p11", "f-p11", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task.setWorkerInstanceId("worker-a");
        task.setStartTime(new Timestamp(currentTime - 600_000)); // 10 min ago >> 5min dispatchTimeout
        taskStore.addRunningTask(task);

        NopJobFire fire = createFire("f-p11", "s-p11", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f-p11", fire);

        NopJobSchedule schedule = createSchedule("s-p11", "job1");
        schedule.setTimeoutSeconds(null);
        scheduleStore.addSchedule("s-p11", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_RUNNING, task.getTaskStatus(),
                "Long-running task without execution timeout must not be killed by dispatchTimeoutMs fallback");
    }

    @Test
    void testExecutionTimeoutNotExpiredYet() {
        checker.setExecutionTimeoutMs(10000);

        NopJobTask task = createTask("t-exec2", "f-exec2", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task.setWorkerInstanceId("worker-a");
        task.setStartTime(new Timestamp(currentTime - 5000));
        taskStore.addRunningTask(task);

        NopJobFire fire = createFire("f-exec2", "s-exec2", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f-exec2", fire);

        NopJobSchedule schedule = createSchedule("s-exec2", "job1");
        schedule.setTimeoutSeconds(null);
        scheduleStore.addSchedule("s-exec2", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_RUNNING, task.getTaskStatus(),
                "Should not timeout if executionTimeoutMs not yet reached");
    }

    @Test
    void testExecutionTimeoutPreferredOverDispatchTimeout() {
        checker.setDispatchTimeoutMs(30000);
        checker.setExecutionTimeoutMs(3000);

        NopJobTask task = createTask("t-pref", "f-pref", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task.setWorkerInstanceId("worker-a");
        task.setStartTime(new Timestamp(currentTime - 5000));
        taskStore.addRunningTask(task);

        NopJobFire fire = createFire("f-pref", "s-pref", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f-pref", fire);

        NopJobSchedule schedule = createSchedule("s-pref", "job1");
        schedule.setTimeoutSeconds(null);
        scheduleStore.addSchedule("s-pref", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_TIMEOUT, task.getTaskStatus(),
                "executionTimeoutMs should be preferred over dispatchTimeoutMs for task execution timeout");
    }

    // ========== AR-88: WAITING-task 派发超时回收 ==========

    /**
     * AR-88: 一个 WAITING 任务归因给不存在的 worker，超过派发等待窗口后被重派发
     *（workerInstanceId 置 null，回到 competing-consumer），不再永久滞留。
     */
    @Test
    void testStaleWaitingTaskReDispatchedWhenAttributedToGoneWorker() {
        checker.setTaskDispatchWaitTimeoutMs(5000);

        NopJobTask stale = createTask("t-stale", "f-stale", _NopJobCoreConstants.TASK_STATUS_WAITING);
        stale.setWorkerInstanceId("worker-gone");
        stale.setCreateTime(new Timestamp(currentTime - 10000));
        taskStore.addWaitingTask(stale);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertNull(stale.getWorkerInstanceId(),
                "stale WAITING task attributed to gone worker must be re-dispatched (workerInstanceId cleared)");
        assertEquals(_NopJobCoreConstants.TASK_STATUS_WAITING, stale.getTaskStatus(),
                "re-dispatch keeps WAITING (not FAILED) to preserve the executable opportunity");
        assertEquals(1, taskStore.resetCallCount,
                "wiring: resetStaleWaitingTasks must be called during scanOnce");
        assertEquals(currentTime - 5000, taskStore.lastResetDeadline,
                "deadline passed to store must be now - taskDispatchWaitTimeoutMs");
    }

    /**
     * AR-88 防误杀：正常等待中的 WAITING 任务（未超窗口）不被重置。
     */
    @Test
    void testFreshWaitingTaskNotReset() {
        checker.setTaskDispatchWaitTimeoutMs(5000);

        NopJobTask fresh = createTask("t-fresh", "f-fresh", _NopJobCoreConstants.TASK_STATUS_WAITING);
        fresh.setWorkerInstanceId("worker-a");
        fresh.setCreateTime(new Timestamp(currentTime - 1000));
        taskStore.addWaitingTask(fresh);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals("worker-a", fresh.getWorkerInstanceId(),
                "fresh WAITING task (within window) must NOT be reset");
        assertEquals(1, taskStore.resetCallCount,
                "resetStaleWaitingTasks is invoked once even when nothing matches");
    }

    /**
     * AR-88 禁用语义：task-dispatch-wait-timeout-ms <= 0 时跳过回收。
     */
    @Test
    void testStaleWaitingTaskResetDisabledWhenZero() {
        checker.setTaskDispatchWaitTimeoutMs(0);

        NopJobTask stale = createTask("t-stale-disabled", "f-stale-disabled",
                _NopJobCoreConstants.TASK_STATUS_WAITING);
        stale.setWorkerInstanceId("worker-gone");
        stale.setCreateTime(new Timestamp(currentTime - 100000));
        taskStore.addWaitingTask(stale);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals("worker-gone", stale.getWorkerInstanceId(),
                "when task-dispatch-wait-timeout-ms <= 0, stale reset must be skipped");
        assertEquals(0, taskStore.resetCallCount,
                "resetStaleWaitingTasks must NOT be called when disabled");
    }

    // ========== Plan 338 Phase 3: cursor pagination / drain / no-dead-loop ==========

    /**
     * Plan 338: 单调度周期内 drain 全部超时 task。25 条超时 RUNNING task、batchSize=10。
     * 期望：scanOnce 内 fetchRunningTasks 调用 3 次（10+10+5），全部 25 条被标 TIMEOUT。
     */
    @Test
    void testDrainAllTimedOutTasksInOneCycle() {
        checker.setBatchSize(10);
        checker.setExecutionTimeoutMs(1000);
        checker.setDispatchTimeoutMs(-1);
        checker.setTaskDispatchWaitTimeoutMs(-1);

        NopJobSchedule schedule = createSchedule("s-drain", "job-drain");
        schedule.setTimeoutSeconds(0);
        scheduleStore.addSchedule("s-drain", schedule);

        Timestamp start = new Timestamp(currentTime - 100_000);
        for (int i = 0; i < 25; i++) {
            String id = String.format("task-drain-%02d", i);
            String fireId = "fire-drain-" + i;
            NopJobTask task = createTask(id, fireId, _NopJobCoreConstants.TASK_STATUS_RUNNING);
            task.setStartTime(start);
            taskStore.addRunningTask(task);

            NopJobFire fire = createFire(fireId, "s-drain",
                    _NopJobCoreConstants.FIRE_STATUS_RUNNING, start);
            fireStore.addFire(fireId, fire);
        }

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        long timeoutCount = taskStore.runningTasks.stream()
                .filter(t -> t.getTaskStatus() == _NopJobCoreConstants.TASK_STATUS_TIMEOUT)
                .count();
        assertEquals(25, timeoutCount, "all 25 timed-out tasks must be marked TIMEOUT in one cycle");

        // Model A (check-at-end): batch 1=10, batch 2=10, batch 3=5(<10 → drained). fetch calls = 3.
        assertEquals(3, taskStore.fetchRunningCursorTimes.size(),
                "fetchRunningTasks must be called exactly 3 times (10+10+5 then drained)");
        assertNull(taskStore.fetchRunningCursorTimes.get(0), "first fetch has null cursor");
        assertNotNull(taskStore.fetchRunningCursorTimes.get(1), "second fetch has advanced cursor");
        assertNotNull(taskStore.fetchRunningCursorTimes.get(2), "third fetch has advanced cursor");
    }

    /**
     * Plan 338: 验证 cursor 防死循环。10 条 RUNNING task 全部未超时、batchSize=10。
     * 期望：scanOnce 退出（不卡到 maxScanLoops 上限）；fetchRunningTimes 调用 2 次
     *（首批 10 条 → cursor 推进 → 第二批 0 条 → taskDrained → 退出）；task 状态不变。
     */
    @Test
    void testNoDeadLoopOnNonTimedOutRunningTasks() {
        checker.setBatchSize(10);
        checker.setExecutionTimeoutMs(100_000); // 100s — none timed out
        checker.setDispatchTimeoutMs(-1);
        checker.setTaskDispatchWaitTimeoutMs(-1);

        NopJobSchedule schedule = createSchedule("s-noop", "job-noop");
        schedule.setTimeoutSeconds(0);
        scheduleStore.addSchedule("s-noop", schedule);

        // 10 tasks, just started (no timeout)
        Timestamp start = new Timestamp(currentTime - 100);
        for (int i = 0; i < 10; i++) {
            String id = String.format("task-noop-%02d", i);
            String fireId = "fire-noop-" + i;
            NopJobTask task = createTask(id, fireId, _NopJobCoreConstants.TASK_STATUS_RUNNING);
            task.setStartTime(start);
            taskStore.addRunningTask(task);

            NopJobFire fire = createFire(fireId, "s-noop",
                    _NopJobCoreConstants.FIRE_STATUS_RUNNING, start);
            fireStore.addFire(fireId, fire);
        }

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        // fetch calls: batch 1 returns 10 (size=batchSize, not drained, cursor advances),
        // batch 2 returns 0 (past cursor) → drained → exit. Total = 2.
        assertEquals(2, taskStore.fetchRunningCursorTimes.size(),
                "fetchRunningTasks must be called exactly 2 times (10 then 0), no dead loop");

        long stillRunning = taskStore.runningTasks.stream()
                .filter(t -> t.getTaskStatus() == _NopJobCoreConstants.TASK_STATUS_RUNNING)
                .count();
        assertEquals(10, stillRunning, "no task should be marked TIMEOUT (deadline not reached)");
    }

    /**
     * Plan 338: 验证跨调度周期 cursor 重置。batchSize=2、5 条 task（不超时）。
     * 期望：第 1 次 scanOnce 内 fetch 调用 3 次（cursor 序列 null→t1→t3）；
     * 第 2 次 scanOnce 第 1 次 fetch 的 cursor 又是 null（onCycleStart reset）。
     */
    @Test
    void testCursorResetBetweenCycles() {
        checker.setBatchSize(2);
        checker.setExecutionTimeoutMs(100_000); // no timeout, focus on cursor behavior
        checker.setDispatchTimeoutMs(-1);
        checker.setTaskDispatchWaitTimeoutMs(-1);

        NopJobSchedule schedule = createSchedule("s-reset", "job-reset");
        schedule.setTimeoutSeconds(0);
        scheduleStore.addSchedule("s-reset", schedule);

        Timestamp start = new Timestamp(currentTime - 100);
        for (int i = 0; i < 5; i++) {
            String id = String.format("task-reset-%02d", i);
            String fireId = "fire-reset-" + i;
            NopJobTask task = createTask(id, fireId, _NopJobCoreConstants.TASK_STATUS_RUNNING);
            task.setStartTime(start);
            taskStore.addRunningTask(task);

            NopJobFire fire = createFire(fireId, "s-reset",
                    _NopJobCoreConstants.FIRE_STATUS_RUNNING, start);
            fireStore.addFire(fireId, fire);
        }

        scheduleStore.setCurrentTime(currentTime);

        // 1st scanOnce
        checker.scanOnce();
        int firstCycleFetches = taskStore.fetchRunningCursorTimes.size();
        assertEquals(3, firstCycleFetches, "1st cycle: 3 fetches (2+2+1, last < batchSize → drained)");
        assertNull(taskStore.fetchRunningCursorTimes.get(0), "1st cycle 1st fetch: null cursor");

        // 2nd scanOnce — onCycleStart should reset cursor
        checker.scanOnce();
        // fetchRunningCursorTimes now has 6 entries (3 from 1st + 3 from 2nd)
        assertEquals(6, taskStore.fetchRunningCursorTimes.size(),
                "2nd cycle: 3 more fetches (cursor reset, same pattern)");
        assertNull(taskStore.fetchRunningCursorTimes.get(3),
                "2nd cycle 1st fetch: cursor reset to null by onCycleStart");
    }

    /**
     * Plan 338: 三类子扫描独立 drain、互不阻塞。25 task + 15 fire + 10 waiting，batchSize=10。
     * 期望：fetchRunningTasks=3、fetchDispatchingFires=2、resetStaleWaitingTasks=2；全部 drain 后 scanBatch 返回 false。
     */
    @Test
    void testDrainMixesTaskTimeoutAndDispatchTimeoutAndStaleWaiting() {
        checker.setBatchSize(10);
        checker.setExecutionTimeoutMs(1000);
        checker.setDispatchTimeoutMs(1000);
        checker.setTaskDispatchWaitTimeoutMs(60_000);

        NopJobSchedule schedule = createSchedule("s-mix", "job-mix");
        schedule.setTimeoutSeconds(0);
        scheduleStore.addSchedule("s-mix", schedule);

        // 25 timed-out RUNNING tasks
        Timestamp taskStart = new Timestamp(currentTime - 100_000);
        for (int i = 0; i < 25; i++) {
            String id = String.format("task-mix-%02d", i);
            String fireId = "fire-mix-task-" + i;
            NopJobTask task = createTask(id, fireId, _NopJobCoreConstants.TASK_STATUS_RUNNING);
            task.setStartTime(taskStart);
            taskStore.addRunningTask(task);

            NopJobFire fire = createFire(fireId, "s-mix",
                    _NopJobCoreConstants.FIRE_STATUS_RUNNING, taskStart);
            fireStore.addFire(fireId, fire);
        }

        // 15 timed-out DISPATCHING fires
        Timestamp fireStart = new Timestamp(currentTime - 100_000);
        for (int i = 0; i < 15; i++) {
            String fireId = "fire-mix-disp-" + i;
            NopJobFire fire = createFire(fireId, "s-mix",
                    _NopJobCoreConstants.FIRE_STATUS_DISPATCHING, fireStart);
            fireStore.addDispatchingFire(fire);
        }

        // 10 stale WAITING tasks (createTime old)
        for (int i = 0; i < 10; i++) {
            String id = String.format("task-mix-wait-%02d", i);
            String fireId = "fire-mix-wait-" + i;
            NopJobTask task = createTask(id, fireId, _NopJobCoreConstants.TASK_STATUS_WAITING);
            task.setCreateTime(new Timestamp(currentTime - 200_000));
            taskStore.addWaitingTask(task);
        }

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        // task drain: 25/10 → 3 fetches
        assertEquals(3, taskStore.fetchRunningCursorTimes.size(),
                "task sub-scan: 3 fetches to drain 25 tasks at batchSize=10");

        // fire drain: 15/10 → 2 fetches (10+5)
        // mock fire store doesn't track cursor times, but we can verify via state changes
        long timeoutFires = fireStore.dispatchingFires.stream()
                .filter(f -> f.getFireStatus() == _NopJobCoreConstants.FIRE_STATUS_TIMEOUT)
                .count();
        assertEquals(15, timeoutFires, "all 15 dispatching fires timed out");

        // waiting drain: 10/10 → 2 fetches (10 + 0)
        assertEquals(2, taskStore.resetCursorTimes.size(),
                "waiting sub-scan: 2 fetches (10 then 0, both < batchSize+1 triggers drained)");
        long resetCount = taskStore.waitingTasks.stream()
                .filter(t -> t.getWorkerInstanceId() == null)
                .count();
        assertEquals(10, resetCount, "all 10 stale waiting tasks had workerInstanceId reset");

        // task timeouts applied
        long taskTimeoutCount = taskStore.runningTasks.stream()
                .filter(t -> t.getTaskStatus() == _NopJobCoreConstants.TASK_STATUS_TIMEOUT)
                .count();
        assertEquals(25, taskTimeoutCount, "all 25 RUNNING tasks timed out");
    }

    private NopJobTask createTask(String taskId, String fireId, int status) {
        NopJobTask task = new NopJobTask();
        task.setJobTaskId(taskId);
        task.setJobFireId(fireId);
        task.setTaskStatus(status);
        return task;
    }

    private NopJobFire createFire(String fireId, String scheduleId, int status, Timestamp startTime) {
        NopJobFire fire = new NopJobFire();
        fire.setJobFireId(fireId);
        fire.setJobScheduleId(scheduleId);
        fire.setFireStatus(status);
        fire.setStartTime(startTime);
        return fire;
    }

    private NopJobSchedule createSchedule(String scheduleId, String jobName) {
        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobScheduleId(scheduleId);
        schedule.setJobName(jobName);
        return schedule;
    }

    private ServiceInstance createInstance(String instanceId) {
        ServiceInstance inst = new ServiceInstance();
        inst.setInstanceId(instanceId);
        inst.setAddr("localhost");
        inst.setPort(8080);
        inst.setHealthy(true);
        inst.setEnabled(true);
        return inst;
    }

    static class MockTaskStore implements IJobTaskStore {
        private List<NopJobTask> runningTasks = new ArrayList<>();
        private List<NopJobTask> waitingTasks = new ArrayList<>();
        int resetCallCount = 0;
        long lastResetDeadline = 0L;
        // Plan 338: cursor capture for cross-cycle reset verification.
        final List<java.sql.Timestamp> fetchRunningCursorTimes = new java.util.ArrayList<>();
        final List<java.sql.Timestamp> resetCursorTimes = new java.util.ArrayList<>();

        void addRunningTask(NopJobTask task) {
            runningTasks.add(task);
        }

        void addWaitingTask(NopJobTask task) {
            waitingTasks.add(task);
        }

        @Override
        public List<NopJobTask> fetchRunningTasks(int limit, IntRangeSet partitions,
                                                  java.sql.Timestamp cursorTime, String cursorId) {
            fetchRunningCursorTimes.add(cursorTime);
            // Cursor predicate: keep rows strictly before cursor in (startTime DESC, jobTaskId DESC) order.
            // Then apply limit truncation (matching JobTaskStoreImpl behavior with setLimit).
            return runningTasks.stream()
                    .filter(t -> cursorTime == null
                            || beforeCursor(t.getStartTime(), t.getJobTaskId(), cursorTime, cursorId))
                    .sorted(java.util.Comparator.comparing(NopJobTask::getStartTime,
                            java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                            .thenComparing(NopJobTask::getJobTaskId, java.util.Comparator.reverseOrder()))
                    .limit(limit)
                    .collect(java.util.stream.Collectors.toList());
        }

                /**
         * Returns true if (t.time, t.id) is strictly before (cursor.time, cursor.id) in DESC order
         * (i.e., would appear AFTER the cursor in descending iteration).
         */
        private static boolean beforeCursor(java.sql.Timestamp tTime, String tId,
                                            java.sql.Timestamp cTime, String cId) {
            if (tTime == null) {
                return false;
            }
            int cmp = tTime.compareTo(cTime);
            if (cmp < 0) {
                return true;
            }
            if (cmp > 0) {
                return false;
            }
            // Equal timestamps: compare ids; null cursorId means "end of equal-time group".
            if (cId == null) {
                return false;
            }
            return cId.compareTo(tId) > 0;
        }

        @Override
        public List<NopJobTask> resetStaleWaitingTasks(int batchSize, IntRangeSet partitions, long deadlineMs,
                                                       java.sql.Timestamp cursorTime, String cursorId) {
            resetCallCount++;
            lastResetDeadline = deadlineMs;
            resetCursorTimes.add(cursorTime);
            List<NopJobTask> result = waitingTasks.stream()
                    .filter(t -> {
                        Integer status = t.getTaskStatus();
                        if (status == null || status != _NopJobCoreConstants.TASK_STATUS_WAITING) {
                            return false;
                        }
                        if (t.getCreateTime() == null || t.getCreateTime().getTime() >= deadlineMs) {
                            return false;
                        }
                        if (cursorTime == null) {
                            return true;
                        }
                        return beforeCursorCreateTime(t.getCreateTime(), t.getJobTaskId(), cursorTime, cursorId);
                    })
                    .sorted(java.util.Comparator.comparing(NopJobTask::getCreateTime,
                            java.util.Comparator.reverseOrder())
                            .thenComparing(NopJobTask::getJobTaskId, java.util.Comparator.reverseOrder()))
                    .limit(batchSize)
                    .collect(java.util.stream.Collectors.toList());
            // Mutate workerInstanceId (matches JobTaskStoreImpl behavior)
            for (NopJobTask task : result) {
                task.setWorkerInstanceId(null);
            }
            return result;
        }

        private static boolean beforeCursorCreateTime(java.sql.Timestamp tTime, String tId,
                                                      java.sql.Timestamp cTime, String cId) {
            int cmp = tTime.compareTo(cTime);
            if (cmp < 0) {
                return true;
            }
            if (cmp > 0) {
                return false;
            }
            if (cId == null) {
                return false;
            }
            return cId.compareTo(tId) > 0;
        }

        @Override public boolean updateTask(NopJobTask task) { return true; }
        @Override public List<NopJobTask> fetchWaitingTasks(int limit, IntRangeSet partitions) { return Collections.emptyList(); }
        @Override public List<NopJobTask> fetchWaitingTasks(int limit, IntRangeSet p, String wid, boolean enfo) { return Collections.emptyList(); }
        @Override public List<NopJobTask> tryLockTasksForExecute(List<NopJobTask> tasks, String workerInstanceId, long lockTimeoutMs) { return tasks; }
        private java.util.Map<String, List<NopJobTask>> tasksByFireId = new java.util.HashMap<>();

        void addTaskForFire(String fireId, NopJobTask task) {
            tasksByFireId.computeIfAbsent(fireId, k -> new ArrayList<>()).add(task);
        }

        @Override public List<NopJobTask> findTasksByFireId(String jobFireId) {
            return tasksByFireId.getOrDefault(jobFireId, Collections.emptyList());
        }
        @Override public NopJobTask loadTask(String jobTaskId) { return null; }
        @Override public long countInFlightTasks(String workerInstanceId) { return 0; }
        @Override public io.nop.job.api.resource.ResourceVector sumReservedCost(String workerInstanceId) { return io.nop.job.api.resource.ResourceVector.ZERO; }
        @Override public java.util.List<io.nop.job.dao.store.WorkerReservedCost> sumReservedCostByWorker() { return java.util.Collections.emptyList(); }
    }

    static class MockFireStore implements IJobFireStore {
        private Map<String, NopJobFire> fireMap = new java.util.HashMap<>();
        private List<NopJobFire> dispatchingFires = new ArrayList<>();
        private String failedFireId;
        private String failedErrorCode;

        void addFire(String fireId, NopJobFire fire) {
            fireMap.put(fireId, fire);
        }

        void addDispatchingFire(NopJobFire fire) {
            dispatchingFires.add(fire);
        }

        String getFailedFireId() { return failedFireId; }
        String getFailedErrorCode() { return failedErrorCode; }

        @Override
        public Map<String, NopJobFire> batchLoadFires(Set<String> fireIds) {
            Map<String, NopJobFire> result = new java.util.HashMap<>();
            for (String id : fireIds) {
                NopJobFire fire = fireMap.get(id);
                if (fire != null) result.put(id, fire);
            }
            return result;
        }

        @Override
        public List<NopJobFire> fetchDispatchingFires(int limit, IntRangeSet partitions,
                                                      java.sql.Timestamp cursorTime, String cursorId) {
            // Cursor predicate + limit truncation + stable ordering matching JobFireStoreImpl.
            return dispatchingFires.stream()
                    .filter(f -> cursorTime == null
                            || beforeFireCursor(f.getStartTime(), f.getJobFireId(), cursorTime, cursorId))
                    .sorted(java.util.Comparator.comparing(NopJobFire::getStartTime,
                            java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                            .thenComparing(NopJobFire::getJobFireId, java.util.Comparator.reverseOrder()))
                    .limit(limit)
                    .collect(java.util.stream.Collectors.toList());
        }

        private static boolean beforeFireCursor(java.sql.Timestamp tTime, String tId,
                                               java.sql.Timestamp cTime, String cId) {
            if (tTime == null) {
                return false;
            }
            int cmp = tTime.compareTo(cTime);
            if (cmp < 0) {
                return true;
            }
            if (cmp > 0) {
                return false;
            }
            if (cId == null) {
                return false;
            }
            return cId.compareTo(tId) > 0;
        }

        @Override public boolean revertDispatchingFireToWaiting(NopJobFire fire, long backoffUntilMs) { return false; }

        @Override public List<NopJobFire> fetchWaitingFires(int limit, IntRangeSet partitions) { return Collections.emptyList(); }
        @Override public List<NopJobFire> fetchRunningFires(int limit, IntRangeSet partitions) { return Collections.emptyList(); }
        @Override public List<NopJobFire> tryLockFiresForDispatch(List<NopJobFire> fires, String dispatchInstanceId, long lockTimeoutMs) { return fires; }
        @Override public void insertTasksAndMarkFireDispatching(NopJobFire fire, List<NopJobTask> tasks) {}

        private FireScheduleOutcome completeOutcome = FireScheduleOutcome.bothUpdated();
        void setCompleteOutcome(FireScheduleOutcome outcome) { this.completeOutcome = outcome; }

        @Override public FireScheduleOutcome completeFireAndUpdateSchedule(NopJobFire fire, NopJobSchedule schedule) { return completeOutcome; }
        @Override public FireScheduleOutcome cancelFire(String jobFireId) { return FireScheduleOutcome.bothFailed(); }
        @Override public boolean failFireWithoutSchedule(String jobFireId, String errorCode, String errorMessage) {
            this.failedFireId = jobFireId;
            this.failedErrorCode = errorCode;
            return true;
        }
        @Override public NopJobFire loadFire(String jobFireId) { return fireMap.get(jobFireId); }
        @Override public NopJobFire getFireById(String jobFireId) { return fireMap.get(jobFireId); }
    }

    static class MockScheduleStore implements IJobScheduleStore {
        private Map<String, NopJobSchedule> scheduleMap = new java.util.HashMap<>();
        private long currentTime;

        void addSchedule(String scheduleId, NopJobSchedule schedule) {
            scheduleMap.put(scheduleId, schedule);
        }

        void setCurrentTime(long time) {
            this.currentTime = time;
        }

        @Override
        public long getCurrentTime() {
            return currentTime;
        }

        @Override
        public NopJobSchedule loadSchedule(String scheduleId) {
            return scheduleMap.get(scheduleId);
        }

        @Override
        public NopJobSchedule tryLoadSchedule(String id) {
            return scheduleMap.get(id);
        }

        @Override
        public Map<String, NopJobSchedule> batchLoadSchedules(Set<String> scheduleIds) {
            Map<String, NopJobSchedule> result = new java.util.HashMap<>();
            for (String id : scheduleIds) {
                NopJobSchedule s = scheduleMap.get(id);
                if (s != null) result.put(id, s);
            }
            return result;
        }

        @Override public List<NopJobSchedule> fetchDueSchedules(int limit, IntRangeSet partitions) { return Collections.emptyList(); }
        @Override public List<NopJobSchedule> tryLockSchedulesForPlan(List<NopJobSchedule> schedules, String plannerInstanceId, long lockTimeoutMs) { return schedules; }
        @Override public void advanceScheduleAfterSkip(NopJobSchedule schedule, Timestamp nextFireTime) {}
        @Override public void insertFireAndAdvanceSchedule(NopJobSchedule schedule, NopJobFire fire, Timestamp nextFireTime, Integer lastFireStatus) {}
        @Override public void overlayFireAndAdvanceSchedule(NopJobSchedule schedule, NopJobFire fire, Timestamp nextFireTime, Integer lastFireStatus) {}
        @Override public void recoveryFireAndAdvanceSchedule(NopJobSchedule schedule, Timestamp nextFireTime) {}
        @Override public boolean insertManualFire(NopJobSchedule schedule, NopJobFire fire) { return true; }
    }

    static class MockNamingService implements INamingService {
        private List<ServiceInstance> instances;

        void setAliveInstances(List<String> instanceIds) {
            List<ServiceInstance> list = new ArrayList<>();
            for (String id : instanceIds) {
                ServiceInstance inst = new ServiceInstance();
                inst.setInstanceId(id);
                inst.setHealthy(true);
                inst.setEnabled(true);
                list.add(inst);
            }
            this.instances = list;
        }

        @Override
        public List<ServiceInstance> getInstances(String serviceName) {
            return instances != null ? new ArrayList<>(instances) : null;
        }

        @Override public void registerInstance(ServiceInstance instance) {}
        @Override public void unregisterInstance(ServiceInstance instance) {}
        @Override public void updateInstance(ServiceInstance instance) {}
        @Override public List<String> getServices() { return Collections.emptyList(); }
    }

    static class MockAlarmHandler implements IJobAlarmHandler {
        private List<JobAlarmEvent> events = new ArrayList<>();

        @Override
        public void onFireTimeout(JobAlarmEvent event) {
            events.add(event);
        }

        @Override
        public void onFireFailed(JobAlarmEvent event) {
            events.add(event);
        }

        int getAlarmCount() {
            return events.size();
        }

        JobAlarmEvent getLastEvent() {
            return events.isEmpty() ? null : events.get(events.size() - 1);
        }
    }
}

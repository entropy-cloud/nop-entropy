package io.nop.job.dao.store;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2254 隔离守卫回归：remote 模式任务与 DB 模式 worker 拉取结果集互斥。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestRemoteDispatchIsolation extends JunitBaseTestCase {
    private static final int SCHEDULE_STATUS_ENABLED = 10;
    private static final int FIRE_STATUS_WAITING = 0;
    private static final int TASK_STATUS_WAITING = 0;
    private static final int TASK_STATUS_RUNNING = 20;
    private static final int TRIGGER_TYPE_FIXED_RATE = 2;
    private static final int TRIGGER_SOURCE_SCHEDULE = 1;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IJobScheduleStore scheduleStore;

    @Inject
    IJobFireStore fireStore;

    @Inject
    IJobTaskStore taskStore;

    @Test
    public void testFetchWaitingTasksExcludesRemoteAndKeepsNullDispatchMode() {
        NopJobSchedule schedule = newSchedule("sched-isol-1", "job-isol-1");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        // remote fire：归因 WAITING 任务
        NopJobFire remoteFire = saveFire("fire-remote-1", schedule, "remote", Map.of("serviceName", "svc-a"));
        NopJobTask remoteTask = saveTask("task-remote-1", remoteFire, "worker-1", "h1:8080", TASK_STATUS_WAITING);

        // NULL dispatchMode fire（历史/默认路径，必须保持可认领）
        NopJobFire nullFire = saveFire("fire-null-1", schedule, null, Map.of());
        NopJobTask nullTask = saveTask("task-null-1", nullFire, null, null, TASK_STATUS_WAITING);

        // bestFit fire：归因 WAITING 任务（DB 模式 dedicated pool 语义，不属 remote）
        NopJobFire bestFitFire = saveFire("fire-bestfit-1", schedule, "bestFit", Map.of("serviceName", "svc-b"));
        NopJobTask bestFitTask = saveTask("task-bestfit-1", bestFitFire, "worker-b", "h2:8080", TASK_STATUS_WAITING);

        // 非归因分支：不含 remote 任务，含 NULL 任务与 bestFit 归因任务（既有语义不变）
        List<NopJobTask> waiting = taskStore.fetchWaitingTasks(100, null);
        List<String> ids = waiting.stream().map(NopJobTask::getJobTaskId).collect(Collectors.toList());
        assertTrue(!ids.contains(remoteTask.getJobTaskId()), "non-attribution fetch must exclude remote tasks");
        assertTrue(ids.contains(nullTask.getJobTaskId()), "NULL dispatchMode tasks must remain claimable");
        assertTrue(ids.contains(bestFitTask.getJobTaskId()), "bestFit attribution unchanged (dedicated pool semantics)");

        // 归因分支：同样排除 remote
        List<NopJobTask> attributed = taskStore.fetchWaitingTasks(100, null, "worker-b", true);
        List<String> attributedIds = attributed.stream().map(NopJobTask::getJobTaskId).collect(Collectors.toList());
        assertTrue(!attributedIds.contains(remoteTask.getJobTaskId()), "attribution fetch must exclude remote tasks");
        assertTrue(attributedIds.contains(bestFitTask.getJobTaskId()));
    }

    @Test
    public void testFetchRemoteTasksOnlyRemoteAttributed() {
        NopJobSchedule schedule = newSchedule("sched-isol-2", "job-isol-2");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire remoteFire = saveFire("fire-remote-2", schedule, "remote", Map.of("serviceName", "svc-a"));
        NopJobTask remoteWaiting = saveTask("task-rw-2", remoteFire, "worker-1", "h1:8080", TASK_STATUS_WAITING);
        NopJobTask remoteRunning = saveTask("task-rr-2", remoteFire, "worker-1", "h1:8080", TASK_STATUS_RUNNING);
        remoteRunning.setStartTime(new Timestamp(System.currentTimeMillis() - 60_000));
        taskStore.updateTask(remoteRunning);

        NopJobFire bestFitFire = saveFire("fire-bf-2", schedule, "bestFit", Map.of("serviceName", "svc-b"));
        saveTask("task-bf-2", bestFitFire, "worker-b", "h2:8080", TASK_STATUS_WAITING);

        // fetchRemoteWaitingTasks：只返回 remote+归因 WAITING，不含 bestFit 归因
        List<NopJobTask> remoteWaitingTasks = taskStore.fetchRemoteWaitingTasks(100, null);
        List<String> rwIds = remoteWaitingTasks.stream().map(NopJobTask::getJobTaskId).collect(Collectors.toList());
        assertTrue(rwIds.contains(remoteWaiting.getJobTaskId()));
        assertTrue(!rwIds.contains("task-bf-2"), "bestFit tasks must not be picked by remote scanner");

        // fetchRemoteRunningTasks：RUNNING_LIKE remote 任务
        List<NopJobTask> remoteRunningTasks = taskStore.fetchRemoteRunningTasks(100, null, null, null);
        List<String> rrIds = remoteRunningTasks.stream().map(NopJobTask::getJobTaskId).collect(Collectors.toList());
        assertTrue(rrIds.contains(remoteRunning.getJobTaskId()));
        assertTrue(!rrIds.contains(remoteWaiting.getJobTaskId()), "WAITING tasks are not RUNNING_LIKE");
    }

    @Test
    public void testResetStaleWaitingTasksKeepsRemoteAttribution() {
        NopJobSchedule schedule = newSchedule("sched-isol-3", "job-isol-3");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire remoteFire = saveFire("fire-remote-3", schedule, "remote", Map.of("serviceName", "svc-a"));
        NopJobTask remoteTask = saveTask("task-rs-3", remoteFire, "worker-1", "h1:8080", TASK_STATUS_WAITING);

        NopJobFire nullFire = saveFire("fire-null-3", schedule, null, Map.of());
        NopJobTask nullTask = saveTask("task-ns-3", nullFire, "worker-x", "h3:8080", TASK_STATUS_WAITING);

        long deadline = System.currentTimeMillis() + 60_000; // 都过期
        List<NopJobTask> stale = taskStore.resetStaleWaitingTasks(100, null, deadline, null, null);

        List<String> staleIds = stale.stream().map(NopJobTask::getJobTaskId).collect(Collectors.toList());
        assertTrue(staleIds.contains(nullTask.getJobTaskId()), "NULL dispatchMode stale task should be reset");
        assertTrue(!staleIds.contains(remoteTask.getJobTaskId()),
                "remote task attribution must survive stale-reset (otherwise remote scanner loses it)");

        NopJobTask reloaded = taskStore.loadTask(remoteTask.getJobTaskId());
        assertEquals("worker-1", reloaded.getWorkerInstanceId(), "remote attribution preserved");
        assertNull(taskStore.loadTask(nullTask.getJobTaskId()).getWorkerInstanceId(),
                "non-remote stale task re-dispatched to competing-consumer");
    }

    // ---- fixtures ----

    private NopJobSchedule newSchedule(String id, String jobName) {
        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobScheduleId(id);
        schedule.setNamespaceId("default");
        schedule.setGroupId("default");
        schedule.setJobName(jobName);
        schedule.setDisplayName(jobName);
        schedule.setScheduleStatus(SCHEDULE_STATUS_ENABLED);
        schedule.setExecutorKind("rpc");
        schedule.setTriggerType(TRIGGER_TYPE_FIXED_RATE);
        schedule.setRepeatIntervalMs(1000L);
        schedule.setPartitionIndex((short) 1);
        schedule.setFireCount(0L);
        schedule.setActiveFireCount(0);
        schedule.setVersion(0L);
        schedule.setCreatedBy("test");
        schedule.setCreateTime(new Timestamp(System.currentTimeMillis()));
        schedule.setUpdatedBy("test");
        schedule.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        return schedule;
    }

    private NopJobFire saveFire(String id, NopJobSchedule schedule, String dispatchMode, Map<String, Object> params) {
        NopJobFire fire = new NopJobFire();
        fire.setJobFireId(id);
        fire.setJobScheduleId(schedule.getJobScheduleId());
        fire.setNamespaceId(schedule.getNamespaceId());
        fire.setGroupId(schedule.getGroupId());
        fire.setJobName(schedule.getJobName());
        fire.setTriggerSource(TRIGGER_SOURCE_SCHEDULE);
        fire.setScheduledFireTime(new Timestamp(System.currentTimeMillis()));
        fire.setFireStatus(FIRE_STATUS_WAITING);
        fire.setExecutorKind("rpc");
        fire.setDispatchMode(dispatchMode);
        fire.setPartitionIndex((short) 1);
        fire.getJobParamsSnapshotComponent().set_jsonValue(params);
        fire.setVersion(0L);
        fire.setCreatedBy("test");
        fire.setCreateTime(new Timestamp(System.currentTimeMillis()));
        fire.setUpdatedBy("test");
        fire.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(fire);
        return fire;
    }

    private NopJobTask saveTask(String id, NopJobFire fire, String workerInstanceId, String targetHost, int status) {
        NopJobTask task = new NopJobTask();
        task.setJobTaskId(id);
        task.setJobFireId(fire.getJobFireId());
        task.setTaskNo(1);
        task.setTaskStatus(status);
        task.setPartitionIndex(fire.getPartitionIndex());
        task.setWorkerInstanceId(workerInstanceId);
        task.setTargetHost(targetHost);
        task.setVersion(0L);
        task.setCreatedBy("test");
        task.setCreateTime(new Timestamp(System.currentTimeMillis()));
        task.setUpdatedBy("test");
        task.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(task);
        return task;
    }
}

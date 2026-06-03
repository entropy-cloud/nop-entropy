package io.nop.job.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.biz.INopJobFireBiz;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static io.nop.job.service.NopJobErrors.ERR_JOB_FIRE_CANCEL_NOT_ALLOWED;
import static io.nop.job.service.NopJobErrors.ERR_JOB_FIRE_RERUN_NOT_ALLOWED;
import static io.nop.job.service.NopJobErrors.ERR_JOB_FIRE_RERUN_DISCARDED;
import static io.nop.job.service.NopJobErrors.ERR_JOB_SCHEDULE_MANUAL_TRIGGER_NOT_ALLOWED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopJobFireBizModel extends JunitBaseTestCase {
    private static final int SCHEDULE_STATUS_ENABLED = 10;
    private static final int FIRE_STATUS_WAITING = 0;
    private static final int FIRE_STATUS_RUNNING = 20;
    private static final int FIRE_STATUS_CANCELED = 60;
    private static final int TASK_STATUS_RUNNING = 20;
    private static final int TASK_STATUS_SUCCESS = 30;
    private static final int TASK_STATUS_CANCELED = 60;
    private static final String EXECUTOR_KIND_TEST = "test";
    private static final int TRIGGER_TYPE_FIXED_RATE = 2;
    private static final int TRIGGER_TYPE_FIXED_DELAY = 3;
    private static final int TRIGGER_SOURCE_SCHEDULE = 1;
    private static final int TRIGGER_SOURCE_MANUAL = 2;
    private static final int BLOCK_STRATEGY_DISCARD = 1;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopJobFireBiz fireBiz;

    @Test
    public void testCancelWaitingFireUpdatesFireAndSchedule() {
        long now = System.currentTimeMillis();
        Timestamp nextFireTime = new Timestamp(now + 60_000L);
        Timestamp scheduledFireTime = new Timestamp(now - 1_000L);

        NopJobSchedule schedule = newSchedule("schedule-fire-cancel-1", "job-fire-cancel-1");
        schedule.setFireCount(1L);
        schedule.setActiveFireCount(1);
        schedule.setLastFireTime(scheduledFireTime);
        schedule.setNextFireTime(nextFireTime);
        saveSchedule(schedule);

        NopJobFire fire = newFire("fire-cancel-1", schedule, FIRE_STATUS_WAITING, TRIGGER_SOURCE_SCHEDULE, scheduledFireTime);
        saveFire(fire);

        fireBiz.cancelFire(fire.getJobFireId(), newContext());

        NopJobFire savedFire = loadFire(fire.getJobFireId());
        assertEquals(FIRE_STATUS_CANCELED, savedFire.getFireStatus());
        assertEquals("JOB_CANCELED", savedFire.getErrorCode());
        assertNotNull(savedFire.getEndTime());

        NopJobSchedule savedSchedule = loadSchedule(schedule.getJobScheduleId());
        assertEquals(0, savedSchedule.getActiveFireCount());
        assertEquals(_NopJobCoreConstants.FIRE_STATUS_CANCELED, savedSchedule.getLastFireStatus());
        assertEquals(nextFireTime, savedSchedule.getNextFireTime());
        assertEquals(savedFire.getEndTime(), savedSchedule.getLastEndTime());
    }

    @Test
    public void testCancelRunningFireCancelsActiveTask() {
        long now = System.currentTimeMillis();

        NopJobSchedule schedule = newSchedule("schedule-fire-cancel-2", "job-fire-cancel-2");
        schedule.setFireCount(1L);
        schedule.setActiveFireCount(1);
        saveSchedule(schedule);

        NopJobFire fire = newFire("fire-cancel-2", schedule, FIRE_STATUS_RUNNING,
                TRIGGER_SOURCE_SCHEDULE, new Timestamp(now - 2_000L));
        fire.setStartTime(new Timestamp(now - 1_500L));
        saveFire(fire);

        NopJobTask task = newTask("task-cancel-1", fire, TASK_STATUS_RUNNING);
        task.setStartTime(new Timestamp(now - 1_000L));
        saveTask(task);

        fireBiz.cancelFire(fire.getJobFireId(), newContext());

        NopJobTask savedTask = loadTask(task.getJobTaskId());
        assertEquals(TASK_STATUS_CANCELED, savedTask.getTaskStatus());
        assertEquals("JOB_CANCELED", savedTask.getErrorCode());
        assertNotNull(savedTask.getEndTime());

        NopJobFire savedFire = loadFire(fire.getJobFireId());
        assertEquals(FIRE_STATUS_CANCELED, savedFire.getFireStatus());
        assertEquals("JOB_CANCELED", savedFire.getErrorCode());
    }

    @Test
    public void testCancelScheduledFixedDelayFireAdvancesNextFireTime() {
        long now = System.currentTimeMillis();
        Timestamp scheduledFireTime = new Timestamp(now - 1_000L);

        NopJobSchedule schedule = newSchedule("schedule-fire-cancel-3", "job-fire-cancel-3");
        schedule.setTriggerType(TRIGGER_TYPE_FIXED_DELAY);
        schedule.setFireCount(1L);
        schedule.setActiveFireCount(1);
        schedule.setLastFireTime(scheduledFireTime);
        schedule.setNextFireTime(null);
        saveSchedule(schedule);

        NopJobFire fire = newFire("fire-cancel-3", schedule, FIRE_STATUS_WAITING, TRIGGER_SOURCE_SCHEDULE, scheduledFireTime);
        saveFire(fire);

        fireBiz.cancelFire(fire.getJobFireId(), newContext());

        NopJobSchedule savedSchedule = loadSchedule(schedule.getJobScheduleId());
        assertNotNull(savedSchedule.getLastEndTime());
        assertNotNull(savedSchedule.getNextFireTime());
        assertEquals(savedSchedule.getLastEndTime().getTime() + schedule.getRepeatIntervalMs(),
                savedSchedule.getNextFireTime().getTime());
    }

    @Test
    public void testCancelManualFixedDelayFireDoesNotMoveScheduleCursor() {
        long now = System.currentTimeMillis();
        Timestamp nextFireTime = new Timestamp(now + 60_000L);

        NopJobSchedule schedule = newSchedule("schedule-fire-cancel-4", "job-fire-cancel-4");
        schedule.setTriggerType(TRIGGER_TYPE_FIXED_DELAY);
        schedule.setFireCount(2L);
        schedule.setActiveFireCount(1);
        schedule.setNextFireTime(nextFireTime);
        saveSchedule(schedule);

        NopJobFire fire = newFire("fire-cancel-4", schedule, FIRE_STATUS_WAITING,
                TRIGGER_SOURCE_MANUAL, new Timestamp(now));
        saveFire(fire);

        fireBiz.cancelFire(fire.getJobFireId(), newContext());

        NopJobSchedule savedSchedule = loadSchedule(schedule.getJobScheduleId());
        assertEquals(nextFireTime, savedSchedule.getNextFireTime());
        assertEquals(0, savedSchedule.getActiveFireCount());
    }

    @Test
    public void testCancelRunningFireRejectedWhenTaskAlreadyFinished() {
        long now = System.currentTimeMillis();

        NopJobSchedule schedule = newSchedule("schedule-fire-cancel-5", "job-fire-cancel-5");
        schedule.setFireCount(1L);
        schedule.setActiveFireCount(1);
        saveSchedule(schedule);

        NopJobFire fire = newFire("fire-cancel-5", schedule, FIRE_STATUS_RUNNING,
                TRIGGER_SOURCE_SCHEDULE, new Timestamp(now - 2_000L));
        saveFire(fire);

        NopJobTask task = newTask("task-cancel-2", fire, TASK_STATUS_SUCCESS);
        task.setStartTime(new Timestamp(now - 1_500L));
        task.setEndTime(new Timestamp(now - 1_000L));
        task.setDurationMs(500L);
        saveTask(task);

        NopException error = assertThrows(NopException.class,
                () -> fireBiz.cancelFire(fire.getJobFireId(), newContext()));
        assertEquals(ERR_JOB_FIRE_CANCEL_NOT_ALLOWED.getErrorCode(), error.getErrorCode());
    }

    @Test
    public void testRerunFireCreatesRecoveryFireFromSourceSnapshots() {
        long now = System.currentTimeMillis();
        Timestamp nextFireTime = new Timestamp(now + 60_000L);

        NopJobSchedule schedule = newSchedule("schedule-fire-rerun-1", "job-fire-rerun-1");
        schedule.setFireCount(1L);
        schedule.setActiveFireCount(0);
        schedule.setNextFireTime(nextFireTime);
        schedule.setExecutorKind("currentInvoker");
        schedule.setJobParams(JsonTool.stringify(Map.of("k", "current")));
        saveSchedule(schedule);

        NopJobFire sourceFire = newFire("fire-rerun-1", schedule, _NopJobCoreConstants.FIRE_STATUS_FAILED,
                TRIGGER_SOURCE_MANUAL, new Timestamp(now - 5_000L));
        sourceFire.setJobParamsSnapshot(JsonTool.stringify(Map.of("k", "source", "x", 1)));
        sourceFire.setExecutorKind("sourceInvoker");
        sourceFire.setRetryPolicyId("retry-policy-1");
        saveFire(sourceFire);

        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName("bob");

        fireBiz.rerunFire(sourceFire.getJobFireId(), context);

        List<NopJobFire> fires = findFiresBySchedule(schedule.getJobScheduleId());
        assertEquals(2, fires.size());

        NopJobFire rerunFire = fires.stream()
                .filter(fire -> !sourceFire.getJobFireId().equals(fire.getJobFireId()))
                .findFirst()
                .orElseThrow();

        assertNotEquals(sourceFire.getJobFireId(), rerunFire.getJobFireId());
        assertEquals(_NopJobCoreConstants.TRIGGER_SOURCE_RECOVERY, rerunFire.getTriggerSource());
        assertEquals(FIRE_STATUS_WAITING, rerunFire.getFireStatus());
        assertEquals("bob", rerunFire.getTriggeredBy());
        assertEquals(JsonTool.parseMap(schedule.getJobParams()), JsonTool.parseMap(rerunFire.getJobParamsSnapshot()));
        assertEquals(schedule.getExecutorKind(), rerunFire.getExecutorKind());
        assertEquals(schedule.getRetryPolicyId(), rerunFire.getRetryPolicyId());
        assertNotNull(rerunFire.getScheduledFireTime());

        NopJobSchedule savedSchedule = loadSchedule(schedule.getJobScheduleId());
        assertEquals(2L, savedSchedule.getFireCount());
        assertEquals(1, savedSchedule.getActiveFireCount());
        assertEquals(nextFireTime, savedSchedule.getNextFireTime());
        assertEquals(schedule.getLastFireTime(), savedSchedule.getLastFireTime());
    }

    @Test
    public void test_rerunFireUsesCurrentScheduleParams() {
        long now = System.currentTimeMillis();
        Timestamp nextFireTime = new Timestamp(now + 60_000L);

        NopJobSchedule schedule = newSchedule("schedule-fire-rerun-cp", "job-fire-rerun-cp");
        schedule.setFireCount(1L);
        schedule.setActiveFireCount(0);
        schedule.setNextFireTime(nextFireTime);
        schedule.setExecutorKind("scheduleExecutor");
        schedule.setRetryPolicyId("scheduleRetryPolicy");
        schedule.setJobParams(JsonTool.stringify(Map.of("k", "scheduleValue", "newKey", "newVal")));
        saveSchedule(schedule);

        NopJobFire sourceFire = newFire("fire-rerun-cp", schedule, _NopJobCoreConstants.FIRE_STATUS_FAILED,
                TRIGGER_SOURCE_MANUAL, new Timestamp(now - 5_000L));
        sourceFire.setJobParamsSnapshot(JsonTool.stringify(Map.of("k", "staleSource")));
        sourceFire.setExecutorKind("staleExecutor");
        sourceFire.setRetryPolicyId("staleRetryPolicy");
        saveFire(sourceFire);

        fireBiz.rerunFire(sourceFire.getJobFireId(), newContext());

        List<NopJobFire> fires = findFiresBySchedule(schedule.getJobScheduleId());
        NopJobFire rerunFire = fires.stream()
                .filter(f -> !sourceFire.getJobFireId().equals(f.getJobFireId()))
                .findFirst()
                .orElseThrow();

        Map<String, Object> rerunParams = JsonTool.parseMap(rerunFire.getJobParamsSnapshot());
        assertEquals("scheduleValue", rerunParams.get("k"),
                "rerun fire should use current schedule params, not stale source fire params");
        assertEquals("newVal", rerunParams.get("newKey"));
        assertEquals("scheduleExecutor", rerunFire.getExecutorKind(),
                "rerun fire should use current schedule executorKind");
        assertEquals("scheduleRetryPolicy", rerunFire.getRetryPolicyId(),
                "rerun fire should use current schedule retryPolicyId");
    }

    @Test
    public void testRerunFireRejectedForNonTerminalStatus() {
        NopJobSchedule schedule = newSchedule("schedule-fire-rerun-2", "job-fire-rerun-2");
        saveSchedule(schedule);

        NopJobFire runningFire = newFire("fire-rerun-2", schedule, FIRE_STATUS_RUNNING,
                TRIGGER_SOURCE_SCHEDULE, new Timestamp(System.currentTimeMillis()));
        saveFire(runningFire);

        NopException error = assertThrows(NopException.class,
                () -> fireBiz.rerunFire(runningFire.getJobFireId(), newContext()));
        assertEquals(ERR_JOB_FIRE_RERUN_NOT_ALLOWED.getErrorCode(), error.getErrorCode());
    }

    @Test
    public void testRerunFireRejectedForArchivedAndCompletedSchedule() {
        NopJobSchedule archivedSchedule = newSchedule("schedule-fire-rerun-3", "job-fire-rerun-3");
        archivedSchedule.setScheduleStatus(_NopJobCoreConstants.SCHEDULE_STATUS_ARCHIVED);
        saveSchedule(archivedSchedule);

        NopJobFire archivedFire = newFire("fire-rerun-3", archivedSchedule, _NopJobCoreConstants.FIRE_STATUS_SUCCESS,
                TRIGGER_SOURCE_SCHEDULE, new Timestamp(System.currentTimeMillis()));
        saveFire(archivedFire);

        NopException archivedError = assertThrows(NopException.class,
                () -> fireBiz.rerunFire(archivedFire.getJobFireId(), newContext()));
        assertEquals(ERR_JOB_SCHEDULE_MANUAL_TRIGGER_NOT_ALLOWED.getErrorCode(), archivedError.getErrorCode());

        NopJobSchedule completedSchedule = newSchedule("schedule-fire-rerun-4", "job-fire-rerun-4");
        completedSchedule.setScheduleStatus(_NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED);
        saveSchedule(completedSchedule);

        NopJobFire completedFire = newFire("fire-rerun-4", completedSchedule, _NopJobCoreConstants.FIRE_STATUS_CANCELED,
                TRIGGER_SOURCE_MANUAL, new Timestamp(System.currentTimeMillis()));
        saveFire(completedFire);

        NopException completedError = assertThrows(NopException.class,
                () -> fireBiz.rerunFire(completedFire.getJobFireId(), newContext()));
        assertEquals(ERR_JOB_SCHEDULE_MANUAL_TRIGGER_NOT_ALLOWED.getErrorCode(), completedError.getErrorCode());
    }

    @Test
    public void testRerunFireDiscardedWithBlockStrategyDiscard() {
        long now = System.currentTimeMillis();

        NopJobSchedule schedule = newSchedule("schedule-fire-rerun-discard", "job-fire-rerun-discard");
        schedule.setBlockStrategy(BLOCK_STRATEGY_DISCARD);
        schedule.setFireCount(1L);
        schedule.setActiveFireCount(1);
        saveSchedule(schedule);

        NopJobFire activeFire = newFire("fire-active-discard", schedule, FIRE_STATUS_WAITING, TRIGGER_SOURCE_SCHEDULE,
                new Timestamp(now));
        saveFire(activeFire);

        NopJobFire sourceFire = newFire("fire-rerun-discard", schedule, _NopJobCoreConstants.FIRE_STATUS_FAILED,
                TRIGGER_SOURCE_MANUAL, new Timestamp(now - 5_000L));
        saveFire(sourceFire);

        NopException error = assertThrows(NopException.class,
                () -> fireBiz.rerunFire(sourceFire.getJobFireId(), newContext()));
        assertEquals(ERR_JOB_FIRE_RERUN_DISCARDED.getErrorCode(), error.getErrorCode());
    }

    private IServiceContext newContext() {
        return new ServiceContextImpl();
    }

    private NopJobSchedule saveSchedule(NopJobSchedule schedule) {
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);
        return schedule;
    }

    private NopJobFire saveFire(NopJobFire fire) {
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(fire);
        return fire;
    }

    private NopJobTask saveTask(NopJobTask task) {
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(task);
        return task;
    }

    private NopJobSchedule loadSchedule(String id) {
        return daoProvider.daoFor(NopJobSchedule.class).getEntityById(id);
    }

    private NopJobFire loadFire(String id) {
        return daoProvider.daoFor(NopJobFire.class).getEntityById(id);
    }

    private NopJobTask loadTask(String id) {
        return daoProvider.daoFor(NopJobTask.class).getEntityById(id);
    }

    private List<NopJobFire> findFiresBySchedule(String scheduleId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("jobScheduleId", scheduleId));
        return daoProvider.daoFor(NopJobFire.class).findAllByQuery(query);
    }

    private NopJobSchedule newSchedule(String id, String jobName) {
        long now = System.currentTimeMillis();

        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobScheduleId(id);
        schedule.setNamespaceId("default");
        schedule.setGroupId("default");
        schedule.setJobName(jobName);
        schedule.setDisplayName(jobName);
        schedule.setScheduleStatus(SCHEDULE_STATUS_ENABLED);
        schedule.setExecutorKind(EXECUTOR_KIND_TEST);
        schedule.setJobParams(JsonTool.stringify(Map.of("k", "v")));
        schedule.setTriggerType(TRIGGER_TYPE_FIXED_RATE);
        schedule.setRepeatIntervalMs(1000L);
        schedule.setPartitionIndex((short) 1);
        schedule.setFireCount(0L);
        schedule.setActiveFireCount(0);
        schedule.setNextFireTime(new Timestamp(now + 1_000L));
        schedule.setVersion(0L);
        schedule.setCreatedBy("test");
        schedule.setCreateTime(new Timestamp(now));
        schedule.setUpdatedBy("test");
        schedule.setUpdateTime(new Timestamp(now));
        return schedule;
    }

    private NopJobFire newFire(String id, NopJobSchedule schedule, int fireStatus,
                               int triggerSource, Timestamp scheduledFireTime) {
        long now = System.currentTimeMillis();

        NopJobFire fire = new NopJobFire();
        fire.setJobFireId(id);
        fire.setJobScheduleId(schedule.getJobScheduleId());
        fire.setNamespaceId(schedule.getNamespaceId());
        fire.setGroupId(schedule.getGroupId());
        fire.setJobName(schedule.getJobName());
        fire.setTriggerSource(triggerSource);
        fire.setScheduledFireTime(scheduledFireTime);
        fire.setFireStatus(fireStatus);
        fire.setJobParamsSnapshot(JsonTool.stringify(Map.of("k", "v")));
        fire.setExecutorKind(schedule.getExecutorKind());
        fire.setPartitionIndex(schedule.getPartitionIndex());
        fire.setVersion(0L);
        fire.setCreatedBy("test");
        fire.setCreateTime(new Timestamp(now));
        fire.setUpdatedBy("test");
        fire.setUpdateTime(new Timestamp(now));
        return fire;
    }

    private NopJobTask newTask(String id, NopJobFire fire, int taskStatus) {
        long now = System.currentTimeMillis();

        NopJobTask task = new NopJobTask();
        task.setJobTaskId(id);
        task.setJobFireId(fire.getJobFireId());
        task.setTaskNo(1);
        task.setTaskStatus(taskStatus);
        task.setTaskPayload(JsonTool.stringify(Map.of("jobFireId", fire.getJobFireId())));
        task.setPartitionIndex(fire.getPartitionIndex());
        task.setVersion(0L);
        task.setCreatedBy("test");
        task.setCreateTime(new Timestamp(now));
        task.setUpdatedBy("test");
        task.setUpdateTime(new Timestamp(now));
        return task;
    }
}

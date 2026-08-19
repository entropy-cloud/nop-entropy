package io.nop.job.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.api.log.TaskLogEntry;
import io.nop.job.biz.INopJobTaskLogBiz;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.entity.NopJobTaskLog;
import io.nop.job.dao.store.IJobTaskStore;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static io.nop.job.api.JobApiErrors.ERR_JOB_LOG_INVALID_ENTRY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2254 Phase 4: reportTaskLog 批量上报端点——校验、批量落库、冗余展示列从 task/fire
 * 快照填充、与任务状态机解耦（task 缺失仍落库）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopJobTaskLogBizModel extends JunitBaseTestCase {

    private static final int SCHEDULE_STATUS_ENABLED = 10;
    private static final int FIRE_STATUS_RUNNING = 20;
    private static final int TASK_STATUS_RUNNING = 20;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopJobTaskLogBiz taskLogBiz;

    @Inject
    IJobTaskStore taskStore;

    @Test
    public void testReportTaskLog_batchInsertAndQueryByTaskId() {
        NopJobSchedule schedule = newSchedule("sched-log-1", "job-log-1");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire fire = newFire("fire-log-1", schedule);
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(fire);

        NopJobTask task = newTask("task-log-1", fire);
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(task);

        long now = System.currentTimeMillis();
        TaskLogEntry e1 = entry("task-log-1", now - 1000, "INFO", "step 1 started",
                Map.of("progress", 10));
        TaskLogEntry e2 = entry("task-log-1", now, "ERROR", "step 1 failed", null);

        int received = taskLogBiz.reportTaskLog(List.of(e1, e2), newContext());

        assertEquals(2, received);

        List<NopJobTaskLog> logs = daoProvider.daoFor(NopJobTaskLog.class).findAll().stream()
                .filter(l -> "task-log-1".equals(l.getJobTaskId()))
                .collect(java.util.stream.Collectors.toList());
        assertEquals(2, logs.size(), "both log rows persisted for the same taskId");

        NopJobTaskLog first = logs.stream()
                .filter(l -> l.getLogLevel() != null && l.getLogLevel() == 30)
                .findFirst().orElse(null);
        assertNotNull(first, "INFO row present");
        assertEquals("step 1 started", first.getLogMessage());
        assertEquals(fire.getJobFireId(), first.getJobFireId());
        assertEquals(fire.getJobScheduleId(), first.getJobScheduleId());
        assertEquals(fire.getJobName(), first.getJobName());
        assertEquals(fire.getGroupId(), first.getGroupId());

        NopJobTaskLog errorLog = logs.stream()
                .filter(l -> l.getLogLevel() != null && l.getLogLevel() == 50)
                .findFirst().orElse(null);
        assertNotNull(errorLog, "ERROR row present");
        assertEquals("step 1 failed", errorLog.getLogMessage());
    }

    @Test
    public void testReportTaskLog_missingTaskStillPersists() {
        long now = System.currentTimeMillis();
        TaskLogEntry e1 = entry("task-gone-1", now, "WARN", "orphan log line", null);

        int received = taskLogBiz.reportTaskLog(List.of(e1), newContext());

        assertEquals(1, received);
        List<NopJobTaskLog> logs = daoProvider.daoFor(NopJobTaskLog.class).findAll().stream()
                .filter(l -> "task-gone-1".equals(l.getJobTaskId()))
                .collect(java.util.stream.Collectors.toList());
        assertEquals(1, logs.size());
        assertNull(logs.get(0).getJobFireId(), "redundant columns left empty when task missing");
        assertEquals(40, logs.get(0).getLogLevel());
    }

    @Test
    public void testReportTaskLog_invalidEntryRejected() {
        long now = System.currentTimeMillis();

        TaskLogEntry noTaskId = entry(null, now, "INFO", "msg", null);
        assertThrows(NopException.class, () -> taskLogBiz.reportTaskLog(List.of(noTaskId), newContext()),
                "missing jobTaskId must fail explicitly");

        TaskLogEntry badLevel = entry("task-x", now, "TRACE2", "msg", null);
        NopException e = assertThrows(NopException.class,
                () -> taskLogBiz.reportTaskLog(List.of(badLevel), newContext()),
                "invalid logLevel must fail explicitly");
        assertEquals(ERR_JOB_LOG_INVALID_ENTRY.getErrorCode(), e.getErrorCode());

        TaskLogEntry noLogTime = entry("task-x", 0, "INFO", "msg", null);
        assertThrows(NopException.class, () -> taskLogBiz.reportTaskLog(List.of(noLogTime), newContext()),
                "missing logTime must fail explicitly");
    }

    @Test
    public void testReportTaskLog_emptyListReturnsZero() {
        int received = taskLogBiz.reportTaskLog(List.of(), newContext());
        assertEquals(0, received);
    }

    private TaskLogEntry entry(String taskId, long logTime, String level, String message,
                               Map<String, Object> payload) {
        TaskLogEntry entry = new TaskLogEntry();
        entry.setJobTaskId(taskId);
        entry.setLogTime(logTime);
        entry.setLogLevel(level);
        entry.setLogMessage(message);
        entry.setLogPayload(payload);
        return entry;
    }

    private IServiceContext newContext() {
        return new ServiceContextImpl();
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
        schedule.setExecutorKind("test");
        schedule.setTriggerType(_NopJobCoreConstants.TRIGGER_TYPE_FIXED_RATE);
        schedule.setRepeatIntervalMs(1000L);
        schedule.setPartitionIndex((short) 1);
        schedule.setFireCount(0L);
        schedule.setActiveFireCount(0);
        schedule.setNextFireTime(new Timestamp(now + 1000L));
        schedule.setVersion(0L);
        schedule.setCreatedBy("test");
        schedule.setCreateTime(new Timestamp(now));
        schedule.setUpdatedBy("test");
        schedule.setUpdateTime(new Timestamp(now));
        return schedule;
    }

    private NopJobFire newFire(String id, NopJobSchedule schedule) {
        long now = System.currentTimeMillis();
        NopJobFire fire = new NopJobFire();
        fire.setJobFireId(id);
        fire.setJobScheduleId(schedule.getJobScheduleId());
        fire.setNamespaceId(schedule.getNamespaceId());
        fire.setGroupId(schedule.getGroupId());
        fire.setJobName(schedule.getJobName());
        fire.setTriggerSource(_NopJobCoreConstants.TRIGGER_SOURCE_SCHEDULE);
        fire.setScheduledFireTime(new Timestamp(now - 1000));
        fire.setFireStatus(FIRE_STATUS_RUNNING);
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

    private NopJobTask newTask(String id, NopJobFire fire) {
        long now = System.currentTimeMillis();
        NopJobTask task = new NopJobTask();
        task.setJobTaskId(id);
        task.setJobFireId(fire.getJobFireId());
        task.setTaskNo(1);
        task.setTaskStatus(TASK_STATUS_RUNNING);
        task.setPartitionIndex(fire.getPartitionIndex());
        task.setVersion(0L);
        task.setCreatedBy("test");
        task.setCreateTime(new Timestamp(now));
        task.setUpdatedBy("test");
        task.setUpdateTime(new Timestamp(now));
        return task;
    }
}

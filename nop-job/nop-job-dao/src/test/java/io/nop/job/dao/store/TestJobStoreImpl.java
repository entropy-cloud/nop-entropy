package io.nop.job.dao.store;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestJobStoreImpl extends JunitBaseTestCase {
    private static final int SCHEDULE_STATUS_ENABLED = 10;
    private static final int FIRE_STATUS_WAITING = 0;
    private static final int FIRE_STATUS_CANCELED = 60;
    private static final int FIRE_STATUS_RUNNING = 20;
    private static final int TASK_STATUS_WAITING = 0;
    private static final int TASK_STATUS_CANCELED = 60;
    private static final String EXECUTOR_KIND_TEST = "test";
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
    public void testFetchAndLockSchedules() {
        NopJobSchedule schedule = newSchedule("schedule-1", "job-1");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        List<NopJobSchedule> dueSchedules = scheduleStore.fetchDueSchedules(10, IntRangeSet.parse("1"));
        assertEquals(1, dueSchedules.size());

        List<NopJobSchedule> locked = scheduleStore.tryLockSchedulesForPlan(dueSchedules, "planner-1", 1000);
        assertEquals(1, locked.size());
        assertNotNull(locked.get(0).getNextFireTime());
    }

    @Test
    public void testInsertFireAndTaskFlow() {
        NopJobSchedule schedule = newSchedule("schedule-2", "job-2");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire fire = newFire("fire-1", schedule);
        scheduleStore.insertFireAndAdvanceSchedule(schedule, fire, new Timestamp(System.currentTimeMillis() + 60000),
                FIRE_STATUS_WAITING);

        List<NopJobFire> waitingFires = fireStore.fetchWaitingFires(10, IntRangeSet.parse("1"));
        assertEquals(1, waitingFires.size());

        List<NopJobFire> lockedFires = fireStore.tryLockFiresForDispatch(waitingFires, "dispatcher-1", 1000);
        assertEquals(1, lockedFires.size());
        assertEquals("dispatcher-1", lockedFires.get(0).getDispatchInstanceId());

        NopJobTask task = newTask("task-1", fire);
        fireStore.insertTaskAndMarkFireDispatching(lockedFires.get(0), task);

        NopJobTask savedTask = taskStore.loadTask("task-1");
        assertNotNull(savedTask);
        assertEquals(TASK_STATUS_WAITING, savedTask.getTaskStatus());

        NopJobFire savedFire = fireStore.loadFire("fire-1");
        assertEquals(FIRE_STATUS_RUNNING, savedFire.getFireStatus());
    }

    @Test
    public void testCanceledDispatchingFireDoesNotCreateTask() {
        NopJobSchedule schedule = newSchedule("schedule-3", "job-3");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire fire = newFire("fire-2", schedule);
        scheduleStore.insertFireAndAdvanceSchedule(schedule, fire, new Timestamp(System.currentTimeMillis() + 60000),
                FIRE_STATUS_WAITING);

        List<NopJobFire> waitingFires = fireStore.fetchWaitingFires(10, IntRangeSet.parse("1"));
        List<NopJobFire> lockedFires = fireStore.tryLockFiresForDispatch(waitingFires, "dispatcher-2", 1000);
        assertEquals(1, lockedFires.size());

        assertEquals(true, fireStore.cancelFire(fire.getJobFireId()));

        NopJobTask task = newTask("task-2", fire);
        fireStore.insertTaskAndMarkFireDispatching(lockedFires.get(0), task);

        List<NopJobTask> tasks = taskStore.findTasksByFireId(fire.getJobFireId());
        assertEquals(0, tasks.size());

        NopJobFire savedFire = fireStore.loadFire(fire.getJobFireId());
        assertEquals(FIRE_STATUS_CANCELED, savedFire.getFireStatus());

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(schedule.getJobScheduleId());
        assertEquals(0, savedSchedule.getActiveFireCount());
        assertEquals(FIRE_STATUS_CANCELED, savedSchedule.getLastFireStatus());
    }

    private NopJobSchedule newSchedule(String id, String jobName) {
        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobScheduleId(id);
        schedule.setNamespaceId("default");
        schedule.setGroupId("default");
        schedule.setJobName(jobName);
        schedule.setDisplayName(jobName);
        schedule.setScheduleStatus(SCHEDULE_STATUS_ENABLED);
        schedule.setExecutorKind(EXECUTOR_KIND_TEST);
        schedule.setExecutorKind("testInvoker");
        schedule.getJobParamsComponent().set_jsonValue(Map.of("k", "v"));
        schedule.setTriggerType(TRIGGER_TYPE_FIXED_RATE);
        schedule.setRepeatIntervalMs(1000L);
        schedule.setPartitionIndex((short) 1);
        schedule.setFireCount(0L);
        schedule.setActiveFireCount(0);
        schedule.setNextFireTime(new Timestamp(System.currentTimeMillis() - 1000));
        schedule.setVersion(0L);
        schedule.setCreatedBy("test");
        schedule.setCreateTime(new Timestamp(System.currentTimeMillis()));
        schedule.setUpdatedBy("test");
        schedule.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        return schedule;
    }

    private NopJobFire newFire(String id, NopJobSchedule schedule) {
        NopJobFire fire = new NopJobFire();
        fire.setJobFireId(id);
        fire.setJobScheduleId(schedule.getJobScheduleId());
        fire.setNamespaceId(schedule.getNamespaceId());
        fire.setGroupId(schedule.getGroupId());
        fire.setJobName(schedule.getJobName());
        fire.setTriggerSource(TRIGGER_SOURCE_SCHEDULE);
        fire.setScheduledFireTime(new Timestamp(System.currentTimeMillis()));
        fire.setFireStatus(FIRE_STATUS_WAITING);
        fire.getJobParamsSnapshotComponent().set_jsonValue(Map.of("k", "v"));
        fire.setExecutorKind(schedule.getExecutorKind());
        fire.setPartitionIndex(schedule.getPartitionIndex());
        fire.setVersion(0L);
        fire.setCreatedBy("test");
        fire.setCreateTime(new Timestamp(System.currentTimeMillis()));
        fire.setUpdatedBy("test");
        fire.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        return fire;
    }

    private NopJobTask newTask(String id, NopJobFire fire) {
        NopJobTask task = new NopJobTask();
        task.setJobTaskId(id);
        task.setJobFireId(fire.getJobFireId());
        task.setTaskNo(1);
        task.setTaskStatus(TASK_STATUS_WAITING);
        task.getTaskPayloadComponent().set_jsonValue(Map.of("jobFireId", fire.getJobFireId()));
        task.setPartitionIndex(fire.getPartitionIndex());
        task.setVersion(0L);
        task.setCreatedBy("test");
        task.setCreateTime(new Timestamp(System.currentTimeMillis()));
        task.setUpdatedBy("test");
        task.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        return task;
    }
}

package io.nop.job.dao.store;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.orm.dao.IOrmEntityDao;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.nop.job.core._NopJobCoreConstants.FIRE_STATUS_CANCELED;
import static io.nop.job.core._NopJobCoreConstants.FIRE_STATUS_RUNNING;
import static io.nop.job.core._NopJobCoreConstants.FIRE_STATUS_SUCCESS;
import static io.nop.job.core._NopJobCoreConstants.FIRE_STATUS_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestJobFireStoreRace extends JunitBaseTestCase {
    private static final int SCHEDULE_STATUS_ENABLED = 10;
    private static final int FIRE_STATUS_WAITING = 0;
    private static final int FIRE_STATUS_DISPATCHING = 10;
    private static final int TRIGGER_TYPE_FIXED_RATE = 2;
    private static final int TRIGGER_SOURCE_SCHEDULE = 1;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IJobScheduleStore scheduleStore;

    @Inject
    IJobFireStore fireStore;

    @Test
    public void testCancelFireOnAlreadyCanceledFireReturnsFalse() {
        NopJobSchedule schedule = newSchedule("race-sched-1", "race-job-1");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire fire = newFire("race-fire-1", schedule);
        scheduleStore.insertFireAndAdvanceSchedule(schedule, fire,
                new Timestamp(System.currentTimeMillis() + 60000), FIRE_STATUS_WAITING);

        List<NopJobFire> waitingFires = fireStore.fetchWaitingFires(10, IntRangeSet.parse("1"));
        List<NopJobFire> lockedFires = fireStore.tryLockFiresForDispatch(waitingFires, "dispatcher-1", 1000);
        assertEquals(1, lockedFires.size());

        boolean firstCancel = fireStore.cancelFire(fire.getJobFireId());
        assert firstCancel;

        boolean secondCancel = fireStore.cancelFire(fire.getJobFireId());
        assertFalse(secondCancel, "cancelFire should return false for an already-canceled fire");

        NopJobFire reloaded = fireStore.loadFire(fire.getJobFireId());
        assertEquals(FIRE_STATUS_CANCELED, reloaded.getFireStatus());
    }

    @Test
    public void testCancelFireOnAlreadySucceededFireReturnsFalse() {
        NopJobSchedule schedule = newSchedule("race-sched-2", "race-job-2");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire fire = newFire("race-fire-2", schedule);
        scheduleStore.insertFireAndAdvanceSchedule(schedule, fire,
                new Timestamp(System.currentTimeMillis() + 60000), FIRE_STATUS_WAITING);

        List<NopJobFire> waitingFires = fireStore.fetchWaitingFires(10, IntRangeSet.parse("1"));
        List<NopJobFire> lockedFires = fireStore.tryLockFiresForDispatch(waitingFires, "dispatcher-2", 1000);
        assertEquals(1, lockedFires.size());

        NopJobTask task = newTask("race-task-2", fire);
        fireStore.insertTasksAndMarkFireDispatching(lockedFires.get(0), Collections.singletonList(task));

        NopJobFire runningFire = fireStore.loadFire(fire.getJobFireId());
        assertEquals(FIRE_STATUS_RUNNING, runningFire.getFireStatus());

        runningFire.setFireStatus(FIRE_STATUS_SUCCESS);
        runningFire.setEndTime(new Timestamp(System.currentTimeMillis()));
        daoProvider.daoFor(NopJobFire.class).updateEntityDirectly(runningFire);

        boolean cancelResult = fireStore.cancelFire(fire.getJobFireId());
        assertFalse(cancelResult, "cancelFire should return false for an already-completed fire");

        NopJobFire reloaded = fireStore.loadFire(fire.getJobFireId());
        assertEquals(FIRE_STATUS_SUCCESS, reloaded.getFireStatus(),
                "Fire status should remain SUCCESS, not overwritten to CANCELED");
    }

    @Test
    public void testCompleteFireDoesNotOverwriteCanceledFire() {
        NopJobSchedule schedule = newSchedule("race-sched-3", "race-job-3");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire fire = newFire("race-fire-3", schedule);
        scheduleStore.insertFireAndAdvanceSchedule(schedule, fire,
                new Timestamp(System.currentTimeMillis() + 60000), FIRE_STATUS_WAITING);

        List<NopJobFire> waitingFires = fireStore.fetchWaitingFires(10, IntRangeSet.parse("1"));
        List<NopJobFire> lockedFires = fireStore.tryLockFiresForDispatch(waitingFires, "dispatcher-3", 1000);
        assertEquals(1, lockedFires.size());

        NopJobTask task = newTask("race-task-3", fire);
        fireStore.insertTasksAndMarkFireDispatching(lockedFires.get(0), Collections.singletonList(task));

        NopJobFire runningFire = fireStore.loadFire(fire.getJobFireId());
        assertEquals(FIRE_STATUS_RUNNING, runningFire.getFireStatus());

        NopJobFire staleFire = fireStore.loadFire(fire.getJobFireId());
        staleFire.setFireStatus(FIRE_STATUS_SUCCESS);
        staleFire.setEndTime(new Timestamp(System.currentTimeMillis()));

        boolean cancelResult = fireStore.cancelFire(fire.getJobFireId());
        assert cancelResult;

        NopJobSchedule currentSchedule = scheduleStore.loadSchedule(schedule.getJobScheduleId());
        fireStore.completeFireAndUpdateSchedule(staleFire, currentSchedule);

        NopJobFire reloaded = fireStore.loadFire(fire.getJobFireId());
        assertEquals(FIRE_STATUS_CANCELED, reloaded.getFireStatus(),
                "completeFire should not overwrite a canceled fire");
    }

    @Test
    public void testCancelFireDetectsVersionMismatch() {
        NopJobSchedule schedule = newSchedule("race-sched-4", "race-job-4");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire fire = newFire("race-fire-4", schedule);
        scheduleStore.insertFireAndAdvanceSchedule(schedule, fire,
                new Timestamp(System.currentTimeMillis() + 60000), FIRE_STATUS_WAITING);

        List<NopJobFire> waitingFires = fireStore.fetchWaitingFires(10, IntRangeSet.parse("1"));
        List<NopJobFire> lockedFires = fireStore.tryLockFiresForDispatch(waitingFires, "dispatcher-4", 1000);
        assertEquals(1, lockedFires.size());

        NopJobTask task = newTask("race-task-4", fire);
        fireStore.insertTasksAndMarkFireDispatching(lockedFires.get(0), Collections.singletonList(task));

        NopJobFire runningFire = fireStore.loadFire(fire.getJobFireId());
        assertEquals(FIRE_STATUS_RUNNING, runningFire.getFireStatus());

        NopJobFire fireForCancel = fireStore.loadFire(fire.getJobFireId());

        IOrmEntityDao<NopJobFire> fireDao = (IOrmEntityDao<NopJobFire>) daoProvider.daoFor(NopJobFire.class);
        NopJobFire concurrentModification = fireDao.requireEntityById(fire.getJobFireId());
        concurrentModification.setFireStatus(FIRE_STATUS_TIMEOUT);
        concurrentModification.setEndTime(new Timestamp(System.currentTimeMillis()));
        fireDao.updateEntityDirectly(concurrentModification);

        NopJobFire reloadedBefore = fireStore.loadFire(fire.getJobFireId());
        assertEquals(FIRE_STATUS_TIMEOUT, reloadedBefore.getFireStatus());

        boolean cancelResult = fireStore.cancelFire(fireForCancel.getJobFireId());
        assertFalse(cancelResult,
                "cancelFire should detect version mismatch and return false");

        NopJobFire reloadedAfter = fireStore.loadFire(fire.getJobFireId());
        assertEquals(FIRE_STATUS_TIMEOUT, reloadedAfter.getFireStatus(),
                "Fire status should remain TIMEOUT, not overwritten to CANCELED");
    }

    private NopJobSchedule newSchedule(String id, String jobName) {
        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobScheduleId(id);
        schedule.setNamespaceId("default");
        schedule.setGroupId("default");
        schedule.setJobName(jobName);
        schedule.setDisplayName(jobName);
        schedule.setScheduleStatus(SCHEDULE_STATUS_ENABLED);
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
        task.setTaskStatus(0);
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

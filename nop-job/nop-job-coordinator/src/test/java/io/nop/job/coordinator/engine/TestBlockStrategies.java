package io.nop.job.coordinator.engine;

import io.nop.api.core.beans.IntRangeSet;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.store.IJobScheduleStore;
import io.nop.job.coordinator.metrics.EmptyJobPlannerMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestBlockStrategies {

    private JobPlannerScannerImpl planner;
    private CountingScheduleStore scheduleStore;
    private long currentTime;

    @BeforeEach
    void setUp() {
        planner = new JobPlannerScannerImpl();
        scheduleStore = new CountingScheduleStore();
        planner.setScheduleStore(scheduleStore);
        planner.setPlannerMetrics(new EmptyJobPlannerMetrics());
        currentTime = System.currentTimeMillis();
        scheduleStore.currentTime = currentTime;
    }

    @Test
    void testDiscard_skipsWhenActiveFireExists() {
        NopJobSchedule schedule = createSchedule("s1");
        schedule.setActiveFireCount(1);
        schedule.setBlockStrategy(_NopJobCoreConstants.BLOCK_STRATEGY_DISCARD);
        schedule.setTriggerType(_NopJobCoreConstants.TRIGGER_TYPE_FIXED_DELAY);
        scheduleStore.dueSchedules.add(schedule);

        planner.scanOnce();

        assertEquals(1, scheduleStore.skipCount);
        assertEquals(0, scheduleStore.insertCount);
    }

    @Test
    void testDiscard_createsFireWhenNoActiveFire() {
        NopJobSchedule schedule = createSchedule("s1");
        schedule.setActiveFireCount(0);
        schedule.setBlockStrategy(_NopJobCoreConstants.BLOCK_STRATEGY_DISCARD);
        schedule.setTriggerType(_NopJobCoreConstants.TRIGGER_TYPE_FIXED_DELAY);
        scheduleStore.dueSchedules.add(schedule);

        planner.scanOnce();

        assertEquals(0, scheduleStore.skipCount);
    }

    @Test
    void testOverlay_usesOverlayPathWhenActiveFire() {
        NopJobSchedule schedule = createSchedule("s1");
        schedule.setActiveFireCount(1);
        schedule.setBlockStrategy(_NopJobCoreConstants.BLOCK_STRATEGY_OVERLAY);
        schedule.setTriggerType(_NopJobCoreConstants.TRIGGER_TYPE_FIXED_DELAY);
        scheduleStore.dueSchedules.add(schedule);

        planner.scanOnce();

        assertEquals(1, scheduleStore.overlayCount);
    }

    @Test
    void testRecovery_usesRecoveryPath() {
        NopJobSchedule schedule = createSchedule("s1");
        schedule.setActiveFireCount(1);
        schedule.setBlockStrategy(_NopJobCoreConstants.BLOCK_STRATEGY_RECOVERY);
        schedule.setTriggerType(_NopJobCoreConstants.TRIGGER_TYPE_FIXED_DELAY);
        scheduleStore.dueSchedules.add(schedule);

        planner.scanOnce();

        assertEquals(1, scheduleStore.recoveryCount);
    }

    @Test
    void testRecovery_createsFireWhenNoActiveFire() {
        NopJobSchedule schedule = createSchedule("s1");
        schedule.setActiveFireCount(0);
        schedule.setBlockStrategy(_NopJobCoreConstants.BLOCK_STRATEGY_RECOVERY);
        schedule.setTriggerType(_NopJobCoreConstants.TRIGGER_TYPE_FIXED_DELAY);
        scheduleStore.dueSchedules.add(schedule);

        planner.scanOnce();

        assertEquals(0, scheduleStore.recoveryCount);
        assertEquals(1, scheduleStore.insertCount);
    }

    @Test
    void testConcurrent_usesInsertWhenNoBlockStrategy() {
        NopJobSchedule schedule = createSchedule("s1");
        schedule.setActiveFireCount(1);
        schedule.setBlockStrategy(null);
        schedule.setTriggerType(_NopJobCoreConstants.TRIGGER_TYPE_FIXED_DELAY);
        scheduleStore.dueSchedules.add(schedule);

        planner.scanOnce();

        assertEquals(1, scheduleStore.insertCount);
        assertEquals(0, scheduleStore.overlayCount);
        assertEquals(0, scheduleStore.recoveryCount);
    }

    private NopJobSchedule createSchedule(String id) {
        NopJobSchedule s = new NopJobSchedule();
        s.setJobScheduleId(id);
        s.setJobName("testJob");
        s.setScheduleStatus(_NopJobCoreConstants.SCHEDULE_STATUS_ENABLED);
        s.setNextFireTime(new Timestamp(currentTime));
        return s;
    }

    static class CountingScheduleStore implements IJobScheduleStore {
        final List<NopJobSchedule> dueSchedules = new ArrayList<>();
        long currentTime;
        int skipCount, overlayCount, insertCount, recoveryCount;

        @Override public long getCurrentTime() { return currentTime; }
        @Override public List<NopJobSchedule> fetchDueSchedules(int limit, IntRangeSet partitions) { return new ArrayList<>(dueSchedules); }
        @Override public List<NopJobSchedule> tryLockSchedulesForPlan(List<NopJobSchedule> schedules, String plannerInstanceId, long lockTimeoutMs) { return new ArrayList<>(schedules); }
        @Override public void advanceScheduleAfterSkip(NopJobSchedule schedule, Timestamp nextFireTime) { skipCount++; }
        @Override public void insertFireAndAdvanceSchedule(NopJobSchedule schedule, NopJobFire fire, Timestamp nextFireTime, Integer lastFireStatus) { insertCount++; }
        @Override public void overlayFireAndAdvanceSchedule(NopJobSchedule schedule, NopJobFire fire, Timestamp nextFireTime, Integer lastFireStatus) { overlayCount++; }
        @Override public void recoveryFireAndAdvanceSchedule(NopJobSchedule schedule, Timestamp nextFireTime) { recoveryCount++; }
        @Override public boolean insertManualFire(NopJobSchedule schedule, NopJobFire fire) { return true; }
        @Override public NopJobSchedule loadSchedule(String scheduleId) { return null; }
        @Override public NopJobSchedule tryLoadSchedule(String id) { return null; }
        @Override public Map<String, NopJobSchedule> batchLoadSchedules(Set<String> scheduleIds) { return Collections.emptyMap(); }
    }
}

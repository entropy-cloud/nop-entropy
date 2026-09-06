package io.nop.job.coordinator.engine;

import io.nop.api.core.beans.IntRangeSet;
import io.nop.job.coordinator.metrics.IJobDispatcherMetrics;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.FireScheduleOutcome;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check2 [P3-11]: dispatcher 的 dispatchedCount/metrics 与 store 的静默跳过分支对齐。
 * {@code insertTasksAndMarkFireDispatching} 对已被并发流转出 DISPATCHING 的 fire 返回 false
 * （未插入任务行），dispatcher 不得把它计入 dispatchedCount/onFiresDispatched。
 */
public class TestJobDispatcherDispatchedMetrics {

    /** 记录回调的 metrics 桩。 */
    static class RecordingMetrics implements IJobDispatcherMetrics {
        int waitingFires;
        int dispatchConflicts;
        int firesDispatched;
        int fireDispatchFailed;

        @Override
        public void onWaitingFires(int count) {
            waitingFires += count;
        }

        @Override
        public void onDispatchConflicts(int count) {
            dispatchConflicts += count;
        }

        @Override
        public void onFiresDispatched(int count) {
            firesDispatched += count;
        }

        @Override
        public void onFireDispatchFailed(int count) {
            fireDispatchFailed += count;
        }
    }

    /** 可配置 insert 返回值的 fire store 桩（其余方法空实现/直通）。 */
    static class StubFireStore implements IJobFireStore {
        final List<NopJobFire> waitingFires = new ArrayList<>();
        boolean insertResult;
        int insertCalls;

        StubFireStore(boolean insertResult) {
            this.insertResult = insertResult;
        }

        @Override
        public List<NopJobFire> fetchWaitingFires(int limit, IntRangeSet partitions) {
            return waitingFires;
        }

        @Override
        public List<NopJobFire> fetchRunningFires(int limit, IntRangeSet partitions) {
            return Collections.emptyList();
        }

        @Override
        public List<NopJobFire> tryLockFiresForDispatch(List<NopJobFire> fires, String dispatchInstanceId,
                                                         long lockTimeoutMs) {
            return fires;
        }

        @Override
        public boolean insertTasksAndMarkFireDispatching(NopJobFire fire, List<NopJobTask> tasks) {
            insertCalls++;
            return insertResult;
        }

        @Override
        public FireScheduleOutcome completeFireAndUpdateSchedule(NopJobFire fire, NopJobSchedule schedule) {
            return FireScheduleOutcome.bothUpdated();
        }

        @Override
        public FireScheduleOutcome cancelFire(String jobFireId) {
            return FireScheduleOutcome.bothFailed();
        }

        @Override
        public NopJobFire loadFire(String jobFireId) {
            return null;
        }

        @Override
        public NopJobFire getFireById(String jobFireId) {
            return null;
        }

        @Override
        public Map<String, NopJobFire> batchLoadFires(Set<String> fireIds) {
            return Map.of();
        }

        @Override
        public List<NopJobFire> fetchDispatchingFires(int limit, IntRangeSet partitions,
                                                      Timestamp cursorTime, String cursorId) {
            return Collections.emptyList();
        }

        @Override
        public boolean revertDispatchingFireToWaiting(NopJobFire fire, long backoffUntilMs) {
            return false;
        }

        @Override
        public boolean failFireWithoutSchedule(String jobFireId, String errorCode, String errorMessage) {
            return false;
        }
    }

    static class StubScheduleStore implements IJobScheduleStore {
        @Override
        public List<NopJobSchedule> fetchDueSchedules(int limit, IntRangeSet partitions) {
            return Collections.emptyList();
        }

        @Override
        public List<NopJobSchedule> tryLockSchedulesForPlan(List<NopJobSchedule> schedules,
                                                            String plannerInstanceId, long lockTimeoutMs) {
            return schedules;
        }

        @Override
        public void advanceScheduleAfterSkip(NopJobSchedule schedule, Timestamp nextFireTime) {
        }

        @Override
        public void insertFireAndAdvanceSchedule(NopJobSchedule schedule, NopJobFire fire,
                                                 Timestamp nextFireTime, Integer lastFireStatus) {
        }

        @Override
        public void overlayFireAndAdvanceSchedule(NopJobSchedule schedule, NopJobFire fire,
                                                  Timestamp nextFireTime, Integer lastFireStatus) {
        }

        @Override
        public void recoveryFireAndAdvanceSchedule(NopJobSchedule schedule, Timestamp nextFireTime) {
        }

        @Override
        public boolean insertManualFire(NopJobSchedule schedule, NopJobFire fire) {
            return true;
        }

        @Override
        public NopJobSchedule loadSchedule(String jobScheduleId) {
            return new NopJobSchedule();
        }

        @Override
        public NopJobSchedule tryLoadSchedule(String jobScheduleId) {
            return new NopJobSchedule();
        }

        @Override
        public Map<String, NopJobSchedule> batchLoadSchedules(Set<String> scheduleIds) {
            return Map.of();
        }

        @Override
        public long getCurrentTime() {
            return System.currentTimeMillis();
        }
    }

    private JobDispatcherScannerImpl newDispatcher(StubFireStore fireStore, RecordingMetrics metrics) {
        JobDispatcherScannerImpl dispatcher = new JobDispatcherScannerImpl();
        dispatcher.setFireStore(fireStore);
        dispatcher.setScheduleStore(new StubScheduleStore());
        dispatcher.setTaskBuilders(Map.of("single", fire -> List.of(new NopJobTask())));
        dispatcher.setDispatcherMetrics(metrics);
        return dispatcher;
    }

    private NopJobFire newDispatchableFire() {
        NopJobFire fire = new NopJobFire();
        fire.setJobFireId("fire-skip-1");
        fire.setDispatchMode("single");
        fire.getJobParamsSnapshotComponent().set_jsonValue(Map.of());
        return fire;
    }

    @Test
    public void testSilentlySkippedFireNotCountedAsDispatched() {
        StubFireStore fireStore = new StubFireStore(false);
        fireStore.waitingFires.add(newDispatchableFire());
        RecordingMetrics metrics = new RecordingMetrics();
        JobDispatcherScannerImpl dispatcher = newDispatcher(fireStore, metrics);

        boolean more = dispatcher.scanBatch();

        assertFalse(more, "waiting fires drained in one batch");
        assertEquals(1, fireStore.insertCalls, "store insert was attempted once");
        assertEquals(0, metrics.firesDispatched,
                "silently skipped fire must NOT be counted in onFiresDispatched");
        assertEquals(0, metrics.fireDispatchFailed, "silent skip is not a dispatch failure either");
    }

    @Test
    public void testActuallyDispatchedFireCounted() {
        StubFireStore fireStore = new StubFireStore(true);
        fireStore.waitingFires.add(newDispatchableFire());
        RecordingMetrics metrics = new RecordingMetrics();
        JobDispatcherScannerImpl dispatcher = newDispatcher(fireStore, metrics);

        dispatcher.scanBatch();

        assertEquals(1, fireStore.insertCalls);
        assertEquals(1, metrics.firesDispatched, "real dispatch is still counted");
        assertTrue(metrics.fireDispatchFailed == 0);
    }
}

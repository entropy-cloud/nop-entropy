package io.nop.job.dao.store;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.api.resource.ResourceVector;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestJobStoreImpl extends JunitBaseTestCase {
    private static final int SCHEDULE_STATUS_ENABLED = 10;
    private static final int FIRE_STATUS_WAITING = 0;
    private static final int FIRE_STATUS_CANCELED = 60;
    private static final int FIRE_STATUS_RUNNING = 20;
    private static final int TASK_STATUS_WAITING = 0;
    private static final int TASK_STATUS_CANCELED = 60;
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
        // check2 [P3-11]: DISPATCHING fire 正常路径返回 true（实际插入并推进到 RUNNING）
        assertTrue(fireStore.insertTasksAndMarkFireDispatching(lockedFires.get(0), Collections.singletonList(task)));

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

        assertEquals(true, fireStore.cancelFire(fire.getJobFireId()).fireUpdated());

        NopJobTask task = newTask("task-2", fire);
        // check2 [P3-11]: fire 已被并发流转出 DISPATCHING（此处为 CANCELED）时返回 false，
        // 调用方（dispatcher）据此不虚增 dispatchedCount/metrics
        assertFalse(fireStore.insertTasksAndMarkFireDispatching(lockedFires.get(0), Collections.singletonList(task)));

        List<NopJobTask> tasks = taskStore.findTasksByFireId(fire.getJobFireId());
        assertEquals(0, tasks.size());

        NopJobFire savedFire = fireStore.loadFire(fire.getJobFireId());
        assertEquals(FIRE_STATUS_CANCELED, savedFire.getFireStatus());

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(schedule.getJobScheduleId());
        assertEquals(0, savedSchedule.getActiveFireCount());
        assertEquals(FIRE_STATUS_CANCELED, savedSchedule.getLastFireStatus());
    }

    @Test
    public void testDispatchStartTimeIsCurrentTime() {
        NopJobSchedule schedule = newSchedule("schedule-ar1", "job-ar1");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire fire = newFire("fire-ar1", schedule);
        scheduleStore.insertFireAndAdvanceSchedule(schedule, fire,
                new Timestamp(System.currentTimeMillis() + 60000), FIRE_STATUS_WAITING);

        long lockTimeoutMs = 5000L;
        List<NopJobFire> waitingFires = fireStore.fetchWaitingFires(10, IntRangeSet.parse("1"));
        List<NopJobFire> lockedFires = fireStore.tryLockFiresForDispatch(waitingFires, "dispatcher-ar1", lockTimeoutMs);

        assertEquals(1, lockedFires.size());
        NopJobFire dispatched = lockedFires.get(0);

        long startTimeMs = dispatched.getStartTime().getTime();
        long approxNow = System.currentTimeMillis();

        assertTrue(Math.abs(startTimeMs - approxNow) < 5000,
                "startTime should be close to current time (dispatch time), not now + lockTimeoutMs");
    }

    /**
     * check2 [P2-1]: CLAIMED 认领时必须写入 startTime（claim 时刻），使该行对
     * {@code fetchRunningTasks}（超时扫描 + worker 存活链的入口）可见。此前 CLAIMED 行的
     * startTime 为 null 而被 {@code not(isNull(startTime))} 过滤排除——worker 在 CAS 认领后、
     * 置 RUNNING 前崩溃，任务对回收链路永久不可见（永不终结、所属 fire 永久 RUNNING）。
     */
    @Test
    public void testClaimedTaskVisibleToRunningScanViaClaimTime() {
        NopJobSchedule schedule = newSchedule("schedule-p21", "job-p21");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);
        NopJobFire fire = newFire("fire-p21", schedule);
        scheduleStore.insertFireAndAdvanceSchedule(schedule, fire,
                new Timestamp(System.currentTimeMillis() + 60000), FIRE_STATUS_WAITING);
        NopJobTask task = newTask("task-p21", fire);
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(task);

        List<NopJobTask> waiting = taskStore.fetchWaitingTasks(10, IntRangeSet.parse("1"));
        assertEquals(1, waiting.size());

        // 模拟 worker 认领后在置 RUNNING 前崩溃：任务停留在 CLAIMED
        List<NopJobTask> claimed = taskStore.tryLockTasksForExecute(waiting, "worker-gone", 1000);
        assertEquals(1, claimed.size());
        assertEquals(_NopJobCoreConstants.TASK_STATUS_CLAIMED, claimed.get(0).getTaskStatus());
        assertNotNull(claimed.get(0).getStartTime(),
                "claim must persist startTime (claim time) so the CLAIMED row is visible to recovery scans");

        // 关键断言：CLAIMED 行进入 fetchRunningTasks（存活链 SUSPICIOUS→TIMEOUT 的入口）
        List<NopJobTask> runningLike = taskStore.fetchRunningTasks(10, IntRangeSet.parse("1"), null, null);
        assertTrue(runningLike.stream().anyMatch(t -> "task-p21".equals(t.getJobTaskId())),
                "CLAIMED task (claimed by a worker that crashed before RUNNING) must be visible to "
                        + "fetchRunningTasks — otherwise no recovery path can ever see it");
    }

    @Test
    public void testRecoveryFireNoFailedFiresContainsAllFields() {
        NopJobSchedule schedule = newSchedule("schedule-ar2", "job-ar2");
        schedule.setRetryPolicyId("retry-abc");
        schedule.setJobParams("{\"p1\":\"v1\"}");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        scheduleStore.recoveryFireAndAdvanceSchedule(schedule,
                new Timestamp(System.currentTimeMillis() + 60000));

        List<NopJobFire> fires = fireStore.fetchWaitingFires(10, IntRangeSet.parse("1"));
        assertEquals(1, fires.size());

        NopJobFire recoveryFire = fires.get(0);
        assertEquals(_NopJobCoreConstants.TRIGGER_SOURCE_RECOVERY, recoveryFire.getTriggerSource());
        assertEquals("retry-abc", recoveryFire.getRetryPolicyId());
        assertNotNull(recoveryFire.getJobParamsSnapshot());
        assertTrue(recoveryFire.getJobParamsSnapshot().contains("p1"),
                "recovery fire should have jobParamsSnapshot from schedule");
        assertEquals(schedule.getExecutorKind(), recoveryFire.getExecutorKind());
    }

    // ============== sumReservedCost（Plan 212 Phase 2）==============

    /**
     * check2 [P3-1]: overlay/manual 路径的 activeFireCount 减法必须带 Math.max 下限。
     * 构造计数已向下漂移的 OVERLAY schedule（recorded=0 但实际 2 个 active fire），手动触发
     * overlay 取消 2 个 fire 后计数不得为负（负值使 shouldDiscard/shouldOverlay/shouldRecovery
     * 的 activeFireCount>0 判定永久失效，退化为无限并发执行）。
     */
    @Test
    public void testManualOverlayFireCountNeverNegative() {
        NopJobSchedule schedule = newSchedule("schedule-p31", "job-p31");
        schedule.setBlockStrategy(2); // BLOCK_STRATEGY_OVERLAY
        schedule.setActiveFireCount(0); // drifted low（外部写入/历史漂移）
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire active1 = newFire("fire-p31-1", schedule);
        active1.setFireStatus(FIRE_STATUS_RUNNING);
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(active1);
        NopJobFire active2 = newFire("fire-p31-2", schedule);
        active2.setFireStatus(FIRE_STATUS_RUNNING);
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(active2);

        NopJobFire manual = newFire("fire-p31-m", schedule);
        boolean inserted = scheduleStore.insertManualFire(schedule, manual);
        assertTrue(inserted);

        NopJobSchedule saved = scheduleStore.loadSchedule(schedule.getJobScheduleId());
        assertTrue(saved.getActiveFireCount() != null && saved.getActiveFireCount() >= 0,
                "activeFireCount must never go negative: " + saved.getActiveFireCount());
        assertEquals(1, saved.getActiveFireCount(),
                "max(0, 0-2 cancelled) + 1 new = 1 (floor applied to the subtraction)");
    }

    /**
     * 无匹配 worker 时返回 ZERO（不抛异常，不返回 null）。
     */
    @Test
    public void testSumReservedCostEmptyReturnsZero() {
        ResourceVector result = taskStore.sumReservedCost("worker-empty");
        assertEquals(ResourceVector.ZERO, result);
    }

    /**
     * 单个 WAITING task：cost 完整计入 reserved。
     */
    @Test
    public void testSumReservedCostSingleWaitingTask() {
        NopJobSchedule schedule = newSchedule("schedule-src-1", "job-src-1");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);
        NopJobFire fire = newFire("fire-src-1", schedule);
        scheduleStore.insertFireAndAdvanceSchedule(schedule, fire,
                new Timestamp(System.currentTimeMillis() + 60000), FIRE_STATUS_WAITING);

        NopJobTask task = newTask("task-src-1", fire);
        task.setWorkerInstanceId("worker-src");
        task.setCostCpu(500);
        task.setCostMemory(1024);
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(task);

        ResourceVector result = taskStore.sumReservedCost("worker-src");
        assertEquals(500, result.getCpu());
        assertEquals(1024, result.getMemory());
    }

    /**
     * 多个非终态 task 求和（WAITING + CLAIMED + SUSPICIOUS + RUNNING 全部计入）。
     */
    @Test
    public void testSumReservedCostMultipleActiveStatuses() {
        NopJobSchedule schedule = newSchedule("schedule-src-2", "job-src-2");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        String workerId = "worker-src-multi";

        saveCostTask("task-src-multi-w", schedule, workerId, _NopJobCoreConstants.TASK_STATUS_WAITING, 100, 200);
        saveCostTask("task-src-multi-c", schedule, workerId, _NopJobCoreConstants.TASK_STATUS_CLAIMED, 200, 300);
        saveCostTask("task-src-multi-s", schedule, workerId, _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS, 300, 400);
        saveCostTask("task-src-multi-r", schedule, workerId, _NopJobCoreConstants.TASK_STATUS_RUNNING, 400, 500);

        ResourceVector result = taskStore.sumReservedCost(workerId);
        // SUM = 100+200+300+400=1000 cpu, 200+300+400+500=1400 memory
        assertEquals(1000, result.getCpu());
        assertEquals(1400, result.getMemory());
    }

    /**
     * 终态 task（SUCCESS / FAILED / TIMEOUT / CANCELED）不计入 reserved。
     */
    @Test
    public void testSumReservedCostExcludesTerminalStatuses() {
        NopJobSchedule schedule = newSchedule("schedule-src-3", "job-src-3");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        String workerId = "worker-src-terminal";

        saveCostTask("task-src-term-r", schedule, workerId, _NopJobCoreConstants.TASK_STATUS_RUNNING, 500, 1024);
        // 这些终态不应计入
        saveCostTask("task-src-term-s", schedule, workerId, _NopJobCoreConstants.TASK_STATUS_SUCCESS, 9999, 9999);
        saveCostTask("task-src-term-f", schedule, workerId, _NopJobCoreConstants.TASK_STATUS_FAILED, 9999, 9999);
        saveCostTask("task-src-term-t", schedule, workerId, _NopJobCoreConstants.TASK_STATUS_TIMEOUT, 9999, 9999);
        saveCostTask("task-src-term-c", schedule, workerId, _NopJobCoreConstants.TASK_STATUS_CANCELED, 9999, 9999);

        ResourceVector result = taskStore.sumReservedCost(workerId);
        assertEquals(500, result.getCpu());
        assertEquals(1024, result.getMemory());
    }

    /**
     * SUSPICIOUS(15) 必须计入 reserved（design §3.3.4 关键约定）。
     */
    @Test
    public void testSumReservedCostSuspiciousCounted() {
        NopJobSchedule schedule = newSchedule("schedule-src-4", "job-src-4");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        String workerId = "worker-src-suspicious";
        saveCostTask("task-src-susp", schedule, workerId, _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS, 700, 2048);

        ResourceVector result = taskStore.sumReservedCost(workerId);
        assertEquals(700, result.getCpu());
        assertEquals(2048, result.getMemory());
    }

    /**
     * 其它 worker 的 task 不应被计入本 worker 的 reserved。
     */
    @Test
    public void testSumReservedCostIsolatesByWorkerId() {
        NopJobSchedule schedule = newSchedule("schedule-src-5", "job-src-5");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        saveCostTask("task-src-iso-mine", schedule, "worker-mine", _NopJobCoreConstants.TASK_STATUS_RUNNING, 300, 600);
        saveCostTask("task-src-iso-other", schedule, "worker-other", _NopJobCoreConstants.TASK_STATUS_RUNNING, 9999, 9999);

        ResourceVector mine = taskStore.sumReservedCost("worker-mine");
        assertEquals(300, mine.getCpu());
        assertEquals(600, mine.getMemory());

        ResourceVector other = taskStore.sumReservedCost("worker-other");
        assertEquals(9999, other.getCpu());
        assertEquals(9999, other.getMemory());

        ResourceVector absent = taskStore.sumReservedCost("worker-absent");
        assertEquals(ResourceVector.ZERO, absent);
    }

    /**
     * AR-83 守卫：Plan 267 Phase 2 在 worker 侧修正 double-count，但**不改**共享
     * {@code RESERVED_TASK_STATUSES}。dispatcher 侧 {@code sumReservedCostByWorker}
     * （best-fit 决策输入）仍必须包含 WAITING 任务成本。本用例验证该输入未被改变。
     */
    @Test
    public void testSumReservedCostByWorkerStillIncludesWaiting() {
        NopJobSchedule schedule = newSchedule("schedule-srcrbw", "job-srcrbw");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        // WAITING task attributed to worker-w1 (the bestFit dispatch scenario)
        saveCostTask("task-srcrbw-w", schedule, "worker-w1", _NopJobCoreConstants.TASK_STATUS_WAITING, 300, 500);
        // NULL workerInstanceId row must NOT be returned (per sql-lib contract)
        saveCostTask("task-srcrbw-null", schedule, null, _NopJobCoreConstants.TASK_STATUS_WAITING, 9999, 9999);

        List<WorkerReservedCost> rows = taskStore.sumReservedCostByWorker();
        assertEquals(1, rows.size(), "only worker-w1 row; null-attribution rows excluded");
        WorkerReservedCost w1 = rows.get(0);
        assertEquals("worker-w1", w1.getWorkerInstanceId());
        assertEquals(300, w1.getCpu());
        assertEquals(500, w1.getMemory());
    }

    // ========== AR-88: resetStaleWaitingTasks 重派发 ==========

    /**
     * AR-88：超时滞留的 WAITING 任务（归因给已下线 worker）被重派发：workerInstanceId 置 null、
     * 状态保持 WAITING（不判 FAILED）、可被 competing-consumer 再次认领；窗口内的 fresh 任务不动。
     */
    @Test
    public void testResetStaleWaitingTasksReDispatchesAndPreservesClaimable() {
        NopJobSchedule schedule = newSchedule("sched-ar88", "job-ar88");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        // stale: WAITING, attributed to gone worker, createTime forced old
        saveCostTask("task-ar88-stale", schedule, "worker-gone", _NopJobCoreConstants.TASK_STATUS_WAITING, 100, 200);
        NopJobTask stale = taskStore.loadTask("task-ar88-stale");
        stale.setCreateTime(new Timestamp(System.currentTimeMillis() - 600_000)); // 10 min ago
        daoProvider.daoFor(NopJobTask.class).updateEntityDirectly(stale);

        // fresh: WAITING, attributed to alive worker, createTime now
        saveCostTask("task-ar88-fresh", schedule, "worker-alive", _NopJobCoreConstants.TASK_STATUS_WAITING, 100, 200);

        long deadline = System.currentTimeMillis() - 300_000; // 5 min ago
        List<NopJobTask> reset = taskStore.resetStaleWaitingTasks(100, null, deadline, null, null);
        assertEquals(1, reset.size(), "only the stale task matches the deadline");

        NopJobTask reDispatched = taskStore.loadTask("task-ar88-stale");
        assertEquals(_NopJobCoreConstants.TASK_STATUS_WAITING, reDispatched.getTaskStatus(),
                "re-dispatch keeps WAITING (not FAILED) to preserve executable opportunity");
        assertNull(reDispatched.getWorkerInstanceId(),
                "stale task re-dispatched: workerInstanceId cleared back to competing-consumer");

        NopJobTask freshLoaded = taskStore.loadTask("task-ar88-fresh");
        assertEquals("worker-alive", freshLoaded.getWorkerInstanceId(),
                "fresh task (within window) must NOT be touched");

        // re-dispatched task is now claimable by any worker (appears in fetchWaitingTasks)
        List<NopJobTask> waiting = taskStore.fetchWaitingTasks(100, null);
        assertTrue(waiting.stream().anyMatch(t -> "task-ar88-stale".equals(t.getJobTaskId())),
                "re-dispatched task must be fetchable as a WAITING candidate");
    }

    // ========== Plan 213 Phase 3: enforceAttribution filter tests ==========

    @Test
    void testFetchWaitingTasksEnforceAttributionFiltersByWorker() {
        NopJobSchedule schedule = newSchedule("sched-attrib", "job-attrib");

        NopJobTask myTask = newTask("task-mine", newFire("fire-mine", schedule));
        myTask.setWorkerInstanceId("worker-A");
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(myTask);

        NopJobTask otherTask = newTask("task-other", newFire("fire-other", schedule));
        otherTask.setWorkerInstanceId("worker-B");
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(otherTask);

        NopJobTask nullTask = newTask("task-null", newFire("fire-null", schedule));
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(nullTask);

        List<NopJobTask> all = taskStore.fetchWaitingTasks(10, IntRangeSet.parse("1"));
        assertEquals(3, all.size(), "Without filter, all 3 WAITING tasks visible");

        List<NopJobTask> mine = taskStore.fetchWaitingTasks(
                10, IntRangeSet.parse("1"), "worker-A", true);
        assertEquals(2, mine.size(), "With enforceAttribution, only own + null-worker tasks visible");
        assertTrue(mine.stream().anyMatch(t -> "task-mine".equals(t.getJobTaskId())));
        assertTrue(mine.stream().anyMatch(t -> "task-null".equals(t.getJobTaskId())));
        assertTrue(mine.stream().noneMatch(t -> "task-other".equals(t.getJobTaskId())));
    }

    @Test
    void testFetchWaitingTasksEnforceAttributionFalseShowsAll() {
        NopJobSchedule schedule = newSchedule("sched-attrib2", "job-attrib2");

        NopJobTask myTask = newTask("task-mine2", newFire("fire-mine2", schedule));
        myTask.setWorkerInstanceId("worker-A");
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(myTask);

        NopJobTask otherTask = newTask("task-other2", newFire("fire-other2", schedule));
        otherTask.setWorkerInstanceId("worker-B");
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(otherTask);

        List<NopJobTask> result = taskStore.fetchWaitingTasks(
                10, IntRangeSet.parse("1"), "worker-A", false);
        assertEquals(2, result.size(), "enforceAttribution=false → competing-consumer, all tasks visible");
    }

    /**
     * Plan 213 Phase 3 scenario C: dedicated pool isolation.
     * A worker with enforceAttribution=true must NOT see single-mode tasks
     * (attributed to coordinator) — only its own partition tasks + unattributed tasks.
     */
    @Test
    void testDedicatedPoolIsolationScenarioC() {
        NopJobSchedule schedule = newSchedule("sched-pool-c", "job-pool-c");

        // Single-mode task attributed to coordinator (not to any worker)
        NopJobTask coordinatorTask = newTask("task-coordinator", newFire("fire-coord", schedule));
        coordinatorTask.setWorkerInstanceId("coordinator-host-1");
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(coordinatorTask);

        // Partition-mode task attributed to worker-A
        NopJobTask workerATask = newTask("task-worker-a", newFire("fire-wa", schedule));
        workerATask.setWorkerInstanceId("worker-A");
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(workerATask);

        // Unattributed task (no workerInstanceId)
        NopJobTask nullTask = newTask("task-unattributed", newFire("fire-null-c", schedule));
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(nullTask);

        // Worker-A with enforceAttribution=true: should see own + null, NOT coordinator's
        List<NopJobTask> visible = taskStore.fetchWaitingTasks(
                10, IntRangeSet.parse("1"), "worker-A", true);
        assertEquals(2, visible.size(), "Dedicated worker sees own partition task + unattributed task");
        assertTrue(visible.stream().anyMatch(t -> "task-worker-a".equals(t.getJobTaskId())));
        assertTrue(visible.stream().anyMatch(t -> "task-unattributed".equals(t.getJobTaskId())));
        assertTrue(visible.stream().noneMatch(t -> "task-coordinator".equals(t.getJobTaskId())),
                "Dedicated worker must NOT see single-mode coordinator-attributed task");
    }

    /**
     * Plan 214: priority-based ordering.
     * fetchWaitingTasks should return tasks ordered by priority DESC, then createTime ASC.
     */
    @Test
    void testFetchWaitingTasksOrderByPriority() {
        NopJobSchedule schedule = newSchedule("sched-prio", "job-prio");

        NopJobTask lowPrio = newTask("task-low", newFire("fire-low", schedule));
        lowPrio.setPriority(-5);
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(lowPrio);

        NopJobTask normalPrio = newTask("task-normal", newFire("fire-normal", schedule));
        normalPrio.setPriority(0);
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(normalPrio);

        NopJobTask highPrio = newTask("task-high", newFire("fire-high", schedule));
        highPrio.setPriority(10);
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(highPrio);

        List<NopJobTask> result = taskStore.fetchWaitingTasks(10, IntRangeSet.parse("1"));
        assertEquals(3, result.size());
        assertEquals("task-high", result.get(0).getJobTaskId(), "Highest priority task should come first");
        assertEquals("task-normal", result.get(1).getJobTaskId(), "Normal priority (0) second");
        assertEquals("task-low", result.get(2).getJobTaskId(), "Low priority (-5) last");
    }

    /**
     * Plan 214: backward compat — all priority=0 should maintain FIFO (createTime ASC).
     */
    @Test
    void testFetchWaitingTasksPriorityZeroMaintainsFIFO() {
        NopJobSchedule schedule = newSchedule("sched-fifo", "job-fifo");

        NopJobTask task1 = newTask("task-fifo-1", newFire("fire-fifo-1", schedule));
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(task1);

        NopJobTask task2 = newTask("task-fifo-2", newFire("fire-fifo-2", schedule));
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(task2);

        List<NopJobTask> result = taskStore.fetchWaitingTasks(10, IntRangeSet.parse("1"));
        assertEquals(2, result.size());
        assertEquals("task-fifo-1", result.get(0).getJobTaskId(), "Same priority → FIFO by createTime");
        assertEquals("task-fifo-2", result.get(1).getJobTaskId());
    }

    private void saveCostTask(String taskId, NopJobSchedule schedule, String workerId,
                              int taskStatus, int cpu, int memory) {
        NopJobFire fire = newFire("fire-" + taskId, schedule);
        scheduleStore.insertFireAndAdvanceSchedule(schedule, fire,
                new Timestamp(System.currentTimeMillis() + 60000), FIRE_STATUS_WAITING);

        NopJobTask task = newTask(taskId, fire);
        task.setWorkerInstanceId(workerId);
        task.setTaskStatus(taskStatus);
        task.setCostCpu(cpu);
        task.setCostMemory(memory);
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(task);
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

    // ========== Plan 338: cursor 分页谓词 focused tests ==========

    /**
     * Plan 338: fetchRunningTasks cursor 谓词。
     * 首批无 cursor 返回全部 RUNNING_LIKE task；第二批传 cursor 后应严格排除 cursor 之前（含同 key 同 id）的 row。
     */
    @Test
    public void testFetchRunningTasksCursorPaginatesStrictly() {
        NopJobSchedule schedule = newSchedule("sched-cursor", "job-cursor");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        // 3 RUNNING tasks，startTime 不同
        Timestamp t1 = new Timestamp(1000L);
        Timestamp t2 = new Timestamp(2000L);
        Timestamp t3 = new Timestamp(3000L);

        NopJobTask task1 = newTask("task-cursor-1", newFire("fire-cursor-1", schedule));
        task1.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_RUNNING);
        task1.setStartTime(t1);
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(task1);

        NopJobTask task2 = newTask("task-cursor-2", newFire("fire-cursor-2", schedule));
        task2.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_RUNNING);
        task2.setStartTime(t2);
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(task2);

        NopJobTask task3 = newTask("task-cursor-3", newFire("fire-cursor-3", schedule));
        task3.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_RUNNING);
        task3.setStartTime(t3);
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(task3);

        // 首批无 cursor：返回全部 3 条，按 startTime DESC 排序
        List<NopJobTask> firstBatch = taskStore.fetchRunningTasks(100, null, null, null);
        assertEquals(3, firstBatch.size(), "first batch without cursor returns all 3 RUNNING tasks");
        assertEquals("task-cursor-3", firstBatch.get(0).getJobTaskId(), "ordered by startTime DESC");
        assertEquals("task-cursor-2", firstBatch.get(1).getJobTaskId());
        assertEquals("task-cursor-1", firstBatch.get(2).getJobTaskId());

        // 第二批 cursor=(t3, task-cursor-3)：应排除 task3，只返回 task2 + task1
        List<NopJobTask> secondBatch = taskStore.fetchRunningTasks(100, null, t3, "task-cursor-3");
        assertEquals(2, secondBatch.size(), "cursor (t3, task-cursor-3) excludes task3");
        assertEquals("task-cursor-2", secondBatch.get(0).getJobTaskId());
        assertEquals("task-cursor-1", secondBatch.get(1).getJobTaskId());

        // 第三批 cursor=(t1, task-cursor-1)：应返回空
        List<NopJobTask> thirdBatch = taskStore.fetchRunningTasks(100, null, t1, "task-cursor-1");
        assertTrue(thirdBatch.isEmpty(), "cursor past last row returns empty");
    }

    /**
     * check 审计 [P1]：CLAIMED∈RUNNING_LIKE_STATUSES 但其 startTime 为 null（进入RUNNING前）。
     * 修复前满批尾部为null-start行时cursor推进为(null,id)，下一轮fetchRunningTasks抛
     * IllegalArgumentException使超时扫描整轮停滞。修复后null startTime行被排除
     * （tryMarkTimeout对null本就跳过，无覆盖损失）。
     */
    @Test
    public void testFetchRunningTasksExcludesClaimedNullStartTime() {
        NopJobSchedule schedule = newSchedule("sched-null-start", "job-null-start");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobTask claimed = newTask("task-claimed-null", newFire("fire-null-1", schedule));
        claimed.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_CLAIMED);
        claimed.setWorkerInstanceId("worker-a");
        // startTime 不设置（null）
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(claimed);

        NopJobTask running = newTask("task-running-ok", newFire("fire-null-2", schedule));
        running.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_RUNNING);
        running.setStartTime(new Timestamp(4000L));
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(running);

        List<NopJobTask> batch = taskStore.fetchRunningTasks(100, null, null, null);
        assertTrue(batch.stream().noneMatch(t -> "task-claimed-null".equals(t.getJobTaskId())),
                "CLAIMED task with null startTime must be excluded from timeout scan");
        assertTrue(batch.stream().anyMatch(t -> "task-running-ok".equals(t.getJobTaskId())),
                "RUNNING task with startTime must remain visible");
    }

    /**
     * Plan 338: cursor 校验——cursorId 非空但 cursorTime 为空必须抛 IllegalArgumentException。
     */
    @Test
    public void testFetchRunningTasksCursorValidationRejectsIdWithoutTime() {
        assertThrows(NopException.class,
                () -> taskStore.fetchRunningTasks(100, null, null, "orphan-id"),
                "cursorId without cursorTime must fail fast");
    }

    /**
     * Plan 338: resetStaleWaitingTasks cursor 谓词。
     * 第一批返回 N 条；第二批传 cursor 后应排除已 fetch 的 row。
     */
    @Test
    public void testResetStaleWaitingTasksCursorPaginatesStrictly() {
        NopJobSchedule schedule = newSchedule("sched-cursor2", "job-cursor2");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        // createTime 是 ORM 审计字段，insert 时会被强制覆写为当前时间，需先保存再回拨
        NopJobTask stale1 = newTask("task-stale-cursor-1", newFire("fire-stale-cursor-1", schedule));
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(stale1);
        stale1.setCreateTime(new Timestamp(System.currentTimeMillis() - 600_000));
        daoProvider.daoFor(NopJobTask.class).updateEntityDirectly(stale1);

        NopJobTask stale2 = newTask("task-stale-cursor-2", newFire("fire-stale-cursor-2", schedule));
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(stale2);
        stale2.setCreateTime(new Timestamp(System.currentTimeMillis() - 700_000));
        daoProvider.daoFor(NopJobTask.class).updateEntityDirectly(stale2);

        long deadline = System.currentTimeMillis() - 300_000;

        // 首批：返回 1 条（按 createTime DESC，更新的 stale1 在前）
        List<NopJobTask> firstBatch = taskStore.resetStaleWaitingTasks(1, null, deadline, null, null);
        assertEquals(1, firstBatch.size(), "first batch returns 1");
        assertEquals("task-stale-cursor-1", firstBatch.get(0).getJobTaskId(),
                "createTime DESC: newer task-stale-cursor-1 first");

        // 第二批 cursor=(stale1.createTime, task-stale-cursor-1)：应只剩 stale2
        List<NopJobTask> secondBatch = taskStore.resetStaleWaitingTasks(100, null, deadline,
                firstBatch.get(0).getCreateTime(), firstBatch.get(0).getJobTaskId());
        assertEquals(1, secondBatch.size(), "cursor advances past stale1");
        assertEquals("task-stale-cursor-2", secondBatch.get(0).getJobTaskId());
    }
}

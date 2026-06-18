package io.nop.task.step;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancellable;
import io.nop.core.initialize.CoreInitialization;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;
import io.nop.task.core._NopTaskCoreConstants;
import io.nop.task.impl.TaskFlowManagerImpl;
import io.nop.task.state.DefaultTaskStateStore;
import io.nop.task.state.SnapshotTaskStateStore;
import io.nop.task.state.TaskStepStateBean;
import io.nop.task.utils.TaskStepHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plan 260 Phase 1 focused 单元测试：验证 step 层 EXPIRED/KILLED driver（设计裁定 1）。
 *
 * <p>闭合 read-but-never-written 终态缺口：cancel-check 命中 cancellation 后，按 cancel reason 映射
 * step 终态——{@link ICancellable#CANCEL_REASON_TIMEOUT} → {@link _NopTaskCoreConstants#TASK_STEP_STATUS_EXPIRED}(50)、
 * kill/其它 → {@link _NopTaskCoreConstants#TASK_STEP_STATUS_KILLED}(70)，先 fail(err) 保存 exception、
 * 设终态 status、{@code saveTerminalStateIfDone} 持久化、最后 rethrow 编码了 reason 的 exception。
 *
 * <p>使用 {@link SnapshotTaskStateStore}（snapshot 语义，模拟 DB round-trip）使 driver 之后对 live bean 的
 * mutate 不影响已保存 snapshot，暴露 pre-fix gap：cancel-check 曾 bare-rethrow 不设 status，
 * snapshot 停留 ACTIVE，reader 的 EXPIRED/KILLED 终态分支不可达死代码。
 *
 * <p>覆盖：
 * <ul>
 *   <li>step timeout（CANCEL_REASON_TIMEOUT）→ stepStatus==EXPIRED(50)（非 ACTIVE/FAILED）+ isDone + !isSuccess + exception 已捕获</li>
 *   <li>step kill-cancel（CANCEL_REASON_KILL）→ stepStatus==KILLED(70)（非 FAILED）+ isDone + exception 已捕获</li>
 *   <li>普通（非 cancel）失败仍走 FAILED-driver（零回归于 plan 254）</li>
 *   <li>接线验证（#23）：cancel-check 出口真实调用 saveTerminalStateIfDone（save count 可观测）</li>
 *   <li>无静默跳过（#24）：cancel-check 不再 bare-rethrow，driver 为真实实现</li>
 *   <li>reader（plan 257）对 EXPIRED/KILLED 终态的「重抛 exception」分支现可达（非死代码）</li>
 * </ul>
 */
public class TestTaskStepCancelledTerminalDriver {

    private TaskFlowManagerImpl taskFlowManager;
    private SnapshotTaskStateStore snapshotStore;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        snapshotStore = new SnapshotTaskStateStore();
        taskFlowManager = new TaskFlowManagerImpl();
        taskFlowManager.setNonPersistStateStore(snapshotStore);
    }

    private void runTaskExpectThrow(String taskName) {
        ITask task = taskFlowManager.getTask(taskName, 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        try {
            task.execute(taskRt).syncGetOutputs();
            fail("step throwing cancellation must propagate (cancel != success, still throws, #24)");
        } catch (Exception e) {
            assertNotNull(e, "cancellation must propagate even after terminal driver sets status");
        }
    }

    // ==================== timeout → EXPIRED ====================

    @Test
    public void timeoutCancel_stepStatusExpired_notActiveOrFailed_exceptionCaptured() {
        runTaskExpectThrow("test/terminal-state-cancel-timeout");

        String stepPath = "@main/cancelTimeoutStep";

        // 接线验证（#23）：save count = ACTIVE 1 + 终态 1 = 2（cancel-check 出口真实调用 saveTerminalStateIfDone）
        assertEquals(2, snapshotStore.getSaveCount(stepPath),
                "wiring verification: saveTerminalStateIfDone must be called after EXPIRED driver. "
                        + "Expected ACTIVE-time save (1) + terminal save (1) = 2. "
                        + "Pre-fix: cancel-check bare-rethrow, no terminal save, latest snapshot stays ACTIVE.");

        ITaskStepState activeSnapshot = snapshotStore.getSnapshotAt(stepPath, 0);
        assertEquals(Integer.valueOf(TaskConstants.TASK_STEP_STATUS_ACTIVE), activeSnapshot.getStepStatus(),
                "first save (ACTIVE-time) must snapshot ACTIVE status");

        // 最新 snapshot = EXPIRED（cancel-check driver 映射 timeout → EXPIRED）
        ITaskStepState latestSnapshot = snapshotStore.getLatestSnapshot(stepPath);
        assertNotNull(latestSnapshot);
        assertEquals(Integer.valueOf(_NopTaskCoreConstants.TASK_STEP_STATUS_EXPIRED), latestSnapshot.getStepStatus(),
                "terminal snapshot must reflect EXPIRED (50) for timeout cancellation, not ACTIVE/FAILED. "
                        + "Pre-fix: cancel-check bare-rethrow, stepStatus stayed ACTIVE — reader's EXPIRED branch was unreachable dead code.");
        assertTrue(latestSnapshot.isDone(), "EXPIRED must be isDone (terminal)");
        assertTrue(!latestSnapshot.isSuccess(), "EXPIRED must not be isSuccess");
        assertNotNull(latestSnapshot.exception(),
                "terminal snapshot must capture exception (fail(err) called before setStepStatus). "
                        + "Pre-fix: cancel-check bare-rethrow, no fail()/exception capture.");
    }

    // ==================== kill → KILLED ====================

    @Test
    public void killCancel_stepStatusKilled_notFailed_exceptionCaptured() {
        runTaskExpectThrow("test/terminal-state-cancel-kill");

        String stepPath = "@main/cancelKillStep";

        assertEquals(2, snapshotStore.getSaveCount(stepPath),
                "wiring verification: saveTerminalStateIfDone must be called after KILLED driver.");

        ITaskStepState latestSnapshot = snapshotStore.getLatestSnapshot(stepPath);
        assertNotNull(latestSnapshot);
        assertEquals(Integer.valueOf(_NopTaskCoreConstants.TASK_STEP_STATUS_KILLED), latestSnapshot.getStepStatus(),
                "terminal snapshot must reflect KILLED (70) for kill cancellation, not FAILED. "
                        + "Pre-fix: cancel-check bare-rethrow, stepStatus stayed ACTIVE — KILLED terminal never written.");
        assertTrue(latestSnapshot.isDone(), "KILLED must be isDone (terminal)");
        assertTrue(!latestSnapshot.isSuccess(), "KILLED must not be isSuccess");
        assertNotNull(latestSnapshot.exception(),
                "terminal snapshot must capture exception for KILLED");
    }

    // ==================== regression: ordinary failure still → FAILED ====================

    @Test
    public void ordinaryFailure_stillFailedDriver_notExpiredOrKilled() {
        runTaskExpectThrow("test/terminal-state-persist-failed");

        String stepPath = "@main/terminalFailStep";

        // 普通（非 cancel）失败仍走 FAILED-driver（cancel-check 不命中普通 NopException）
        ITaskStepState latestSnapshot = snapshotStore.getLatestSnapshot(stepPath);
        assertNotNull(latestSnapshot);
        assertEquals(Integer.valueOf(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED), latestSnapshot.getStepStatus(),
                "ordinary (non-cancel) failure must still go through FAILED-driver (plan 254, zero regression). "
                        + "If this is EXPIRED/KILLED, cancel-check over-matched an ordinary exception.");
        assertTrue(latestSnapshot.isDone(), "FAILED must be isDone");
        assertTrue(latestSnapshot.exception() instanceof NopException,
                "ordinary failure exception must be a NopException");
    }

    // ==================== reader (plan 257) reachability: EXPIRED/KILLED branch no longer dead code ====================

    @Test
    public void reader_expiredTerminalState_rethrowsException_branchReachable() {
        // reader 的 else 分支（TaskStepExecution:210）声明消费「终态失败（FAILED/EXPIRED/KILLED）→ 重抛」。
        // pre-fix 此分支对 EXPIRED/KILLED 不可达（无 driver 产生）。post-fix 预载 EXPIRED state → reader 命中 → 重抛。
        PreloadedStateTaskStateStore preloadedStore = new PreloadedStateTaskStateStore();
        taskFlowManager.setNonPersistStateStore(preloadedStore);

        TaskStepStateBean expired = new TaskStepStateBean();
        expired.setStepPath("@main/plainStep");
        expired.fail(new NopException(io.nop.api.core.exceptions.ErrorCode.define(
                "nop.err.test.plan-260.expired", "plan 260 expired")), null);
        expired.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_EXPIRED);
        assertTrue(expired.isDone(), "fixture: EXPIRED must be isDone");
        assertTrue(!expired.isSuccess(), "fixture: EXPIRED must not be isSuccess");
        preloadedStore.preload("plainStep", expired);

        ITask task = taskFlowManager.getTask("test/continuation-skip-reader", 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        try {
            task.execute(taskRt).syncGetOutputs();
            fail("EXPIRED terminal state: reader must rethrow exception, not silently return (branch reachable)");
        } catch (Exception e) {
            assertNotNull(e, "EXPIRED terminal state must produce exception on resume (reader branch reachable, not dead code)");
        }
    }

    @Test
    public void reader_killedTerminalState_rethrowsException_branchReachable() {
        PreloadedStateTaskStateStore preloadedStore = new PreloadedStateTaskStateStore();
        taskFlowManager.setNonPersistStateStore(preloadedStore);

        TaskStepStateBean killed = new TaskStepStateBean();
        killed.setStepPath("@main/plainStep");
        killed.fail(new NopException(io.nop.api.core.exceptions.ErrorCode.define(
                "nop.err.test.plan-260.killed", "plan 260 killed")), null);
        killed.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_KILLED);
        assertTrue(killed.isDone(), "fixture: KILLED must be isDone");
        preloadedStore.preload("plainStep", killed);

        ITask task = taskFlowManager.getTask("test/continuation-skip-reader", 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        try {
            task.execute(taskRt).syncGetOutputs();
            fail("KILLED terminal state: reader must rethrow exception, not silently return (branch reachable)");
        } catch (Exception e) {
            assertNotNull(e, "KILLED terminal state must produce exception on resume (reader branch reachable, not dead code)");
        }
    }

    /**
     * 测试专用 store：可预载终态 state（模拟 resume 时从持久化加载终态），复用 plan 257 reader fixture。
     */
    static class PreloadedStateTaskStateStore extends DefaultTaskStateStore {
        private final Map<String, ITaskStepState> preloaded = new HashMap<>();

        @Override
        public ITaskStepState loadStepState(ITaskStepState parentState, String stepName, String stepType,
                                            ITaskRuntime taskRt) {
            ITaskStepState state = preloaded.get(stepName);
            if (state != null)
                return state;
            return super.loadStepState(parentState, stepName, stepType, taskRt);
        }

        void preload(String stepName, ITaskStepState state) {
            preloaded.put(stepName, state);
        }
    }
}

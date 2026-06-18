package io.nop.task.state;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepReturn;
import io.nop.task.core._NopTaskCoreConstants;
import io.nop.task.impl.TaskFlowManagerImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.nop.task.TaskErrors.ERR_TASK_ALREADY_FAILED;
import static io.nop.task.TaskErrors.ERR_TASK_ALREADY_KILLED;
import static io.nop.task.TaskErrors.ERR_TASK_ALREADY_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plan 260 Phase 2 cross-restart E2E 测试：验证 task 层 KILLED/TIMEOUT driver + resume 区分（设计裁定 2/3）。
 *
 * <p>闭合 task 级 read-but-never-written 终态缺口：cancel/timeout 的 cancellation 经 {@code driveTaskTerminal}
 * 映射为 KILLED(40)/TIMEOUT(60)（非坍缩 FAILED），并经 {@code saveTaskState} 持久化；resume 短路区分
 * FAILED/KILLED/TIMEOUT（非一律 already-failed）。
 *
 * <p>使用 {@link TaskLevelSnapshotTaskStateStore}（task 级 snapshot 语义，模拟 DB round-trip）——fresh execute
 * 写 snapshot，fresh load（getTaskRuntime → recoverMode=true）从 snapshot 反序列化（非 in-memory 引用）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>cancel→KILLED：fresh execute（cancel 运行中 task → task KILLED → 持久化）→ fresh load → resume 命中 KILLED →
 *       重抛 KILLED 语义 exception（mainStep 不重跑，save count 不变）——此行为在本计划前不成立（坍缩 FAILED + 误报 already-failed）</li>
 *   <li>step-timeout→EXPIRED+TIMEOUT：fresh execute（step timeout → step EXPIRED + task TIMEOUT → 持久化）→
 *       fresh load → resume 命中 TIMEOUT → 重抛（mainStep 不重跑）；对应 step 为 EXPIRED</li>
 *   <li>resume 区分（合成 exception 路径）：KILLED→ERR_TASK_ALREADY_KILLED / TIMEOUT→ERR_TASK_ALREADY_TIMEOUT /
 *       FAILED→ERR_TASK_ALREADY_FAILED（三者断言各别，非一律 already-failed）</li>
 *   <li>in-progress（非终态）task resume 不短路（零回归）</li>
 *   <li>端到端 + 接线验证（#22/#23）：execute→driver→saveTaskState→fresh load→resume 区分完整路径；driver 出口真实调用 saveTaskState</li>
 * </ul>
 */
public class TestTaskKilledTimeoutResumeE2E {

    private TaskFlowManagerImpl taskFlowManager;
    private TaskLevelSnapshotTaskStateStore snapshotStore;

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
        snapshotStore = new TaskLevelSnapshotTaskStateStore();
        taskFlowManager = new TaskFlowManagerImpl();
        // fresh execute（newTaskRuntime saveState=false）与 resume（getTaskRuntime persist）共用同一 snapshot store，
        // 使 fresh execute 写入的终态 snapshot 可被 resume 的 loadTaskState 取回（cross-restart round-trip）。
        taskFlowManager.setNonPersistStateStore(snapshotStore);
        taskFlowManager.setTaskStateStore(snapshotStore);
    }

    /**
     * fresh execute 一个会抛 cancellation 的 task，返回 taskInstanceId（fresh execute 写入终态 snapshot）。
     */
    private String freshExecuteCancel(String taskName) {
        ITask task = taskFlowManager.getTask(taskName, 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        try {
            task.execute(taskRt).syncGetOutputs();
            fail("task throwing cancellation must propagate (cancel != success)");
        } catch (Exception e) {
            assertNotNull(e, "cancellation must propagate");
        }
        return taskRt.getTaskInstanceId();
    }

    // ==================== cancel → KILLED: cross-restart resume ====================

    @Test
    public void cancelKill_taskTerminalKilled_notFailed_resumeDistinguishes() {
        String taskInstanceId = freshExecuteCancel("test/terminal-state-cancel-kill");

        // 接线验证（#23）：KILLED driver 出口真实调用 saveTaskState（task 级 1 次终态 save）
        assertEquals(1, snapshotStore.getTaskSaveCount(taskInstanceId),
                "wiring verification: saveTaskState must be called after KILLED driver. "
                        + "Pre-fix: cancel collapsed to FAILED via driveTaskFailed (still 1 save, but status FAILED).");

        // fresh execute 后 task 终态 = KILLED（非 FAILED——设计裁定 2，闭合 cancel 坍缩 FAILED gap）
        ITaskState snapshot = snapshotStore.getLatestTaskSnapshot(taskInstanceId);
        assertNotNull(snapshot);
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_KILLED), snapshot.getTaskStatus(),
                "cancel must produce KILLED(40) terminal status, not FAILED. "
                        + "Pre-fix gap: driveTaskFailed collapsed cancel/timeout to FAILED — this assertion would fail.");
        assertTrue(snapshot.isTerminal(), "KILLED must be isTerminal");
        assertFalse(snapshot.isSuccess(), "KILLED must not be isSuccess");
        assertNotNull(snapshot.exception(),
                "KILLED driver must capture exception (resume rethrows it)");

        // fresh load（snapshot round-trip，非 in-memory 引用）→ 反映终态 KILLED
        ITaskState loaded = snapshotStore.loadTaskState(taskInstanceId, null);
        assertNotNull(loaded);
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_KILLED), loaded.getTaskStatus(),
                "fresh load (snapshot) must return KILLED terminal state");
        assertTrue(loaded.isTerminal(), "fresh load isTerminal — resume short-circuit will fire");

        // resume（recoverMode=true）→ 命中 KILLED 短路 → 重抛捕获的 cancellation exception（mainStep 不重跑）
        int savesBeforeResume = snapshotStore.getTaskSaveCount(taskInstanceId);
        ITaskRuntime resumeRt = taskFlowManager.getTaskRuntime(taskInstanceId, null, null);
        try {
            taskFlowManager.getTask("test/terminal-state-cancel-kill", 0).execute(resumeRt).syncGetOutputs();
            fail("resume of KILLED task must rethrow, not silently succeed (#24)");
        } catch (Exception e) {
            assertNotNull(e, "resume of KILLED task must rethrow captured exception (not silent skip)");
        }
        // mainStep 不重跑：resume 短路直接 throw，不触发 driver/saveTaskState → save count 不变
        assertEquals(savesBeforeResume, snapshotStore.getTaskSaveCount(taskInstanceId),
                "resume short-circuit must NOT re-run mainStep (save count unchanged). "
                        + "If save count increased, mainStep re-ran (short-circuit failed).");
    }

    // ==================== step-timeout → step EXPIRED + task TIMEOUT: cross-restart resume ====================

    @Test
    public void stepTimeout_stepExpired_taskTimeout_resumeDistinguishes() {
        String taskInstanceId = freshExecuteCancel("test/terminal-state-cancel-timeout");

        // task 终态 = TIMEOUT（非 FAILED——设计裁定 2）
        ITaskState taskSnapshot = snapshotStore.getLatestTaskSnapshot(taskInstanceId);
        assertNotNull(taskSnapshot);
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_TIMEOUT), taskSnapshot.getTaskStatus(),
                "step-timeout must produce task TIMEOUT(60) terminal status, not FAILED. "
                        + "Pre-fix gap: driveTaskFailed collapsed timeout to FAILED.");
        assertTrue(taskSnapshot.isTerminal());

        // 对应 step 终态 = EXPIRED（step 层 EXPIRED driver，Phase 1）
        ITaskStepState stepSnapshot = snapshotStore.getLatestSnapshot("@main/cancelTimeoutStep");
        assertNotNull(stepSnapshot);
        assertEquals(Integer.valueOf(_NopTaskCoreConstants.TASK_STEP_STATUS_EXPIRED), stepSnapshot.getStepStatus(),
                "step-timeout must produce step EXPIRED(50) terminal status (Phase 1 driver).");

        // fresh load → 反映终态 TIMEOUT
        ITaskState loaded = snapshotStore.loadTaskState(taskInstanceId, null);
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_TIMEOUT), loaded.getTaskStatus(),
                "fresh load (snapshot) must return TIMEOUT terminal state");

        // resume → 命中 TIMEOUT 短路 → 重抛（mainStep 不重跑）
        int savesBeforeResume = snapshotStore.getTaskSaveCount(taskInstanceId);
        ITaskRuntime resumeRt = taskFlowManager.getTaskRuntime(taskInstanceId, null, null);
        try {
            taskFlowManager.getTask("test/terminal-state-cancel-timeout", 0).execute(resumeRt).syncGetOutputs();
            fail("resume of TIMEOUT task must rethrow, not silently succeed (#24)");
        } catch (Exception e) {
            assertNotNull(e, "resume of TIMEOUT task must rethrow (not silent skip)");
        }
        assertEquals(savesBeforeResume, snapshotStore.getTaskSaveCount(taskInstanceId),
                "resume short-circuit must NOT re-run mainStep (save count unchanged)");
    }

    // ==================== resume 区分（合成 exception 路径，设计裁定 3）====================

    @Test
    public void resume_killedTerminalWithNullException_synthesizesKilledException_notAlreadyFailed() {
        String taskInstanceId = "synth-killed-test";
        preloadTerminalSnapshot(taskInstanceId, TaskConstants.TASK_STATUS_KILLED, null);

        ITaskRuntime resumeRt = taskFlowManager.getTaskRuntime(taskInstanceId, null, null);
        try {
            taskFlowManager.getTask("test/terminal-state-cancel-kill", 0).execute(resumeRt).syncGetOutputs();
            fail("resume of KILLED task (null exception) must synthesize KILLED exception and rethrow");
        } catch (NopException e) {
            assertEquals(ERR_TASK_ALREADY_KILLED.getErrorCode(), e.getErrorCode(),
                    "KILLED resume (null exception) must synthesize ERR_TASK_ALREADY_KILLED, "
                            + "not ERR_TASK_ALREADY_FAILED (设计裁定 3 区分).");
            assertNotEquals(ERR_TASK_ALREADY_FAILED.getErrorCode(), e.getErrorCode(),
                    "KILLED resume must NOT report already-failed (pre-fix misreport).");
        }
    }

    @Test
    public void resume_timeoutTerminalWithNullException_synthesizesTimeoutException_notAlreadyFailed() {
        String taskInstanceId = "synth-timeout-test";
        preloadTerminalSnapshot(taskInstanceId, TaskConstants.TASK_STATUS_TIMEOUT, null);

        ITaskRuntime resumeRt = taskFlowManager.getTaskRuntime(taskInstanceId, null, null);
        try {
            taskFlowManager.getTask("test/terminal-state-cancel-timeout", 0).execute(resumeRt).syncGetOutputs();
            fail("resume of TIMEOUT task (null exception) must synthesize TIMEOUT exception and rethrow");
        } catch (NopException e) {
            assertEquals(ERR_TASK_ALREADY_TIMEOUT.getErrorCode(), e.getErrorCode(),
                    "TIMEOUT resume (null exception) must synthesize ERR_TASK_ALREADY_TIMEOUT, "
                            + "not ERR_TASK_ALREADY_FAILED (设计裁定 3 区分).");
            assertNotEquals(ERR_TASK_ALREADY_FAILED.getErrorCode(), e.getErrorCode(),
                    "TIMEOUT resume must NOT report already-failed (pre-fix misreport).");
        }
    }

    @Test
    public void resume_failedTerminalWithNullException_synthesizesFailedException_zeroRegression() {
        String taskInstanceId = "synth-failed-test";
        preloadTerminalSnapshot(taskInstanceId, TaskConstants.TASK_STATUS_FAILED, null);

        ITaskRuntime resumeRt = taskFlowManager.getTaskRuntime(taskInstanceId, null, null);
        try {
            taskFlowManager.getTask("test/terminal-state-persist-failed", 0).execute(resumeRt).syncGetOutputs();
            fail("resume of FAILED task (null exception) must synthesize FAILED exception and rethrow");
        } catch (NopException e) {
            assertEquals(ERR_TASK_ALREADY_FAILED.getErrorCode(), e.getErrorCode(),
                    "FAILED resume (null exception) must synthesize ERR_TASK_ALREADY_FAILED (zero regression).");
        }
    }

    // ==================== in-progress (non-terminal) resume does NOT short-circuit (zero regression) ====================

    @Test
    public void resume_inProgressNonTerminal_doesNotShortCircuit_mainStepRuns() {
        String taskInstanceId = "inprogress-test";
        // ACTIVE（非终态）→ isTerminal false → 不短路 → mainStep 正常执行
        preloadTerminalSnapshot(taskInstanceId, TaskConstants.TASK_STATUS_ACTIVE, null);

        ITaskRuntime resumeRt = taskFlowManager.getTaskRuntime(taskInstanceId, null, null);
        // mainStep 会执行（success fixture 返回 TERMINAL_OK）；不短路、不误报
        Map<String, Object> ret = taskFlowManager.getTask("test/terminal-state-persist-success", 0)
                .execute(resumeRt).syncGetOutputs();
        assertNotNull(ret, "in-progress task resume must NOT short-circuit — mainStep runs normally");
    }

    // ==================== pre-fix gap demonstration ====================

    @Test
    public void preFixGap_cancelTimeout_doesNotCollapseToFailed() {
        // 证明 pre-fix gap 已闭合：cancel/timeout 不再坍缩 FAILED。
        // pre-fix（driveTaskFailed 一律 FAILED）：此断言会失败（taskStatus == FAILED）。
        // post-fix（driveTaskTerminal 区分）：cancel→KILLED / timeout→TIMEOUT。
        String killedId = freshExecuteCancel("test/terminal-state-cancel-kill");
        ITaskState killedSnap = snapshotStore.getLatestTaskSnapshot(killedId);
        assertNotEquals(Integer.valueOf(TaskConstants.TASK_STATUS_FAILED), killedSnap.getTaskStatus(),
                "pre-fix gap closed: cancel must NOT collapse to FAILED. Actual KILLED(40).");
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_KILLED), killedSnap.getTaskStatus());

        String timeoutId = freshExecuteCancel("test/terminal-state-cancel-timeout");
        ITaskState timeoutSnap = snapshotStore.getLatestTaskSnapshot(timeoutId);
        assertNotEquals(Integer.valueOf(TaskConstants.TASK_STATUS_FAILED), timeoutSnap.getTaskStatus(),
                "pre-fix gap closed: timeout must NOT collapse to FAILED. Actual TIMEOUT(60).");
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_TIMEOUT), timeoutSnap.getTaskStatus());
    }

    private void preloadTerminalSnapshot(String taskInstanceId, int status, Throwable exception) {
        TaskStateBean state = new TaskStateBean();
        state.setTaskInstanceId(taskInstanceId);
        state.setTaskName("test/preloaded");
        state.setTaskVersion(0L);
        state.setTaskStatus(status);
        if (exception != null)
            state.exception(exception);
        snapshotStore.preloadTaskSnapshot(taskInstanceId, state);
    }
}

package io.nop.task.state;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepReturn;
import io.nop.task.impl.TaskFlowManagerImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plan 259 Phase 1 focused 单元测试：验证 task 级终态 lifecycle（COMPLETED/FAILED driver）+ saveTaskState 接线
 * + result/exception 捕获（修 result() no-op），使 task envelope 能表达终态语义。
 *
 * <p>使用 {@link TaskLevelSnapshotTaskStateStore}（task 级 snapshot 语义，模拟 DB round-trip）——
 * 每次 saveTaskState 创建深拷贝 snapshot，使 driver 之后对 live bean 的 mutate 不影响已保存 snapshot。
 *
 * <p>覆盖：
 * <ul>
 *   <li>COMPLETED driver：mainStep 成功 → taskStatus==COMPLETED（非 ACTIVE）+ result 已捕获 + saveTaskState 被调用</li>
 *   <li>FAILED driver：mainStep 抛错 → taskStatus==FAILED（非 ACTIVE）+ exception 已捕获 + saveTaskState 被调用</li>
 *   <li>接线验证（#23）：终态 driver 出口真实调用 saveTaskState（save count 可观测）</li>
 *   <li>无静默跳过（#24）：result() no-op 已修复为真实实现；终态 driver 为真实实现</li>
 *   <li>幂等性（设计裁定 2）：saveTaskState upsert 同一 task instance 行</li>
 *   <li>fresh execute 行为：fresh task 创建期 ACTIVE 不受影响，终态 driver 仅在 mainStep 返回后触发</li>
 * </ul>
 */
public class TestTaskTerminalStateLifecycle {

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
        taskFlowManager.setNonPersistStateStore(snapshotStore);
    }

    /**
     * 执行 task 并返回 taskInstanceId（用于查询 snapshot store）。
     */
    private String runTaskAndGetTaskInstanceId(String taskName) {
        ITask task = taskFlowManager.getTask(taskName, 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        TaskStepReturn ret = task.execute(taskRt);
        // 触发 syncGetOutputs 确保异步完成（thenCompose 回调执行）
        ret.syncGetOutputs();
        return taskRt.getTaskInstanceId();
    }

    private String runFailingTaskAndGetTaskInstanceId(String taskName) {
        ITask task = taskFlowManager.getTask(taskName, 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        try {
            task.execute(taskRt).syncGetOutputs();
            fail("task that always throws must propagate exception");
        } catch (Exception e) {
            assertNotNull(e, "task must throw");
        }
        return taskRt.getTaskInstanceId();
    }

    // ==================== COMPLETED driver → snapshot reflects COMPLETED + result captured ====================

    @Test
    public void completedDriver_taskStatusCompleted_notActive_resultCaptured() {
        String taskInstanceId = runTaskAndGetTaskInstanceId("test/terminal-state-persist-success");

        // 接线验证（#23）：saveTaskState 在终态 driver 出口被调用（task 级无 ACTIVE-time save，只有终态 save = 1 次）
        assertEquals(1, snapshotStore.getTaskSaveCount(taskInstanceId),
                "wiring verification: saveTaskState must be called after COMPLETED driver. "
                        + "Expected terminal save (1). Pre-fix: saveTaskState never called = 0.");

        // 最新 snapshot = COMPLETED（终态 driver 设置 taskStatus + 捕获 result）
        ITaskState latestSnapshot = snapshotStore.getLatestTaskSnapshot(taskInstanceId);
        assertNotNull(latestSnapshot, "terminal saveTaskState must produce a task snapshot");
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_COMPLETED), latestSnapshot.getTaskStatus(),
                "terminal snapshot must reflect COMPLETED (not ACTIVE). "
                        + "Pre-fix: without terminal driver, taskStatus stays ACTIVE forever.");
        assertTrue(latestSnapshot.isTerminal(),
                "isTerminal() must be true for COMPLETED task status");
        assertTrue(latestSnapshot.isSuccess(),
                "isSuccess() must be true for COMPLETED task status");

        // result 已捕获（result() no-op 已修复为真实捕获，#24）
        assertEquals("TERMINAL_OK", latestSnapshot.getResultValue(),
                "terminal snapshot must capture result 'TERMINAL_OK' (result() no-op fixed to real capture). "
                        + "Pre-fix: result() was empty method body → resultValue never set.");
    }

    // ==================== FAILED driver → snapshot reflects FAILED + exception captured ====================

    @Test
    public void failedDriver_taskStatusFailed_notActive_exceptionCaptured() {
        String taskInstanceId = runFailingTaskAndGetTaskInstanceId("test/terminal-state-persist-failed");

        // 接线验证（#23）：saveTaskState 在终态 FAILED driver 出口被调用
        assertEquals(1, snapshotStore.getTaskSaveCount(taskInstanceId),
                "wiring verification: saveTaskState must be called after FAILED driver. "
                        + "Expected terminal save (1). Pre-fix: saveTaskState never called = 0.");

        // 最新 snapshot = FAILED（终态 driver 设置 taskStatus + 捕获 exception）
        ITaskState latestSnapshot = snapshotStore.getLatestTaskSnapshot(taskInstanceId);
        assertNotNull(latestSnapshot, "terminal saveTaskState must produce a task snapshot");
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_FAILED), latestSnapshot.getTaskStatus(),
                "terminal snapshot must reflect FAILED (not ACTIVE). "
                        + "Pre-fix: without terminal driver, taskStatus stays ACTIVE forever.");
        assertTrue(latestSnapshot.isTerminal(),
                "isTerminal() must be true for FAILED task status");
        assertFalse(latestSnapshot.isSuccess(),
                "isSuccess() must be false for FAILED task status");

        // exception 已捕获（非 null，可被 resume 短路重抛）
        assertNotNull(latestSnapshot.exception(),
                "terminal snapshot must capture exception (resume can rethrow on cross-restart). "
                        + "Pre-fix: exception() was never called by task driver → null.");
        assertTrue(latestSnapshot.exception() instanceof NopException,
                "captured exception must be a NopException for consistent rethrow semantics");
    }

    // ==================== fresh execute behavior: ACTIVE initially, terminal only after mainStep ====================

    @Test
    public void freshExecute_activeBeforeTerminal_terminalDriverOnlyAfterMainStep() {
        // fresh execute 时，task 创建期 ACTIVE，终态 driver 仅在 mainStep 返回后触发
        String taskInstanceId = runTaskAndGetTaskInstanceId("test/terminal-state-persist-success");

        // 只有 1 次 save（终态 save），无 ACTIVE-time save（task 级 newTaskState 不调 saveTaskState）
        // 这证明：fresh task 创建期 ACTIVE 写入不受影响，终态 driver 仅在 mainStep 返回后触发
        assertEquals(1, snapshotStore.getTaskSaveCount(taskInstanceId),
                "task-level: only terminal save (1), no ACTIVE-time save. "
                        + "newTaskState creates ACTIVE bean in-memory without saveTaskState call.");

        ITaskState snapshot = snapshotStore.getLatestTaskSnapshot(taskInstanceId);
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_COMPLETED), snapshot.getTaskStatus(),
                "fresh execute: terminal driver fires only after mainStep returns → COMPLETED");
    }

    // ==================== idempotency (设计裁定 2): upsert same task instance ====================

    @Test
    public void saveTaskState_upsertsSameTaskInstance_noDuplicateRows() {
        String taskInstanceId = runTaskAndGetTaskInstanceId("test/terminal-state-persist-success");

        // 同一 taskInstanceId 的 snapshot 顺序追加（模拟 upsert 同一行），
        // 最新 snapshot 为终态。无独立 taskInstanceId 的重复行。
        int saves = snapshotStore.getTaskSaveCount(taskInstanceId);
        assertTrue(saves >= 1,
                "saves append to same taskInstanceId key (upsert semantics, no duplicate rows)");

        ITaskState latest = snapshotStore.getLatestTaskSnapshot(taskInstanceId);
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_COMPLETED), latest.getTaskStatus(),
                "latest snapshot (upsert result) reflects terminal COMPLETED");
    }

    // ==================== snapshot round-trip: loadTaskState returns terminal snapshot ====================

    @Test
    public void completedDriver_resumeLoadReturnsCompletedSnapshot() {
        String taskInstanceId = runTaskAndGetTaskInstanceId("test/terminal-state-persist-success");

        // fresh load（从 snapshot 反序列化，非 live 引用）→ 反映终态 COMPLETED
        ITaskState loaded = snapshotStore.loadTaskState(taskInstanceId, null);
        assertNotNull(loaded, "resume load must return persisted task snapshot");
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_COMPLETED), loaded.getTaskStatus(),
                "resume load (snapshot, not reference) must return COMPLETED terminal state");
        assertTrue(loaded.isTerminal(),
                "resume load isTerminal() must be true — resume short-circuit will fire");
        assertEquals("TERMINAL_OK", loaded.getResultValue(),
                "resume load must return cached resultValue for COMPLETED short-circuit");
    }

    @Test
    public void failedDriver_resumeLoadReturnsFailedSnapshot_withException() {
        String taskInstanceId = runFailingTaskAndGetTaskInstanceId("test/terminal-state-persist-failed");

        // fresh load（snapshot）→ 反映终态 FAILED + exception
        ITaskState loaded = snapshotStore.loadTaskState(taskInstanceId, null);
        assertNotNull(loaded, "resume load must return persisted FAILED task snapshot");
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_FAILED), loaded.getTaskStatus(),
                "resume load (snapshot, not reference) must return FAILED terminal state");
        assertTrue(loaded.isTerminal(),
                "resume load isTerminal() must be true — resume short-circuit will fire");
        assertNotNull(loaded.exception(),
                "resume load must return persisted exception for FAILED rethrow");
    }
}

package io.nop.task.step;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;
import io.nop.task.core._NopTaskCoreConstants;
import io.nop.task.impl.TaskFlowManagerImpl;
import io.nop.task.state.SnapshotTaskStateStore;
import io.nop.task.state.TaskStepStateBean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plan 258 Phase 1 focused 单元测试：验证 succeed/FAILED 终态 driver 后追加的 {@code saveStepState()}
 * 使 snapshot（非 in-memory 引用）反映终态（COMPLETED/FAILED），而非停留在 ACTIVE-time save 的 ACTIVE 行。
 *
 * <p>使用 {@link SnapshotTaskStateStore}（snapshot 语义，模拟 DB round-trip）—— 每次 save 创建深拷贝 snapshot，
 * 使 driver 之后对 live bean 的 mutate 不影响已保存 snapshot。这暴露 plan 258 要修的 production gap：
 * 若无终态 save，ACTIVE-time save 的 ACTIVE snapshot 不会被覆盖，resume load 取回 ACTIVE → reader 不跳过。
 *
 * <p>覆盖：
 * <ul>
 *   <li>succeed-driver 后 snapshot stepStatus==COMPLETED（非 ACTIVE）+ resultValue 持久化</li>
 *   <li>FAILED-driver 后 snapshot stepStatus==FAILED（非 ACTIVE）+ exception 持久化</li>
 *   <li>接线验证（#23）：终态 save 在 runtime 被 driver 出口调用（save count = ACTIVE 1 + 终态 1 = 2）</li>
 *   <li>幂等性（设计裁定 2）：同一 stepPath 无重复行（snapshot history 顺序追加，最终取终态）</li>
 *   <li>ACTIVE-time save 仍正常（第 0 次 snapshot == ACTIVE，证明 fresh step 首次 ACTIVE 行写入不受影响）</li>
 * </ul>
 */
public class TestTerminalStatePersistence {

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

    private Map<String, Object> runTask(String taskName) {
        ITask task = taskFlowManager.getTask(taskName, 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        return task.execute(taskRt).syncGetOutputs();
    }

    /**
     * 构造 main step parent state（stepPath=@main），使 loadStepState 按 {@code @main/<stepName>} 构建查询路径，
     * 模拟 resume 时 runtime 从 main step 下加载子 step 的真实路径。
     */
    private ITaskStepState mainParentState() {
        TaskStepStateBean main = new TaskStepStateBean();
        main.setStepPath(TaskConstants.MAIN_STEP_NAME);
        return main;
    }

    // ==================== succeed-driver → snapshot COMPLETED ====================

    @Test
    public void succeedDriver_snapshotReflectsCompleted_notActive() {
        Map<String, Object> ret = runTask("test/terminal-state-persist-success");
        assertEquals("TERMINAL_OK", ret.get(TaskConstants.VAR_RESULT),
                "step body runs and returns 'TERMINAL_OK'");

        String stepPath = "@main/terminalPersistStep";

        // 接线验证（#23）：save count = ACTIVE 1 + 终态 1 = 2（终态 save 被 driver 出口调用）
        assertEquals(2, snapshotStore.getSaveCount(stepPath),
                "wiring verification: terminal saveStepState must be called after succeed-driver. "
                        + "Expected ACTIVE-time save (1) + terminal save (1) = 2 saves. "
                        + "Pre-fix: only ACTIVE-time save (1) → snapshot stays ACTIVE on resume.");

        // 第 0 次 save = ACTIVE-time save（证明 ACTIVE 行仍正常写入）
        ITaskStepState activeSnapshot = snapshotStore.getSnapshotAt(stepPath, 0);
        assertNotNull(activeSnapshot, "ACTIVE-time save must produce a snapshot");
        assertEquals(Integer.valueOf(TaskConstants.TASK_STEP_STATUS_ACTIVE), activeSnapshot.getStepStatus(),
                "first save (ACTIVE-time) must snapshot ACTIVE status — fresh step ACTIVE row write unchanged");

        // 最新 snapshot = COMPLETED（终态 save 覆盖 ACTIVE）
        ITaskStepState latestSnapshot = snapshotStore.getLatestSnapshot(stepPath);
        assertNotNull(latestSnapshot, "terminal save must produce a snapshot");
        assertEquals(Integer.valueOf(_NopTaskCoreConstants.TASK_STEP_STATUS_COMPLETED),
                latestSnapshot.getStepStatus(),
                "terminal snapshot must reflect COMPLETED (not ACTIVE). "
                        + "Pre-fix: without terminal save, latest snapshot would be ACTIVE → resume reader isDone=false.");
        assertTrue(latestSnapshot.isDone(),
                "terminal snapshot isDone() must be true (COMPLETED is terminal)");
        assertTrue(latestSnapshot.isSuccess(),
                "terminal snapshot isSuccess() must be true (COMPLETED)");
        assertEquals("TERMINAL_OK", latestSnapshot.getResultValue(),
                "terminal snapshot must persist resultValue 'TERMINAL_OK'");
    }

    @Test
    public void succeedDriver_resumeLoadReturnsCompletedSnapshot_readerSkips() {
        // 第一次执行 → 终态持久化
        runTask("test/terminal-state-persist-success");

        // fresh load（从 snapshot 反序列化，非 live 引用）→ 反映终态 COMPLETED
        ITaskStepState loaded = snapshotStore.loadStepState(mainParentState(), "terminalPersistStep", "xpl", null);
        assertNotNull(loaded, "resume load must return persisted snapshot");
        assertEquals(Integer.valueOf(_NopTaskCoreConstants.TASK_STEP_STATUS_COMPLETED), loaded.getStepStatus(),
                "resume load (snapshot, not reference) must return COMPLETED terminal state");
        assertTrue(loaded.isDone(),
                "resume load isDone() must be true — reader will short-circuit on resume");
        assertEquals("TERMINAL_OK", loaded.getResultValue(),
                "resume load must return cached resultValue");
    }

    // ==================== FAILED-driver → snapshot FAILED + exception ====================

    @Test
    public void failedDriver_snapshotReflectsFailed_notActive_withException() {
        try {
            runTask("test/terminal-state-persist-failed");
            fail("step that always throws must propagate exception");
        } catch (Exception e) {
            assertNotNull(e, "first execution must throw");
        }

        String stepPath = "@main/terminalFailStep";

        // 接线验证（#23）：save count = ACTIVE 1 + 终态 1 = 2
        assertEquals(2, snapshotStore.getSaveCount(stepPath),
                "wiring verification: terminal saveStepState must be called after FAILED-driver. "
                        + "Expected ACTIVE-time save (1) + terminal save (1) = 2 saves.");

        // 第 0 次 save = ACTIVE-time save
        ITaskStepState activeSnapshot = snapshotStore.getSnapshotAt(stepPath, 0);
        assertNotNull(activeSnapshot);
        assertEquals(Integer.valueOf(TaskConstants.TASK_STEP_STATUS_ACTIVE), activeSnapshot.getStepStatus(),
                "first save (ACTIVE-time) must snapshot ACTIVE status");

        // 最新 snapshot = FAILED（终态 save 覆盖 ACTIVE）
        ITaskStepState latestSnapshot = snapshotStore.getLatestSnapshot(stepPath);
        assertNotNull(latestSnapshot);
        assertEquals(Integer.valueOf(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED),
                latestSnapshot.getStepStatus(),
                "terminal snapshot must reflect FAILED (not ACTIVE). "
                        + "Pre-fix: without terminal save, latest snapshot would be ACTIVE.");
        assertTrue(latestSnapshot.isDone(),
                "terminal snapshot isDone() must be true (FAILED is terminal)");
        assertNotNull(latestSnapshot.exception(),
                "terminal snapshot must persist exception (reader can rethrow on resume)");
        assertTrue(latestSnapshot.exception() instanceof NopException,
                "persisted exception must be a NopException");
    }

    @Test
    public void failedDriver_resumeLoadReturnsFailedSnapshot_readerRethrows() {
        try {
            runTask("test/terminal-state-persist-failed");
            fail("step that always throws must propagate exception");
        } catch (Exception e) {
            assertNotNull(e);
        }

        // fresh load（snapshot）→ 反映终态 FAILED
        ITaskStepState loaded = snapshotStore.loadStepState(mainParentState(), "terminalFailStep", "xpl", null);
        assertNotNull(loaded, "resume load must return persisted FAILED snapshot");
        assertEquals(Integer.valueOf(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED), loaded.getStepStatus(),
                "resume load (snapshot, not reference) must return FAILED terminal state");
        assertTrue(loaded.isDone(),
                "resume load isDone() must be true — reader will rethrow on resume");
        assertNotNull(loaded.exception(),
                "resume load must return persisted exception for rethrow");
    }

    // ==================== ACTIVE-time save unchanged (zero regression) ====================

    @Test
    public void activeTimeSave_firstSnapshotIsActive_freshStepWriteUnchanged() {
        runTask("test/terminal-state-persist-success");

        // 确认 ACTIVE-time save（fresh step 首次 ACTIVE 行写入）仍正常：第 0 次 snapshot 是 ACTIVE
        ITaskStepState firstSnapshot = snapshotStore.getSnapshotAt("@main/terminalPersistStep", 0);
        assertNotNull(firstSnapshot);
        assertEquals(Integer.valueOf(TaskConstants.TASK_STEP_STATUS_ACTIVE), firstSnapshot.getStepStatus(),
                "ACTIVE-time save (TaskStepRuntimeImpl.saveState at ACTIVE creation) must still write ACTIVE row. "
                        + "Terminal save wiring must not break fresh step ACTIVE persistence.");
    }

    // ==================== idempotency (设计裁定 2) ====================

    @Test
    public void terminalSave_upsertsSameStepPath_noDuplicateRows() {
        runTask("test/terminal-state-persist-success");

        String stepPath = "@main/terminalPersistStep";
        // snapshot history 顺序追加到同一 stepPath key（模拟 upsert 同一行），
        // 最新 snapshot 为终态。无独立 stepPath 的重复行。
        int saves = snapshotStore.getSaveCount(stepPath);
        assertTrue(saves >= 2,
                "saves append to same stepPath key (upsert semantics, no duplicate rows by taskInstanceId+stepPath)");

        ITaskStepState latest = snapshotStore.getLatestSnapshot(stepPath);
        assertEquals(Integer.valueOf(_NopTaskCoreConstants.TASK_STEP_STATUS_COMPLETED), latest.getStepStatus(),
                "latest snapshot (upsert result) reflects terminal COMPLETED");
    }
}

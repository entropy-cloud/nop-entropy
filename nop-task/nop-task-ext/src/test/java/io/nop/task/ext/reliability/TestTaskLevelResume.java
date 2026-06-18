package io.nop.task.ext.reliability;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepReturn;
import io.nop.task.impl.TaskFlowManagerImpl;
import io.nop.task.state.TaskStateBean;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plan 259 Phase 2 cross-restart resume E2E：端到端验证 task 级 resume 短路——cross-restart 不重跑已终态 task。
 *
 * <p>使用 {@link TaskResumeSnapshotTaskStateStore}（task 级 snapshot 语义，模拟 DB round-trip）——
 * 每次 saveTaskState 创建深拷贝 snapshot，使终态 driver 之后对 live bean 的 mutate 不影响已保存 snapshot。
 * resume 经 {@link ITaskFlowManager#getTaskRuntime} fresh load（snapshot，非引用）→ recoverMode=true →
 * {@code TaskImpl.execute} 检查 {@code taskState.isTerminal()} → 短路命中终态。
 *
 * <p>端到端路径（#22 Anti-Hollow）：fresh execute（saveState=true）→ mainStep 完成 → task 终态 driver →
 * task 终态 saveTaskState 持久化 snapshot → fresh load（snapshot，recoverMode=true）→ resume 短路 →
 * mainStep 不重跑（counter==0）→ 返回缓存 result / 重抛 exception。
 *
 * <p>覆盖：
 * <ul>
 *   <li>COMPLETED resume：fresh execute→终态 driver→持久化→fresh load→短路命中→mainStep 不重跑→返回缓存 result</li>
 *   <li>FAILED resume：fresh execute→终态 FAILED→持久化→fresh load→短路命中→重抛 exception（非静默跳过）</li>
 *   <li>pre-fix gap 证明：终态 save 使 snapshot 反映终态（无终态 save 则 snapshot ACTIVE → 不短路 → 重跑）</li>
 *   <li>in-progress（非终态）resume：ACTIVE task 不短路 → mainStep 重跑（不误短路 in-progress task）</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        testBeansFile = "/nop/task/test/beans/test-reliability.beans.xml")
public class TestTaskLevelResume extends JunitBaseTestCase {

    @Inject
    ITaskFlowManager injectedTaskFlowManager;

    private TaskResumeSnapshotTaskStateStore snapshotStore;
    private TaskFlowManagerImpl taskFlowManager;
    private ExecutionCounterBean counter;

    @BeforeEach
    public void setUpTaskResume() {
        counter = (ExecutionCounterBean) io.nop.api.core.ioc.BeanContainer.instance()
                .getBean("testExecutionCounter");
        snapshotStore = new TaskResumeSnapshotTaskStateStore();
        counter.reset();
        snapshotStore.reset();

        taskFlowManager = new TaskFlowManagerImpl();
        // persist store 用于 first execute（saveState=true）和 resume（getTaskRuntime）
        taskFlowManager.setTaskStateStore(snapshotStore);
        taskFlowManager.setNonPersistStateStore(snapshotStore);
    }

    /**
     * 第一次执行（fresh execute）：saveState=true 经 persist store → 终态 driver → saveTaskState 持久化终态 snapshot。
     * 返回 taskInstanceId 供 resume 使用。
     */
    private String firstExecute(String taskName) {
        ITask task = taskFlowManager.getTask(taskName, 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, true, null);
        task.execute(taskRt).syncGetOutputs();
        return taskRt.getTaskInstanceId();
    }

    private String firstExecuteFailing(String taskName) {
        ITask task = taskFlowManager.getTask(taskName, 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, true, null);
        try {
            task.execute(taskRt).syncGetOutputs();
            fail("task that always throws must propagate exception");
        } catch (Exception e) {
            assertNotNull(e, "first execution must throw");
        }
        return taskRt.getTaskInstanceId();
    }

    /**
     * Resume：fresh load（snapshot，recoverMode=true）→ execute → 短路判定。
     */
    private Map<String, Object> resumeTask(String taskName, String taskInstanceId) {
        ITask task = taskFlowManager.getTask(taskName, 0);
        ITaskRuntime taskRt = taskFlowManager.getTaskRuntime(taskInstanceId, null, null);
        assertTrue(taskRt.isRecoverMode(), "getTaskRuntime must set recoverMode=true");
        return task.execute(taskRt).syncGetOutputs();
    }

    private void resumeTaskFailing(String taskName, String taskInstanceId) {
        ITask task = taskFlowManager.getTask(taskName, 0);
        ITaskRuntime taskRt = taskFlowManager.getTaskRuntime(taskInstanceId, null, null);
        assertTrue(taskRt.isRecoverMode(), "getTaskRuntime must set recoverMode=true");
        task.execute(taskRt).syncGetOutputs();
    }

    // ==================== cross-restart resume COMPLETED → short-circuit, return cached result ====================

    @Test
    public void resume_completedTask_snapshotLoadShortCircuits_returnsCachedResult_plan259() {
        // ---- 第一次执行：task 完成 → 终态 COMPLETED driver → 终态 saveTaskState 持久化 COMPLETED snapshot ----
        String taskInstanceId = firstExecute("test/no-decorator-baseline");
        assertEquals(1, counter.get(),
                "first execution: step body must execute exactly once");
        assertEquals("OK", snapshotStore.getLatestTaskSnapshot(taskInstanceId).getResultValue(),
                "first execution: terminal driver captures result");

        // 接线验证（#23）：终态 saveTaskState 被调用（task 级无 ACTIVE-time save，只有终态 save = 1 次）
        assertEquals(1, snapshotStore.getTaskSaveCount(taskInstanceId),
                "wiring: terminal saveTaskState called after COMPLETED driver. "
                        + "Pre-fix: saveTaskState never called = 0.");

        // ---- Reset counter ONLY（snapshotStore 保留——终态 snapshot 用于 resume）----
        counter.reset();

        // ---- Resume：fresh DB load（snapshot，非引用，recoverMode=true）→ 短路命中 COMPLETED → mainStep 不重跑 ----
        Map<String, Object> ret = resumeTask("test/no-decorator-baseline", taskInstanceId);
        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT),
                "resume: must return cached result 'OK' (from persisted COMPLETED snapshot, not re-executed)");

        // 端到端验证（#22）/ 接线验证（#23）：mainStep body 不被重新调用（counter==0）
        // 关键：snapshot 语义下，load 取回的是 COMPLETED snapshot（终态）→ isTerminal true → 短路 → mainStep 不重跑
        // Pre-fix（无终态 driver/saveTaskState）：snapshot 不存在 → getTaskRuntime 抛 ERR_TASK_UNKNOWN_TASK_INSTANCE
        // 或若仅有 ACTIVE snapshot → load 取回 ACTIVE → isTerminal false → mainStep 重跑（counter==1）
        assertEquals(0, counter.get(),
                "resume: mainStep body must NOT be re-executed (counter=0). "
                        + "Snapshot semantics: fresh load returns COMPLETED snapshot (terminal). "
                        + "Pre-fix (no terminal driver): no saveTaskState → no snapshot → either ERR_TASK_UNKNOWN_TASK_INSTANCE or ACTIVE → re-run.");
    }

    // ==================== cross-restart resume FAILED → short-circuit, rethrow exception ====================

    @Test
    public void resume_failedTask_snapshotLoadShortCircuits_rethrowsException_plan259() {
        // ---- 第一次执行：task 失败 → 终态 FAILED driver → 终态 saveTaskState 持久化 FAILED snapshot ----
        String taskInstanceId = firstExecuteFailing("test/resume-failed-step");
        assertEquals(1, counter.get(),
                "first execution: step body must execute once before failing");

        // 接线验证（#23）：终态 saveTaskState 被调用
        assertEquals(1, snapshotStore.getTaskSaveCount(taskInstanceId),
                "wiring: terminal saveTaskState called after FAILED driver.");

        ITaskState failedSnapshot = snapshotStore.getLatestTaskSnapshot(taskInstanceId);
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_FAILED), failedSnapshot.getTaskStatus(),
                "terminal snapshot must reflect FAILED");
        assertNotNull(failedSnapshot.exception(),
                "terminal snapshot must persist exception (resume can rethrow)");

        // ---- Reset counter ONLY ----
        counter.reset();

        // ---- Resume：fresh DB load（snapshot）→ 短路命中 FAILED → 重抛 exception（非静默跳过，#24）----
        try {
            resumeTaskFailing("test/resume-failed-step", taskInstanceId);
            fail("resume: FAILED terminal snapshot must rethrow exception, not silently return");
        } catch (Exception e) {
            // 无静默跳过（#24）：FAILED 终态 snapshot 经 fresh load → 短路重抛 exception
            assertNotNull(e, "resume must rethrow exception for FAILED terminal snapshot (not silent skip). "
                    + "Snapshot semantics: fresh load returns FAILED snapshot (terminal).");
        }

        // 接线验证（#23）：mainStep body 不被重新调用（counter==0——短路在 mainStep 之前）
        assertEquals(0, counter.get(),
                "resume: mainStep body must NOT be re-executed for FAILED snapshot (counter=0, short-circuited).");
    }

    // ==================== pre-fix gap proof: terminal save produces COMPLETED snapshot ====================

    @Test
    public void preFixGap_terminalSaveProducesCompletedSnapshot_provesDriverNecessity_plan259() {
        // 执行 → 终态 driver → 终态 snapshot
        String taskInstanceId = firstExecute("test/no-decorator-baseline");

        ITaskState snapshot = snapshotStore.getLatestTaskSnapshot(taskInstanceId);
        assertNotNull(snapshot, "terminal saveTaskState must produce a snapshot");

        // snapshot = COMPLETED + result captured（证明终态 driver + saveTaskState wiring 生效）
        // Pre-fix（无终态 driver）：saveTaskState 从未被调用 → snapshot 不存在 → resume 抛 ERR_TASK_UNKNOWN_TASK_INSTANCE
        // 或若 task 停留 ACTIVE → 不短路 → mainStep 重跑
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_COMPLETED), snapshot.getTaskStatus(),
                "terminal snapshot = COMPLETED (proves terminal driver + saveTaskState wiring). "
                        + "Pre-fix: taskStatus stays ACTIVE → resume would re-run mainStep.");
        assertTrue(snapshot.isTerminal(),
                "snapshot isTerminal()=true (resume short-circuit fires on this). "
                        + "Pre-fix: ACTIVE snapshot isTerminal()=false → no short-circuit.");
        assertFalse(Integer.valueOf(TaskConstants.TASK_STATUS_ACTIVE).equals(snapshot.getTaskStatus()),
                "snapshot must NOT be ACTIVE (proves terminal driver changed status from ACTIVE to COMPLETED).");
    }

    // ==================== in-progress (non-terminal) resume: NOT short-circuited → mainStep re-runs ====================

    @Test
    public void resume_inProgressTask_notShortCircuited_mainStepReRuns_plan259() {
        // 模拟一个 in-progress（ACTIVE，非终态）task：手动注入 ACTIVE snapshot（模拟 crash 前 task 未完成）
        String taskInstanceId = "in-progress-test-" + System.nanoTime();
        TaskStateBean activeState = new TaskStateBean();
        activeState.setTaskInstanceId(taskInstanceId);
        activeState.setTaskName("no-decorator-baseline");
        activeState.setTaskVersion(0L);
        activeState.setTaskStatus(TaskConstants.TASK_STATUS_ACTIVE);
        snapshotStore.injectTaskSnapshot(activeState);

        counter.reset();

        // Resume：fresh DB load 取回 ACTIVE snapshot → isTerminal false → 不短路 → mainStep 重跑
        ITask task = taskFlowManager.getTask("test/no-decorator-baseline", 0);
        ITaskRuntime taskRt = taskFlowManager.getTaskRuntime(taskInstanceId, null, null);
        assertTrue(taskRt.isRecoverMode(), "getTaskRuntime must set recoverMode=true");
        assertFalse(taskRt.getTaskState().isTerminal(),
                "ACTIVE task state must NOT be terminal → resume must NOT short-circuit");

        Map<String, Object> ret = task.execute(taskRt).syncGetOutputs();
        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT),
                "in-progress resume: mainStep re-runs and returns 'OK' (not short-circuited)");

        // in-progress task 不被误短路：mainStep body 被重新调用（counter==1）
        assertEquals(1, counter.get(),
                "in-progress resume: mainStep body MUST be re-executed (counter=1, NOT short-circuited). "
                        + "Only terminal (COMPLETED/FAILED) tasks are short-circuited. "
                        + "ACTIVE is non-terminal → normal mainStep re-run + leaf-skip (plans 252-258).");
    }
}

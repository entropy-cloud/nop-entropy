package io.nop.task.ext.reliability;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;
import io.nop.task.impl.TaskFlowManagerImpl;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plan 257 Phase 3 resume E2E：端到端验证 continuation-skip reader 在真实 save→load→skip cycle 中生效。
 *
 * <p>所有测试经标准 `.task.xml` → {@link ITask#execute} → step 完成 → state 经 {@link ResumeCapableTaskStateStore}
 * save → resume（新 runtime、加载持久化 state）→ reader 命中 isDone → step body **不被重新调用** → 返回缓存 result。
 *
 * <p>这是 plan 257 的端到端 Anti-Hollow Proof（#22 端到端, #23 接线, #24 无静默跳过）：
 * 从 task execute 入口 → step 完成 → state 持久化 → resume 新 runtime 加载 state →
 * reader 检查 isDone → 跳过 step body → 返回缓存 result 完整路径连通。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        testBeansFile = "/nop/task/test/beans/test-reliability.beans.xml")
public class TestStepContinuationSkipResume extends JunitBaseTestCase {

    @Inject
    ITaskFlowManager injectedTaskFlowManager;

    private ResumeCapableTaskStateStore resumeStore;
    private TaskFlowManagerImpl taskFlowManager;
    private ExecutionCounterBean counter;

    @BeforeEach
    public void setUpResume() {
        counter = (ExecutionCounterBean) io.nop.api.core.ioc.BeanContainer.instance()
                .getBean("testExecutionCounter");
        resumeStore = new ResumeCapableTaskStateStore();
        counter.reset();
        resumeStore.reset();

        // 用 ResumeCapableTaskStateStore 配置一个新的 TaskFlowManagerImpl
        taskFlowManager = new TaskFlowManagerImpl();
        taskFlowManager.setNonPersistStateStore(resumeStore);
    }

    private Map<String, Object> runTask(String taskName) {
        ITask task = taskFlowManager.getTask(taskName, 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        return task.execute(taskRt).syncGetOutputs();
    }

    // ==================== resume COMPLETED → skip body, return cached result ====================

    @Test
    public void resume_completedStep_skipsBody_returnsCachedResult_plan257() {
        // ---- 第一次执行：step 完成 → state 经 saveStepState 保存（引用，后 mutate 为 COMPLETED）----
        Map<String, Object> ret1 = runTask("test/no-decorator-baseline");
        assertEquals("OK", ret1.get(TaskConstants.VAR_RESULT),
                "first execution: step body runs and returns 'OK'");
        assertEquals(1, counter.get(),
                "first execution: step body must execute exactly once");

        // 验证 state 已保存且为终态 COMPLETED
        ITaskStepState savedState = resumeStore.getSavedState("@main/plainStep");
        assertNotNull(savedState, "state must have been saved by saveStepState during first execution");
        assertTrue(savedState.isDone(),
                "saved state must be terminal (COMPLETED) after succeed-driver ran");
        assertTrue(savedState.isSuccess(),
                "saved state must be COMPLETED (isSuccess==true)");

        // ---- Reset counter ONLY（resumeStore 保留——state 用于 resume）----
        counter.reset();

        // ---- Resume：新 runtime、加载持久化 state → reader 命中 isDone → 跳过 step body ----
        Map<String, Object> ret2 = runTask("test/no-decorator-baseline");
        assertEquals("OK", ret2.get(TaskConstants.VAR_RESULT),
                "resume: must return cached result 'OK' (from persisted COMPLETED state)");

        // 端到端验证（#22）/ 接线验证（#23）：step body **不被重新调用**（counter==0）
        assertEquals(0, counter.get(),
                "resume: step body must NOT be re-executed (counter=0). "
                        + "Pre-plan-257: no reader existed → step body always ran (counter would be 1). "
                        + "If counter is 1, the reader failed to short-circuit on resume.");
    }

    // ==================== resume FAILED → skip body, rethrow exception ====================

    @Test
    public void resume_failedStep_rethrowsException_plan257() {
        // ---- 第一次执行：step 失败 → state 保存为 FAILED ----
        try {
            runTask("test/resume-failed-step");
            fail("first execution: step that always throws should propagate exception");
        } catch (Exception e) {
            assertNotNull(e, "first execution must throw (step body always fails)");
        }
        assertEquals(1, counter.get(),
                "first execution: step body must execute once before failing");

        // 验证 state 已保存且为终态 FAILED
        ITaskStepState savedState = resumeStore.getSavedState("@main/resumeFailStep");
        assertNotNull(savedState, "state must have been saved by saveStepState during first execution");
        assertTrue(savedState.isDone(),
                "saved state must be terminal (FAILED) after FAILED-driver ran");
        assertEquals(Integer.valueOf(60), savedState.getStepStatus(),
                "saved state must be FAILED(60)");
        assertNotNull(savedState.exception(),
                "saved state must have exception saved (via state.fail)");

        // ---- Reset counter ONLY ----
        counter.reset();

        // ---- Resume：loadStepState 返回 FAILED → reader 重抛 exception（非静默跳过）----
        try {
            runTask("test/resume-failed-step");
            fail("resume: FAILED terminal state must rethrow exception, not silently return");
        } catch (Exception e) {
            // 无静默跳过（#24）：FAILED 终态重抛 exception（非空返回 / 非 continue 跳过）
            assertNotNull(e, "resume must rethrow exception for FAILED terminal state (not silent skip). "
                    + "Pre-plan-257: no reader existed → step body would re-run (counter would be 1).");
        }

        // 接线验证（#23）：step body 不被重新调用（counter==0——reader 短路在 step body 之前）
        assertEquals(0, counter.get(),
                "resume: step body must NOT be re-executed for FAILED state (counter=0, reader short-circuited). "
                        + "If counter is 1, the reader failed to short-circuit and the step body ran again.");
    }
}

package io.nop.task.step;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;
import io.nop.task.core._NopTaskCoreConstants;
import io.nop.task.impl.TaskFlowManagerImpl;
import io.nop.task.state.DefaultTaskStateStore;
import io.nop.task.state.TaskStepStateBean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plan 257 Phase 2 focused 单元测试：验证 continuation-skip reader 在 step 执行入口 choke-point
 * （{@link TaskStepExecution#executeWithParentRt}）检查 {@code state.isDone()}，终态则跳过 step body。
 *
 * <p>覆盖四种场景：
 * <ul>
 *   <li>预载终态 COMPLETED state → reader 命中 → step body **未被调用** → 返回缓存 result</li>
 *   <li>预载终态 FAILED state → reader 命中 → step body 未被调用 → 重抛 exception（非静默跳过）</li>
 *   <li>fresh state（stepStatus==null）→ reader 不命中 → step body 正常调用（防首次执行误跳过）</li>
 *   <li>非终态 stepStatus（ACTIVE）→ reader 不跳过（防 over-matching）</li>
 * </ul>
 *
 * <p>reader 的核心价值：使 {@code ITaskStepState.isDone()/result()} 首次被 production 消费。
 * 修复前（plan 257 前）：{@code state.isDone()} 在 nop-task-core/src/main grep 返回 0 站点——
 * 状态机写出的终态 stepStatus 从未被读。
 */
public class TestTaskStepContinuationSkipReader {

    private TaskFlowManagerImpl taskFlowManager;
    private PreloadedStateTaskStateStore stateStore;

    private static final ErrorCode ERR_TEST_READER =
            ErrorCode.define("nop.err.test.plan-257.reader", "plan 257 reader test");

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
        stateStore = new PreloadedStateTaskStateStore();
        taskFlowManager = new TaskFlowManagerImpl();
        taskFlowManager.setNonPersistStateStore(stateStore);
    }

    private Map<String, Object> runTask() {
        ITask task = taskFlowManager.getTask("test/continuation-skip-reader", 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        return task.execute(taskRt).syncGetOutputs();
    }

    private TaskStepStateBean newCompletedState(String resultValue) {
        TaskStepStateBean state = new TaskStepStateBean();
        state.setStepPath("@main/plainStep");
        state.succeed(resultValue, null, null);
        return state;
    }

    private TaskStepStateBean newFailedState() {
        TaskStepStateBean state = new TaskStepStateBean();
        state.setStepPath("@main/plainStep");
        state.fail(new NopException(ERR_TEST_READER), null);
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);
        return state;
    }

    private TaskStepStateBean newActiveState() {
        TaskStepStateBean state = new TaskStepStateBean();
        state.setStepPath("@main/plainStep");
        state.setStepStatus(TaskConstants.TASK_STEP_STATUS_ACTIVE);
        return state;
    }

    // ==================== COMPLETED → skip body, return cached result ====================

    @Test
    public void completedState_readerSkipsBody_returnsCachedResult() {
        // 预载终态 COMPLETED state（resultValue = "CACHED_RESULT"）
        stateStore.preload("plainStep", newCompletedState("CACHED_RESULT"));

        Map<String, Object> ret = runTask();

        // reader 命中 isDone → 跳过 step body → 返回缓存 result（"CACHED_RESULT"）
        // step body 的 source 返回 "BODY_RESULT"——若 reader 未跳过，结果为 "BODY_RESULT"
        assertEquals("CACHED_RESULT", ret.get(TaskConstants.VAR_RESULT),
                "COMPLETED state: reader must skip step body and return cached result. "
                        + "If result is 'BODY_RESULT', the reader failed to short-circuit (step body was executed).");
    }

    // ==================== FAILED → skip body, rethrow exception ====================

    @Test
    public void failedState_readerSkipsBody_rethrowsException() {
        // 预载终态 FAILED state（exception saved）
        stateStore.preload("plainStep", newFailedState());

        try {
            runTask();
            fail("FAILED state: reader must rethrow exception, not silently return");
        } catch (Exception e) {
            // reader 命中 isDone (FAILED) → 重抛 exception（非静默跳过，设计裁定 4）
            assertNotNull(e, "FAILED terminal state must produce exception on resume (rethrow, not silent skip)");
        }
    }

    // ==================== fresh state → reader does NOT skip, body runs ====================

    @Test
    public void freshState_readerDoesNotSkip_bodyExecutesNormally() {
        // 不预载任何 state（fresh stepStatus==null, isDone()==false）
        Map<String, Object> ret = runTask();

        // reader 不命中（fresh state, isDone==false）→ step body 正常执行 → 返回 "BODY_RESULT"
        assertEquals("BODY_RESULT", ret.get(TaskConstants.VAR_RESULT),
                "fresh state: reader must NOT skip — step body should execute normally. "
                        + "Pre-loading a terminal state for a first-time step would be a false-skip bug (设计裁定 5).");
    }

    // ==================== non-terminal (ACTIVE) → reader does NOT skip ====================

    @Test
    public void activeState_readerDoesNotSkip_bodyExecutesNormally() {
        // 预载 ACTIVE state（非终态, isDone()==false）
        stateStore.preload("plainStep", newActiveState());

        Map<String, Object> ret = runTask();

        // reader 不命中（ACTIVE 不是终态）→ step body 正常执行
        assertEquals("BODY_RESULT", ret.get(TaskConstants.VAR_RESULT),
                "ACTIVE state: reader must NOT skip — only terminal statuses (COMPLETED/FAILED/EXPIRED/KILLED) trigger skip. "
                        + "ACTIVE is non-terminal → body must run.");
    }

    // ==================== isDone truth table verification (no over-matching) ====================

    @Test
    public void isDone_falseForNonTerminalStatuses_preventsReaderOverMatching() {
        // 验证 reader 不引入 over-matching：非终态 stepStatus 不触发跳过
        TaskStepStateBean active = newActiveState();
        assertFalse(active.isDone(), "ACTIVE must not be isDone");
        assertFalse(active.isSuccess(), "ACTIVE must not be isSuccess");

        TaskStepStateBean fresh = new TaskStepStateBean();
        assertFalse(fresh.isDone(), "null status must not be isDone (null-safe)");
        assertFalse(fresh.isSuccess(), "null status must not be isSuccess");

        TaskStepStateBean completed = newCompletedState("ok");
        assertTrue(completed.isDone(), "COMPLETED must be isDone");
        assertTrue(completed.isSuccess(), "COMPLETED must be isSuccess");

        TaskStepStateBean failed = newFailedState();
        assertTrue(failed.isDone(), "FAILED must be isDone");
        assertFalse(failed.isSuccess(), "FAILED must not be isSuccess");
    }

    /**
     * 测试专用 store：可预载终态 state，使 loadStepState 返回该 state（模拟 resume 时从持久化加载终态）。
     *
     * <p>继承 {@link DefaultTaskStateStore}（non-persist），仅在 loadStepState 中检查预载 map。
     * 若 stepName 未预载，回退 super（返回 null → fresh state）。
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

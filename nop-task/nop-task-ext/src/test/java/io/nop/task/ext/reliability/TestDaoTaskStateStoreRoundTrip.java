package io.nop.task.ext.reliability;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.core._NopTaskCoreConstants;
import io.nop.task.dao.store.DaoTaskStateStore;
import io.nop.task.impl.TaskRuntimeImpl;
import io.nop.task.impl.TaskStepRuntimeImpl;
import io.nop.task.state.TaskStepStateBean;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.nop.task.TaskStepReturn.RETURN_RESULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 257 Phase 1 focused 测试：验证 {@link DaoTaskStateStore} 的 save→load round-trip
 * 能正确持久化并取回终态 step state（COMPLETED / FAILED），使 reader 检查的 {@code state.isDone()}
 * 在 resume 时为 true（非 freshly-constructed 永远 false 的空壳）。
 *
 * <p>这是 continuation-skip reader 的 load-side proof：闭合独立审计 Round 1 Blocker 证伪的
 * 「DaoTaskStateStore 已满足 prerequisite」假前提——本计划前它全部 load/save 为 stub。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDaoTaskStateStoreRoundTrip extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    private DaoTaskStateStore store;
    private ITaskRuntime taskRt;
    private ITaskState taskState;

    private static final ErrorCode ERR_TEST_ROUND_TRIP =
            ErrorCode.define("nop.err.test.plan-257.round-trip", "plan 257 round-trip test");

    @BeforeEach
    public void setUpStore() {
        store = new DaoTaskStateStore();
        store.setDaoProvider(daoProvider);

        TaskRuntimeImpl runtime = new TaskRuntimeImpl(null, store, null, XLang.newEvalScope(), false);
        taskState = store.newTaskState("testRoundTripTask", 0, runtime);
        runtime.setTaskState(taskState);
        taskRt = runtime;
    }

    private ITaskStepRuntime newStepRt(ITaskStepState parentState, String stepName) {
        ITaskStepState state = store.newStepState(parentState, stepName, "xpl", taskRt);
        TaskStepRuntimeImpl stepRt = new TaskStepRuntimeImpl(taskRt, store,
                XLang.newEvalScope());
        stepRt.setState(state);
        return stepRt;
    }

    @Test
    public void completedStep_saveLoadRoundTrip_isDoneAndIsSuccessAndResultNonNullable() {
        // 1. 创建 step state + succeed（终态 COMPLETED）
        ITaskStepRuntime stepRt = newStepRt(null, "completedStep");
        ITaskStepState state = stepRt.getState();
        state.succeed("CACHED_RESULT", null, taskRt);

        // 2. save（持久化终态到 DB）
        store.saveStepState(stepRt);

        // 3. load（从 DB 取回）
        ITaskStepState loaded = store.loadStepState(null, "completedStep", "xpl", taskRt);
        assertNotNull(loaded, "loadStepState must return persisted state (non-null), not fall back to fresh bean");

        // 4. 断言终态反映（reader 可用的 load-side proof）
        assertEquals(Integer.valueOf(_NopTaskCoreConstants.TASK_STEP_STATUS_COMPLETED), loaded.getStepStatus(),
                "round-trip must preserve terminal stepStatus=COMPLETED(40)");
        assertTrue(loaded.isDone(),
                "round-trip after save→load: isDone() must be true (COMPLETED is terminal). "
                        + "Pre-fix DaoTaskStateStore.loadStepState returned null → fresh bean → isDone always false.");
        assertTrue(loaded.isSuccess(),
                "round-trip after save→load: isSuccess() must be true (stepStatus==COMPLETED)");
        assertNotNull(loaded.result(),
                "round-trip after save→load: result() must be non-null (derived from persisted resultValue)");
        assertEquals("CACHED_RESULT", loaded.result().getResult(),
                "round-trip must preserve the cached result value 'CACHED_RESULT'");
    }

    @Test
    public void failedStep_saveLoadRoundTrip_isDoneAndExceptionNonNullable() {
        // 1. 创建 step state + 终态失败（FAILED + exception）
        ITaskStepRuntime stepRt = newStepRt(null, "failedStep");
        ITaskStepState state = stepRt.getState();
        NopException exp = new NopException(ERR_TEST_ROUND_TRIP);
        state.fail(exp, taskRt);
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);

        // 2. save（持久化终态 + exception 到 DB）
        store.saveStepState(stepRt);

        // 3. load（从 DB 取回）
        ITaskStepState loaded = store.loadStepState(null, "failedStep", "xpl", taskRt);
        assertNotNull(loaded, "loadStepState must return persisted FAILED state (non-null)");

        // 4. 断言终态反映（reader 可重抛）
        assertEquals(Integer.valueOf(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED), loaded.getStepStatus(),
                "round-trip must preserve terminal stepStatus=FAILED(60)");
        assertTrue(loaded.isDone(),
                "round-trip after save→load: isDone() must be true (FAILED is terminal)");
        assertFalse(loaded.isSuccess(),
                "round-trip after save→load: isSuccess() must be false (FAILED != COMPLETED)");
        assertNotNull(loaded.exception(),
                "round-trip after save→load: exception() must be non-null (reader can rethrow). "
                        + "Pre-fix loadStepState returned null → no exception available for rethrow.");
        assertTrue(loaded.exception() instanceof NopException,
                "reconstructed exception must be a NopException for consistent rethrow semantics");
        assertEquals(ERR_TEST_ROUND_TRIP.getErrorCode(), ((NopException) loaded.exception()).getErrorCode(),
                "round-trip must preserve the exception errorCode for reader rethrow");
    }

    @Test
    public void freshStep_loadReturnsNull_fallsBackToNewState() {
        // 未 save 的 step → loadStepState 返回 null → 回退 newStepState（fresh ACTIVE state）
        ITaskStepState loaded = store.loadStepState(null, "unsavedStep", "xpl", taskRt);
        // DaoTaskStateStore.loadStepState 对未持久化的 step 返回 null（而非空壳 bean）
        // 防止「首次执行误跳过」（设计裁定 5）
        assertTrue(loaded == null,
                "loadStepState for unsaved step must return null → caller falls back to newStepState (fresh ACTIVE). "
                        + "Returning a fake terminal state would cause first-execution false-skip.");
    }
}

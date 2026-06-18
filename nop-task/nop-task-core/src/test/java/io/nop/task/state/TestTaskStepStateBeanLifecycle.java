package io.nop.task.state;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.task.TaskStepReturn;
import io.nop.task.core._NopTaskCoreConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 252 Phase 3 focused 单元测试：验证 {@link TaskStepStateBean} 的 result/success 状态机全部转移。
 *
 * <p>Baseline（修复前）：{@code succeed}/{@code isDone}/{@code isSuccess}/{@code result()}/{@code result(TaskStepReturn)}
 * 均为 no-op（空方法体 / 恒 false / 恒 null），违反 Minimum Rules #24（禁止静默 no-op）。
 *
 * <p>修复后（plan 252）：
 * <ul>
 *   <li>{@code succeed(R, nextStepId, taskRt)} = {@code setResultValue(R)} + {@code setStepStatus(COMPLETED)}</li>
 *   <li>{@code isDone()} = stepStatus 为终态值 {COMPLETED, FAILED, EXPIRED, KILLED}（null-safe）</li>
 *   <li>{@code isSuccess()} = {@code COMPLETED.equals(getStepStatus())}（null-safe）</li>
 *   <li>{@code result()} = getResultValue()==null ? null : {@code RETURN_RESULT(getResultValue())}（注意 CONTINUE null 陷阱）</li>
 *   <li>{@code result(TaskStepReturn)} = {@code setResultValue(result.getResult())}</li>
 *   <li>{@code fail()} 行为零变更（plan 247：仅 exception(exp)，不终止生命周期）</li>
 * </ul>
 */
public class TestTaskStepStateBeanLifecycle {

    private static final ErrorCode ERR_TEST_LIFECYCLE =
            ErrorCode.define("nop.err.test.state.lifecycle", "test lifecycle");

    // ==================== fresh state（stepStatus==null）null-safe 验证 ====================

    @Test
    public void freshState_isDoneAndIsSuccessReturnFalse_andResultReturnsNull() {
        TaskStepStateBean state = new TaskStepStateBean();
        assertNull(state.getStepStatus(), "fresh state should have null stepStatus");

        assertFalse(state.isDone(),
                "fresh state (stepStatus==null) must return false for isDone() — null-safe, no NPE");
        assertFalse(state.isSuccess(),
                "fresh state (stepStatus==null) must return false for isSuccess() — null-safe, no NPE");
        assertNull(state.result(),
                "fresh state (resultValue==null) must return null for result() — not CONTINUE (null trap avoidance)");
    }

    // ==================== succeed(R, nextStepId, taskRt) ====================

    @Test
    public void succeed_setsResultValueAndStepStatusCompleted() {
        TaskStepStateBean state = new TaskStepStateBean();

        state.succeed("hello", null, null);

        assertEquals("hello", state.getResultValue(),
                "succeed(R,...) must setResultValue(R)");
        assertEquals(Integer.valueOf(_NopTaskCoreConstants.TASK_STEP_STATUS_COMPLETED), state.getStepStatus(),
                "succeed must setStepStatus(COMPLETED=40)");
    }

    @Test
    public void succeed_makesIsDoneAndIsSuccessTrue_andResultNonNullable() {
        TaskStepStateBean state = new TaskStepStateBean();

        state.succeed("world", null, null);

        assertTrue(state.isDone(),
                "after succeed, isDone() must be true (COMPLETED is terminal)");
        assertTrue(state.isSuccess(),
                "after succeed, isSuccess() must be true (stepStatus==COMPLETED)");

        TaskStepReturn ret = state.result();
        assertNotNull(ret, "after succeed(R,...), result() must be non-null (derived from R)");
        assertEquals("world", ret.getResult(),
                "result() must derive from the R passed to succeed");
    }

    @Test
    public void succeed_nullResultValueMakesResultReturnNull_notCONTINUE() {
        // succeed(null,...) sets resultValue=null → result() must return null (NOT CONTINUE).
        // This is the "null trap" from design adjudication 4:
        // TaskStepReturn.RETURN_RESULT(null) / of(null,null) both return CONTINUE, not null.
        // result() must explicitly check getResultValue()==null and return null.
        TaskStepStateBean state = new TaskStepStateBean();

        state.succeed(null, null, null);

        assertTrue(state.isDone(), "stepStatus=COMPLETED → isDone still true even if resultValue is null");
        assertTrue(state.isSuccess(), "stepStatus=COMPLETED → isSuccess still true");
        assertNull(state.result(),
                "succeed(null,...) → resultValue=null → result() must return null, NOT CONTINUE (null trap avoidance)");
    }

    // ==================== result(TaskStepReturn) round-trip ====================

    @Test
    public void resultSetter_resultRoundTripConsistent() {
        // result(T) stores T.getResult() into resultValue → result() rebuilds from resultValue
        TaskStepStateBean state = new TaskStepStateBean();

        TaskStepReturn original = TaskStepReturn.RETURN_RESULT("round-trip-value");
        state.result(original);

        assertEquals("round-trip-value", state.getResultValue(),
                "result(T) must extract main result value (T.getResult()) and store via setResultValue");

        TaskStepReturn rebuilt = state.result();
        assertNotNull(rebuilt, "result() must be non-null when resultValue is non-null");
        assertEquals("round-trip-value", rebuilt.getResult(),
                "round-trip: result() must derive the same value that result(T) stored");
    }

    @Test
    public void resultSetter_nullTaskStepReturnClearsResultValue() {
        TaskStepStateBean state = new TaskStepStateBean();
        state.setResultValue("previous");

        state.result((TaskStepReturn) null);

        assertNull(state.getResultValue(),
                "result(null) must clear resultValue (result == null → setResultValue(null))");
        assertNull(state.result(),
                "after result(null), result() must return null (resultValue==null)");
    }

    // ==================== fail(E, taskRt) — plan 247 一致：不终止生命周期 ====================

    @Test
    public void fail_savesException_butDoesNotMakeDoneOrSuccess() {
        // plan 247 裁定：fail() 仅记录 exception，不 setStepStatus、不设 done。
        // fail 在 retry 内为瞬态（每次失败调用，retry loop 随后继续）。
        TaskStepStateBean state = new TaskStepStateBean();

        NopException exp = new NopException(ERR_TEST_LIFECYCLE);
        state.fail(exp, null);

        assertSame(exp, state.exception(),
                "fail(E,...) must save E (plan 247 behavior, unchanged)");
        assertFalse(state.isDone(),
                "fail must NOT set done (plan 247: fail is transient in retry loop, does not terminate lifecycle)");
        assertFalse(state.isSuccess(),
                "fail must NOT set success");
        assertNull(state.getStepStatus(),
                "fail must NOT change stepStatus (plan 247 adjudication: fail only records exception)");
    }

    // ==================== terminal state set coverage for isDone() ====================

    @Test
    public void isDone_trueForAllTerminalStatuses() {
        TaskStepStateBean state = new TaskStepStateBean();

        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_COMPLETED);
        assertTrue(state.isDone(), "COMPLETED(40) is terminal → isDone true");
        assertTrue(state.isSuccess(), "COMPLETED(40) → isSuccess true");

        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);
        assertTrue(state.isDone(), "FAILED(60) is terminal → isDone true");
        assertFalse(state.isSuccess(), "FAILED(60) → isSuccess false");

        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_EXPIRED);
        assertTrue(state.isDone(), "EXPIRED(50) is terminal → isDone true");
        assertFalse(state.isSuccess(), "EXPIRED(50) → isSuccess false");

        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_KILLED);
        assertTrue(state.isDone(), "KILLED(70) is terminal → isDone true");
        assertFalse(state.isSuccess(), "KILLED(70) → isSuccess false");
    }

    @Test
    public void isDone_falseForNonTerminalStatuses() {
        TaskStepStateBean state = new TaskStepStateBean();

        // Non-terminal statuses should return false for isDone
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_CREATED);
        assertFalse(state.isDone(), "CREATED(0) is not terminal → isDone false");

        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_SUSPENDED);
        assertFalse(state.isDone(), "SUSPENDED(10) is not terminal → isDone false");

        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_ACTIVATED);
        assertFalse(state.isDone(), "ACTIVATED(30) is not terminal → isDone false");
    }
}

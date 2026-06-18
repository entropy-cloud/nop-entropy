package io.nop.task.utils;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.task.exceptions.NopTaskCancelledException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;

import static io.nop.task.TaskErrors.ERR_TASK_CANCELLED;
import static io.nop.xlang.XLangErrors.ERR_EXEC_INVOKE_METHOD_FAIL;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 254 Phase 2 + Plan 256 Phase 1 focused 单元测试：验证 {@link TaskStepHelper#isCancelledException} 的 cancel 识别逻辑。
 *
 * <p>背景：plan 254 在 {@link io.nop.task.step.TaskStepExecution} 终态失败 choke-point 接入 FAILED-driver wiring，
 * 设计裁定 3 要求「FAILED 标记点在 cancel-check 之后」。{@code TaskStepExecution} sync catch（{@code :280`）和
 * async err!=null 分支（{@code :230`）的 cancel-check 调 {@link TaskStepHelper#isCancelledException}：
 * 若返回 true 则立即 throw（plan 254 FAILED-driver wiring 在 {@code :291-292`} / {@code :238-239`} 之后，
 * 被 cancel-check throw 跳过）→ cancelled step 不被误标记 FAILED。
 *
 * <p>Plan 256 修复：原 {@code isCancelledException} 直接检查顶层异常、不解包 cause。当 cancelled step 经 xpl
 * 方法调用（{@code AbstractObjFunctionExecutable.doInvoke*}）抛 {@link NopTaskCancelledException} 时，
 * {@code wrapInvokeException} 将其包装为 {@code NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL, cause=...)}，
 * 原实现顶层 {@code NopEvalException} 非任何 cancellation 类型 → 返回 false → cancel-check 未命中 → FAILED-driver
 * 误标记 cancelled step FAILED。Plan 256 让 {@code isCancelledException} 遍历 cause chain，使包装 cancellation 仍被识别。
 *
 * <p>本测试覆盖两层语义：
 * <ul>
 *   <li><b>顶层匹配</b>（plan 254 真值表）：{@link CancellationException} / {@link NopTaskCancelledException} /
 *       {@link NopException} 带 {@code ERR_TASK_CANCELLED} errorCode → true；普通 {@link NopException} /
 *       普通 {@link RuntimeException} → false。</li>
 *   <li><b>cause-chain 解包</b>（plan 256 新行为）：包装异常
 *       {@code NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL, cause=cancellation)} → true；
 *       包装非 cancellation → false（无 over-matching）；多层嵌套 / {@code CancellationException} cause 变体
 *       各有断言。</li>
 * </ul>
 */
public class TestTaskStepHelperIsCancelledException {

    @Test
    public void cancellationException_isCancelled() {
        assertTrue(TaskStepHelper.isCancelledException(new CancellationException()),
                "CancellationException must be recognized as cancelled (first-arm match). "
                        + "This proves cancel-check throws before plan 254 FAILED-driver wiring.");
    }

    @Test
    public void nopTaskCancelledException_isCancelled() {
        assertTrue(TaskStepHelper.isCancelledException(NopTaskCancelledException.INSTANCE),
                "NopTaskCancelledException (thrown by TaskStepHelper.checkNotCancelled) must be recognized "
                        + "as cancelled (second-arm match). Proves cancel-check throws before FAILED-driver wiring.");
    }

    @Test
    public void nopExceptionWithCancelledErrorCode_isCancelled() {
        NopException cancelledErr = new NopException(ERR_TASK_CANCELLED);
        assertTrue(TaskStepHelper.isCancelledException(cancelledErr),
                "NopException with ERR_TASK_CANCELLED errorCode must be recognized as cancelled (third-arm match).");
    }

    @Test
    public void ordinaryNopException_notCancelled() {
        NopException ordinaryErr = new NopException(ErrorCode.define("nop.err.test.ordinary", "ordinary"));
        assertFalse(TaskStepHelper.isCancelledException(ordinaryErr),
                "ordinary NopException (non-cancelled errorCode) must NOT be recognized as cancelled. "
                        + "It is a terminal failure → plan 254 FAILED-driver wiring should mark step FAILED.");
    }

    @Test
    public void ordinaryRuntimeException_notCancelled() {
        assertFalse(TaskStepHelper.isCancelledException(new RuntimeException("ordinary")),
                "ordinary RuntimeException must NOT be recognized as cancelled. "
                        + "It is a terminal failure → plan 254 FAILED-driver wiring should mark step FAILED.");
    }

    @Test
    public void xplWrappedCancellation_isCancelled_plan256() {
        NopEvalException wrapped = new NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL,
                NopTaskCancelledException.INSTANCE);
        assertTrue(TaskStepHelper.isCancelledException(wrapped),
                "NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL) wrapping NopTaskCancelledException must be "
                        + "recognized as cancelled via cause-chain unwrapping (plan 256 core new behavior). "
                        + "This is the exact shape produced by AbstractObjFunctionExecutable.wrapInvokeException "
                        + "when a bean method throws NopTaskCancelledException through xpl method invocation.");
    }

    @Test
    public void xplWrappedOrdinaryException_notCancelled_plan256() {
        NopException ordinaryCause = new NopException(
                ErrorCode.define("nop.err.test.ordinary.cause", "ordinary cause"));
        NopEvalException wrapped = new NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL, ordinaryCause);
        assertFalse(TaskStepHelper.isCancelledException(wrapped),
                "NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL) wrapping an ordinary (non-cancellation) NopException "
                        + "must NOT be recognized as cancelled. Prevents over-matching: only cause chain containing "
                        + "a cancellation type/errorCode should match.");
    }

    @Test
    public void deeplyNestedCancellation_isCancelled_plan256() {
        NopEvalException inner = new NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL,
                NopTaskCancelledException.INSTANCE);
        NopEvalException outer = new NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL, inner);
        assertTrue(TaskStepHelper.isCancelledException(outer),
                "Deeply nested (>=2 layers) cancellation wrapping must be recognized as cancelled. "
                        + "Verifies cause-chain traversal iterates beyond a single level (design adjudication 3: "
                        + "anti-cycle + depth coverage).");
    }

    @Test
    public void xplWrappedCancellationException_isCancelled_plan256() {
        NopEvalException wrapped = new NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL,
                new CancellationException());
        assertTrue(TaskStepHelper.isCancelledException(wrapped),
                "NopEvalException wrapping a CancellationException cause must be recognized as cancelled. "
                        + "Covers the cause-chain first-arm variant (CancellationException instanceof check applies "
                        + "at the cause level, not only the top level).");
    }
}

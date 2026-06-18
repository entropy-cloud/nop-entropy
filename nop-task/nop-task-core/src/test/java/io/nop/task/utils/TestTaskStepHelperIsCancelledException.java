package io.nop.task.utils;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.task.exceptions.NopTaskCancelledException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;

import static io.nop.task.TaskErrors.ERR_TASK_CANCELLED;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 254 Phase 2 focused 单元测试：验证 {@link TaskStepHelper#isCancelledException} 的 cancel 识别逻辑。
 *
 * <p>背景：plan 254 在 {@link io.nop.task.step.TaskStepExecution} 终态失败 choke-point 接入 FAILED-driver wiring，
 * 设计裁定 3 要求「FAILED 标记点在 cancel-check 之后」。{@code TaskStepExecution} sync catch（{@code :280}）和
 * async err!=null 分支（{@code :230`）的 cancel-check 调 {@link TaskStepHelper#isCancelledException}：
 * 若返回 true 则立即 throw（plan 254 FAILED-driver wiring 在 {@code :291-292` / {@code :238-239} 之后，
 * 被 cancel-check throw 跳过）→ cancelled step 不被误标记 FAILED。
 *
 * <p>本测试验证 cancel-check 的真值表（证明 wiring 之前的 cancel-check 确实识别 cancelled 异常）：
 * <ul>
 *   <li>{@link CancellationException} → true（第一档匹配）。</li>
 *   <li>{@link NopTaskCancelledException}（step body 经 checkNotCancelled 抛出）→ true（第二档匹配）。</li>
 *   <li>{@link NopException} 带 {@code ERR_TASK_CANCELLED} errorCode → true（第三档匹配）。</li>
 *   <li>{@link NopException} 带其他 errorCode → false（普通失败，非 cancelled）。</li>
 *   <li>普通 {@link RuntimeException} → false。</li>
 * </ul>
 *
 * <p>结合代码复核：{@code TaskStepExecution} 的 cancel-check（{@code :230}/{@code :280`）在 FAILED-driver
 * wiring（{@code :238-239`/{@code :291-292`}）之前，本测试证明 cancel-check 真值表正确识别 cancelled 异常，
 * 故 cancelled step 不被误标记 FAILED（cancel 排除语义成立）。
 *
 * <p>注：xpl 方法调用（{@code AbstractObjFunctionExecutable.doInvoke*}）会把 {@code NopTaskCancelledException}
 * 包装为 {@code NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL)}，丢失 cancellation 字符（与 plan 249 前 bizFatal
 * 同类问题）。故端到端 {@code .task.xml} cancelled 测试不可行——cancelled 排除语义在单元测试层验证（本测试）。
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
}

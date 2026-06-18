package io.nop.task.exceptions;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancellable;

import static io.nop.task.TaskErrors.ERR_TASK_CANCELLED;

/**
 * plan 260: reason-carrying cancellation exception（曾是无 reason 单例 {@code NopSingletonException}）。
 *
 * <p>step 层 EXPIRED/KILLED driver（plan 260 设计裁定 1）需要把判定出的 cancel reason（timeout/kill）
 * 编码进传播到 task seam 的 exception，使 task 层 KILLED/TIMEOUT driver（裁定 2）能区分 timeout 与 kill
 * （task seam 处 {@code taskRt.getCancelReason()} 与 step token reason 均不可靠，唯一可靠信号是此 exception）。
 *
 * <p>改为 {@link NopException} 子类以允许携带不同 reason 的多实例（{@code NopSingletonException} 按 errorCode
 * 注册单例、禁止多实例）。仍被 {@code TaskStepHelper.isCancelledException} 第二档
 * {@code instanceof NopTaskCancelledException} 识别。
 */
public class NopTaskCancelledException extends NopException {

    /**
     * 默认实例（reason = {@link ICancellable#CANCEL_REASON_KILL}），保留以兼容既有调用方
     * （如 {@code FailureSimulatorBean.throwCancelled()}）。
     */
    public static final NopTaskCancelledException INSTANCE =
            new NopTaskCancelledException(ICancellable.CANCEL_REASON_KILL);

    private final String cancelReason;

    public NopTaskCancelledException(String cancelReason) {
        super(ERR_TASK_CANCELLED);
        this.cancelReason = cancelReason;
    }

    public NopTaskCancelledException(String cancelReason, Throwable cause) {
        super(ERR_TASK_CANCELLED, cause);
        this.cancelReason = cancelReason;
    }

    /**
     * 取消原因（{@link ICancellable#CANCEL_REASON_TIMEOUT} / {@link ICancellable#CANCEL_REASON_KILL} / 其它 / null）。
     * null 视为 kill（与 {@link ICancellable#cancel()} 默认 reason 一致）。
     */
    public String getCancelReason() {
        return cancelReason;
    }

    public static NopTaskCancelledException forReason(String cancelReason) {
        return new NopTaskCancelledException(cancelReason);
    }

    public static NopTaskCancelledException forReason(String cancelReason, Throwable cause) {
        return new NopTaskCancelledException(cancelReason, cause);
    }
}

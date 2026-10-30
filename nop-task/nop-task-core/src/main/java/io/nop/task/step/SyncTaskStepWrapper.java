package io.nop.task.step;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskConfigs;
import io.nop.task.TaskErrors;
import io.nop.task.TaskStepReturn;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.nop.task.TaskErrors.ARG_ATTR_NAME;
import static io.nop.task.TaskErrors.ARG_ATTR_VALUE;
import static io.nop.task.TaskErrors.ARG_REASON;
import static io.nop.task.TaskErrors.ARG_STEP_PATH;
import static io.nop.task.TaskErrors.ERR_TASK_STEP_CONFIG_INVALID;

/**
 * 同步等待包装（sync=true）。
 *
 * <p>plan 2514 MA-03（1.5-C3-002，裁定③）：同步等待由无超时、不响应取消的
 * {@code FutureHelper.syncGet} 改为<b>带取消唤醒 + 可配置时限的有界等待</b>——
 * 闭合 C2-010 通用挂死面与 sync×executor 同池嵌套死锁面。时限键
 * {@code nop.task.step.sync-wait-timeout-ms}（缺省 600000，0=不限时仅取消唤醒，
 * 负值装配期由 {@code TaskStepEnhancer} fail-fast）；时限超时显式抛
 * ERR_TASK_STEP_CONFIG_INVALID 族错误（非静默吞）。
 */
public class SyncTaskStepWrapper extends DelegateTaskStep {
    private static final Logger LOG = LoggerFactory.getLogger(SyncTaskStepWrapper.class);

    /**
     * 有界等待的轮询窗口：窗口内复查取消 token 与时限，保证 cancel 唤醒与时限失败都有界触发
     */
    private static final long POLL_INTERVAL_MS = 100;

    public SyncTaskStepWrapper(ITaskStep taskStep) {
        super(taskStep);
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        TaskStepReturn ret = getTaskStep().execute(stepRt);
        if (!ret.isAsync())
            return ret;

        long timeoutMs = TaskConfigs.CFG_TASK_STEP_SYNC_WAIT_TIMEOUT_MS.get();
        if (timeoutMs < 0)
            throw waitConfigError(stepRt, timeoutMs, "must be >= 0 (0 = wait until completion, cancel-wake only)");
        return boundedSync(ret, stepRt, timeoutMs);
    }

    private TaskStepReturn boundedSync(TaskStepReturn ret, ITaskStepRuntime stepRt, long timeoutMs) {
        ICancelToken cancelToken = stepRt.getCancelToken();
        CompletableFuture<TaskStepReturn> future = ret.getReturnPromise().toCompletableFuture();
        long deadline = timeoutMs == 0 ? Long.MAX_VALUE : System.currentTimeMillis() + timeoutMs;
        while (true) {
            // 取消唤醒（与 ExecutorTaskStepWrapper 取消兜底对称）：cancel token 触发等待显式取消完结
            if (cancelToken != null && cancelToken.isCancelled())
                throw new NopException(TaskErrors.ERR_TASK_CANCELLED);

            long remain = deadline - System.currentTimeMillis();
            if (remain <= 0) {
                LOG.warn("nop.task.sync-wait-timeout:stepPath={},timeoutMs={}",
                        stepRt.getStepPath(), timeoutMs);
                throw waitConfigError(stepRt, timeoutMs, "sync wait exceeded "
                        + timeoutMs + "ms (nop.task.step.sync-wait-timeout-ms)");
            }

            try {
                return future.get(Math.min(remain, POLL_INTERVAL_MS), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                // 轮询窗口内未完成：回环复查取消与时限（有界等待的轮询点）
            } catch (ExecutionException e) {
                throw NopException.adapt(e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NopException(TaskErrors.ERR_TASK_CANCELLED);
            }
        }
    }

    private NopException waitConfigError(ITaskStepRuntime stepRt, long value, String reason) {
        return new NopException(ERR_TASK_STEP_CONFIG_INVALID)
                .param(ARG_STEP_PATH, stepRt.getStepPath())
                .param(ARG_ATTR_NAME, "syncWaitTimeoutMs")
                .param(ARG_ATTR_VALUE, value)
                .param(ARG_REASON, reason);
    }
}

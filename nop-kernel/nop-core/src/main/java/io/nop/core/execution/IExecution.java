package io.nop.core.execution;

import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.util.retry.IRetryPolicy;
import io.nop.commons.util.retry.RetryHelper;

import java.util.concurrent.CompletionStage;

/**
 * 对异步任务执行进行建模，支持取消。不传递参数意味着它是一个闭包
 */
@FunctionalInterface
public interface IExecution<T> {
    CompletionStage<T> executeAsync(ICancelToken cancelToken);

    default <C> IExecution<T> withRetry(IRetryPolicy<C> retryPolicy, IScheduledExecutor executor, C context) {
        return cancelToken ->
                RetryHelper.retryExecute(() -> executeAsync(cancelToken), retryPolicy, executor, context);
    }
}
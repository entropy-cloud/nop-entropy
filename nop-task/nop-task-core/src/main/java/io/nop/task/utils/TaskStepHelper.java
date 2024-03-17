package io.nop.task.utils;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.api.core.util.ICancellable;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.util.retry.IRetryPolicy;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskErrors;
import io.nop.task.TaskStepResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static io.nop.core.CoreErrors.ARG_VAR_NAME;
import static io.nop.task.TaskConstants.SPECIAL_STEP_PREFIX;
import static io.nop.task.TaskErrors.ERR_TASK_CANCELLED;
import static io.nop.task.TaskErrors.ERR_TASK_RETRY_TIMES_EXCEED_LIMIT;

public class TaskStepHelper {

    public static TaskStepResult toStepResult(Object ret, String nextStepId) {
        if (ret instanceof TaskStepResult)
            return ((TaskStepResult) ret);
        return TaskStepResult.of(nextStepId, ret);
    }

    public static int getStepIndex(List<ITaskStep> steps, String stepId, int startIndex) {
        for (int i = startIndex; i < steps.size(); i++) {
            ITaskStep step = steps.get(i);
            // 所有step都必然存在id
            if (stepId.equals(step.getStepName()))
                return i;
        }
        return -1;
    }

    public static boolean isSpecialStep(String stepId) {
        return stepId.charAt(0) == SPECIAL_STEP_PREFIX;
    }

    public static NopException newError(ITaskStep step, ErrorCode errorCode, ITaskRuntime context, Throwable e) {
        if (e == null)
            return newError(step, errorCode, context);
        throw new NopException(errorCode, e).source(step).param(TaskErrors.ARG_TASK_NAME, context.getTaskName())
                .param(TaskErrors.ARG_STEP_NAME, step.getStepName()).param(TaskErrors.ARG_STEP_TYPE, step.getStepType());
    }

    public static NopException newError(ITaskStep step, ErrorCode errorCode, ITaskRuntime context) {
        throw new NopException(errorCode).source(step).param(TaskErrors.ARG_TASK_NAME, context.getTaskName())
                .param(TaskErrors.ARG_STEP_NAME, step.getStepName()).param(TaskErrors.ARG_STEP_TYPE, step.getStepType());
    }

    public static String getInternalStepId(String stepId, String internalName) {
        return stepId + '@' + internalName;
    }

    public static long getLong(Map<String, Object> vars, String name, long defaultValue) {
        Long value = ConvertHelper.toLong(vars.get(name), err -> new NopException(err).param(ARG_VAR_NAME, name));
        if (value == null)
            value = defaultValue;
        return value;
    }

    public static int getInt(Map<String, Object> vars, String name, int defaultValue) {
        Integer value = ConvertHelper.toInt(vars.get(name), err -> new NopException(err).param(ARG_VAR_NAME, name));
        if (value == null)
            value = defaultValue;
        return value;
    }

    public static TaskStepResult timeout(long timeout, Function<ICancelToken, TaskStepResult> task,
                                         ICancelToken cancelToken, ITaskRuntime taskRt) {
        IScheduledExecutor executor = taskRt.getScheduledExecutor();

        Cancellable cancellable = new Cancellable();
        if (cancelToken != null)
            cancellable.append(cancellable);

        Future<?> future = executor.schedule(() -> {
            cancellable.cancel(ICancellable.CANCEL_REASON_TIMEOUT);
            return null;
        }, timeout, TimeUnit.MILLISECONDS);

        TaskStepResult result = task.apply(cancellable);

        return result.whenComplete((v, e) -> {
            future.cancel(false);
        });
    }

    public static TaskStepResult retry(ITaskStep step, ITaskStepState state, ICancelToken cancelToken, ITaskRuntime taskRt,
                                       IRetryPolicy<ITaskStepState> retryPolicy, Callable<TaskStepResult> action) {
        do {
            if (cancelToken != null && cancelToken.isCancelled())
                throw newError(step, ERR_TASK_CANCELLED, taskRt);

            int retryAttempt = getInt(state.getRetryAttempt());
            if (retryAttempt > 0) {
                long delay = retryPolicy.getRetryDelay(state.exception(), retryAttempt, state);
                if (delay < 0) {
                    Throwable e = state.exception();
                    if (e == null)
                        e = newError(step, ERR_TASK_RETRY_TIMES_EXCEED_LIMIT, taskRt);
                    throw NopException.adapt(e);
                }

                if (delay > 0) {
                    return TaskStepResult.of(null, taskRt.getScheduledExecutor()
                            .schedule(action, delay, TimeUnit.MILLISECONDS).thenApply(result -> {
                                try {
                                    TaskStepResult ret = action.call();
                                    if (ret.isAsync()) {
                                        if (ret.isDone())
                                            return result.resolve();
                                    }
                                    return (Object) result.thenCompose((v, err) -> doRetry(v, err, step,
                                            state, cancelToken, taskRt, retryPolicy, action));
                                } catch (Exception e) {
                                    throw NopException.adapt(e);
                                }
                            }).exceptionally(err -> doRetry(null, err,
                                    step, state, cancelToken, taskRt, retryPolicy, action)
                            ));
                }
            }

            try {
                TaskStepResult result = action.call();
                if (result.isAsync()) {
                    if (result.isDone())
                        return result.resolve();

                    return result.thenCompose((v, err) -> doRetry(v, err, step,
                            state, cancelToken, taskRt, retryPolicy, action));
                }
            } catch (Exception e) {
                state.fail(e, taskRt);
            }

            state.setRetryAttempt(retryAttempt + 1);
            state.save();
        } while (true);
    }

    static Object doRetry(Object value, Throwable err,
                          ITaskStep step, ITaskStepState state, ICancelToken cancelToken, ITaskRuntime taskRt,
                          IRetryPolicy<ITaskStepState> retryPolicy, Callable<TaskStepResult> action) {
        if (err != null) {
            state.setRetryAttempt(getInt(state.getRetryAttempt()) + 1);
            state.save();
            return retry(step, state, cancelToken, taskRt, retryPolicy, action);
        } else {
            return value;
        }
    }

    static int getInt(Integer value) {
        return value == null ? 0 : value;
    }
}

package io.nop.task.utils;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.api.core.util.ICancellable;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.retry.IRetryPolicy;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskErrors;
import io.nop.task.TaskStepReturn;
import io.nop.task.exceptions.NopTaskCancelledException;
import io.nop.task.exceptions.NopTaskFailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.nop.core.CoreErrors.ARG_VAR_NAME;
import static io.nop.task.TaskErrors.ERR_TASK_CANCELLED;
import static io.nop.task.TaskErrors.ERR_TASK_RETRY_TIMES_EXCEED_LIMIT;

public class TaskStepHelper {
    static final Logger LOG = LoggerFactory.getLogger(TaskStepHelper.class);

    public static String buildStepPath(String parentPath, String stepName) {
        if (StringHelper.isEmpty(parentPath))
            return stepName;
        return parentPath + '/' + stepName;
    }

    public static NopException newError(SourceLocation loc,
                                        ITaskStepRuntime stepRt, ErrorCode errorCode, Throwable e) {
        if (e == null)
            return newError(loc, stepRt, errorCode);
        throw new NopException(errorCode, e).loc(loc).param(TaskErrors.ARG_TASK_NAME, stepRt.getTaskRuntime().getTaskName())
                .param(TaskErrors.ARG_STEP_PATH, stepRt.getStepPath()).param(TaskErrors.ARG_RUN_ID, stepRt.getRunId())
                .param(TaskErrors.ARG_STEP_TYPE, stepRt.getStepType());
    }

    public static NopException newError(SourceLocation loc, ITaskStepRuntime stepRt, ErrorCode errorCode) {
        throw new NopException(errorCode).loc(loc).param(TaskErrors.ARG_TASK_NAME, stepRt.getTaskRuntime().getTaskName())
                .param(TaskErrors.ARG_STEP_PATH, stepRt.getStepPath()).param(TaskErrors.ARG_RUN_ID, stepRt.getRunId())
                .param(TaskErrors.ARG_STEP_TYPE, stepRt.getStepType());
    }

    public static void checkNotCancelled(ITaskStepRuntime stepRt) {
        if (stepRt.isCancelled()) {
            LOG.warn("nop.task.step.cancelled:taskName={},stepPath={},taskId={},runId={}",
                    stepRt.getTaskRuntime().getTaskName(), stepRt.getStepPath(), stepRt.getTaskInstanceId(), stepRt.getRunId());
            throw NopTaskCancelledException.INSTANCE;
        }
    }

    public static boolean isCancelledException(Throwable e) {
        if (e instanceof CancellationException)
            return true;
        if (e instanceof NopTaskFailException || e instanceof NopTaskCancelledException)
            return true;
        if (e instanceof NopException)
            return ((NopException) e).getErrorCode().equals(ERR_TASK_CANCELLED.getErrorCode());
        return false;
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

    public static int castInt(Object value, SourceLocation loc, ITaskStepRuntime stepRt) {
        return ConvertHelper.toPrimitiveInt(value, err -> {
            return newError(loc, stepRt, err);
        });
    }

    public static TaskStepReturn timeout(long timeout, Function<ICancellable, TaskStepReturn> task,
                                         ICancelToken cancelToken, IScheduledExecutor executor) {
        Cancellable cancellable = new Cancellable();
        Consumer<String> cancel = cancellable::cancel;
        if (cancelToken != null)
            cancellable.append(cancellable);

        Future<?> future = executor.schedule(() -> {
            cancellable.cancel(ICancellable.CANCEL_REASON_TIMEOUT);
            return null;
        }, timeout, TimeUnit.MILLISECONDS);

        TaskStepReturn result = task.apply(cancellable);

        try {
            return result.whenComplete((v, e) -> {
                if (cancelToken != null)
                    cancelToken.removeOnCancel(cancel);
                future.cancel(false);
            });
        } catch (Exception e) {
            if (cancelToken != null) {
                cancelToken.removeOnCancel(cancel);
            }
            throw NopException.adapt(e);
        }
    }

    public static TaskStepReturn retry(SourceLocation loc, ITaskStepRuntime stepRt,
                                       IRetryPolicy<ITaskStepRuntime> retryPolicy, Callable<TaskStepReturn> action) {
        do {
            checkNotCancelled(stepRt);

            ITaskStepState state = stepRt.getState();
            int retryAttempt = getInt(state.getRetryAttempt());
            if (retryAttempt > 0) {
                long delay = retryPolicy.getRetryDelay(state.exception(), retryAttempt, stepRt);
                if (delay < 0) {
                    Throwable e = state.exception();
                    if (e == null)
                        e = newError(loc, stepRt, ERR_TASK_RETRY_TIMES_EXCEED_LIMIT);
                    throw NopException.adapt(e);
                }

                if (delay > 0) {
                    return TaskStepReturn.of(null, stepRt.getTaskRuntime().getScheduledExecutor()
                            .schedule(action, delay, TimeUnit.MILLISECONDS).thenApply(result -> {
                                try {
                                    TaskStepReturn ret = action.call();
                                    if (ret.isAsync()) {
                                        if (ret.isDone())
                                            return result.sync();
                                    }
                                    return (Object) result.thenCompose((v, err) -> doRetry(v, err, loc,
                                            stepRt, retryPolicy, action));
                                } catch (Exception e) {
                                    throw NopException.adapt(e);
                                }
                            }).exceptionally(err -> doRetry(null, err,
                                    loc, stepRt, retryPolicy, action)
                            ));
                }
            }

            try {
                TaskStepReturn result = action.call();
                if (result.isAsync()) {
                    if (result.isDone())
                        return result.sync();

                    return result.thenCompose((v, err) -> doRetry(v, err, loc,
                            stepRt, retryPolicy, action));
                }
            } catch (Exception e) {
                state.fail(e, stepRt.getTaskRuntime());
            }

            state.setRetryAttempt(retryAttempt + 1);
            stepRt.saveState();
        } while (true);
    }

    static TaskStepReturn doRetry(TaskStepReturn value, Throwable err,
                                  SourceLocation loc, ITaskStepRuntime stepRt,
                                  IRetryPolicy<ITaskStepRuntime> retryPolicy, Callable<TaskStepReturn> action) {
        if (err != null) {
            ITaskStepState state = stepRt.getState();
            state.setRetryAttempt(getInt(state.getRetryAttempt()) + 1);
            stepRt.saveState();
            return retry(loc, stepRt, retryPolicy, action);
        } else {
            return value;
        }
    }

    public static <T> CompletionStage<T> withCancellable(Supplier<CompletionStage<T>> task,
                                                         ITaskStepRuntime stepRt, boolean autoCancelUnfinished) {
        if (!autoCancelUnfinished)
            return task.get();

        Cancellable cancellable = new Cancellable();
        Consumer<String> cancel = cancellable::cancel;

        ICancelToken cancelToken = stepRt.getCancelToken();
        if (cancelToken != null) {
            cancelToken.appendOnCancel(cancel);
        }

        stepRt.setCancelToken(cancellable);

        try {
            return task.get().whenComplete((ret, err) -> {
                if (cancelToken != null)
                    cancelToken.removeOnCancel(cancel);
                cancellable.cancel(ICancellable.CANCEL_REASON_KILL);
                stepRt.setCancelToken(cancelToken);
            });
        } catch (Exception e) {
            if (cancelToken != null)
                cancelToken.removeOnCancel(cancel);
            cancellable.cancel();
            stepRt.setCancelToken(cancelToken);
            throw NopException.adapt(e);
        }
    }

    public static IEvalAction notNull(IEvalAction action) {
        return action == null ? IEvalAction.NULL_ACTION : action;
    }

    static int getInt(Integer value) {
        return value == null ? 0 : value;
    }

    public static Object getDumpValue(Object value) {
        if (value instanceof XNode)
            return ((XNode) value).xml();
        if (value.getClass().isAnnotationPresent(DataBean.class)) {
            return JsonTool.serialize(value, true);
        }
        return value;
    }
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;


import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ResolvedPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.nop.task.TaskConstants.STEP_NAME_END;
import static io.nop.task.TaskConstants.STEP_NAME_EXIT;
import static io.nop.task.TaskConstants.STEP_NAME_SUSPEND;

public final class TaskStepReturn {
    static final Logger LOG = LoggerFactory.getLogger(TaskStepReturn.class);

    public static final TaskStepReturn CONTINUE = new TaskStepReturn(null, null);
    public static final TaskStepReturn SUSPEND = new TaskStepReturn(STEP_NAME_SUSPEND, null);

    public static TaskStepReturn RETURN_RESULT_END(Object result) {
        Guard.checkArgument(!(result instanceof CompletionStage), "not allow async result");
        return new TaskStepReturn(STEP_NAME_END, Collections.singletonMap(TaskConstants.VAR_RESULT, result));
    }

    public static TaskStepReturn EXIT(Map<String, Object> outputs) {
        return new TaskStepReturn(STEP_NAME_EXIT, outputs);
    }

    public static TaskStepReturn RETURN(Map<String, Object> outputs) {
        if (outputs == null)
            return CONTINUE;
        return new TaskStepReturn(null, outputs);
    }

    public static TaskStepReturn RETURN_RESULT(Object value) {
        if (value == null)
            return CONTINUE;
        Guard.checkArgument(!(value instanceof CompletionStage), "not allow async result");
        return new TaskStepReturn(null, Collections.singletonMap(TaskConstants.VAR_RESULT, value));
    }

    public static TaskStepReturn RETURN(String nextStepName, Map<String, Object> outputs) {
        if (nextStepName == null && outputs == null)
            return CONTINUE;
        return new TaskStepReturn(nextStepName, outputs);
    }

    public static TaskStepReturn ASYNC_RETURN(CompletionStage<TaskStepReturn> future) {
        return new TaskStepReturn(future);
    }

    public static TaskStepReturn ASYNC(String nextStepName, CompletionStage<?> future) {
        if (FutureHelper.isFutureDone(future)) {
            return TaskStepReturn.of(nextStepName, FutureHelper.syncGet(future));
        }
        return new TaskStepReturn(future.thenApply(data -> {
            TaskStepReturn ret = TaskStepReturn.of(nextStepName, data);
            Guard.checkArgument(!ret.isAsync(), "not allow async return async result");
            return ret;
        }));
    }

    private final String nextStepName;
    private final Map<String, Object> outputs;
    private final CompletionStage<TaskStepReturn> future;

    private TaskStepReturn(String nextStepName, Map<String, Object> outputs, CompletionStage<TaskStepReturn> future) {
        this.nextStepName = nextStepName;
        this.outputs = outputs;
        this.future = future;
    }

    private static CompletionStage<TaskStepReturn> hookFuture(CompletionStage<TaskStepReturn> future) {
        if (future == null)
            return null;
        return future.thenCompose(ret -> {
            // 确保Promise返回的时候必然得到一个非异步的结果
            if (ret.isAsync()) {
                return ret.getReturnPromise();
            }
            return FutureHelper.success(ret);
        });
    }

    private TaskStepReturn(CompletionStage<TaskStepReturn> future) {
        this(null, null, hookFuture(future));
    }

    private TaskStepReturn(String nextStepName, Map<String, Object> outputs) {
        this(nextStepName, outputs, null);
    }

    public static TaskStepReturn of(String nextStepName, Object returnValue) {
        if (nextStepName == null && returnValue == null)
            return CONTINUE;

        if (returnValue instanceof TaskStepReturn) {
            TaskStepReturn returnResult = (TaskStepReturn) returnValue;
            if (returnResult.getNextStepName() == null && nextStepName != null)
                return new TaskStepReturn(nextStepName, returnResult.getOutputs());
            return returnResult;
        }

        if (STEP_NAME_SUSPEND.equals(nextStepName))
            return SUSPEND;

        if (returnValue instanceof CompletionStage)
            return new TaskStepReturn(((CompletionStage<?>) returnValue).thenApply(v -> {
                TaskStepReturn ret = of(nextStepName, v);
                Guard.checkArgument(!ret.isAsync(), "asyncReturn result must not be async");
                return ret;
            }));

        Map<String, Object> ret = Collections.singletonMap(TaskConstants.VAR_RESULT, returnValue);

        return new TaskStepReturn(nextStepName, ret);
    }

    /**
     * 判断返回结果是否可以被看作是TRUE值，用于上层的selector判断。
     *
     * <p>1. 如果没有任何输出，则认为是false</p>
     * <p>2. 如果输出中有RESULT变量，则判断RESULT变量是否为true</p>
     * <p>3. 如果没有RESULT变量，但是有其他输出，则认为是true </p>
     */
    public boolean isResultTruthy() {
        if (outputs == null || outputs.isEmpty())
            return false;

        if (outputs.containsKey(TaskConstants.VAR_RESULT))
            return ConvertHelper.toTruthy(outputs.get(TaskConstants.VAR_RESULT));

        return true;
    }

    public boolean isAsync() {
        return future != null;
    }

    public boolean isDone() {
        return FutureHelper.isFutureDone(future);
    }

    public TaskStepReturn syncIfDone() {
        if (future == null)
            return this;
        if (isDone())
            return sync();
        return this;
    }

    public Map<String, Object> syncGetOutputs() {
        if (future == null)
            return outputs;
        return FutureHelper.syncGet(future).get();
    }

    public Object syncGetResult() {
        Map<String, Object> ret = syncGetOutputs();
        if (ret == null)
            return null;
        return ret.get(TaskConstants.VAR_RESULT);
    }

    public Map<String, Object> get() {
        if (future != null)
            throw new IllegalArgumentException("nop.err.step-result-is-async");
        return outputs;
    }

    public Object getOutput(String name) {
        if (TaskConstants.VAR_OUTPUTS.equals(name))
            return outputs;
        if (outputs == null)
            return null;
        return outputs.get(name);
    }

    public Object getResult() {
        return getOutput(TaskConstants.VAR_RESULT);
    }

    public Map<String, Object> getOutputs() {
        return outputs;
    }

    public TaskStepReturn sync() {
        if (future == null)
            return this;

        return FutureHelper.syncGet(future);
    }

    public boolean isSuspend() {
        return this == SUSPEND;
    }

    public boolean isEnd() {
        return STEP_NAME_END.equals(nextStepName);
    }

    public boolean isExit() {
        return STEP_NAME_EXIT.equals(nextStepName);
    }

    public String getNextStepName() {
        return nextStepName;
    }

    public CompletionStage<TaskStepReturn> getReturnPromise() {
        if (future != null)
            return future;

        return FutureHelper.success(this);
    }

    public CompletionStage<Map<String, Object>> asyncOutputs() {
        return getReturnPromise().thenApply(TaskStepReturn::getOutputs);
    }

    public CompletionStage<Object> asyncResult() {
        return getReturnPromise().thenApply(ret -> {
            return ret.getOutput(TaskConstants.VAR_RESULT);
        });
    }

    public TaskStepReturn dropNextStepName() {
        if (nextStepName == null)
            return this;
        return new TaskStepReturn(null, outputs, this.future);
    }

    public TaskStepReturn thenCompose(BiFunction<TaskStepReturn, Throwable, TaskStepReturn> fn) {
        if (!isAsync()) {
            return fn.apply(this, null);
        }

        if (future instanceof ResolvedPromise) {
            ResolvedPromise<TaskStepReturn> resolved = (ResolvedPromise<TaskStepReturn>) future;
            return fn.apply(resolved.getResult(), resolved.getException());
        }

        return new TaskStepReturn(FutureHelper.thenCompleteAsync(future, fn));
    }

    public TaskStepReturn thenApply(Function<TaskStepReturn, TaskStepReturn> fn) {
        if (!isAsync()) {
            return fn.apply(this);
        }

        return new TaskStepReturn(future.thenApply(fn));
    }

    public TaskStepReturn whenComplete(BiConsumer<? super TaskStepReturn, ? super Throwable> consumer) {
        if (!isAsync()) {
            consumer.accept(this, null);
            return this;
        }

        return new TaskStepReturn(future.whenComplete(consumer));
    }

    public TaskStepReturn thenOnContext(IContext context) {
        return ASYNC(null, ContextProvider.thenOnContext(getReturnPromise(), context));
    }
}
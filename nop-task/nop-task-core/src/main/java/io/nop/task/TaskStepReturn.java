/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;


import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ResolvedPromise;

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
    public static final TaskStepReturn CONTINUE = new TaskStepReturn(null, null);
    public static final TaskStepReturn SUSPEND = new TaskStepReturn(STEP_NAME_SUSPEND, null);

    public static TaskStepReturn RETURN_RESULT_END(Object result) {
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
        return new TaskStepReturn(null, Collections.singletonMap(TaskConstants.VAR_RESULT, value));
    }

    public static TaskStepReturn RETURN(String nextStepName, Map<String, Object> outputs) {
        if (nextStepName == null && outputs == null)
            return CONTINUE;
        return new TaskStepReturn(nextStepName, outputs);
    }

    public static TaskStepReturn ASYNC(String nextStepName, CompletionStage<?> future) {
        if (FutureHelper.isFutureDone(future))
            return TaskStepReturn.of(nextStepName, FutureHelper.syncGet(future));
        return new TaskStepReturn(future.thenApply(data -> TaskStepReturn.of(nextStepName, data)));
    }

    private final String nextStepName;
    private final Map<String, Object> outputs;
    private final CompletionStage<TaskStepReturn> future;

    private TaskStepReturn(String nextStepName, Map<String, Object> outputs, CompletionStage<TaskStepReturn> future) {
        this.nextStepName = nextStepName;
        this.outputs = outputs;
        this.future = future;
    }

    private TaskStepReturn(CompletionStage<TaskStepReturn> future) {
        this(null, null, future);
    }

    private TaskStepReturn(String nextStepName, Map<String, Object> outputs) {
        this(nextStepName, outputs, null);
    }

    public static TaskStepReturn of(String nextStepName, Object returnValue) {
        if (nextStepName == null && returnValue == null)
            return CONTINUE;

        if (returnValue instanceof TaskStepReturn) {
            return (TaskStepReturn) returnValue;
        }

        if (STEP_NAME_SUSPEND.equals(nextStepName))
            return SUSPEND;

        if (returnValue instanceof Map)
            return new TaskStepReturn(nextStepName, (Map<String, Object>) returnValue);

        if (returnValue instanceof CompletionStage)
            return new TaskStepReturn(((CompletionStage<?>) returnValue).thenApply(v -> of(nextStepName, v)));

        Map<String, Object> ret = Collections.singletonMap(TaskConstants.VAR_RESULT, returnValue);

        return new TaskStepReturn(nextStepName, ret);
    }

    public boolean isAsync() {
        return future != null;
    }

    public boolean isDone() {
        return FutureHelper.isFutureDone(future);
    }

    public Map<String, Object> syncGet() {
        if (future == null)
            return outputs;
        return FutureHelper.syncGet(future).get();
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

    public Map<String, Object> getOutputs() {
        return outputs;
    }

    public TaskStepReturn resolve() {
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

    public TaskStepReturn runOnContext() {
        if (isDone())
            return this;

        return ASYNC(null, ContextProvider.thenOnContext(getReturnPromise()));
    }
}
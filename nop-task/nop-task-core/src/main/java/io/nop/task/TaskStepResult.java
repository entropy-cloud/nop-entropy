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

public final class TaskStepResult {
    public static final TaskStepResult CONTINUE = new TaskStepResult(null, null);
    public static final TaskStepResult SUSPEND = new TaskStepResult(STEP_NAME_SUSPEND, null);

    public static TaskStepResult RETURN_RESULT_END(Object result) {
        return new TaskStepResult(STEP_NAME_END, Collections.singletonMap(TaskConstants.VAR_RESULT, result));
    }

    public static TaskStepResult EXIT(Map<String, Object> returnValues) {
        return new TaskStepResult(STEP_NAME_EXIT, returnValues);
    }

    public static TaskStepResult RETURN(Map<String, Object> returnValues) {
        if (returnValues == null)
            return CONTINUE;
        return new TaskStepResult(null, returnValues);
    }

    public static TaskStepResult RETURN_RESULT(Object value) {
        if (value == null)
            return CONTINUE;
        return new TaskStepResult(null, Collections.singletonMap(TaskConstants.VAR_RESULT, value));
    }

    public static TaskStepResult RETURN(String nextStepName, Map<String, Object> returnValues) {
        if (nextStepName == null && returnValues == null)
            return CONTINUE;
        return new TaskStepResult(nextStepName, returnValues);
    }

    public static TaskStepResult ASYNC(String nextStepName, CompletionStage<?> future) {
        if (FutureHelper.isFutureDone(future))
            return TaskStepResult.of(nextStepName, FutureHelper.syncGet(future));
        return new TaskStepResult(future.thenApply(data -> TaskStepResult.of(nextStepName, data)));
    }

    private final String nextStepName;
    private final Map<String, Object> outputs;
    private final CompletionStage<TaskStepResult> future;

    private TaskStepResult(String nextStepName, Map<String, Object> outputs, CompletionStage<TaskStepResult> future) {
        this.nextStepName = nextStepName;
        this.outputs = outputs;
        this.future = future;
    }

    private TaskStepResult(CompletionStage<TaskStepResult> future) {
        this(null, null, future);
    }

    private TaskStepResult(String nextStepName, Map<String, Object> outputs) {
        this(nextStepName, outputs, null);
    }

    public static TaskStepResult of(String nextStepName, Object returnValue) {
        if (nextStepName == null && returnValue == null)
            return CONTINUE;

        if (returnValue instanceof TaskStepResult) {
            return (TaskStepResult) returnValue;
        }

        if (STEP_NAME_SUSPEND.equals(nextStepName))
            return SUSPEND;

        if (returnValue instanceof Map)
            return new TaskStepResult(nextStepName, (Map<String, Object>) returnValue);

        if (returnValue instanceof CompletionStage)
            return new TaskStepResult(((CompletionStage<?>) returnValue).thenApply(v -> of(nextStepName, v)));

        Map<String, Object> ret = Collections.singletonMap(TaskConstants.VAR_RESULT, returnValue);

        return new TaskStepResult(nextStepName, ret);
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

    public TaskStepResult resolve() {
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

    public CompletionStage<TaskStepResult> getReturnPromise() {
        if (future != null)
            return future;

        return FutureHelper.success(this);
    }

    public TaskStepResult thenCompose(BiFunction<TaskStepResult, Throwable, TaskStepResult> fn) {
        if (!isAsync()) {
            return fn.apply(this, null);
        }

        if (future instanceof ResolvedPromise) {
            ResolvedPromise<TaskStepResult> resolved = (ResolvedPromise<TaskStepResult>) future;
            return fn.apply(resolved.getResult(), resolved.getException());
        }

        return new TaskStepResult(FutureHelper.thenCompleteAsync(future, fn));
    }

    public TaskStepResult thenApply(Function<TaskStepResult, TaskStepResult> fn) {
        if (!isAsync()) {
            return fn.apply(this);
        }

        return new TaskStepResult(future.thenApply(fn));
    }

    public TaskStepResult whenComplete(BiConsumer<? super TaskStepResult, ? super Throwable> consumer) {
        if (!isAsync()) {
            consumer.accept(this, null);
            return this;
        }

        return new TaskStepResult(future.whenComplete(consumer));
    }

    public TaskStepResult runOnContext() {
        if (isDone())
            return this;

        return ASYNC(null, ContextProvider.thenOnContext(getReturnPromise()));
    }
}
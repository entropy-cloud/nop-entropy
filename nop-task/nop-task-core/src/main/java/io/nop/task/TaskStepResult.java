/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;


import io.nop.api.core.util.FutureHelper;

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

    public static TaskStepResult RESULT_END(Object returnValue) {
        return of(STEP_NAME_END, returnValue);
    }

    public static TaskStepResult RESULT_EXIT(Object returnValue) {
        return of(STEP_NAME_EXIT, returnValue);
    }

    private final String nextStepName;
    private final Object returnValue;

    private TaskStepResult(String nextStepName, Object returnValue) {
        this.nextStepName = nextStepName;
        this.returnValue = returnValue;
    }

    public static TaskStepResult of(String nextStepName, Object returnValue) {
        if (nextStepName == null && returnValue == null)
            return CONTINUE;
        if (STEP_NAME_SUSPEND.equals(nextStepName))
            return SUSPEND;
        if (returnValue instanceof TaskStepResult) {
            return (TaskStepResult) returnValue;
        }
        return new TaskStepResult(nextStepName, returnValue);
    }

    public boolean shouldExit() {
        return isEnd() || isExit();
    }

    public boolean isAsync() {
        return returnValue instanceof CompletionStage;
    }

    public boolean isDone() {
        return FutureHelper.isDone(returnValue);
    }

    public Object getResolvedValue() {
        return FutureHelper.getResult(returnValue);
    }

    public TaskStepResult resolve() {
        Object value = getResolvedValue();
        if (this.returnValue == value)
            return this;
        return of(getNextStepName(), value);
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

    public Object getReturnValue() {
        return returnValue;
    }

    public <T> CompletionStage<T> getReturnPromise() {
        return FutureHelper.toCompletionStage(returnValue);
    }

    public <T> TaskStepResult thenCompose(BiFunction<T, Throwable, ?> fn) {
        if (!isAsync()) {
            return TaskStepResult.of(getNextStepName(), fn.apply((T) getReturnValue(), null));
        }

        CompletionStage<T> promise = getReturnPromise();
        return TaskStepResult.of(getNextStepName(), FutureHelper.thenCompleteAsync(promise, fn));
    }

    public <T> TaskStepResult thenApply(Function<T, ?> fn) {
        if (!isAsync()) {
            return TaskStepResult.of(getNextStepName(), fn.apply((T) getReturnValue()));
        }
        CompletionStage<T> promise = getReturnPromise();
        return TaskStepResult.of(getNextStepName(), promise.thenApply(fn));
    }

    public <T> TaskStepResult whenComplete(BiConsumer<? super T, ? super Throwable> consumer) {
        if (!isAsync()) {
            consumer.accept((T) getReturnValue(), null);
            return this;
        }

        CompletionStage<T> promise = getReturnPromise();
        return TaskStepResult.of(getNextStepName(), promise.whenComplete(consumer));
    }
}
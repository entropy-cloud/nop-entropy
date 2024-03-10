/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;


import io.nop.api.core.util.FutureHelper;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.nop.task.TaskConstants.STEP_ID_END;
import static io.nop.task.TaskConstants.STEP_ID_EXIT;
import static io.nop.task.TaskConstants.STEP_ID_SUSPEND;

public final class TaskStepResult {
    public static final TaskStepResult RESULT_SUCCESS = new TaskStepResult(null, null);
    public static final TaskStepResult RESULT_SUSPEND = new TaskStepResult(STEP_ID_SUSPEND, null);

    public static TaskStepResult RESULT_END(Object returnValue) {
        return of(STEP_ID_END, returnValue);
    }

    public static TaskStepResult RESULT_EXIT(Object returnValue) {
        return of(STEP_ID_EXIT, returnValue);
    }

    private final String nextStepId;
    private final Object returnValue;

    private TaskStepResult(String nextStepId, Object returnValue) {
        this.nextStepId = nextStepId;
        this.returnValue = returnValue;
    }

    public static TaskStepResult of(String nextStepId, Object returnValue) {
        if (nextStepId == null && returnValue == null)
            return RESULT_SUCCESS;
        if (STEP_ID_SUSPEND.equals(nextStepId))
            return RESULT_SUSPEND;
        if (returnValue instanceof TaskStepResult) {
            TaskStepResult result = (TaskStepResult) returnValue;
            if (!Objects.equals(result.getNextStepId(), nextStepId))
                return TaskStepResult.of(nextStepId, returnValue);
            return result;
        }
        return new TaskStepResult(nextStepId, returnValue);
    }

    public boolean shouldNotContinue() {
        return isEnd() || isExit();
    }

    public boolean isResolved() {
        return !isAsync() && this != RESULT_SUSPEND;
    }

    public boolean isAsync() {
        return returnValue instanceof CompletionStage;
    }

    public boolean isNull() {
        return this == RESULT_SUCCESS;
    }

    public boolean isSuspend() {
        return this == RESULT_SUSPEND;
    }

    public boolean isEnd() {
        return STEP_ID_END.equals(nextStepId);
    }

    public boolean isExit() {
        return STEP_ID_EXIT.equals(nextStepId);
    }

    public String getNextStepId() {
        return nextStepId;
    }

    public Object getReturnValue() {
        return returnValue;
    }

    public <T> CompletionStage<T> getReturnPromise() {
        return FutureHelper.toCompletionStage(returnValue);
    }

    public <T> TaskStepResult thenCompose(BiFunction<T, Throwable, ?> fn) {
        CompletionStage<T> promise = getReturnPromise();
        return TaskStepResult.of(null, FutureHelper.thenCompleteAsync(promise, fn));
    }

    public <T> TaskStepResult thenApply(Function<T, ?> fn) {
        CompletionStage<T> promise = getReturnPromise();
        return TaskStepResult.of(null, promise.thenApply(fn));
    }

    public <T> TaskStepResult whenComplete(BiConsumer<? super T, ? super Throwable> consumer) {
        CompletionStage<T> promise = getReturnPromise();
        promise.whenComplete(consumer);
        return this;
    }
}
/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;
import jakarta.annotation.Nonnull;

import java.util.Set;

public class TryTaskStep extends DelegateTaskStep {
    private final IEvalAction catchAction;
    private final IEvalAction finallyAction;
    private final String nextOnError;

    public TryTaskStep(ITaskStep body, IEvalAction catchAction, IEvalAction finallyAction, String nextOnError) {
        super(body);
        this.catchAction = catchAction;
        this.finallyAction = finallyAction;
        this.nextOnError = nextOnError;
    }

    @Override
    @Nonnull
    public TaskStepResult execute(ITaskStepState stepState, Set<String> outputNames,
                                  ICancelToken cancelToken, ITaskRuntime taskRt) {

        IEvalScope scope = stepState.getEvalScope();
        boolean error = false;
        try {
            TaskStepResult result = getTaskStep().execute(stepState, outputNames, cancelToken, taskRt);
            if (result.isAsync()) {
                return result.thenCompose((ret, err) -> {
                    try {
                        if (err != null) {
                            if (catchAction != null) {
                                return TaskStepResult.of(nextOnError, catchAction.invoke(scope));
                            } else {
                                throw NopException.adapt(err);
                            }
                        }
                        return ret;
                    } finally {
                        if (finallyAction != null)
                            finallyAction.invoke(scope);
                    }
                });
            } else {
                return result;
            }
        } catch (Exception e) {
            error = true;
            if (catchAction != null) {
                return TaskStepResult.of(nextOnError, catchAction.invoke(scope));
            } else {
                throw NopException.adapt(e);
            }
        } finally {
            if (error && finallyAction != null) {
                finallyAction.invoke(scope);
            }
        }
    }
}
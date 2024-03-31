/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import jakarta.annotation.Nonnull;

public class TryTaskStepWrapper extends DelegateTaskStep {
    private final IEvalAction catchAction;
    private final IEvalAction finallyAction;

    public TryTaskStepWrapper(ITaskStep body, IEvalAction catchAction, IEvalAction finallyAction) {
        super(body);
        this.catchAction = catchAction;
        this.finallyAction = finallyAction;
    }

    @Override
    @Nonnull
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {

        boolean async = false;
        try {
            TaskStepReturn result = getTaskStep().execute(stepRt);
            if (result.isAsync()) {
                async = true;
                return result.thenCompose((ret, err) -> {
                    try {
                        if (err != null) {
                            if (catchAction != null) {
                                return TaskStepReturn.of(null, catchAction.invoke(stepRt));
                            } else {
                                throw NopException.adapt(err);
                            }
                        }
                        return ret;
                    } finally {
                        if (finallyAction != null)
                            finallyAction.invoke(stepRt);
                    }
                });
            } else {
                return result;
            }
        } catch (Exception e) {
            async = false;
            if (catchAction != null) {
                return TaskStepReturn.of(null, catchAction.invoke(stepRt));
            } else {
                throw NopException.adapt(e);
            }
        } finally {
            if (finallyAction != null && !async) {
                finallyAction.invoke(stepRt);
            }
        }
    }
}
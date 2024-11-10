/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;

public class TryTaskStepWrapper extends DelegateTaskStep {
    private final IEvalFunction catchAction;
    private final IEvalFunction finallyAction;

    private final boolean catchInternalException;

    public TryTaskStepWrapper(ITaskStep body, IEvalFunction catchAction, IEvalFunction finallyAction,
                              Boolean catchInternalException) {
        super(body);
        this.catchAction = catchAction;
        this.finallyAction = finallyAction;
        this.catchInternalException = Boolean.TRUE.equals(catchInternalException);
    }

    @Override
    @Nonnull
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        Throwable error = null;

        boolean async = false;
        try {
            TaskStepReturn result = getTaskStep().execute(stepRt);
            if (result.isAsync()) {
                async = true;
                return result.thenCompose((ret, err) -> {
                    try {
                        if (err != null) {
                            stepRt.setException(err);
                            if (!catchInternalException && TaskStepHelper.isCancelledException(err))
                                throw NopException.adapt(err);

                            if (catchAction != null) {
                                return TaskStepReturn.of(null, catchAction.call2(null, err, stepRt, stepRt.getEvalScope()));
                            } else {
                                throw NopException.adapt(err);
                            }
                        }
                        return ret;
                    } finally {
                        stepRt.runStepCleanups();
                        if (finallyAction != null)
                            finallyAction.call2(null, err, stepRt, stepRt.getEvalScope());
                    }
                });
            } else {
                return result;
            }
        } catch (Exception e) {
            error = e;
            stepRt.setException(e);

            if (!catchInternalException && TaskStepHelper.isCancelledException(e))
                throw NopException.adapt(e);

            async = false;
            if (catchAction != null) {
                return TaskStepReturn.of(null, catchAction.call2(null, error, stepRt, stepRt.getEvalScope()));
            } else {
                throw NopException.adapt(e);
            }
        } finally {
            if (!async) {
                stepRt.runStepCleanups();
                if (finallyAction != null) {
                    finallyAction.call2(null, error, stepRt, stepRt.getEvalScope());
                }
            }
        }
    }
}
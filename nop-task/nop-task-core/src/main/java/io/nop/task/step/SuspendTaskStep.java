/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepResult;
import jakarta.annotation.Nonnull;

public class SuspendTaskStep extends AbstractTaskStep {
    private IEvalPredicate resumeWhen;

    public IEvalPredicate getResumeWhen() {
        return resumeWhen;
    }

    public void setResumeWhen(IEvalPredicate resumeWhen) {
        this.resumeWhen = resumeWhen;
    }

    @Nonnull
    @Override
    public TaskStepResult execute(ITaskStepRuntime stepRt) {
        if (resumeWhen == null)
            return TaskStepResult.CONTINUE;

        if (!resumeWhen.passConditions(stepRt))
            return TaskStepResult.SUSPEND;
        return TaskStepResult.CONTINUE;
    }
}
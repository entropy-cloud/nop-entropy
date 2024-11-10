/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.task.ITaskStepExecution;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import jakarta.annotation.Nonnull;

public class IfTaskStep extends AbstractTaskStep {
    private IEvalPredicate condition;

    private ITaskStepExecution then;
    private ITaskStepExecution elseStep;

    public IEvalPredicate getCondition() {
        return condition;
    }

    public void setCondition(IEvalPredicate condition) {
        this.condition = condition;
    }

    public ITaskStepExecution getThen() {
        return then;
    }

    public void setThen(ITaskStepExecution then) {
        this.then = then;
    }

    public ITaskStepExecution getElseStep() {
        return elseStep;
    }

    public void setElseStep(ITaskStepExecution elseStep) {
        this.elseStep = elseStep;
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        Boolean caseValue = stepRt.getStateBean(Boolean.class);
        if (caseValue == null) {
            caseValue = condition.passConditions(stepRt);
            stepRt.setStateBean(caseValue);
            stepRt.saveState();
        }

        if (caseValue) {
            if (then == null)
                return TaskStepReturn.CONTINUE;

            return then.executeWithParentRt(stepRt);
        } else {
            if (elseStep == null)
                return TaskStepReturn.CONTINUE;
            return elseStep.executeWithParentRt(stepRt);
        }
    }
}

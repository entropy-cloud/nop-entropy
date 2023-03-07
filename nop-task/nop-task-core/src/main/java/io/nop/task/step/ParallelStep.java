/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.task.step;

import io.nop.task.ITaskContext;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class ParallelStep extends AbstractStep {
    private List<ITaskStep> steps;

    public List<ITaskStep> getSteps() {
        return steps;
    }

    public void setSteps(List<ITaskStep> steps) {
        this.steps = steps;
    }

    public boolean isShareState() {
        return false;
    }

    @Override
    protected void initStepState(ITaskStepState state) {
        state.setStateBean(new ParallelStateBean());
    }

    @Override
    protected TaskStepResult doExecute(ITaskStepState state, ITaskContext context) {
        ParallelStateBean states = state.getStateBean(ParallelStateBean.class);

        List<CompletionStage<?>> promises = new ArrayList<>();

        for (int i = 0, n = steps.size(); i < n; i++) {
            TaskStepResult result = steps.get(i).execute(state.getRunId(), null, state, context);
            if (result.isAsync()) {

            }
        }
        return null;
    }
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.AsyncHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class ForkNTaskStep extends AbstractForkTaskStep {
    private IEvalAction countExpr;

    public IEvalAction getCountExpr() {
        return countExpr;
    }

    public void setCountExpr(IEvalAction countExpr) {
        this.countExpr = countExpr;
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        Integer count = stepRt.getStateBean(Integer.class);
        if (count == null) {
            count = TaskStepHelper.castInt(countExpr.invoke(stepRt), getLocation(), stepRt);
            stepRt.setStateBean(count);
        }

        List<CompletionStage<TaskStepReturn>> promises = new ArrayList<>(count);

        int countParam = count;
        CompletionStage<Void> promise = TaskStepHelper.withCancellable(() -> {
            for (int i = 0; i < countParam; i++) {
                try {
                    TaskStepReturn result = executeFork(stepRt, null, i);
                    promises.add(result.getReturnPromise());
                } catch (Exception e) {
                    promises.add(FutureHelper.reject(e));
                }
            }

            return AsyncHelper.waitAsync(promises, getStepJoinType());
        }, stepRt, isAutoCancelUnfinished());

        return buildAggResult(promise, promises, stepRt);
    }
}

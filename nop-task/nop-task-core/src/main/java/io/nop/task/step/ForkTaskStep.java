/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.AsyncHelper;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class ForkTaskStep extends AbstractForkTaskStep {
    private IEvalAction producer;

    public IEvalAction getProducer() {
        return producer;
    }

    public void setProducer(IEvalAction producer) {
        this.producer = producer;
    }

    @DataBean
    public static class ForkStateBean {
        private List<Object> items;

        public List<Object> getItems() {
            return items;
        }

        public void setItems(List<Object> items) {
            this.items = items;
        }
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        ForkStateBean stateBean = stepRt.getStateBean(ForkStateBean.class);
        if (stateBean == null) {
            stateBean = new ForkStateBean();
            List<Object> produced = CollectionHelper.toList(producer.invoke(stepRt));
            // producer 求值为 null 视为空集合（对齐 LoopTaskStep 的空守卫，plan 349 Phase 5），
            // 修复前 toList(null) 返回 null 导致 items.size() NPE
            stateBean.setItems(produced == null ? new ArrayList<>() : produced);
            stepRt.setStateBean(stateBean);
            stepRt.saveState();
        }

        List<Object> items = stateBean.getItems();
        List<CompletionStage<TaskStepReturn>> promises = new ArrayList<>(items == null ? 0 : items.size());

        CompletionStage<Void> promise;
        if (items != null && !items.isEmpty()) {
            promise = TaskStepHelper.withCancellable(() -> {
                for (int i = 0; i < items.size(); i++) {
                    try {
                        TaskStepReturn result = executeFork(stepRt, items.get(i), i);
                        promises.add(result.getReturnPromise());
                    } catch (Exception e) {
                        promises.add(FutureHelper.reject(e));
                    }
                }

                return AsyncHelper.waitAsync(promises, getStepJoinType());
            }, stepRt, isAutoCancelUnfinished());
        } else {
            promise = FutureHelper.success(null);
        }


        return buildAggResult(promise, promises, stepRt);
    }
}

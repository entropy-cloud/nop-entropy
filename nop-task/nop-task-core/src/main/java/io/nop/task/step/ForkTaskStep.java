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

    /**
     * W-07（CRA 8.49 R4-09，1.5-C5-005 裁定 D）：items 移出持久化面——stateBean 仅持
     * itemCount（resume 去重标记语义保留：stateBean 非空即已产出），items 经 task attribute
     * 进程内持有（{@link #ITEMS_ATTR}），跨 resume 经 producer 重算。
     */
    @DataBean
    public static class ForkStateBean {
        private int itemCount;

        public int getItemCount() {
            return itemCount;
        }

        public void setItemCount(int itemCount) {
            this.itemCount = itemCount;
        }

        /**
         * W-07 向后兼容只写属性：修复前的持久化行把全量 items 写在 wrapper 内。保留该 setter
         * 使旧行仍可反序列化（无对应 getter，故新序列化结果不再含 items）；值直接丢弃——
         * items 一律经 {@link #resolveItems} 由 task attribute/producer 重算。
         */
        public void setItems(List<Object> ignoredLegacyItems) {
            // no-op：仅为旧持久化行反序列化兼容
        }
    }

    /**
     * W-07（裁定 D）：items 进程内持有键（不跨 resume——缺失时以 producer 重算）。
     */
    static final String ITEMS_ATTR = "nop.task.fork.items";

    /**
     * W-07（裁定 D）：items 解析——优先 task attribute，缺失时经 producer 重算
     * （producer 求值为 null 视为空集合，plan 349 Phase 5 空守卫零回退）。
     */
    private List<Object> resolveItems(ITaskStepRuntime stepRt) {
        Object cached = stepRt.getTaskRuntime().getAttribute(ITEMS_ATTR);
        if (cached instanceof List)
            return (List<Object>) cached;
        List<Object> produced = CollectionHelper.toList(producer.invoke(stepRt));
        List<Object> items = produced == null ? new ArrayList<>() : produced;
        stepRt.getTaskRuntime().setAttribute(ITEMS_ATTR, items);
        return items;
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        ForkStateBean stateBean = stepRt.getStateBean(ForkStateBean.class);
        List<Object> items = resolveItems(stepRt);
        if (stateBean == null) {
            stateBean = new ForkStateBean();
            stateBean.setItemCount(items == null ? 0 : items.size());
            stepRt.setStateBean(stateBean);
            stepRt.saveState();
        }
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

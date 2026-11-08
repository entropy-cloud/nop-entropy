/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import jakarta.annotation.Nonnull;

import java.util.List;

import static io.nop.task.TaskStepReturn.RETURN;
import static io.nop.task.TaskStepReturn.RETURN_RESULT;
import static io.nop.task.TaskStepReturn.RETURN_RESULT_END;


public class LoopTaskStep extends AbstractTaskStep {
    private String varName;
    private String indexName;

    private IEvalAction itemsExpr;
    private int maxCount;

    /**
     * W-07（裁定 D）：items 进程内持有键（不跨 resume——resume 每次 newMainStepRuntime 重建
     * stepRt，缺失时以 itemsExpr 重算）。itemsExpr 须为确定性/纯函数以支持跨进程断点续跑。
     */
    static final String ITEMS_ATTR = "nop.task.loop.items";
    private ITaskStep body;
    private IEvalPredicate untilExpr;
    private IEvalPredicate whileExpr;

    public int getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }

    public IEvalPredicate getWhileExpr() {
        return whileExpr;
    }

    public void setWhileExpr(IEvalPredicate whileExpr) {
        this.whileExpr = whileExpr;
    }

    public IEvalPredicate getUntilExpr() {
        return untilExpr;
    }

    public void setUntilExpr(IEvalPredicate untilExpr) {
        this.untilExpr = untilExpr;
    }

    public String getVarName() {
        return varName;
    }

    public void setVarName(String varName) {
        this.varName = varName;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public IEvalAction getItemsExpr() {
        return itemsExpr;
    }

    public void setItemsExpr(IEvalAction itemsExpr) {
        this.itemsExpr = itemsExpr;
    }

    public void setBody(ITaskStep body) {
        this.body = body;
    }

    /**
     * W-07（CRA 8.49 R4-09，1.5-C5-005 裁定 D）：items 已移出持久化面——仅持有 index，
     * items 经 task attribute 进程内持有（{@link #ITEMS_ATTR}），跨进程 resume 经 itemsExpr
     * 重算。旧持久化行 wrapper 内残留的 items 键由 {@link #setItems} 只写兼容属性吸收
     * （向后兼容零迁移）。
     *
     * <p>效果：每迭代 saveState 的 stateBean 序列化量 O(1)（修复前含全量 items = 累计 O(N²)）。
     */
    @DataBean
    public static class LoopStateBean {
        /**
         * 当前正在执行的循环下标
         */
        private int index;

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public void incIndex() {
            this.index++;
        }

        /**
         * W-07 向后兼容只写属性：修复前的持久化行把全量 items 写在 wrapper 内。保留该 setter
         * 使旧行仍可反序列化（无对应 getter，故新序列化结果不再含 items）；值直接丢弃——
         * items 一律经 {@link #resolveItems} 由 task attribute/itemsExpr 重算。
         */
        public void setItems(List<Object> ignoredLegacyItems) {
            // no-op：仅为旧持久化行反序列化兼容
        }
    }

    /**
     * W-07（裁定 D）：items 解析——优先 task attribute（同一次 execute 调用内存活），
     * 缺失时经 itemsExpr 重算（resume 路径确定性重建）。
     */
    private List<Object> resolveItems(ITaskStepRuntime stepRt) {
        Object cached = stepRt.getTaskRuntime().getAttribute(ITEMS_ATTR);
        if (cached instanceof List)
            return (List<Object>) cached;
        List<Object> items = CollectionHelper.toList(itemsExpr.invoke(stepRt));
        stepRt.getTaskRuntime().setAttribute(ITEMS_ATTR, items);
        return items;
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        LoopStateBean stateBean = stepRt.getStateBean(LoopStateBean.class);
        List<Object> items = resolveItems(stepRt);
        if (stateBean == null) {
            stateBean = new LoopStateBean();
            stateBean.setIndex(0);
            stepRt.setStateBean(stateBean);
        }

        if (CollectionHelper.isEmpty(items))
            return RETURN_RESULT_END(stepRt.getResult());

        if (whileExpr != null) {
            if (!whileExpr.passConditions(stepRt))
                return RETURN_RESULT_END(stepRt.getResult());
        }

        do {
            if (varName != null) {
                int index = stateBean.getIndex();
                Object item = items.get(index);
                stepRt.setValue(varName, item);
            }
            if (indexName != null) {
                stepRt.setValue(indexName, stateBean.getIndex());
            }

            TaskStepReturn stepResult = body.execute(stepRt);
            if (stepResult.isSuspend())
                return stepResult;

            if (stepResult.isDone()) {
                stepResult = stepResult.sync();

                stateBean.incIndex();
                stepRt.setBodyStepIndex(0);
                stepRt.saveState();

                if (stepResult.isEnd())
                    return stepResult;
                if (stepResult.isExit())
                    return RETURN(stepResult.getOutputs());
            } else {
                LoopStateBean stateParam = stateBean;
                return stepResult.thenApply(ret -> {
                    if (ret.isSuspend())
                        return ret;

                    stateParam.incIndex();
                    stepRt.setBodyStepIndex(0);
                    stepRt.saveState();

                    if (ret.isEnd())
                        return RETURN_RESULT_END(stepRt.getResult());

                    if (ret.isExit())
                        return RETURN(ret.getOutputs());

                    if (!shouldContinue(stateParam, stepRt))
                        return RETURN_RESULT(stepRt.getResult());

                    return execute(stepRt);
                });
            }

            if (!shouldContinue(stateBean, stepRt))
                return RETURN_RESULT(stepRt.getResult());

        } while (true);
    }

    boolean shouldContinue(LoopStateBean state, ITaskStepRuntime stepRt) {
        // W-07（裁定 D）：items 不再驻留 stateBean——经 attribute 取回（缺失时重算）
        List<Object> items = resolveItems(stepRt);
        if (items != null) {
            if (maxCount > 0 && state.getIndex() >= maxCount)
                return false;

            if (state.getIndex() >= items.size())
                return false;
        }

        if (whileExpr != null && !whileExpr.passConditions(stepRt))
            return false;

        if (untilExpr != null && untilExpr.passConditions(stepRt)) {
            return false;
        }

        return true;
    }
}
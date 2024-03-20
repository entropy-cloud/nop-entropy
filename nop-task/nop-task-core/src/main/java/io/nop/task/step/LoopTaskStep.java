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
import io.nop.task.IEnhancedTaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepResult;
import jakarta.annotation.Nonnull;

import java.util.List;

import static io.nop.task.TaskStepResult.RETURN_RESULT;
import static io.nop.task.TaskStepResult.RETURN_RESULT_END;


public class LoopTaskStep extends AbstractTaskStep {
    private String varName;
    private String indexName;

    private IEvalAction itemsExpr;
    private IEnhancedTaskStep body;

    private IEvalPredicate untilExpr;

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

    public void setBody(IEnhancedTaskStep body) {
        this.body = body;
    }

    @DataBean
    public static class LoopStateBean {
        private List<Object> items;

        /**
         * 当前正在执行的循环下标
         */
        private int index;

        public List<Object> getItems() {
            return items;
        }

        public void setItems(List<Object> items) {
            this.items = items;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public void incIndex() {
            this.index++;
        }
    }

    @Nonnull
    @Override
    public TaskStepResult execute(ITaskStepRuntime stepRt) {
        LoopStateBean stateBean = stepRt.getStateBean(LoopStateBean.class);
        if (stateBean == null) {
            stateBean = new LoopStateBean();
            List<Object> items = CollectionHelper.toList(itemsExpr.invoke(stepRt));
            stateBean.setItems(items);
            stateBean.setIndex(0);
            stepRt.setStateBean(stateBean);
        }

        do {
            if (shouldContinue(stateBean, stepRt))
                return RETURN_RESULT(stepRt.getResult());

            TaskStepResult stepResult = body.executeWithParentRt(stepRt);
            if (stepResult.isSuspend())
                return stepResult;

            if (stepResult.isDone()) {
                stateBean.incIndex();
                stepRt.saveState();

                stepResult = stepResult.resolve();
                if (stepResult.isEnd())
                    return stepResult;
                if (stepResult.isExit())
                    return RETURN_RESULT(stepRt.getResult());
            } else {
                LoopStateBean stateParam = stateBean;
                return stepResult.thenApply(ret -> {
                    if (ret.isSuspend())
                        return ret;

                    stateParam.incIndex();
                    stepRt.saveState();

                    if (ret.isEnd())
                        return RETURN_RESULT_END(stepRt.getResult());

                    if (ret.isExit())
                        return RETURN_RESULT(stepRt.getResult());
                    return execute(stepRt);
                });
            }
        } while (true);
    }

    boolean shouldContinue(LoopStateBean state, ITaskStepRuntime stepRt) {
        if (state.getItems() != null) {
            if (state.getIndex() >= state.getItems().size())
                return false;
        }

        if (untilExpr != null) {
            return untilExpr.passConditions(stepRt);
        }

        return true;
    }
}
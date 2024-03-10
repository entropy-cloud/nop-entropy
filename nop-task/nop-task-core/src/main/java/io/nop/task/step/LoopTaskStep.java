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
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITaskContext;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;

import java.util.List;
import java.util.concurrent.CompletionStage;

import static io.nop.task.TaskStepResult.RESULT_SUSPEND;


public class LoopTaskStep extends AbstractTaskStep {
    private String varName;
    private String indexName;

    private IEvalAction itemsExpr;
    private ITaskStep body;

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

    public ITaskStep getBody() {
        return body;
    }

    public void setBody(ITaskStep body) {
        this.body = body;
    }

    @Override
    protected void initStepState(ITaskStepState state, ITaskContext context) {
        List<Object> items = CollectionHelper.toList(getItemsExpr().invoke(state.getEvalScope()));
        LoopStateBean stateBean = new LoopStateBean();
        stateBean.setBodyRunId(0);
        stateBean.setIndex(0);
        stateBean.setItems(items);
        state.setStateBean(stateBean);
    }

    @DataBean
    public static class LoopStateBean {
        private int bodyRunId;

        private List<Object> items;

        /**
         * 当前正在执行的循环下标
         */
        private int index;

        public int getBodyRunId() {
            return bodyRunId;
        }

        public void setBodyRunId(int bodyRunId) {
            this.bodyRunId = bodyRunId;
        }

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

    public boolean isShareState() {
        return false;
    }

    @Override
    protected TaskStepResult doExecute(ITaskStepState state, ITaskContext context) {

        LoopStateBean stateBean = state.getStateBean(LoopStateBean.class);

        do {
            TaskStepResult stepResult = state.result();
            if (stepResult.isEnd())
                return stepResult;

            if (stepResult.isExit()) {
                return toStepResult(stepResult.getReturnValue());
            }

            if (!shouldContinue(stateBean, state.getEvalScope(), context))
                return TaskStepResult.RESULT_SUCCESS;

            int bodyRunId = stateBean.getBodyRunId();

            stepResult = body.execute(bodyRunId, state, context);
            if (stepResult.isAsync()) {
                CompletionStage<Object> promise = stepResult.getReturnPromise().thenApply(ret -> {
                    TaskStepResult result = toStepResult(ret);
                    state.result(result);
                    stateBean.incIndex();
                    saveState(state, context);
                    return doExecute(state, context);
                });
                return TaskStepResult.of(null, promise);
            }

            state.setResultValue(stepResult);
            stateBean.incIndex();
            stateBean.setBodyRunId(context.newRunId());
            saveState(state, context);

            // 在saveState之后判断suspend。刚进入doExecute时不能判断suspend, 因为有可能是从休眠中恢复
            if (stepResult == RESULT_SUSPEND)
                return stepResult;

        } while (true);
    }

    boolean shouldContinue(LoopStateBean state, IEvalScope scope, ITaskContext context) {
        if (state.getItems() != null) {
            if (state.getIndex() >= state.getItems().size())
                return false;
        }

        if (untilExpr != null) {
            return untilExpr.passConditions(scope);
        }

        return true;
    }
}
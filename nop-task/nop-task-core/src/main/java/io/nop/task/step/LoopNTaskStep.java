/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITaskContext;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;

import java.util.concurrent.CompletionStage;

import static io.nop.task.TaskStepResult.RESULT_SUSPEND;


public class LoopNTaskStep extends AbstractTaskStep {
    private String varName;
    private String indexName;

    private int step = 1;

    private IEvalAction beginExpr;

    private IEvalAction endExpr;
    private ITaskStep body;

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

    public ITaskStep getBody() {
        return body;
    }

    public void setBody(ITaskStep body) {
        this.body = body;
    }

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        Guard.checkArgument(step != 0, "step must not be zero");
        this.step = step;
    }

    public void setBeginExpr(IEvalAction beginExpr) {
        this.beginExpr = beginExpr;
    }

    public void setEndExpr(IEvalAction endExpr) {
        this.endExpr = endExpr;
    }

    @Override
    protected void initStepState(ITaskStepState state, ITaskContext context) {
        IEvalScope scope = state.getEvalScope();
        int begin = ConvertHelper.toPrimitiveInt(beginExpr.invoke(scope), 0, NopException::new);
        int end = ConvertHelper.toPrimitiveInt(endExpr.invoke(scope), 0, NopException::new);

        LoopStateBean stateBean = new LoopStateBean();
        stateBean.setBodyRunId(0);
        stateBean.setIndex(begin);
        stateBean.setEnd(end);
        state.setStateBean(stateBean);
    }

    @DataBean
    public static class LoopStateBean {
        private int bodyRunId;

        private int end;

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

        public int getEnd() {
            return end;
        }

        public void setEnd(int end) {
            this.end = end;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public void incStep(int step) {
            this.index += step;
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
                    stateBean.incStep(step);
                    saveState(state, context);
                    return doExecute(state, context);
                });
                return TaskStepResult.of(null, promise);
            }

            state.setResultValue(stepResult);
            stateBean.incStep(step);
            stateBean.setBodyRunId(context.newRunId());
            saveState(state, context);

            // 在saveState之后判断suspend。刚进入doExecute时不能判断suspend, 因为有可能是从休眠中恢复
            if (stepResult == RESULT_SUSPEND)
                return stepResult;

        } while (true);
    }

    boolean shouldContinue(LoopStateBean state, IEvalScope scope, ITaskContext context) {
        if (step > 0) {
            return state.getIndex() < state.getEnd();
        }
        return state.getIndex() > state.getEnd();
    }
}
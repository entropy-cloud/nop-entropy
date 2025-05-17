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
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;

import static io.nop.task.TaskErrors.ARG_BEGIN;
import static io.nop.task.TaskErrors.ARG_END;
import static io.nop.task.TaskErrors.ARG_STEP;
import static io.nop.task.TaskErrors.ERR_TASK_LOOP_STEP_INVALID_LOOP_VAR;
import static io.nop.task.TaskStepReturn.RETURN_RESULT;
import static io.nop.task.TaskStepReturn.RETURN_RESULT_END;


public class LoopNTaskStep extends AbstractTaskStep {
    private String varName;
    private String indexName;

    private IEvalAction stepExpr;

    private IEvalAction beginExpr;

    private IEvalAction endExpr;
    private IEvalPredicate untilExpr;

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

    public void setBody(ITaskStep body) {
        this.body = body;
    }

    public void setBeginExpr(IEvalAction beginExpr) {
        this.beginExpr = beginExpr;
    }

    public void setEndExpr(IEvalAction endExpr) {
        this.endExpr = endExpr;
    }

    public IEvalAction getStepExpr() {
        return stepExpr;
    }

    public void setStepExpr(IEvalAction stepExpr) {
        this.stepExpr = stepExpr;
    }

    public IEvalPredicate getUntilExpr() {
        return untilExpr;
    }

    public void setUntilExpr(IEvalPredicate untilExpr) {
        this.untilExpr = untilExpr;
    }

    @DataBean
    public static class LoopStateBean {
        private int bodyRunId;

        private int end;

        private int step;

        /**
         * 当前正在执行的循环下标
         */
        private int current;

        private int index;

        public boolean shouldContinue() {
            if (step > 0)
                return current <= end;
            return current >= end;
        }

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

        public int getStep() {
            return step;
        }

        public void setStep(int step) {
            this.step = step;
        }

        public int getCurrent() {
            return current;
        }

        public void setCurrent(int current) {
            this.current = current;
        }

        public int getIndex() {
            return index;
        }

        public void incStep() {
            this.index++;
            this.current += step;
        }
    }


    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {

        LoopStateBean stateBean = stepRt.getStateBean(LoopStateBean.class);
        if (stateBean == null) {
            stateBean = new LoopStateBean();
            // 初始化循环变量
            int begin = ConvertHelper.toPrimitiveInt(beginExpr.invoke(stepRt), NopException::new);
            int end = ConvertHelper.toPrimitiveInt(endExpr.invoke(stepRt), NopException::new);
            int step = stepExpr == null ? 1 : ConvertHelper.toPrimitiveInt(stepExpr.invoke(stepRt), NopException::new);

            if (step == 0)
                throw TaskStepHelper.newError(getLocation(), stepRt, ERR_TASK_LOOP_STEP_INVALID_LOOP_VAR)
                        .param(ARG_BEGIN, begin).param(ARG_END, end).param(ARG_STEP, step);

            stateBean.setCurrent(begin);
            stateBean.setEnd(end);
            stateBean.setStep(step);
            stepRt.setStateBean(stateBean);

        }

        do {
            if (!stateBean.shouldContinue()) {
                return TaskStepReturn.RETURN_RESULT(stepRt.getResult());
            }

            if (varName != null) {
                stepRt.setValue(varName, stateBean.getCurrent());
            }
            if (indexName != null) {
                stepRt.setValue(indexName, stateBean.getIndex());
            }

            TaskStepReturn stepResult = body.execute(stepRt);
            if (stepResult.isSuspend())
                return stepResult;

            if (stepResult.isDone()) {
                stepResult = stepResult.sync();

                stateBean.incStep();
                stepRt.setBodyStepIndex(0);
                stepRt.saveState();

                if (stepResult.isEnd())
                    return stepResult;
                if (stepResult.isExit())
                    return RETURN_RESULT(stepRt.getResult());
            } else {
                LoopStateBean stateParam = stateBean;
                return stepResult.thenApply(ret -> {
                    if (ret.isSuspend())
                        return ret;

                    stateParam.incStep();
                    stepRt.setBodyStepIndex(0);
                    stepRt.saveState();

                    if (ret.isEnd())
                        return RETURN_RESULT_END(stepRt.getResult());

                    if (ret.isExit())
                        return RETURN_RESULT(stepRt.getResult());

                    if (untilExpr != null && untilExpr.passConditions(stepRt))
                        return RETURN_RESULT_END(stepRt.getResult());

                    return execute(stepRt);
                });
            }

            if (untilExpr != null && untilExpr.passConditions(stepRt)) {
                return TaskStepReturn.RETURN_RESULT(stepRt.getResult());
            }
        } while (true);
    }
}
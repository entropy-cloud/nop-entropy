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

import static io.nop.task.TaskConfigs.CFG_TASK_STEP_MAX_LOOP_COUNT;
import static io.nop.task.TaskErrors.ARG_BEGIN;
import static io.nop.task.TaskErrors.ARG_END;
import static io.nop.task.TaskErrors.ARG_REASON;
import static io.nop.task.TaskErrors.ARG_STEP;
import static io.nop.task.TaskErrors.ERR_TASK_LOOP_STEP_INVALID_LOOP_VAR;
import static io.nop.task.TaskErrors.ERR_TASK_STEP_CONFIG_INVALID;
import static io.nop.task.TaskStepReturn.RETURN;
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

            // W-10（CRA 8.48 R4-08，MA-18 裁定 D，运行期上限路线 β）：迭代数上界校验（long 域
            // 防 int 溢出误判）。含端点语义（current<=end / current>=end）：迭代数 =
            // floor(|end-begin|/|step|)+1，begin=end 实跑 1 次迭代天然放行；超上界运行期显式拒绝
            //（消除负 step 反向计数/整数回绕导致的失控循环面——极端 step 值跨越 int 边界由
            // incLoopStepChecked 运行期兜底硬失败）
            long iterationCount = Math.abs((long) end - (long) begin) / Math.abs((long) step) + 1;
            long maxLoopCount = CFG_TASK_STEP_MAX_LOOP_COUNT.get().longValue();
            if (iterationCount > maxLoopCount) {
                throw TaskStepHelper.newError(getLocation(), stepRt, ERR_TASK_STEP_CONFIG_INVALID)
                        .param(ARG_BEGIN, begin).param(ARG_END, end).param(ARG_STEP, step)
                        .param(ARG_REASON, "loop iteration count " + iterationCount
                                + " (floor(|end-begin|/|step|)+1) exceeds nop.task.step.max-loop-count="
                                + maxLoopCount);
            }

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

                incLoopStepChecked(stepRt, stateBean);
                stepRt.setBodyStepIndex(0);
                stepRt.saveState();

                if (stepResult.isEnd())
                    return stepResult;
                // exit 携带 body 的退出 outputs（对齐 LoopTaskStep:162，plan 349 Phase 5）
                if (stepResult.isExit())
                    return RETURN(stepResult.getOutputs());
            } else {
                LoopStateBean stateParam = stateBean;
                return stepResult.thenApply(ret -> {
                    if (ret.isSuspend())
                        return ret;

                    incLoopStepChecked(stepRt, stateParam);
                    stepRt.setBodyStepIndex(0);
                    stepRt.saveState();

                    if (ret.isEnd())
                        return RETURN_RESULT_END(stepRt.getResult());

                    // exit 携带 body 的退出 outputs（对齐 LoopTaskStep:177，plan 349 Phase 5）
                    if (ret.isExit())
                        return RETURN(ret.getOutputs());

                    if (untilExpr != null && untilExpr.passConditions(stepRt))
                        // 循环因 until 满足而正常结束：返回 CONTINUE 语义（后续步骤继续执行）。
                        // 修复前异步路径返回 RETURN_RESULT_END（END 语义），与同步路径 :223 不一致，
                        // 同一模型仅因循环体同步/异步而终止单/继续执行后续步骤
                        return RETURN_RESULT(stepRt.getResult());

                    return execute(stepRt);
                });
            }

            if (untilExpr != null && untilExpr.passConditions(stepRt)) {
                return TaskStepReturn.RETURN_RESULT(stepRt.getResult());
            }
        } while (true);
    }

    /**
     * W-10（CRA 8.48 R4-08，MA-18 裁定 D (ii)）：incStep 运行期下溢/溢出兜底——current 跨越
     * int 边界符号翻转（如 step 值极大导致 begin+step 溢出）即硬失败。常态越界由初始化段的
     * 迭代数上界前置拦截，本兜底仅承载极端 step 值的运行期断言义务（禁静默回绕续跑）。
     */
    private void incLoopStepChecked(ITaskStepRuntime stepRt, LoopStateBean stateBean) {
        int before = stateBean.getCurrent();
        stateBean.incStep();
        int now = stateBean.getCurrent();
        int step = stateBean.getStep();
        boolean overflow = step > 0 ? now < before : now > before;
        if (overflow) {
            throw TaskStepHelper.newError(getLocation(), stepRt, ERR_TASK_STEP_CONFIG_INVALID)
                    .param(ARG_BEGIN, before).param(ARG_END, now).param(ARG_STEP, step)
                    .param(ARG_REASON, "loop current overflowed int range (underflow guard, CRA 8.48 R4-08 W-10)");
        }
    }
}
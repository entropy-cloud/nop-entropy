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

import static io.nop.task.TaskConfigs.CFG_TASK_STEP_MAX_FORK_COUNT;
import static io.nop.task.TaskErrors.ARG_ATTR_NAME;
import static io.nop.task.TaskErrors.ARG_ATTR_VALUE;
import static io.nop.task.TaskErrors.ARG_REASON;
import static io.nop.task.TaskErrors.ERR_TASK_STEP_CONFIG_INVALID;

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
            // W-10（CRA 8.48 R4-08，MA-18 裁定 D，运行期上限路线 β）：count 为运行期表达式
            // （构建期校验不可判定），超上界运行期显式拒绝——外部输入驱动的单请求 OOM 通道
            // 闭合（count<=0 维持既有空 fork no-op 语义不拒绝，校验仅承载超上界单语义）。
            // 校验位于 setStateBean 之前：拒绝即无分支执行、无状态残留
            int maxForkCount = CFG_TASK_STEP_MAX_FORK_COUNT.get();
            if (count > maxForkCount) {
                throw TaskStepHelper.newError(getLocation(), stepRt, ERR_TASK_STEP_CONFIG_INVALID)
                        .param(ARG_ATTR_NAME, "forkN.count")
                        .param(ARG_ATTR_VALUE, count)
                        .param(ARG_REASON, "fork count is a runtime expression; " + count
                                + " exceeds nop.task.step.max-fork-count=" + maxForkCount);
            }
            stepRt.setStateBean(count);
        }

        // W-10（MA-18 裁定 D）：预分配改增量——`new ArrayList<>(count)` 按值即时预分配是
        // 校验前形态下的 OOM 通道本体；合法上限内预分配收益可忽略
        List<CompletionStage<TaskStepReturn>> promises = new ArrayList<>();

        CompletionStage<Void> promise;
        if (count > 0) {
            int countParam = count;
            promise = TaskStepHelper.withCancellable(() -> {
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
        } else {
            // count<=0 视为空 fork（对齐 ForkTaskStep 空集合语义，plan 349 Phase 5）：
            // 修复前空 promises 传入 AsyncHelper.waitAsync 触发 Guard IllegalArgumentException
            promise = FutureHelper.success(null);
        }

        return buildAggResult(promise, promises, stepRt);
    }
}

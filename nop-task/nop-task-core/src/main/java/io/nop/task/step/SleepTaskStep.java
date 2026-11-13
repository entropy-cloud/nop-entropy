/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;

import static io.nop.task.TaskConfigs.CFG_TASK_STEP_MAX_SLEEP_MS;
import static io.nop.task.TaskErrors.ARG_ATTR_NAME;
import static io.nop.task.TaskErrors.ARG_ATTR_VALUE;
import static io.nop.task.TaskErrors.ARG_REASON;
import static io.nop.task.TaskErrors.ERR_TASK_STEP_CONFIG_INVALID;

public class SleepTaskStep extends AbstractTaskStep {
    private IEvalAction sleepMillisExpr;

    public IEvalAction getSleepMillisExpr() {
        return sleepMillisExpr;
    }

    public void setSleepMillisExpr(IEvalAction sleepMillisExpr) {
        this.sleepMillisExpr = sleepMillisExpr;
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        Long sleep = ConvertHelper.toLong(sleepMillisExpr.invoke(stepRt));
        if (sleep == null)
            sleep = -1L;
        // W-09（CRA 8.48 R4-08，MA-16 裁定 B，运行期上限路线 β）：sleep 时长为运行期表达式
        // （构建期校验不可判定——R2 Major-1 义务），超上限在执行期显式拒绝（消除调用线程长时
        // 占用面）。负值/零值维持既有 no-op 短路语义不拒绝（校验仅承载超上界单语义）
        long maxSleepMs = CFG_TASK_STEP_MAX_SLEEP_MS.get();
        if (sleep > maxSleepMs) {
            throw TaskStepHelper.newError(getLocation(), stepRt, ERR_TASK_STEP_CONFIG_INVALID)
                    .param(ARG_ATTR_NAME, "sleepMillis")
                    .param(ARG_ATTR_VALUE, sleep)
                    .param(ARG_REASON, "sleep duration is a runtime expression; " + sleep
                            + "ms exceeds nop.task.step.max-sleep-ms=" + maxSleepMs
                            + " (use <delay> for long waits)");
        }
        if (sleep <= 0)
            return TaskStepReturn.CONTINUE;

        FutureHelper.waitUntil(stepRt::isCancelled, sleep);

        // 取消必须显式失败（plan 349 Phase 4）：reason 编码进异常供 task 层 KILLED/TIMEOUT driver 分类，
        // 静默 CONTINUE 会把被取消的 sleep 记为成功步骤、取消延迟到下一个步骤边界
        TaskStepHelper.checkNotCancelled(stepRt);

        return TaskStepReturn.CONTINUE;
    }
}
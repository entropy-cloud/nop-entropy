/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.core.trigger;

import io.nop.api.core.util.Guard;
import io.nop.job.core.ITrigger;
import io.nop.job.core.ITriggerEvalContext;

/**
 * 按照固定周期触发
 *
 * @author canonical_entropy@163.com
 */
public class PeriodicTrigger implements ITrigger {
    private final long period;

    /**
     * fixedDelay表示从上一次执行结束时间开始计时。否则从minExecutionTime按照固定周期进行计时
     */
    private final boolean fixedDelay;

    public PeriodicTrigger(long period, boolean fixedDelay) {
        this.period = Guard.positiveLong(period, "period");
        this.fixedDelay = fixedDelay;
    }

    /**
     * Returns the time after which a task should run again.
     */
    @Override
    public long nextScheduleTime(long afterTime, ITriggerEvalContext evalContext) {
        if (fixedDelay) {
            if (evalContext.getLastScheduledTime() <= 0) {
                return afterTime + 1;
            }

            long lastEnd = evalContext.getLastEndTime();
            if (lastEnd <= 0)
                lastEnd = afterTime;

            long time = lastEnd + period;
            if (time <= afterTime)
                time = afterTime + 1;
            return time;
        } else {
            long start = evalContext.getLastScheduledTime();
            if (start < 0) {
                start = evalContext.getMinScheduleTime();
                if (start < 0) {
                    return afterTime + 1;
                } else {
                    if (afterTime < start) {
                        return start;
                    }
                }
            }

            if (afterTime < start)
                afterTime = start;

            long n = (afterTime - start) / period + 1;
            return start + n * period;
        }
    }
}
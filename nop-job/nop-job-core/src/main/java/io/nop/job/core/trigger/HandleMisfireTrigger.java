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
 * @author canonical_entropy@163.com
 */
public class HandleMisfireTrigger implements ITrigger {

    private final ITrigger trigger;
    private final long misfireThreshold;

    public HandleMisfireTrigger(long misfireThreshold, ITrigger trigger) {
        this.misfireThreshold = Guard.positiveLong(misfireThreshold, "misfireThreshold");
        this.trigger = trigger;
    }

    @Override
    public long nextScheduleTime(long afterTime, ITriggerEvalContext evalContext) {
        long startTime = evalContext.getLastScheduledTime();
        if (startTime <= 0) {
            startTime = evalContext.getMinScheduleTime();
            if (startTime <= 0)
                return trigger.nextScheduleTime(afterTime, evalContext);
        }

        if (startTime < afterTime - misfireThreshold)
            startTime = afterTime - misfireThreshold;

        long next = trigger.nextScheduleTime(startTime, evalContext);
        return next;
    }
}
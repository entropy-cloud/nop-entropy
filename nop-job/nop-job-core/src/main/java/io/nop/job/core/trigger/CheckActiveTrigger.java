/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.core.trigger;

import io.nop.job.core.ITrigger;
import io.nop.job.core.ITriggerEvalContext;

public class CheckActiveTrigger implements ITrigger {
    private final ITrigger trigger;

    public CheckActiveTrigger(ITrigger trigger) {
        this.trigger = trigger;
    }

    @Override
    public long nextScheduleTime(long afterTime, ITriggerEvalContext evalContext) {
        if (evalContext.isScheduleCompleted())
            return -1;
        return trigger.nextScheduleTime(afterTime, evalContext);
    }
}
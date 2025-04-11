/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.core.trigger;

import io.nop.job.core.ITrigger;
import io.nop.job.core.ITriggerContext;

/**
 * @author canonical_entropy@163.com
 */
public class LimitCountTrigger implements ITrigger {
    private final ITrigger trigger;

    public LimitCountTrigger(ITrigger trigger) {
        this.trigger = trigger;
    }

    @Override
    public long nextScheduleTime(long afterTime, ITriggerContext triggerContext) {
        long maxRepeatCount = triggerContext.getMaxExecutionCount();
        if (maxRepeatCount > 0) {
            if (triggerContext.getExecCount() >= maxRepeatCount)
                return -1;
        }

        return trigger.nextScheduleTime(afterTime, triggerContext);
    }
}
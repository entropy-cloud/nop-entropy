/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.job.core.trigger;

import io.nop.job.core.ITrigger;
import io.nop.job.core.ITriggerContext;

/**
 * @author canonical_entropy@163.com
 */
public class LimitTimeTrigger implements ITrigger {
    private final ITrigger trigger;

    public LimitTimeTrigger(ITrigger trigger) {
        this.trigger = trigger;
    }

    @Override
    public long nextScheduleTime(long afterTime, ITriggerContext triggerContext) {
        long max = triggerContext.getMaxScheduleTime();
        if (max > 0 && max <= afterTime)
            return -1;

        long min = triggerContext.getMinScheduleTime();
        if (min > 0 && min > afterTime)
            afterTime = min - 1;

        long time = trigger.nextScheduleTime(afterTime, triggerContext);
        if (max > 0 && max < time)
            return -1;

        return time;
    }
}

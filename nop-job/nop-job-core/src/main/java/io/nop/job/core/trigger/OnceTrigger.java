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
 * 只在指定时刻执行一次
 */
public class OnceTrigger implements ITrigger {
    private boolean first = true;
    private final long scheduleTime;

    public OnceTrigger(long scheduleTime) {
        this.scheduleTime = scheduleTime;
    }

    @Override
    public long nextScheduleTime(long afterTime, ITriggerContext triggerContext) {
        if (!first) {
            return -1;
        }
        first = false;

        if (scheduleTime > 0)
            return scheduleTime;

        return afterTime + 1;
    }
}

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
import io.nop.job.core.ITriggerContext;
import io.nop.job.core.utils.CronExpression;

/**
 * @author canonical_entropy@163.com
 */
public class CronTrigger implements ITrigger {
    private final CronExpression cronExpression;

    public CronTrigger(CronExpression cronExpression) {
        this.cronExpression = Guard.notNull(cronExpression, "cronExpression");
    }

    @Override
    public long nextScheduleTime(long afterTime, ITriggerContext triggerContext) {
        // 如果是第一次执行
        long time = triggerContext.getLastScheduleTime();
        if (time < afterTime)
            time = afterTime;

        long date = this.cronExpression.getTimeAfter(time);
        return date;
    }
}
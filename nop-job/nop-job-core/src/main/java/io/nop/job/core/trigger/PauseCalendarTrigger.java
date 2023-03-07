/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.job.core.trigger;

import io.nop.api.core.exceptions.NopException;
import io.nop.job.core.ICalendar;
import io.nop.job.core.ITrigger;
import io.nop.job.core.ITriggerContext;

import static io.nop.job.core.JobCoreErrors.ARG_LOOP_COUNT;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_TRIGGER_LOOP_COUNT_EXCEED_LIMIT;

/**
 * @author canonical_entropy@163.com
 */
public class PauseCalendarTrigger implements ITrigger {
    static final int MAX_TRY_COUNT = 10000;

    private final ICalendar calendar;
    private final ITrigger trigger;

    public PauseCalendarTrigger(ICalendar calendar, ITrigger trigger) {
        this.calendar = calendar;
        this.trigger = trigger;
    }

    @Override
    public long nextScheduleTime(long afterTime, ITriggerContext triggerContext) {
        long time = trigger.nextScheduleTime(afterTime, triggerContext);
        int count = 0;
        while (time > 0 && !calendar.isTimeIncluded(time)) {
            time = calendar.getNextIncludedTime(time);
            if (time > 0) {
                time = trigger.nextScheduleTime(time - 1, triggerContext);
            }
            count++;
            if (count > MAX_TRY_COUNT)
                throw new NopException(ERR_JOB_TRIGGER_LOOP_COUNT_EXCEED_LIMIT).param(ARG_LOOP_COUNT, count);
        }
        return time;
    }
}
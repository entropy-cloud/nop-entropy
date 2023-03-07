/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.job.core.trigger;

import io.nop.commons.util.StringHelper;
import io.nop.job.api.spec.ITriggerSpec;
import io.nop.job.core.ICalendar;
import io.nop.job.core.ITrigger;
import io.nop.job.core.calendar.CalendarBuilder;
import io.nop.job.core.utils.CronExpression;

/**
 * @author canonical_entropy@163.com
 */
public class TriggerBuilder {
    public static ITrigger buildTrigger(ITriggerSpec spec, ICalendar defaultCalendar) {
        ITrigger trigger;
        if (!StringHelper.isEmpty(spec.getCronExpr())) {
            CronExpression cronExpr = new CronExpression(spec.getCronExpr());
            trigger = new CronTrigger(cronExpr);
        } else {
            trigger = new PeriodicTrigger(spec.getRepeatInterval(), spec.isRepeatFixedDelay());
        }

        trigger = new LimitCountTrigger(trigger);
        trigger = new LimitTimeTrigger(trigger);
        trigger = new CheckActiveTrigger(trigger);

        if (spec.getPauseCalendars() != null && !spec.getPauseCalendars().isEmpty()) {
            ICalendar cal = CalendarBuilder.buildCalendar(spec.getPauseCalendars());
            if (cal != null)
                trigger = new PauseCalendarTrigger(cal, trigger);
        }

        if (spec.isUseDefaultCalendar() && defaultCalendar != null) {
            trigger = new PauseCalendarTrigger(defaultCalendar, trigger);
        }

        if (spec.getMisfireThreshold() > 0) {
            trigger = new HandleMisfireTrigger(spec.getMisfireThreshold(), trigger);
        }

        return trigger;
    }
}
package io.nop.job.core.trigger;

import io.nop.api.core.util.Guard;
import io.nop.job.api.spec.ITriggerSpec;
import io.nop.job.core.ICalendar;
import io.nop.job.core.ITrigger;
import io.nop.job.core.ITriggerEvalContext;

/**
 * 新 planner 使用的 trigger 纯计算入口。
 */
public final class JobTriggerCalculator {
    private JobTriggerCalculator() {
    }

    public static long calculateNextFireTime(ITriggerSpec spec, ITriggerEvalContext evalContext, long now) {
        return calculateNextFireTime(spec, evalContext, now, null);
    }

    public static long calculateNextFireTime(ITriggerSpec spec, ITriggerEvalContext evalContext,
                                             long now, ICalendar defaultCalendar) {
        Guard.notNull(spec, "spec");
        Guard.notNull(evalContext, "evalContext");

        ITrigger trigger = TriggerBuilder.buildTrigger(spec, defaultCalendar);
        return trigger.nextScheduleTime(now, evalContext);
    }
}

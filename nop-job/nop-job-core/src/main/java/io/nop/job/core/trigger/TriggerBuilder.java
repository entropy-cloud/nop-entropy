/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.core.trigger;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.job.api.spec.ITriggerSpec;
import io.nop.job.core.ICalendar;
import io.nop.job.core.ITrigger;
import io.nop.job.core.JobCoreErrors;
import io.nop.job.core.calendar.CalendarBuilder;
import io.nop.job.core.utils.CronExpression;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import static io.nop.job.core.JobCoreErrors.ARG_CRON_EXPR;
import static io.nop.job.core.JobCoreErrors.ARG_MAX_SCHEDULE_TIME;
import static io.nop.job.core.JobCoreErrors.ARG_MIN_SCHEDULE_TIME;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_TRIGGER_INVALID_ANCHOR_CONFIG;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_TRIGGER_INVALID_RANGE_CONFIG;

/**
 * @author canonical_entropy@163.com
 */
public class TriggerBuilder {
    public static ITrigger buildTrigger(ITriggerSpec spec, ICalendar defaultCalendar) {
        validateRangeConfig(spec);

        ITrigger trigger;
        long onceScheduleTime = 0L;
        if (!StringHelper.isEmpty(spec.getCronExpr())) {
            CronExpression cronExpr = new CronExpression(spec.getCronExpr());
            trigger = new CronTrigger(cronExpr);
        } else if (spec.getRepeatInterval() <= 0) {
            trigger = new OnceTrigger(spec.getMinScheduleTime());
            onceScheduleTime = ((OnceTrigger) trigger).getScheduleTime();
        } else {
            trigger = new PeriodicTrigger(spec.getRepeatInterval(), spec.isRepeatFixedDelay());
        }

        // 锚点周期过滤（cron 轨道 C3，plan 537）：仅作用于 cron 触发器，置于 LimitCount 之前（向量 RG-06 的 repeat→count 序）
        if (hasAnchorConfig(spec)) {
            if (StringHelper.isEmpty(spec.getCronExpr())) {
                throw anchorError(spec, "repeat anchor config is only supported for cron triggers");
            }
            validateAnchorConfig(spec);
            trigger = new AnchorRepeatTrigger(trigger, spec.getRepeatAnchorEvery(), spec.getRepeatAnchorUnit(),
                    spec.getRepeatAnchorDate());
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
            // 包装前捕获once语义：包装链上instanceof OnceTrigger永不成立
            trigger = new HandleMisfireTrigger(spec.getMisfireThreshold(), trigger, onceScheduleTime);
        }

        return trigger;
    }

    private static boolean hasAnchorConfig(ITriggerSpec spec) {
        return spec.getRepeatAnchorEvery() > 0 || !StringHelper.isEmpty(spec.getRepeatAnchorUnit())
                || !StringHelper.isEmpty(spec.getRepeatAnchorDate());
    }

    /**
     * 调度时间窗口组合校验：两端都是正数时 min 不得大于 max，否则 LimitTimeTrigger 永不触发
     * （静默失效），因此在构建期直接拒绝。
     */
    private static void validateRangeConfig(ITriggerSpec spec) {
        long min = spec.getMinScheduleTime();
        long max = spec.getMaxScheduleTime();
        if (min > 0 && max > 0 && min > max) {
            throw new NopException(ERR_JOB_TRIGGER_INVALID_RANGE_CONFIG)
                    .param(ARG_MIN_SCHEDULE_TIME, min)
                    .param(ARG_MAX_SCHEDULE_TIME, max);
        }
    }

    private static void validateAnchorConfig(ITriggerSpec spec) {
        if (spec.getRepeatAnchorEvery() <= 0) {
            throw anchorError(spec, "repeatAnchorEvery must be >= 1 when anchor config is present");
        }
        String unit = spec.getRepeatAnchorUnit();
        if (!"day".equals(unit) && !"week".equals(unit)) {
            throw anchorError(spec, "repeatAnchorUnit must be 'day' or 'week', got: " + unit);
        }
        String date = spec.getRepeatAnchorDate();
        if (StringHelper.isEmpty(date)) {
            throw anchorError(spec, "repeatAnchorDate is required when anchor config is present");
        }
        try {
            LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw anchorError(spec, "repeatAnchorDate must be YYYY-MM-DD, got: " + date);
        }
    }

    private static NopException anchorError(ITriggerSpec spec, String reason) {
        return new NopException(ERR_JOB_TRIGGER_INVALID_ANCHOR_CONFIG)
                .param(ARG_CRON_EXPR, spec.getCronExpr())
                .param("reason", reason);
    }
}
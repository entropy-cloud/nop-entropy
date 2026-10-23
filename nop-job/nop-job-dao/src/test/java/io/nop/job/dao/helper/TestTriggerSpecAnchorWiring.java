/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.dao.helper;

import io.nop.api.core.exceptions.NopException;
import io.nop.job.api.spec.TriggerSpec;
import io.nop.job.core.ITrigger;
import io.nop.job.core.ITriggerEvalContext;
import io.nop.job.core.trigger.TriggerBuilder;
import io.nop.job.dao.entity.NopJobSchedule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;

import static io.nop.job.core.JobCoreErrors.ERR_JOB_TRIGGER_INVALID_ANCHOR_CONFIG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * cron 锚点周期（C3，plan 537）实体路径接线验证：实体三列 → TriggerSpec → TriggerBuilder →
 * AnchorRepeatTrigger → nextScheduleTime 序列。C3 交付时锚点只有程序化 TriggerSpec 可达，
 * 本用例固化「标准实体配置同样生效」。
 */
public class TestTriggerSpecAnchorWiring {
    private static TimeZone originalTz;

    @BeforeAll
    static void setUp() {
        originalTz = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterAll
    static void tearDown() {
        TimeZone.setDefault(originalTz);
    }

    private static long midnight(String date) {
        return LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static String iso(long millis) {
        return Instant.ofEpochMilli(millis).toString();
    }

    private static NopJobSchedule newCronSchedule(String cronExpr) {
        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setCronExpr(cronExpr);
        schedule.setFireCount(0L);
        schedule.setMaxExecutionCount(0);
        return schedule;
    }

    @Test
    void entityColumns_mappedToTriggerSpec() {
        NopJobSchedule schedule = newCronSchedule("0 0 9 * * ?");
        schedule.setRepeatAnchorEvery(2);
        schedule.setRepeatAnchorUnit("week");
        schedule.setRepeatAnchorDate("2026-01-05");

        TriggerSpec spec = TriggerSpecHelper.toTriggerSpec(schedule);
        assertEquals(2, spec.getRepeatAnchorEvery());
        assertEquals("week", spec.getRepeatAnchorUnit());
        assertEquals("2026-01-05", spec.getRepeatAnchorDate());
    }

    @Test
    void dayUnit_everyTwoDaysFilteredFromEntityPath() {
        NopJobSchedule schedule = newCronSchedule("0 0 9 * * ?");
        schedule.setRepeatAnchorEvery(2);
        schedule.setRepeatAnchorUnit("day");
        schedule.setRepeatAnchorDate("2026-01-05");

        ITrigger trigger = TriggerBuilder.buildTrigger(TriggerSpecHelper.toTriggerSpec(schedule), null);
        ITriggerEvalContext ctx = TriggerSpecHelper.toEvalContext(schedule);

        long t = trigger.nextScheduleTime(midnight("2026-01-04"), ctx);
        assertEquals("2026-01-05T09:00:00Z", iso(t));

        t = trigger.nextScheduleTime(t, ctx);
        assertEquals("2026-01-07T09:00:00Z", iso(t));

        t = trigger.nextScheduleTime(t, ctx);
        assertEquals("2026-01-09T09:00:00Z", iso(t));
    }

    @Test
    void weekUnit_everyTwoWeeksFilteredFromEntityPath() {
        NopJobSchedule schedule = newCronSchedule("0 0 9 ? * MON");
        schedule.setRepeatAnchorEvery(2);
        schedule.setRepeatAnchorUnit("week");
        schedule.setRepeatAnchorDate("2026-01-05");

        ITrigger trigger = TriggerBuilder.buildTrigger(TriggerSpecHelper.toTriggerSpec(schedule), null);
        ITriggerEvalContext ctx = TriggerSpecHelper.toEvalContext(schedule);

        long t = trigger.nextScheduleTime(midnight("2026-01-01"), ctx);
        assertEquals("2026-01-05T09:00:00Z", iso(t));

        t = trigger.nextScheduleTime(t, ctx);
        assertEquals("2026-01-19T09:00:00Z", iso(t));

        t = trigger.nextScheduleTime(t, ctx);
        assertEquals("2026-02-02T09:00:00Z", iso(t));
    }

    @Test
    void invalidAnchorUnit_rejectedViaEntityPath() {
        NopJobSchedule schedule = newCronSchedule("0 0 9 * * ?");
        schedule.setRepeatAnchorEvery(2);
        schedule.setRepeatAnchorUnit("month");
        schedule.setRepeatAnchorDate("2026-01-05");

        NopException error = assertThrows(NopException.class,
                () -> TriggerBuilder.buildTrigger(TriggerSpecHelper.toTriggerSpec(schedule), null));
        assertEquals(ERR_JOB_TRIGGER_INVALID_ANCHOR_CONFIG.getErrorCode(), error.getErrorCode());
    }

    @Test
    void anchorWithoutCron_rejectedViaEntityPath() {
        NopJobSchedule schedule = newCronSchedule(null);
        schedule.setRepeatAnchorEvery(1);
        schedule.setRepeatAnchorUnit("day");
        schedule.setRepeatAnchorDate("2026-01-05");

        NopException error = assertThrows(NopException.class,
                () -> TriggerBuilder.buildTrigger(TriggerSpecHelper.toTriggerSpec(schedule), null));
        assertEquals(ERR_JOB_TRIGGER_INVALID_ANCHOR_CONFIG.getErrorCode(), error.getErrorCode());
    }
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.core.trigger;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.DateHelper;
import io.nop.commons.util.StringHelper;
import io.nop.job.api.spec.AnnualCalendarSpec;
import io.nop.job.api.spec.TriggerSpec;
import io.nop.job.core.ITrigger;
import io.nop.job.core.NopJobCoreConstants;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled
public class TestTrigger {
    @Test
    public void testPeriod() {
        TriggerSpec spec = new TriggerSpec();
        spec.setMaxExecutionCount(100);
        spec.setRepeatInterval(1);
        spec.setRepeatFixedDelay(true);
        spec.setMinScheduleTime(CoreMetrics.currentTimeMillis());

        ITrigger trigger = TriggerBuilder.buildTrigger(spec, null);
        TriggerContextImpl context = new TriggerContextImpl("test", spec);
        long beginTime = spec.getMinScheduleTime() - 1;
        for (int i = 0; i < 100; i++) {
            long time = trigger.nextScheduleTime(beginTime, context);
            assertEquals(time, beginTime + 1);
            context.onEndExecute(time);
            beginTime = time;
            assertTrue(beginTime > 0);
        }
        assertEquals(-1, trigger.nextScheduleTime(beginTime, context));

        assertEquals(NopJobCoreConstants.JOB_INSTANCE_STATUS_RUNNING, context.getTriggerStatus());
        context.onCompleted(beginTime);

        assertEquals(NopJobCoreConstants.JOB_INSTANCE_STATUS_JOB_FINISHED, context.getTriggerStatus());
    }

    @Test
    public void testCron() {
        TriggerSpec spec = new TriggerSpec();
        spec.setMaxExecutionCount(10);
        // 每天的6点和19点
        spec.setCronExpr("0 0 6,19 * * *");
        spec.setMaxScheduleTime(DateHelper.dateMillis(2022, 2, 15));

        AnnualCalendarSpec cal = new AnnualCalendarSpec();
        cal.setExcludes(List.of(MonthDay.of(2, 11), MonthDay.of(2, 13)));
        spec.setPauseCalendars(Arrays.asList(cal));

        ITrigger trigger = TriggerBuilder.buildTrigger(spec, null);
        TriggerContextImpl context = new TriggerContextImpl("test", spec);
        long beginTime = DateHelper.dateMillis(2022, 2, 10);
        List<LocalDateTime> times = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            long time = trigger.nextScheduleTime(beginTime, context);
            if (time <= 0)
                break;
            times.add(DateHelper.millisToDateTime(time));
            context.onEndExecute(time + 100);
            beginTime = context.getLastExecEndTime();
        }
        System.out.println(StringHelper.join(times, "\n"));

        // 10, 12, 14
        assertEquals(6, times.size());
        assertEquals("2022-02-10T06:00", times.get(0).toString());
        assertEquals("2022-02-10T19:00", times.get(1).toString());
        assertEquals("2022-02-12T06:00", times.get(2).toString());
        assertEquals("2022-02-14T19:00", times.get(5).toString());

        assertEquals(NopJobCoreConstants.JOB_INSTANCE_STATUS_RUNNING, context.getTriggerStatus());
        context.onCompleted(beginTime);

        assertEquals(NopJobCoreConstants.JOB_INSTANCE_STATUS_JOB_FINISHED, context.getTriggerStatus());
    }

    @Test
    public void testMisfire() {
        TriggerSpec spec = new TriggerSpec();
        spec.setMaxExecutionCount(10);
        // 每天的6点和19点
        spec.setCronExpr("0 0 6,19 * * *");
        spec.setMaxScheduleTime(DateHelper.dateMillis(2022, 2, 15));

        AnnualCalendarSpec cal = new AnnualCalendarSpec();
        cal.setExcludes(List.of(MonthDay.of(2, 11), MonthDay.of(2, 13)));
        spec.setPauseCalendars(Arrays.asList(cal));
        spec.setMisfireThreshold(1000 * 10);

        ITrigger trigger = TriggerBuilder.buildTrigger(spec, null);
        TriggerContextImpl context = new TriggerContextImpl("test", spec);
        long beginTime = DateHelper.dateMillis(2022, 2, 10);

        long time = trigger.nextScheduleTime(beginTime, context);
        context.onSchedule(time, time);
        context.onEndExecute(time);

        time = trigger.nextScheduleTime(DateHelper.dateTimeToMillis(LocalDateTime.of(2022, 2, 12, 19, 0, 1)),
                context);

        assertEquals("2022-02-12T19:00", DateHelper.millisToDateTime(time).toString());
    }
}

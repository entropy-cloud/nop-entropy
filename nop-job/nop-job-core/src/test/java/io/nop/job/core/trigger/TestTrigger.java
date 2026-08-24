package io.nop.job.core.trigger;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.DateHelper;
import io.nop.commons.util.StringHelper;
import io.nop.job.api.spec.AnnualCalendarSpec;
import io.nop.job.api.spec.TriggerSpec;
import io.nop.job.core.ITrigger;
import io.nop.job.core.ITriggerEvalContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestTrigger {

    static class SimpleContext implements ITriggerEvalContext {
        long fireCount;
        long lastScheduledTime;
        long lastEndTime;
        long minScheduleTime;
        long maxScheduleTime;
        long maxExecutionCount;
        boolean completed;

        @Override public long getFireCount() { return fireCount; }
        @Override public long getLastScheduledTime() { return lastScheduledTime; }
        @Override public long getLastEndTime() { return lastEndTime; }
        @Override public long getMinScheduleTime() { return minScheduleTime; }
        @Override public long getMaxScheduleTime() { return maxScheduleTime; }
        @Override public long getMaxExecutionCount() { return maxExecutionCount; }
        @Override public boolean isScheduleCompleted() { return completed; }
    }

    private SimpleContext newContext(TriggerSpec spec) {
        SimpleContext ctx = new SimpleContext();
        ctx.maxExecutionCount = spec.getMaxExecutionCount();
        ctx.minScheduleTime = spec.getMinScheduleTime();
        ctx.maxScheduleTime = spec.getMaxScheduleTime();
        return ctx;
    }

    @Test
    public void testPeriod() {
        TriggerSpec spec = new TriggerSpec();
        spec.setMaxExecutionCount(100);
        spec.setRepeatInterval(1);
        spec.setRepeatFixedDelay(true);
        spec.setMinScheduleTime(CoreMetrics.currentTimeMillis());

        ITrigger trigger = TriggerBuilder.buildTrigger(spec, null);
        SimpleContext ctx = newContext(spec);
        long beginTime = spec.getMinScheduleTime() - 1;
        for (int i = 0; i < 100; i++) {
            long time = trigger.nextScheduleTime(beginTime, ctx);
            assertEquals(beginTime + 1, time);
            ctx.fireCount++;
            ctx.lastScheduledTime = time;
            ctx.lastEndTime = time;
            beginTime = time;
            assertTrue(beginTime > 0);
        }
        assertEquals(-1, trigger.nextScheduleTime(beginTime, ctx));
    }

    @Test
    public void testCron() {
        TriggerSpec spec = new TriggerSpec();
        spec.setMaxExecutionCount(10);
        spec.setCronExpr("0 0 6,19 * * *");
        spec.setMaxScheduleTime(DateHelper.dateMillis(2022, 2, 15));

        AnnualCalendarSpec cal = new AnnualCalendarSpec();
        cal.setExcludes(List.of(MonthDay.of(2, 11), MonthDay.of(2, 13)));
        spec.setPauseCalendars(Arrays.asList(cal));

        ITrigger trigger = TriggerBuilder.buildTrigger(spec, null);
        SimpleContext ctx = newContext(spec);
        long beginTime = DateHelper.dateMillis(2022, 2, 10);
        List<LocalDateTime> times = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            long time = trigger.nextScheduleTime(beginTime, ctx);
            if (time <= 0)
                break;
            times.add(DateHelper.millisToDateTime(time));
            ctx.fireCount++;
            ctx.lastScheduledTime = time;
            ctx.lastEndTime = time + 100;
            beginTime = ctx.lastEndTime;
        }
        System.out.println(StringHelper.join(times, "\n"));

        assertEquals(6, times.size());
        assertEquals("2022-02-10T06:00", times.get(0).toString());
        assertEquals("2022-02-10T19:00", times.get(1).toString());
        assertEquals("2022-02-12T06:00", times.get(2).toString());
        assertEquals("2022-02-14T19:00", times.get(5).toString());
    }

    @Test
    public void testMisfire() {
        TriggerSpec spec = new TriggerSpec();
        spec.setMaxExecutionCount(10);
        spec.setCronExpr("0 0 6,19 * * *");
        spec.setMaxScheduleTime(DateHelper.dateMillis(2022, 2, 15));

        AnnualCalendarSpec cal = new AnnualCalendarSpec();
        cal.setExcludes(List.of(MonthDay.of(2, 11), MonthDay.of(2, 13)));
        spec.setPauseCalendars(Arrays.asList(cal));
        spec.setMisfireThreshold(1000 * 10);

        ITrigger trigger = TriggerBuilder.buildTrigger(spec, null);
        SimpleContext ctx = newContext(spec);
        long beginTime = DateHelper.dateMillis(2022, 2, 10);

        long time = trigger.nextScheduleTime(beginTime, ctx);
        ctx.lastScheduledTime = time;
        ctx.fireCount++;
        ctx.lastEndTime = time;

        time = trigger.nextScheduleTime(DateHelper.dateTimeToMillis(LocalDateTime.of(2022, 2, 12, 19, 0, 1)),
                ctx);

        assertEquals("2022-02-12T19:00", DateHelper.millisToDateTime(time).toString());
    }

    @Test
    public void testOnceTriggerMisfireReturnsNegativeOne() {
        long scheduleTime = CoreMetrics.currentTimeMillis() - 60_000;
        OnceTrigger once = new OnceTrigger(scheduleTime);
        HandleMisfireTrigger trigger = new HandleMisfireTrigger(10_000, once);

        SimpleContext ctx = new SimpleContext();
        ctx.lastScheduledTime = 0;
        ctx.minScheduleTime = 0;

        long afterTime = CoreMetrics.currentTimeMillis();
        long result = trigger.nextScheduleTime(afterTime, ctx);
        assertEquals(-1, result);
    }

    @Test
    public void testOnceTriggerNotMisfire() {
        long scheduleTime = CoreMetrics.currentTimeMillis() + 60_000;
        OnceTrigger once = new OnceTrigger(scheduleTime);
        HandleMisfireTrigger trigger = new HandleMisfireTrigger(10_000, once);

        SimpleContext ctx = new SimpleContext();
        ctx.lastScheduledTime = 0;
        ctx.minScheduleTime = 0;

        long afterTime = CoreMetrics.currentTimeMillis();
        long result = trigger.nextScheduleTime(afterTime, ctx);
        assertEquals(scheduleTime, result);
    }

    /**
     * check2 [P0]：once 语义必须由 evalContext（持久化状态）承载。planner 路径每次计算都重建
     * 全新 trigger 链（JobTriggerCalculator → TriggerBuilder.buildTrigger），全新实例的 once
     * 判定只能依赖 ctx：lastScheduledTime >= onceTime（已在 once 时刻产生过 fire）→ -1；
     * fireCount > 0（立即执行一次的形态已触发过）→ -1。
     */
    @Test
    public void testOnceTriggerFreshInstanceAlreadyFiredReturnsNegativeOne() {
        long onceTime = CoreMetrics.currentTimeMillis() - 60_000;

        // 形态 1：显式 once 时刻，全新实例 + 已触发上下文
        SimpleContext fired = new SimpleContext();
        fired.lastScheduledTime = onceTime; // schedule.lastFireTime 已推进到 once 时刻
        assertEquals(-1L, new OnceTrigger(onceTime).nextScheduleTime(CoreMetrics.currentTimeMillis(), fired),
                "fresh trigger instance with lastScheduledTime >= onceTime must report exhausted");

        // 形态 2：无显式时刻（立即执行一次），全新实例 + fireCount>0
        SimpleContext firedImmediate = new SimpleContext();
        firedImmediate.fireCount = 1;
        assertEquals(-1L, new OnceTrigger(0).nextScheduleTime(CoreMetrics.currentTimeMillis(), firedImmediate),
                "fresh immediate-once trigger with fireCount > 0 must report exhausted");

        // 对照：全新实例 + 未触发上下文仍正常返回触发时刻（planner 首次 due）
        SimpleContext neverFired = new SimpleContext();
        assertEquals(onceTime, new OnceTrigger(onceTime).nextScheduleTime(CoreMetrics.currentTimeMillis(), neverFired));
        assertTrue(new OnceTrigger(0).nextScheduleTime(1000L, neverFired) > 1000L);
    }
}

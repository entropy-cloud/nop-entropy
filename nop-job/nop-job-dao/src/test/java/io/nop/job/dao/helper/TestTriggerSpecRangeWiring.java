package io.nop.job.dao.helper;

import io.nop.job.api.spec.TriggerSpec;
import io.nop.job.core.ITrigger;
import io.nop.job.core.ITriggerEvalContext;
import io.nop.job.core.trigger.TriggerBuilder;
import io.nop.job.dao.entity.NopJobSchedule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * plan 537（cron 轨道 C3）：范围三字段（minScheduleTime/maxScheduleTime/maxExecutionCount）的
 * 实体 → TriggerSpec/evalContext → 触发链端到端接线验证（既有接线，行为无变更）。
 */
public class TestTriggerSpecRangeWiring {
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

    private static Timestamp midnight(String date) {
        return new Timestamp(LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    private static String iso(long millis) {
        return Instant.ofEpochMilli(millis).toString();
    }

    @Test
    void rangeTrio_wiredFromEntityToEndOfTriggerChain() {
        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setCronExpr("0 0 9 * * ?");
        schedule.setMinScheduleTime(midnight("2026-01-05"));
        schedule.setMaxScheduleTime(midnight("2026-01-08"));
        schedule.setMaxExecutionCount(2);
        schedule.setFireCount(0L);

        TriggerSpec spec = TriggerSpecHelper.toTriggerSpec(schedule);
        assertEquals(2L, spec.getMaxExecutionCount());
        assertEquals(midnight("2026-01-05").getTime(), spec.getMinScheduleTime());

        ITrigger trigger = TriggerBuilder.buildTrigger(spec, null);
        ITriggerEvalContext ctx = TriggerSpecHelper.toEvalContext(schedule);

        // start 截断：01-01 ~ 01-04 不触发
        long t = trigger.nextScheduleTime(midnight("2026-01-01").getTime(), ctx);
        assertEquals("2026-01-05T09:00:00Z", iso(t));
        schedule.setFireCount(1L);

        t = trigger.nextScheduleTime(t, ctx);
        assertEquals("2026-01-06T09:00:00Z", iso(t));
        schedule.setFireCount(2L);

        // count 截断：maxExecutionCount=2 已用尽
        assertEquals(-1L, trigger.nextScheduleTime(t, ctx));
    }

    @Test
    void rangeEnd_truncatesViaEntityPath() {
        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setCronExpr("0 0 9 * * ?");
        // end 日期含当天（对齐宿主拆列语义）：maxScheduleTime = end 当日末毫秒
        schedule.setMaxScheduleTime(new Timestamp(midnight("2026-01-04").getTime() - 1));
        schedule.setFireCount(0L);

        TriggerSpec spec = TriggerSpecHelper.toTriggerSpec(schedule);
        assertEquals(new Timestamp(midnight("2026-01-04").getTime() - 1).getTime(), spec.getMaxScheduleTime());

        ITrigger trigger = TriggerBuilder.buildTrigger(spec, null);
        ITriggerEvalContext ctx = TriggerSpecHelper.toEvalContext(schedule);

        long t = trigger.nextScheduleTime(midnight("2026-01-01").getTime(), ctx);
        assertEquals("2026-01-01T09:00:00Z", iso(t));
        t = trigger.nextScheduleTime(t, ctx);
        assertEquals("2026-01-02T09:00:00Z", iso(t));
        t = trigger.nextScheduleTime(t, ctx);
        assertEquals("2026-01-03T09:00:00Z", iso(t));

        // end 截断：01-04 起 -1
        assertEquals(-1L, trigger.nextScheduleTime(t, ctx));
    }
}

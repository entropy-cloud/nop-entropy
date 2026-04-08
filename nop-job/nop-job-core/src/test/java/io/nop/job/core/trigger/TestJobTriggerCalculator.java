package io.nop.job.core.trigger;

import io.nop.commons.util.DateHelper;
import io.nop.job.api.spec.AnnualCalendarSpec;
import io.nop.job.api.spec.TriggerSpec;
import io.nop.job.core.ITriggerEvalContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.MonthDay;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestJobTriggerCalculator {
    @Test
    public void testCron() {
        TriggerSpec spec = new TriggerSpec();
        spec.setCronExpr("0 0 6,19 * * *");

        long time = JobTriggerCalculator.calculateNextFireTime(spec,
                context(0, 0, 0, 0, 0, 0, false),
                DateHelper.dateMillis(2022, 2, 10));

        assertEquals("2022-02-10T06:00", DateHelper.millisToDateTime(time).toString());
    }

    @Test
    public void testFixedRate() {
        TriggerSpec spec = new TriggerSpec();
        spec.setRepeatInterval(1000);
        spec.setRepeatFixedDelay(false);
        spec.setMinScheduleTime(1000);

        long time = JobTriggerCalculator.calculateNextFireTime(spec,
                context(1, 2000, 2500, 1000, 0, 0, false),
                2500);

        assertEquals(3000, time);
    }

    @Test
    public void testFixedDelay() {
        TriggerSpec spec = new TriggerSpec();
        spec.setRepeatInterval(1000);
        spec.setRepeatFixedDelay(true);

        long time = JobTriggerCalculator.calculateNextFireTime(spec,
                context(1, 2000, 2500, 0, 0, 0, false),
                2500);

        assertEquals(3500, time);
    }

    @Test
    public void testPauseCalendar() {
        TriggerSpec spec = new TriggerSpec();
        spec.setCronExpr("0 0 6,19 * * *");

        AnnualCalendarSpec cal = new AnnualCalendarSpec();
        cal.setExcludes(List.of(MonthDay.of(2, 11)));
        spec.setPauseCalendars(List.of(cal));

        long time = JobTriggerCalculator.calculateNextFireTime(spec,
                context(0, 0, 0, 0, 0, 0, false),
                DateHelper.dateTimeToMillis(LocalDateTime.of(2022, 2, 10, 19, 0, 0)));

        assertEquals("2022-02-12T06:00", DateHelper.millisToDateTime(time).toString());
    }

    @Test
    public void testMisfire() {
        TriggerSpec spec = new TriggerSpec();
        spec.setCronExpr("0 0 6,19 * * *");
        spec.setMisfireThreshold(1000 * 10);

        long time = JobTriggerCalculator.calculateNextFireTime(spec,
                context(1, DateHelper.dateTimeToMillis(LocalDateTime.of(2022, 2, 10, 6, 0, 0)),
                        DateHelper.dateTimeToMillis(LocalDateTime.of(2022, 2, 10, 6, 0, 0)),
                        0, 0, 0, false),
                DateHelper.dateTimeToMillis(LocalDateTime.of(2022, 2, 12, 19, 0, 1)));

        assertEquals("2022-02-12T19:00", DateHelper.millisToDateTime(time).toString());
    }

    @Test
    public void testMaxExecutionCount() {
        TriggerSpec spec = new TriggerSpec();
        spec.setRepeatInterval(1000);
        spec.setMaxExecutionCount(2);

        long time = JobTriggerCalculator.calculateNextFireTime(spec,
                context(2, 2000, 2000, 0, 0, 2, false),
                2000);

        assertEquals(-1, time);
    }

    private ITriggerEvalContext context(long fireCount, long lastScheduledTime, long lastEndTime,
                                        long minScheduleTime, long maxScheduleTime,
                                        long maxExecutionCount,
                                        boolean scheduleCompleted) {
        return new ITriggerEvalContext() {
            @Override
            public long getFireCount() {
                return fireCount;
            }

            @Override
            public long getLastScheduledTime() {
                return lastScheduledTime;
            }

            @Override
            public long getLastEndTime() {
                return lastEndTime;
            }

            @Override
            public long getMinScheduleTime() {
                return minScheduleTime;
            }

            @Override
            public long getMaxScheduleTime() {
                return maxScheduleTime;
            }

            @Override
            public long getMaxExecutionCount() {
                return maxExecutionCount;
            }

            @Override
            public boolean isScheduleCompleted() {
                return scheduleCompleted;
            }
        };
    }
}

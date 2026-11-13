package io.nop.job.core.utils;

import io.nop.job.core.utils.CronExpression;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.46 R4-06 (plan 2539) W-04 / 1.4-MA-07 — driver 红③ (structure pin, honest
 * structure-red discipline): {@code CronExpression} must not create a per-instance
 * {@link ThreadLocal} scratch calendar. The vendored class builds a NEW instance per
 * planner scan ({@code TriggerBuilder.buildTrigger}), so every instance contributed a
 * stale ThreadLocal entry to pooled scheduler threads' ThreadLocalMaps with no
 * removal point. Post-fix the scratch calendar is stack-local inside
 * {@code getTimeAfter}. Functional green legs = the existing cron test family
 * (TestCronExpression / TestCronGoldenVectors / TestJobTriggerCalculator / calendar
 *族) — they run in the same module suite and pin the parse/doNext algorithm.
 */
public class TestCronExpressionNoThreadLocalR406 {

    @Test
    public void noThreadLocalFieldsRemain() {
        int threadLocalFields = 0;
        for (Field field : CronExpression.class.getDeclaredFields()) {
            boolean isThreadLocal = field.getType() == ThreadLocal.class
                    || ThreadLocal.class.isAssignableFrom(field.getType());
            assertTrue(!isThreadLocal,
                    "CronExpression must not declare a ThreadLocal scratch field (W-04 stack-local "
                            + "calendar), found: " + field.getName());
            if (isThreadLocal) {
                threadLocalFields++;
            }
        }
        assertEquals(0, threadLocalFields, "zero ThreadLocal fields expected");
    }

    /** functional sanity leg: the stack-local calendar path computes correctly. */
    @Test
    public void getTimeAfterStillComputesOnStackLocalCalendar() {
        CronExpression expression = new CronExpression("0 0 12 * * ?", TimeZone.getDefault());
        long base = 1700000000000L;
        long next = expression.getTimeAfter(base);
        assertTrue(next > base, "getTimeAfter must return a future time, got " + next);
        // repeated calls (fresh scratch calendar each call) stay deterministic
        assertEquals(next, expression.getTimeAfter(base),
                "repeated computation must be deterministic (no cross-call state)");
    }
}

package io.nop.job.core.calendar;

import io.nop.api.core.exceptions.NopException;
import io.nop.job.core.ICalendar;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCronCalendar {

    @Test
    void testGetNextIncludedTime_doesNotDegradeToMsScan_withLongBaseExclusion() {
        final long excludeStart = 1000000L;
        final long excludeEnd = excludeStart + 86400000L * 30;

        ICalendar baseCalendar = new BaseCalendar((ICalendar) null) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isTimeIncluded(long timeStamp) {
                return timeStamp < excludeStart || timeStamp > excludeEnd;
            }

            @Override
            public long getNextIncludedTime(long timeStamp) {
                if (timeStamp < excludeStart) return excludeStart - 1;
                if (timeStamp <= excludeEnd) return excludeEnd + 1;
                return timeStamp;
            }
        };

        CronCalendar cal;
        try {
            cal = new CronCalendar(baseCalendar, "* * 0-7,18-23 ? * *");
        } catch (Exception e) {
            throw NopException.adapt(new RuntimeException(e));
        }

        long start = excludeStart + 1000;
        long deadline = System.currentTimeMillis() + 5000;
        long result = cal.getNextIncludedTime(start);

        assertTrue(result > 0, "Should return a valid timestamp");
        assertTrue(System.currentTimeMillis() < deadline,
                "Should complete within reasonable time, not degrade to ms-by-ms scan");
    }

    @Test
    void test_maxIterationProtection() {
        ICalendar neverIncluded = new BaseCalendar((ICalendar) null) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isTimeIncluded(long timeStamp) {
                return false;
            }

            @Override
            public long getNextIncludedTime(long timeStamp) {
                return timeStamp + 1;
            }
        };

        CronCalendar cal;
        try {
            cal = new CronCalendar(neverIncluded, "0 0 12 ? * MON-FRI");
        } catch (Exception e) {
            throw NopException.adapt(new RuntimeException(e));
        }

        long start = 1000L;
        long beforeMs = System.currentTimeMillis();
        long result = cal.getNextIncludedTime(start);
        long elapsed = System.currentTimeMillis() - beforeMs;

        assertTrue(elapsed < 5000,
                "Should terminate quickly even when base calendar never includes, elapsed=" + elapsed + "ms");

        assertTrue(result > start,
                "Should return some time after hitting max iteration limit");
    }
}

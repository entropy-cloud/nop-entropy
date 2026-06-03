package io.nop.job.core.calendar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestAnnualCalendar {

    @Test
    void testIsTimeIncludedWithoutExcludeDays() {
        AnnualCalendar cal = new AnnualCalendar(null);
        long now = System.currentTimeMillis();
        assertDoesNotThrow(() -> cal.isTimeIncluded(now));
        assertTrue(cal.isTimeIncluded(now));
    }

    @Test
    void testGetNextIncludedTimeWithoutExcludeDays() {
        AnnualCalendar cal = new AnnualCalendar(null);
        long now = System.currentTimeMillis();
        assertDoesNotThrow(() -> cal.getNextIncludedTime(now));
        assertTrue(cal.getNextIncludedTime(now) > 0);
    }

    @Test
    void testIsExcludedDayNPE() {
        AnnualCalendar cal = new AnnualCalendar(null);
        assertDoesNotThrow(() -> cal.isTimeIncluded(System.currentTimeMillis()));
    }
}

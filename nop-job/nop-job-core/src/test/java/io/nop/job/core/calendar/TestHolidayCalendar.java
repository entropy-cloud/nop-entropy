package io.nop.job.core.calendar;

import io.nop.commons.util.DateHelper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestHolidayCalendar {

    @Test
    void testGetNextIncludedTime_doesNotInfiniteLoop_whenAllDaysExcluded() {
        TreeSet<LocalDate> allDays = new TreeSet<>();
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 4000; i++) {
            allDays.add(start.plusDays(i));
        }

        HolidayCalendar cal = new HolidayCalendar(null);
        cal.addExcludedDays(allDays);

        long timeStamp = DateHelper.dateToMillis(LocalDate.of(2026, 6, 1));
        long result = cal.getNextIncludedTime(timeStamp);

        assertTrue(result > 0, "Should return a valid timestamp without infinite loop");
    }

    @Test
    void testGetNextIncludedTime_findsNextIncludedDay() {
        LocalDate day1 = LocalDate.of(2026, 6, 1);
        LocalDate day2 = LocalDate.of(2026, 6, 2);
        LocalDate day3 = LocalDate.of(2026, 6, 3);

        HolidayCalendar cal = new HolidayCalendar(null);
        cal.addExcludedDays(Set.of(day1, day2));

        long timeStamp = DateHelper.dateToMillis(day1);
        long result = cal.getNextIncludedTime(timeStamp);

        assertEquals(DateHelper.dateToMillis(day3), result,
                "Should skip excluded days and find the next included day");
    }
}

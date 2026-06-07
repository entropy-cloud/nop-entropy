package io.nop.job.core.calendar;

import io.nop.api.core.exceptions.NopException;
import io.nop.job.api.spec.CalendarSpec;
import io.nop.job.api.spec.DailyCalendarSpec;
import io.nop.job.api.spec.WeeklyCalendarSpec;
import io.nop.job.core.ICalendar;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCalendarBuilder {

    @Test
    void testDailyCalendarSpec_noEndTime_doesNotCrash() {
        DailyCalendarSpec spec = new DailyCalendarSpec();
        spec.setStart(LocalTime.of(9, 0));

        List<CalendarSpec> calendars = new ArrayList<>();
        calendars.add(spec);

        ICalendar cal = CalendarBuilder.buildCalendar(calendars);
        assertNotNull(cal);
        assertTrue(cal instanceof DailyCalendar);

        Calendar nineAm = Calendar.getInstance();
        nineAm.set(Calendar.HOUR_OF_DAY, 9);
        nineAm.set(Calendar.MINUTE, 0);
        nineAm.set(Calendar.SECOND, 0);
        nineAm.set(Calendar.MILLISECOND, 0);

        Calendar eightAm = Calendar.getInstance();
        eightAm.set(Calendar.HOUR_OF_DAY, 8);
        eightAm.set(Calendar.MINUTE, 0);
        eightAm.set(Calendar.SECOND, 0);
        eightAm.set(Calendar.MILLISECOND, 0);

        assertTrue(cal.isTimeIncluded(eightAm.getTimeInMillis()));
        assertFalse(cal.isTimeIncluded(nineAm.getTimeInMillis()));
    }

    @Test
    void testWeeklyCalendarSpec_isoMapping_excludesCorrectDays() {
        WeeklyCalendarSpec spec = new WeeklyCalendarSpec();
        spec.setExcludes(new int[]{6, 7});

        List<CalendarSpec> calendars = new ArrayList<>();
        calendars.add(spec);

        ICalendar cal = CalendarBuilder.buildCalendar(calendars);
        assertNotNull(cal);
        assertTrue(cal instanceof WeeklyCalendar);

        WeeklyCalendar weekly = (WeeklyCalendar) cal;

        assertTrue(weekly.isDayExcluded(Calendar.SATURDAY),
                "ISO day 6 (Saturday) should be excluded");
        assertTrue(weekly.isDayExcluded(Calendar.SUNDAY),
                "ISO day 7 (Sunday) should be excluded");
        assertFalse(weekly.isDayExcluded(Calendar.FRIDAY),
                "Friday should NOT be excluded");
        assertFalse(weekly.isDayExcluded(Calendar.MONDAY),
                "Monday should NOT be excluded");

        Calendar saturday = Calendar.getInstance();
        saturday.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY);
        saturday.set(Calendar.HOUR_OF_DAY, 12);
        saturday.set(Calendar.MINUTE, 0);
        saturday.set(Calendar.SECOND, 0);
        saturday.set(Calendar.MILLISECOND, 0);

        assertFalse(cal.isTimeIncluded(saturday.getTimeInMillis()),
                "Saturday should be excluded from calendar");
    }

    @Test
    void testDailyCalendar_midnightIncludedInNonInvertedMode() {
        DailyCalendar cal = new DailyCalendar((ICalendar) null,
                LocalTime.of(8, 0), LocalTime.of(17, 0));

        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);

        assertTrue(cal.isTimeIncluded(midnight.getTimeInMillis()),
                "Midnight (start of day) should be included in non-inverted mode when outside excluded range");
    }

    @Test
    void testDailyCalendar_midnightCronDoesNotLoop() {
        DailyCalendar dailyCal = new DailyCalendar((ICalendar) null,
                LocalTime.of(8, 0), LocalTime.of(17, 0));

        long midnightMs = dailyCal.getStartOfDayJavaCalendar(System.currentTimeMillis()).getTimeInMillis();
        long nextIncluded = dailyCal.getNextIncludedTime(midnightMs);

        assertTrue(nextIncluded > 0);
        assertTrue(dailyCal.isTimeIncluded(nextIncluded),
                "nextIncludedTime should actually be included");
        assertTrue(nextIncluded <= midnightMs + 86400000L * 2,
                "Should find an included time within 2 days");
    }

    @Test
    void testDailyCalendar_maxIterationProtection() {
        DailyCalendar dailyCal = new DailyCalendar((ICalendar) null,
                LocalTime.of(0, 0), LocalTime.of(23, 59, 59));

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
        dailyCal.setBaseCalendar(neverIncluded);

        assertThrows(NopException.class,
                () -> dailyCal.getNextIncludedTime(System.currentTimeMillis()),
                "Should throw when max iteration exceeded");
    }
}

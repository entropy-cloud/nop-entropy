package io.nop.job.core.calendar;

import io.nop.api.core.exceptions.NopException;
import io.nop.job.api.spec.CalendarSpec;
import io.nop.job.api.spec.DailyCalendarSpec;
import io.nop.job.api.spec.HolidayCalendarSpec;
import io.nop.job.api.spec.WeeklyCalendarSpec;
import io.nop.job.core.ICalendar;
import io.nop.job.core.JobCoreErrors;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /**
     * check2 [P2-2]: yearDays 位串长度超过当年天数（平年 365 天配 366 位，如闰年配置复制到
     * 平年）必须在解析期抛带上下文的 {@link NopException}（fail-fast），而非裸
     * DateTimeException——后者在 planner 路径每周期重复失败、nextFireTime 永不推进。
     */
    @Test
    void testHolidayCalendarSpec_yearDaysBeyondYearLength_failsFastWithNopException() {
        HolidayCalendarSpec spec = new HolidayCalendarSpec();
        // 2023 平年（365 天），位串 366 位且第 366 位为 '1'
        spec.setYearDays(java.util.Map.of("2023", "1".repeat(366)));

        List<CalendarSpec> calendars = new ArrayList<>();
        calendars.add(spec);

        NopException e = assertThrows(NopException.class, () -> CalendarBuilder.buildCalendar(calendars),
                "over-length yearDays must fail fast with a contextual NopException, not a bare DateTimeException");
        assertEquals(JobCoreErrors.ERR_JOB_CALENDAR_INVALID_YEAR_DAYS.getErrorCode(), e.getErrorCode());
        assertEquals(2023, e.getParam("year"));
        assertEquals(365, e.getParam("expectedMax"));
    }

    /** 对照：闰年 366 位合法（第 366 天存在），正常构建。 */
    @Test
    void testHolidayCalendarSpec_leapYear366Days_valid() {
        HolidayCalendarSpec spec = new HolidayCalendarSpec();
        spec.setYearDays(java.util.Map.of("2024", "1".repeat(366)));

        List<CalendarSpec> calendars = new ArrayList<>();
        calendars.add(spec);

        ICalendar cal = CalendarBuilder.buildCalendar(calendars);
        assertNotNull(cal);
        assertTrue(cal instanceof HolidayCalendar);
    }

    /**
     * check2 [P3-9]: setDaysExcluded 长度 <8 时 fail-fast 抛 NopException（对齐 MonthlyCalendar），
     * 而非接受后由 isDayExcluded 抛无上下文的 ArrayIndexOutOfBoundsException。
     */
    @Test
    void testWeeklyCalendar_shortDaysArray_failsFast() {
        WeeklyCalendar weekly = new WeeklyCalendar();
        assertThrows(NopException.class, () -> weekly.setDaysExcluded(new boolean[7]),
                "weekDays array shorter than 8 must fail fast with NopException, not a later bare AIOOBE");
    }

    /** check2 [P3-9]: getDaysExcluded 返回防御性拷贝，外部修改不得影响内部状态。 */
    @Test
    void testWeeklyCalendar_getDaysExcludedReturnsDefensiveCopy() {
        WeeklyCalendar weekly = new WeeklyCalendar();
        // default excludes SUNDAY and SATURDAY
        assertTrue(weekly.isDayExcluded(Calendar.SUNDAY));

        boolean[] exposed = weekly.getDaysExcluded();
        exposed[Calendar.SUNDAY] = false; // external mutation attempt

        assertTrue(weekly.isDayExcluded(Calendar.SUNDAY),
                "mutating the array returned by getDaysExcluded must not alter internal state");
    }
}

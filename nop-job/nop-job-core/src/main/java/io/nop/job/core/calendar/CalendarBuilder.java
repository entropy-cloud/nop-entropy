/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.job.core.calendar;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.ArrayHelper;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.job.api.spec.AnnualCalendarSpec;
import io.nop.job.api.spec.CalendarSpec;
import io.nop.job.api.spec.CronCalendarSpec;
import io.nop.job.api.spec.DailyCalendarSpec;
import io.nop.job.api.spec.HolidayCalendarSpec;
import io.nop.job.api.spec.MonthlyCalendarSpec;
import io.nop.job.api.spec.WeeklyCalendarSpec;
import io.nop.job.core.ICalendar;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author canonical_entropy@163.com
 */
public class CalendarBuilder {
    public static ICalendar buildCalendar(List<CalendarSpec> calendars) {
        if (calendars == null)
            return null;

        ICalendar cal = null;
        for (CalendarSpec calInfo : calendars) {
            if (calInfo instanceof AnnualCalendarSpec) {
                AnnualCalendarSpec spec = (AnnualCalendarSpec) calInfo;
                if (ArrayHelper.isEmpty(spec.getExcludes()))
                    continue;

                AnnualCalendar annual = new AnnualCalendar(cal);
                annual.setExcludeDays(spec.getExcludes());
                cal = annual;
            } else if (calInfo instanceof MonthlyCalendarSpec) {
                MonthlyCalendarSpec spec = (MonthlyCalendarSpec) calInfo;
                if (ArrayHelper.isEmpty(spec.getExcludes()))
                    continue;

                MonthlyCalendar monthly = new MonthlyCalendar(cal);
                for (int day : spec.getExcludes()) {
                    monthly.setDayExcluded(day, true);
                }
                cal = monthly;
            } else if (calInfo instanceof WeeklyCalendarSpec) {
                WeeklyCalendarSpec spec = (WeeklyCalendarSpec) calInfo;
                if (ArrayHelper.isEmpty(spec.getExcludes()))
                    continue;

                WeeklyCalendar weekly = new WeeklyCalendar(cal);
                for (int day : spec.getExcludes()) {
                    weekly.setDayExcluded(day, true);
                }
                cal = weekly;
            } else if (calInfo instanceof DailyCalendarSpec) {
                DailyCalendarSpec spec = (DailyCalendarSpec) calInfo;
                LocalTime start = spec.getStart();
                LocalTime end = spec.getEnd();
                if (start == null && end == null) {
                    continue;
                }

                if (start == null)
                    start = LocalTime.of(0, 0);

                if (end == null) {
                    end = LocalTime.of(24, 0, 0);
                }

                DailyCalendar daily = new DailyCalendar(cal, start, end);
                cal = daily;
            } else if (calInfo instanceof CronCalendarSpec) {
                CronCalendarSpec spec = (CronCalendarSpec) calInfo;
                if (StringHelper.isEmpty(spec.getExpr()))
                    continue;

                CronCalendar cron = new CronCalendar(cal, spec.getExpr());
                cal = cron;

            } else if (calInfo instanceof HolidayCalendarSpec) {
                HolidayCalendarSpec spec = (HolidayCalendarSpec) calInfo;
                HolidayCalendar holiday = new HolidayCalendar(cal);
                if (CollectionHelper.isEmptyMap(spec.getYearDays()))
                    continue;

                List<LocalDate> days = new ArrayList<>();
                for (Map.Entry<String, String> entry : spec.getYearDays().entrySet()) {
                    int year = ConvertHelper.toPrimitiveInt(entry.getKey(), NopException::new);
                    String str = entry.getValue();
                    if (!StringHelper.isEmpty(str)) {
                        for (int i = 0, n = str.length(); i < n; i++) {
                            if (str.charAt(i) == '1') {
                                LocalDate date = LocalDate.ofYearDay(year, i + 1);
                                days.add(date);
                            }
                        }
                    }
                }
                holiday.addExcludedDays(days);
                cal = holiday;
            }
        }
        return cal;
    }
}
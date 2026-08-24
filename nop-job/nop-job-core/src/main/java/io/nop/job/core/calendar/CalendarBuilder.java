/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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
import io.nop.job.core.JobCoreErrors;

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
                if (CollectionHelper.isEmpty(spec.getExcludes()))
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
                for (int isoDay : spec.getExcludes()) {
                    int calendarDay = (isoDay % 7) + 1;
                    weekly.setDayExcluded(calendarDay, true);
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
                    end = LocalTime.MAX;
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
                        // check2 [P2-2]: 位串第 i 位表示第 i+1 天，长度不得超过当年实际天数
                        //（闰年 366/平年 365）。此前未校验——平年配置 366 位且第 366 位为 '1'
                        //（如闰年配置复制到平年）会抛裸 DateTimeException，planner 路径每周期
                        // 重复失败且 nextFireTime 不推进（schedule 永不触发 + error 日志风暴）。
                        int yearLen = java.time.Year.isLeap(year) ? 366 : 365;
                        if (str.length() > yearLen) {
                            throw new NopException(JobCoreErrors.ERR_JOB_CALENDAR_INVALID_YEAR_DAYS)
                                    .param(JobCoreErrors.ARG_YEAR, year)
                                    .param(JobCoreErrors.ARG_EXPECTED_MAX, yearLen)
                                    .param(JobCoreErrors.ARG_ACTUAL, str.length());
                        }
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
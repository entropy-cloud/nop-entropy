/*
 * Copyright 2001-2009 Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */

package io.nop.job.core.calendar;

import io.nop.api.core.exceptions.NopException;
import io.nop.job.core.ICalendar;
import io.nop.job.core.JobCoreErrors;

import java.io.Serializable;
import java.util.TimeZone;

/**
 * <p>
 * This implementation of the Calendar excludes a set of days of the week. You may use it to exclude weekends for
 * example. But you may define any day of the week. By default it excludes SATURDAY and SUNDAY.
 * </p>
 *
 * @author Juergen Donnerstag
 * @see ICalendar
 * @see BaseCalendar
 */
public class WeeklyCalendar extends BaseCalendar implements ICalendar, Serializable {
    static final long serialVersionUID = -6809298821229007586L;

    // An array to store the week days which are to be excluded.
    // java.util.Calendar.MONDAY etc. are used as index.
    private boolean[] excludeDays = new boolean[8];

    // Will be set to true, if all week days are excluded
    private boolean excludeAll = false;

    public WeeklyCalendar() {
        this(null, null);
    }

    public WeeklyCalendar(ICalendar baseCalendar) {
        this(baseCalendar, null);
    }

    public WeeklyCalendar(TimeZone timeZone) {
        super(null, timeZone);
    }

    public WeeklyCalendar(ICalendar baseCalendar, TimeZone timeZone) {
        super(baseCalendar, timeZone);

        excludeDays[java.util.Calendar.SUNDAY] = true;
        excludeDays[java.util.Calendar.SATURDAY] = true;
        excludeAll = areAllDaysExcluded();
    }

    /**
     * <p>
     * Get the array with the week days
     * </p>
     */
    public boolean[] getDaysExcluded() {
        // check2 [P3-9]: defensive copy——直接返回内部数组时，外部修改会破坏 excludeAll 缓存一致性
        return excludeDays.clone();
    }

    /**
     * <p>
     * Return true, if wday (see Calendar.get()) is defined to be exluded. E. g. saturday and sunday.
     * </p>
     */
    public boolean isDayExcluded(int wday) {
        return excludeDays[wday];
    }

    /**
     * <p>
     * Redefine the array of days excluded. The array must of size greater or equal 8. java.util.Calendar's constants
     * like MONDAY should be used as index. A value of true is regarded as: exclude it.
     * </p>
     */
    public void setDaysExcluded(boolean[] weekDays) {
        if (weekDays == null) {
            return;
        }
        // check2 [P3-9]: 长度校验对齐 MonthlyCalendar——长度 <8 时 isDayExcluded(wday) 随后
        // 抛 ArrayIndexOutOfBoundsException，配置错误应 fail-fast 抛带上下文的 NopException
        if (weekDays.length < 8) {
            throw new NopException(JobCoreErrors.ERR_JOB_CALENDAR_NULL_DAYS)
                    .param("reason", "weekDays array must have at least 8 elements "
                            + "(java.util.Calendar day constants), got " + weekDays.length);
        }

        excludeDays = weekDays;
        excludeAll = areAllDaysExcluded();
    }

    /**
     * <p>
     * Redefine a certain day of the week to be excluded (true) or included (false). Use java.util.Calendar's constants
     * like MONDAY to determine the wday.
     * </p>
     */
    public void setDayExcluded(int wday, boolean exclude) {
        excludeDays[wday] = exclude;
        excludeAll = areAllDaysExcluded();
    }

    /**
     * <p>
     * Check if all week days are excluded. That is no day is included.
     * </p>
     *
     * @return boolean
     */
    public boolean areAllDaysExcluded() {
        return isDayExcluded(java.util.Calendar.SUNDAY) && isDayExcluded(java.util.Calendar.MONDAY)
                && isDayExcluded(java.util.Calendar.TUESDAY) && isDayExcluded(java.util.Calendar.WEDNESDAY)
                && isDayExcluded(java.util.Calendar.THURSDAY) && isDayExcluded(java.util.Calendar.FRIDAY)
                && isDayExcluded(java.util.Calendar.SATURDAY);
    }

    /**
     * <p>
     * Determine whether the given time (in milliseconds) is 'included' by the Calendar.
     * </p>
     *
     * <p>
     * Note that this Calendar is only has full-day precision.
     * </p>
     */
    @Override
    public boolean isTimeIncluded(long timeStamp) {
        if (timeStamp <= 0)
            return false;

        if (excludeAll == true) {
            return false;
        }

        // Test the base calendar first. Only if the base calendar not already
        // excludes the time/date, continue evaluating this calendar instance.
        if (super.isTimeIncluded(timeStamp) == false) {
            return false;
        }

        java.util.Calendar cl = createJavaCalendar(timeStamp);
        int wday = cl.get(java.util.Calendar.DAY_OF_WEEK);

        return !(isDayExcluded(wday));
    }

    /**
     * <p>
     * Determine the next time (in milliseconds) that is 'included' by the Calendar after the given time. Return the
     * original value if timeStamp is included. Return 0 if all days are excluded.
     * </p>
     *
     * <p>
     * Note that this Calendar is only has full-day precision.
     * </p>
     */
    @Override
    public long getNextIncludedTime(long timeStamp) {
        if (excludeAll == true) {
            return 0;
        }

        // Call base calendar implementation first
        long baseTime = super.getNextIncludedTime(timeStamp);
        if ((baseTime > 0) && (baseTime > timeStamp)) {
            timeStamp = baseTime;
        }

        // Get timestamp for 00:00:00
        java.util.Calendar cl = getStartOfDayJavaCalendar(timeStamp);
        int wday = cl.get(java.util.Calendar.DAY_OF_WEEK);

        if (!isDayExcluded(wday)) {
            return timeStamp; // return the original value
        }

        while (isDayExcluded(wday) == true) {
            cl.add(java.util.Calendar.DATE, 1);
            wday = cl.get(java.util.Calendar.DAY_OF_WEEK);
        }

        return cl.getTime().getTime();
    }
}

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
package io.nop.job.core.utils;

// 根据Quartz的CronExpression提取的接口。具体CronExpression采用的是spring框架的实现，它的代码比Quartz要简单。

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public interface ICronExpression {
    String getExpression();

    TimeZone getTimeZone();

    /**
     * Get the next {@link Date} in the sequence matching the Cron pattern and after the value provided. The return
     * value will have a whole number of seconds, and will be after the input value.
     *
     * @param date a seed value
     * @return the next value matching the pattern
     */
    long getTimeAfter(long date);

    /**
     * Indicates whether the given date satisfies the cron expression. Note that milliseconds are ignored, so two Dates
     * falling on different milliseconds of the same second will always have the same result here.
     *
     * @param date the date to evaluate
     * @return a boolean indicating whether the given date satisfies the cron expression
     */
    default boolean isSatisfiedBy(long date) {
        Calendar testDateCal = Calendar.getInstance(getTimeZone());
        testDateCal.setTimeInMillis(date);
        testDateCal.set(Calendar.MILLISECOND, 0);
        long originalDate = testDateCal.getTimeInMillis();

        testDateCal.add(Calendar.SECOND, -1);

        long timeAfter = getTimeAfter(testDateCal.getTimeInMillis());
        return timeAfter > 0 && originalDate == timeAfter;
    }

    /**
     * Returns the next date/time <I>after</I> the given date/time which does <I>not</I> satisfy the expression
     *
     * @param date the date/time at which to begin the search for the next invalid date/time
     * @return the next valid date/time
     */
    default long getNextInvalidTimeAfter(long date) {
        long difference = 1000;

        // move back to the nearest second so differences will be accurate
        Calendar adjustCal = Calendar.getInstance(getTimeZone());
        adjustCal.setTimeInMillis(date);
        adjustCal.set(Calendar.MILLISECOND, 0);
        long lastDate = adjustCal.getTimeInMillis();

        long newDate;

        // FUTURE_TODO: (QUARTZ-481) IMPROVE THIS! The following is a BAD solution to this problem.
        // Performance will be very bad here, depending on the cron expression. It is, however A solution.

        // keep getting the next included time until it's farther than one second
        // apart. At that point, lastDate is the last valid fire time. We return
        // the second immediately following it.
        while (difference == 1000) {
            newDate = getTimeAfter(lastDate);
            if (newDate < 0)
                break;

            difference = newDate - lastDate;

            if (difference == 1000) {
                lastDate = newDate;
            }
        }

        return lastDate + 1000;
    }
}

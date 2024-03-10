/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.core.calendar;

import io.nop.commons.util.DateHelper;
import io.nop.job.core.ICalendar;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Arrays;

public class AnnualCalendar extends BaseCalendar implements ICalendar, Serializable {

    private static final long serialVersionUID = 7346867105876610961L;

    /**
     * excludeDays需要按时间顺序排好序
     */
    private MonthDay[] excludeDays;

    public AnnualCalendar(ICalendar baseCalendar) {
        super(baseCalendar);
    }

    public void setExcludeDays(MonthDay[] excludeDays) {
        this.excludeDays = excludeDays;
        Arrays.sort(excludeDays);
    }

    private boolean isExcludedDay(LocalDate day) {
        for (MonthDay excludedDay : excludeDays) {
            if (day.getMonthValue() < excludedDay.getMonthValue())
                return false;

            if (DateHelper.isMonthDay(day, excludedDay))
                return true;
        }

        return false;
    }

    @Override
    public boolean isTimeIncluded(long timeStamp) {
        if (!super.isTimeIncluded(timeStamp)) {
            return false;
        }

        LocalDate day = DateHelper.millisToDate(timeStamp);

        return !(isExcludedDay(day));
    }

    @Override
    public long getNextIncludedTime(long timeStamp) {
        long baseTime = super.getNextIncludedTime(timeStamp);
        if ((baseTime > 0) && (baseTime > timeStamp)) {
            timeStamp = baseTime;
        }

        LocalDate day = DateHelper.millisToDate(timeStamp);

        while (isExcludedDay(day)) {
            day = day.plusDays(1);
        }

        return DateHelper.dateToMillis(day);
    }
}

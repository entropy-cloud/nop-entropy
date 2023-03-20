/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.job.core.calendar;

import io.nop.commons.util.DateHelper;
import io.nop.job.core.ICalendar;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Collection;
import java.util.TreeSet;

public class HolidayCalendar extends BaseCalendar implements ICalendar, Serializable {
    static final long serialVersionUID = -7590908752291814693L;

    private TreeSet<LocalDate> excludedDays = new TreeSet<>();

    public HolidayCalendar(ICalendar baseCalendar) {
        super(baseCalendar);
    }

    public void addExcludedDays(Collection<LocalDate> days) {
        this.excludedDays.addAll(days);
    }

    @Override
    public boolean isTimeIncluded(long timeStamp) {
        if (!super.isTimeIncluded(timeStamp)) {
            return false;
        }

        LocalDate day = DateHelper.millisToDate(timeStamp);

        return !(excludedDays.contains(day));
    }

    @Override
    public long getNextIncludedTime(long timeStamp) {
        long baseTime = super.getNextIncludedTime(timeStamp);
        if ((baseTime > 0) && (baseTime > timeStamp)) {
            timeStamp = baseTime;
        }

        LocalDate day = DateHelper.millisToDate(timeStamp);

        while (excludedDays.contains(day)) {
            day = day.plusDays(1);
        }

        return DateHelper.dateToMillis(day);
    }
}

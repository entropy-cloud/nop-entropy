/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.job.api.spec;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
public class TriggerSpec implements ITriggerSpec {
    private String cronExpr;
    private long repeatInterval;
    private boolean repeatFixedDelay;
    private long maxExecutionCount;
    private long minScheduleTime;
    private long maxScheduleTime;

    private long misfireThreshold;
    private boolean useDefaultCalendar;
    private int maxFailedCount;
    private List<CalendarSpec> pauseCalendars;

    @Override
    public long getMisfireThreshold() {
        return misfireThreshold;
    }

    public void setMisfireThreshold(long misfireThreshold) {
        this.misfireThreshold = misfireThreshold;
    }

    @Override
    public boolean isUseDefaultCalendar() {
        return useDefaultCalendar;
    }

    public void setUseDefaultCalendar(boolean useDefaultCalendar) {
        this.useDefaultCalendar = useDefaultCalendar;
    }

    public void setPauseCalendars(List<CalendarSpec> pauseCalendars) {
        this.pauseCalendars = pauseCalendars;
    }

    @Override
    public List<CalendarSpec> getPauseCalendars() {
        return pauseCalendars;
    }

    @Override
    public String getCronExpr() {
        return cronExpr;
    }

    public void setCronExpr(String cronExpr) {
        this.cronExpr = cronExpr;
    }

    @Override
    public long getRepeatInterval() {
        return repeatInterval;
    }

    public void setRepeatInterval(long repeatInterval) {
        this.repeatInterval = repeatInterval;
    }

    @Override
    public boolean isRepeatFixedDelay() {
        return repeatFixedDelay;
    }

    public void setRepeatFixedDelay(boolean repeatFixedDelay) {
        this.repeatFixedDelay = repeatFixedDelay;
    }

    @Override
    public long getMaxExecutionCount() {
        return maxExecutionCount;
    }

    public void setMaxExecutionCount(long maxExecutionCount) {
        this.maxExecutionCount = maxExecutionCount;
    }

    @Override
    public long getMinScheduleTime() {
        return minScheduleTime;
    }

    public void setMinScheduleTime(long minScheduleTime) {
        this.minScheduleTime = minScheduleTime;
    }

    @Override
    public int getMaxFailedCount() {
        return maxFailedCount;
    }

    public void setMaxFailedCount(int maxFailedCount) {
        this.maxFailedCount = maxFailedCount;
    }

    @Override
    public long getMaxScheduleTime() {
        return maxScheduleTime;
    }

    public void setMaxScheduleTime(long maxScheduleTime) {
        this.maxScheduleTime = maxScheduleTime;
    }
}
/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.job.api.spec;

import java.util.List;

/**
 * 定时触发器的配置信息。支持按时间间隔触发以及按照cron表达式触发，同时支持calendar配置来跳过节假日，避免节假日触发
 * <p>
 * calendar配置的格式如下 [ { type: "annual", excludes: ["01-01","12-01"]}, { type: "monthly", excludes:[1,2,3,4,31] }, { type:
 * "weekly", excludes:[6,7]}, { type: "daily", start:"12:01", end: "13:00"}, { type: "cron", expr: "0-20,40-59 * * * *
 * ?"} ]
 *
 * @author canonical_entropy@163.com
 */
public interface ITriggerSpec {

    /**
     * 任务调度时刻对应的cron表达式。如果设置了cronExpr, 会忽略repeatInterval和repeatFixedDelay设置
     *
     * @return
     */
    String getCronExpr();

    /**
     * 如果大于0,则表示按照固定时间间隔执行. 如果希望在整点执行，可以通过设置beginTime来控制第一次执行时间
     *
     * @return 单位为毫秒
     */
    long getRepeatInterval();

    /**
     * 如果按照固定时间间隔调度，执行fixedDelay还是fixedRate策略，缺省为按照fixedRate
     *
     * @return
     */
    boolean isRepeatFixedDelay();

    /**
     * 执行次数达到最大执行次数时，job自动结束．设置了cronExpr的情况下，此参数也会起到限制执行次数的作用。 如果返回1, 则表示任务仅执行一次。如果值小于等于０，则忽略此设置。
     *
     * @return
     */
    long getMaxExecutionCount();

    /**
     * 第一次调度在此时间之后进行。如果小于等于0,则忽略此参数
     *
     * @return
     */
    long getMinScheduleTime();

    /**
     * 整个任务执行时间不超过此时间。如果小于等于0,则忽略此参数
     *
     * @return
     */
    long getMaxScheduleTime();

    /**
     * 错过多少时间将被认为是misfire. 如果小于等于0， 则忽略此参数。 如果本次调度misfire, 则本次调用会被忽略，等到下次调度时间再执行
     *
     * @return
     */
    long getMisfireThreshold();

    int getMaxFailedCount();

    /**
     * 是否使用缺省的calendar。缺省calendar将和pauseCalendars配置合并在一起，构成最终的calendar对昂
     */
    boolean isUseDefaultCalendar();

    /**
     * 指定哪些时间范围内的任务触发被自动忽略
     */
    List<CalendarSpec> getPauseCalendars();
}
/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.job.api.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({@JsonSubTypes.Type(value = AnnualCalendarSpec.class, name = "annual"),
        @JsonSubTypes.Type(value = MonthlyCalendarSpec.class, name = "monthly"),
        @JsonSubTypes.Type(value = WeeklyCalendarSpec.class, name = "weekly"),
        @JsonSubTypes.Type(value = DailyCalendarSpec.class, name = "daily"),
        @JsonSubTypes.Type(value = HolidayCalendarSpec.class, name = "holiday"),
        @JsonSubTypes.Type(value = CronCalendarSpec.class, name = "cron")})
public abstract class CalendarSpec implements Serializable {
    private static final long serialVersionUID = 6756041836462853393L;

}

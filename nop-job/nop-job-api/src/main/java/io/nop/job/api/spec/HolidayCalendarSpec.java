/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.api.spec;

import io.nop.api.core.annotations.data.DataBean;

import java.util.Map;

@DataBean
public class HolidayCalendarSpec extends CalendarSpec {

    /**
     * 从年份映射到日期列表。日期列表为0和1组成的数组，1表示假日。第一个值对应于年内的第一天，第二个值对应年内的第二天，依此类推
     */
    private Map<String, String> yearDays;

    public Map<String, String> getYearDays() {
        return yearDays;
    }

    public void setYearDays(Map<String, String> yearDays) {
        this.yearDays = yearDays;
    }
}
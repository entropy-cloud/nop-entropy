/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.job.api.spec;

import io.nop.api.core.annotations.data.DataBean;

import java.time.LocalTime;

@DataBean
public class DailyCalendarSpec extends CalendarSpec {
    private static final long serialVersionUID = 1L;

    private LocalTime start;

    /**
     * 不包含区间的结束时间
     */
    private LocalTime end;

    public LocalTime getStart() {
        return start;
    }

    public void setStart(LocalTime start) {
        this.start = start;
    }

    public LocalTime getEnd() {
        return end;
    }

    public void setEnd(LocalTime end) {
        this.end = end;
    }
}

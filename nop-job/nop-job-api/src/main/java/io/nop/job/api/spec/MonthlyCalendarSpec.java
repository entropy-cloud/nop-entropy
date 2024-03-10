/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.api.spec;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class MonthlyCalendarSpec extends CalendarSpec {
    private static final long serialVersionUID = 1L;

    /**
     * 从1到31，对应忽略每月的指定日期
     */
    private int[] excludes;

    public int[] getExcludes() {
        return excludes;
    }

    public void setExcludes(int[] excludes) {
        this.excludes = excludes;
    }
}
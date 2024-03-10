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
public class WeeklyCalendarSpec extends CalendarSpec {
    private static final long serialVersionUID = 1L;

    /**
     * 从1到7表示星期一到星期天。
     * <p>
     * 按照国际标准 ISO 8601 的说法，星期一是一周的开始，而星期日是一周的结束。 同时参考国家标准 GB/T 7408-2005，其中也明确表述了星期一为一周的开始。
     * 但是很多国家，比如「美国」、「加拿大」和「澳大利亚」等国家，依然以星期日作为一周的开始。
     */
    private int[] excludes;

    public int[] getExcludes() {
        return excludes;
    }

    public void setExcludes(int[] excludes) {
        this.excludes = excludes;
    }
}

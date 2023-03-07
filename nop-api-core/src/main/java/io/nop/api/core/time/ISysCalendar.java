/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.time;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 业务日历表，业务日期与系统物理时钟当前的日期可没有关系
 */
public interface ISysCalendar {
    /**
     * 是否是工作日
     */
    boolean isWorkDay(LocalDate date);

    /**
     * 返回下一个工作日
     */
    LocalDate nextWorkDay(LocalDate date);

    /**
     * 返回当前业务日期
     */
    LocalDate getSysDate();

    LocalDateTime getSysDateTime();
}

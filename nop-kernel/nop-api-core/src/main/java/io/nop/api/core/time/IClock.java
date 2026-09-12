/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.time;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface IClock {
    long currentTimeMillis();

    /**
     * 单调相对时间（纳秒）。实现必须保证单调递增（与 System.nanoTime 语义一致），
     * 不得与 currentTimeMillis 的时钟调整联动回退。仅用于相对计时（耗时测量、deadline），
     * 不承载绝对时间语义。
     */
    default long nanoTime() {
        return System.nanoTime();
    }

    LocalDate currentDate();

    LocalDateTime currentDateTime();
}

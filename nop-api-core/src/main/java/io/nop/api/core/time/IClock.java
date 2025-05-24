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

    default long nanoTime() {
        return System.nanoTime();
    }

    LocalDate currentDate();

    LocalDateTime currentDateTime();
}

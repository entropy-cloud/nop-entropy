/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import java.util.List;

public interface IBatchRetryConsumeListener<R, C> {
    void beforeRetry(List<R> items, C context);

    void afterRetry(Throwable exception, List<R> items, C context);
}
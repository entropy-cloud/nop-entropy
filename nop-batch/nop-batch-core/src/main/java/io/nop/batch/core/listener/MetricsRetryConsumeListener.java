/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.listener;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchRetryConsumeListener;
import io.nop.batch.core.IBatchTaskMetrics;

import java.util.List;

public class MetricsRetryConsumeListener<R> implements IBatchRetryConsumeListener<R, IBatchChunkContext> {
    @Override
    public void beforeRetry(List<R> items, IBatchChunkContext context) {
        IBatchTaskMetrics metrics = context.getTaskContext().getMetrics();
        if (metrics != null)
            metrics.retry(items.size());
    }

    @Override
    public void afterRetry(Throwable exception, List<R> items, IBatchChunkContext context) {
    }
}
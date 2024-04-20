/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import java.util.function.BiPredicate;

public class BatchSkipPolicy {
    private long maxSkipCount;
    private BiPredicate<Throwable, IBatchChunkContext> skipExceptionFilter;

    public long getMaxSkipCount() {
        return maxSkipCount;
    }

    public void setMaxSkipCount(long maxSkipCount) {
        this.maxSkipCount = maxSkipCount;
    }

    public BatchSkipPolicy maxSkipCount(long maxSkipCount) {
        this.setMaxSkipCount(maxSkipCount);
        return this;
    }

    public BatchSkipPolicy skipExceptionFilter(BiPredicate<Throwable, IBatchChunkContext> filter) {
        this.skipExceptionFilter = filter;
        return this;
    }

    public BiPredicate<Throwable, IBatchChunkContext> getSkipExceptionFilter() {
        return skipExceptionFilter;
    }

    public void setSkipExceptionFilter(BiPredicate<Throwable, IBatchChunkContext> skipExceptionFilter) {
        this.skipExceptionFilter = skipExceptionFilter;
    }

    public boolean shouldSkip(Throwable exception, long skipCount, IBatchChunkContext context) {
        if (maxSkipCount > 0 && skipCount >= maxSkipCount) {
            return false;
        }

        if (this.skipExceptionFilter != null)
            return skipExceptionFilter.test(exception, context);

        return !(exception instanceof Error);
    }
}
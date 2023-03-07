/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core;

import java.util.function.Predicate;

public class BatchSkipPolicy {
    private long maxSkipCount;
    private Predicate<Throwable> skipExceptionFilter;

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

    public BatchSkipPolicy skipExceptionFilter(Predicate<Throwable> filter) {
        this.skipExceptionFilter = filter;
        return this;
    }

    public Predicate<Throwable> getSkipExceptionFilter() {
        return skipExceptionFilter;
    }

    public void setSkipExceptionFilter(Predicate<Throwable> skipExceptionFilter) {
        this.skipExceptionFilter = skipExceptionFilter;
    }

    public boolean shouldSkip(Throwable exception, long skipCount) {
        if (maxSkipCount > 0 && skipCount >= maxSkipCount) {
            return false;
        }

        if (this.skipExceptionFilter != null)
            return skipExceptionFilter.test(exception);

        return !(exception instanceof Error);
    }
}
/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import java.util.function.BiPredicate;

import static io.nop.batch.core.BatchErrors.ARG_SKIP_COUNT;
import static io.nop.batch.core.BatchErrors.ERR_BATCH_SKIP_COUNT_INVALID;

public class BatchSkipPolicy {
    private long maxSkipCount;
    private BiPredicate<Throwable, IBatchChunkContext> skipExceptionFilter;

    public long getMaxSkipCount() {
        return maxSkipCount;
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

    /**
     * 1.7-C7-005（plan 2546 W-01）：负值在 {@code shouldSkip} 中与 0 同义（都不设上限），
     * 但"负值=无限"与多数配置惯例（-1=禁用）相反，是静默配置陷阱。装配期类型化 fail-fast。
     * <p>
     * <b>0 是显式的"不设上限"</b>——语义见 {@link #shouldSkip}：{@code maxSkipCount > 0} 才做饱和检查。
     */
    public void setMaxSkipCount(long maxSkipCount) {
        if (maxSkipCount < 0)
            throw new io.nop.api.core.exceptions.NopException(ERR_BATCH_SKIP_COUNT_INVALID)
                    .param(ARG_SKIP_COUNT, maxSkipCount);
        this.maxSkipCount = maxSkipCount;
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
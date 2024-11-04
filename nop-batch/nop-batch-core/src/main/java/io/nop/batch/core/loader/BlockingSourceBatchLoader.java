/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.loader;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.exceptions.BatchCancelException;
import io.nop.commons.concurrent.IBlockingSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.nop.api.core.util.ICancellable.CANCEL_REASON_STOP;
import static io.nop.batch.core.BatchErrors.ERR_BATCH_CANCEL_LOAD;

/**
 * 从BlockingSource阻塞加载数据
 */
public class BlockingSourceBatchLoader<S> implements IBatchLoaderProvider<S> {
    private IBlockingSource<S> source;
    private long minWaitInterval;
    private long pollInterval;

    public IBlockingSource<S> getSource() {
        return source;
    }

    public void setSource(IBlockingSource<S> source) {
        this.source = source;
    }

    public long getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(long pollInterval) {
        this.pollInterval = pollInterval;
    }

    public long getMinWaitInterval() {
        return minWaitInterval;
    }

    public void setMinWaitInterval(long minWaitInterval) {
        this.minWaitInterval = minWaitInterval;
    }

    @Override
    public IBatchLoader<S> setup(IBatchTaskContext context) {
        Guard.positiveLong(pollInterval, "pollInterval");

        return (batchSize, ctx) -> {
            do {
                if (ctx.isCancelled()) {
                    if (CANCEL_REASON_STOP.equals(ctx.getCancelReason()))
                        return Collections.emptyList();

                    throw new BatchCancelException(ERR_BATCH_CANCEL_LOAD);
                }

                if (context.isCancelled()) {
                    if (CANCEL_REASON_STOP.equals(context.getCancelReason()))
                        return Collections.emptyList();

                    throw new BatchCancelException(ERR_BATCH_CANCEL_LOAD);
                }

                List<S> items = new ArrayList<>(batchSize);
                try {
                    source.drainTo(items, batchSize, minWaitInterval, pollInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw NopException.adapt(e);
                }

                if (!items.isEmpty())
                    return items;
            } while (true);
        };
    }
}
package io.nop.batch.core.consumer;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchRecordSnapshotBuilder;
import io.nop.commons.util.retry.IRetryPolicy;

import java.util.Collection;

public class RetryAllBatchConsumer<R> extends AbstractRetryBatchConsumer<R> {
    public RetryAllBatchConsumer(IBatchConsumerProvider.IBatchConsumer<R> consumer,
                                 IRetryPolicy<IBatchChunkContext> retryPolicy,
                                 IBatchRecordSnapshotBuilder<R> snapshotBuilder) {
        super(consumer, retryPolicy, snapshotBuilder);
    }

    @Override
    protected void retryConsume(IBatchRecordSnapshotBuilder.ISnapshot<R> snapshot,
                                Collection<R> items, IBatchChunkContext context) {
        RetryConsumeHelper.retryConsume(retryPolicy, consumer, snapshot, items, context);
    }
}

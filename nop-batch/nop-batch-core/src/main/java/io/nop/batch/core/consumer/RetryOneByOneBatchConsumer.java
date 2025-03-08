package io.nop.batch.core.consumer;

import io.nop.batch.core.BatchSkipPolicy;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchRecordSnapshotBuilder;
import io.nop.commons.util.retry.IRetryPolicy;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RetryOneByOneBatchConsumer<R> extends AbstractRetryBatchConsumer<R> {
    private final BatchSkipPolicy skipPolicy;

    public RetryOneByOneBatchConsumer(IBatchConsumerProvider.IBatchConsumer<R> consumer,
                                      IRetryPolicy<IBatchChunkContext> retryPolicy,
                                      IBatchRecordSnapshotBuilder<R> snapshotBuilder,
                                      BatchSkipPolicy skipPolicy) {
        super(consumer, retryPolicy, snapshotBuilder);
        this.skipPolicy = skipPolicy;
    }

    @Override
    protected void retryConsume(IBatchRecordSnapshotBuilder.ISnapshot<R> snapshot,
                                Collection<R> items, IBatchChunkContext context) {
        for (R item : items) {
            List<R> retryItems = Collections.singletonList(item);
            SkipConsumeHelper.consumeWithSkipPolicy(skipPolicy, (list, ctx) -> {
                RetryConsumeHelper.retryConsume(retryPolicy, consumer, snapshot, items, context);
            }, retryItems, context);
        }
    }

}
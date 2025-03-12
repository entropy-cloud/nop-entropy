package io.nop.batch.core.consumer;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;
import io.nop.batch.core.IBatchRecordSnapshotBuilder;
import io.nop.batch.core.exceptions.BatchCancelException;
import io.nop.commons.util.retry.IRetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;

public abstract class AbstractRetryBatchConsumer<R> implements IBatchConsumer<R> {
    static final Logger LOG = LoggerFactory.getLogger(AbstractRetryBatchConsumer.class);

    protected final IBatchConsumer<R> consumer;
    protected final IRetryPolicy<IBatchChunkContext> retryPolicy;
    private final IBatchRecordSnapshotBuilder<R> snapshotBuilder;

    public AbstractRetryBatchConsumer(IBatchConsumer<R> consumer,
                                      IRetryPolicy<IBatchChunkContext> retryPolicy,
                                      IBatchRecordSnapshotBuilder<R> snapshotBuilder) {
        this.consumer = consumer;
        this.retryPolicy = retryPolicy;
        this.snapshotBuilder = snapshotBuilder;
    }

    @Override
    public void consume(Collection<R> items, IBatchChunkContext context) {
        IBatchRecordSnapshotBuilder.ISnapshot<R> snapshot =
                snapshotBuilder == null ? null : snapshotBuilder.buildSnapshot(items, context);

        context.setSingleMode(items.size() == 1);
        try {
            consumer.consume(items, context);
        } catch (BatchCancelException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("nop.err.batch.consume-fail", e);

            // 有可能部分记录已经被处理，不需要被重试
            if (context.getCompletedItemCount() > 0) {
                items = new ArrayList<>(items);
                items.removeAll(context.getCompletedItems());
            }

            if (snapshot != null)
                snapshot.onError(e);

            if (items.isEmpty())
                return;

            checkRetry(e, 0, context);

            if (snapshot != null)
                items = snapshot.restore(items, context);

            try {
                retryConsume(snapshot, items, context);
            } catch (Exception e2) {
                if (snapshot != null)
                    snapshot.onError(e2);
                throw NopException.adapt(e);
            }
        } finally {
            context.setSingleMode(false);
        }
    }

    protected void checkRetry(Throwable e, int retryTimes, IBatchChunkContext context) {
        RetryConsumeHelper.checkRetry(retryPolicy, e, retryTimes, context);
    }

    protected abstract void retryConsume(IBatchRecordSnapshotBuilder.ISnapshot<R> snapshot,
                                         Collection<R> items, IBatchChunkContext context);
}
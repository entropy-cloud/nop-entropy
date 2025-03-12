package io.nop.batch.core.consumer;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchRecordSnapshotBuilder;
import io.nop.batch.core.exceptions.BatchCancelException;
import io.nop.commons.concurrent.thread.ThreadHelper;
import io.nop.commons.util.retry.IRetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;

public class RetryConsumeHelper {
    static final Logger LOG = LoggerFactory.getLogger(RetryConsumeHelper.class);

    public static void checkRetry(IRetryPolicy<IBatchChunkContext> retryPolicy,
                                  Throwable e, int retryTimes, IBatchChunkContext context) {
        if (retryPolicy != null) {
            long delay = retryPolicy.getRetryDelay(e, retryTimes, context);
            if (delay < 0)
                throw NopException.adapt(e);

            if (delay > 0) {
                ThreadHelper.sleep(delay);
            }
        }
        context.incRetryCount();
    }

    public static <R> void retryConsume(IRetryPolicy<IBatchChunkContext> retryPolicy,
                                        IBatchConsumerProvider.IBatchConsumer<R> consumer,
                                        IBatchRecordSnapshotBuilder.ISnapshot<R> snapshot,
                                        Collection<R> items, IBatchChunkContext context) {
        if (items.isEmpty())
            return;

        int retryCount = 0;
        Collection<R> retryItems = items;
        do {
            try {
                context.getTaskContext().fireChunkTryBegin(retryItems, context);
                context.setSingleMode(retryItems.size() == 1);
                consumer.consume(retryItems, context);
                context.getTaskContext().fireChunkTryEnd(context, null);
                return;
            } catch (BatchCancelException e) {
                context.getTaskContext().fireChunkTryEnd(context, e);
                throw e;
            } catch (Throwable e) {
                LOG.error("nop.err.batch.retry-consume-fail:retryCount={}", retryCount, e);
                context.getTaskContext().fireChunkTryEnd(context, e);

                if (snapshot != null)
                    snapshot.onError(e);

                // 有可能部分记录已经被处理，不需要被重试
                if (context.getCompletedItemCount() > 0) {
                    retryItems = new ArrayList<>(retryItems);
                    retryItems.removeAll(context.getCompletedItems());
                }

                if (retryItems.isEmpty())
                    return;

                retryCount++;
                checkRetry(retryPolicy, e, retryCount, context);

                if (snapshot != null)
                    retryItems = snapshot.restore(retryItems, context);
            }
        } while (true);
    }
}

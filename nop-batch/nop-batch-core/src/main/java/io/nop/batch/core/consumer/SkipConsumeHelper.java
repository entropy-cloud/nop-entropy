package io.nop.batch.core.consumer;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.BatchSkipPolicy;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchTaskMetrics;
import io.nop.batch.core.exceptions.BatchCancelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class SkipConsumeHelper {
    static final Logger LOG = LoggerFactory.getLogger(SkipConsumeHelper.class);

    public static <R> void consumeWithSkipPolicy(BatchSkipPolicy skipPolicy,
                                                 IBatchConsumerProvider.IBatchConsumer<R> consumer,
                                                 Collection<R> items,
                                                 IBatchChunkContext context) {
        if (skipPolicy == null) {
            consumer.consume(items, context);
            return;
        }

        IBatchTaskMetrics metrics = context.getTaskContext().getMetrics();
        int completedCount = context.getCompletedItemCount();
        try {
            // 内部有可能根据retryPolicy进行重试
            consumer.consume(items, context);
        } catch (BatchCancelException e) {
            throw e;
        } catch (Throwable e) {
            if (skipPolicy.shouldSkip(e, context.getTaskContext().getSkipItemCount(), context)) {
                int count = items.size() - context.getCompletedItemCount() + completedCount;
                LOG.info("nop.batch.skip-error:skipCount={},totalSkipCount={}", count,
                        context.getTaskContext().getSkipItemCount(), e);

                if (metrics != null) {
                    metrics.skipError(count);
                }
                context.getTaskContext().incSkipItemCount(count);
            } else {
                throw NopException.adapt(e);
            }
        }
    }
}

package io.nop.batch.core.consumer;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;

import java.util.Collection;

public class AddCompletedBatchConsumer<R> implements IBatchConsumer<R> {
    private final IBatchConsumer<R> consumer;

    public AddCompletedBatchConsumer(IBatchConsumer<R> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void consume(Collection<R> items, IBatchChunkContext context) {
        consumer.consume(items, context);
        context.addCompletedItems(items);
    }
}

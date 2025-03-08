package io.nop.batch.core.consumer;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;

import java.util.Collection;
import java.util.Collections;

public class SingleModeBatchConsumer<R> implements IBatchConsumer<R> {
    private final IBatchConsumer<R> consumer;

    public SingleModeBatchConsumer(IBatchConsumer<R> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void consume(Collection<R> items, IBatchChunkContext context) {
        context.setSingleMode(true);
        for (R item : items) {
            consumer.consume(Collections.singletonList(item), context);
        }
        context.setSingleMode(false);
    }
}

package io.nop.batch.core.consumer;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchTaskContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

public class TransformedBatchConsumerProvider<R> implements IBatchConsumerProvider<R> {
    private final IBatchConsumerProvider<R> provider;
    private final BiFunction<R, IBatchChunkContext, R> transformer;

    public TransformedBatchConsumerProvider(IBatchConsumerProvider<R> provider, BiFunction<R, IBatchChunkContext, R> transformer) {
        this.provider = provider;
        this.transformer = transformer;
    }

    @Override
    public IBatchConsumer<R> setup(IBatchTaskContext context) {
        return new TransformedBatchConsumer(provider.setup(context), transformer);
    }

    static class TransformedBatchConsumer<R> implements IBatchConsumer<R> {
        private final IBatchConsumer<R> consumer;
        private final BiFunction<R, IBatchChunkContext, R> transformer;

        public TransformedBatchConsumer(IBatchConsumer<R> consumer, BiFunction<R, IBatchChunkContext, R> transformer) {
            this.consumer = consumer;
            this.transformer = transformer;
        }

        @Override
        public void consume(Collection<R> items, IBatchChunkContext context) {
            List<R> list = new ArrayList<>(items.size());
            for (R item : items) {
                R newItem = transformer.apply(item, context);
                if (newItem != null)
                    list.add(newItem);
            }
            consumer.consume(list, context);
        }
    }
}
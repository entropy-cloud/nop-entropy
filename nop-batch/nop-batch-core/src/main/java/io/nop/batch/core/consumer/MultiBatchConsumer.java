package io.nop.batch.core.consumer;

import io.nop.batch.core.IBatchConsumer;

import java.util.List;

public class MultiBatchConsumer<R, C> implements IBatchConsumer<R, C> {
    private final List<IBatchConsumer<R, C>> list;

    public MultiBatchConsumer(List<IBatchConsumer<R, C>> list) {
        this.list = list;
    }

    @Override
    public void consume(List<R> items, C context) {
        for (IBatchConsumer<R, C> consumer : list) {
            consumer.consume(items, context);
        }
    }
}

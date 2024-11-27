package io.nop.batch.core.consumer;

import io.nop.api.core.util.Guard;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;
import io.nop.batch.core.IBatchRecordFilter;

import java.util.List;
import java.util.stream.Collectors;

public class FilteredBatchConsumer<R> implements IBatchConsumer<R> {
    private final IBatchRecordFilter<R, IBatchChunkContext> filter;
    private final IBatchConsumer<R> consumer;

    public FilteredBatchConsumer(IBatchRecordFilter<R, IBatchChunkContext> filter,
                                 IBatchConsumer<R> consumer) {
        this.filter = Guard.notNull(filter, "filter");
        this.consumer = consumer;
    }

    @Override
    public void consume(List<R> items, IBatchChunkContext context) {
        List<R> filtered = items.stream().filter(item -> filter.accept(item, context))
                .collect(Collectors.toList());
        if (!filtered.isEmpty()) {
            consumer.consume(filtered, context);
        }
    }
}

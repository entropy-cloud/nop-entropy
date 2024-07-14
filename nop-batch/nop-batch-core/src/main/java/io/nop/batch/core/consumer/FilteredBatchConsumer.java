package io.nop.batch.core.consumer;

import io.nop.api.core.util.Guard;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumer;
import io.nop.batch.core.IBatchRecordFilter;
import io.nop.batch.core.IBatchTaskContext;

import java.util.List;
import java.util.stream.Collectors;

public class FilteredBatchConsumer<R> implements IBatchConsumer<R, IBatchChunkContext> {
    private final IBatchRecordFilter<R> filter;
    private final IBatchConsumer<R, IBatchChunkContext> consumer;

    public FilteredBatchConsumer(IBatchRecordFilter<R> filter,
                                 IBatchConsumer<R, IBatchChunkContext> consumer) {
        this.filter = Guard.notNull(filter, "filter");
        this.consumer = consumer;
    }

    @Override
    public void consume(List<R> items, IBatchChunkContext context) {
        IBatchTaskContext taskContext = context.getTaskContext();
        List<R> filtered = items.stream().filter(item -> filter.accept(item, taskContext))
                .collect(Collectors.toList());
        if (!filtered.isEmpty()) {
            consumer.consume(filtered, context);
        }
    }
}

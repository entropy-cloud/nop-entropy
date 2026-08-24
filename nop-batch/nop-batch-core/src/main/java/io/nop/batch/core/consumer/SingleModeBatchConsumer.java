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
        boolean singleMode = context.isSingleMode();
        context.setSingleMode(true);
        try {
            for (R item : items) {
                consumer.consume(Collections.singletonList(item), context);
            }
        } finally {
            // 内层consume抛异常时也必须恢复singleMode标志，
            // 否则同chunk上下文内捕获异常继续处理的外层逻辑（如skip策略）会看到错误状态
            context.setSingleMode(singleMode);
        }
    }
}

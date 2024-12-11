/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.consumer;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.commons.collections.MultiMapCollector;
import io.nop.dataset.record.IRecordSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * 将一条记录拆分成多条记录，分别由不同的consumer消费
 *
 * @param <R> 原始记录类型
 * @param <T> 拆分后的记录类型
 */
public class SplitBatchConsumer<R, T> implements IBatchConsumerProvider<R> {
    static final Logger LOG = LoggerFactory.getLogger(SplitBatchConsumer.class);

    private final IRecordSplitter<R, T, IBatchChunkContext> splitter;
    private final BiFunction<String, IBatchChunkContext, IBatchConsumer<T>> consumerProvider;

    private final boolean lazyInit;

    public SplitBatchConsumer(IRecordSplitter<R, T, IBatchChunkContext> splitter,
                              BiFunction<String, IBatchChunkContext, IBatchConsumer<T>> consumerProvider,
                              boolean lazyInit) {
        this.splitter = splitter;
        this.consumerProvider = consumerProvider;
        this.lazyInit = lazyInit;
    }

    @Override
    public IBatchConsumer<R> setup(IBatchTaskContext context) {
        Map<String, IBatchConsumer<T>> activeConsumers = new ConcurrentHashMap<>();
        return (items, ctx) -> consume(items, ctx, activeConsumers);
    }

    void consume(Collection<R> items, IBatchChunkContext context, Map<String, IBatchConsumer<T>> activeConsumers) {
        MultiMapCollector<String, T> collector = new MultiMapCollector<>();
        splitter.splitMulti(items, collector, context);
        Map<String, List<T>> map = collector.getResultMap();
        for (Map.Entry<String, List<T>> entry : map.entrySet()) {
            IBatchConsumer<T> consumer = getConsumer(entry.getKey(), context, activeConsumers);
            if (consumer != null) {
                consumer.consume(entry.getValue(), context);
            }
        }
    }

    protected synchronized IBatchConsumer<T> getConsumer(String tag, IBatchChunkContext context,
                                                         Map<String, IBatchConsumer<T>> activeConsumers) {
        IBatchConsumer<T> consumer = activeConsumers.get(tag);
        if (consumer != null)
            return consumer;

        consumer = consumerProvider.apply(tag, context);
        if (consumer != null) {
            activeConsumers.put(tag, consumer);
        }
        return consumer;
    }
}
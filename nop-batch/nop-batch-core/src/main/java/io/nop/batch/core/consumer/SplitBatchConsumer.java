/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core.consumer;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumer;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.IBatchTaskListener;
import io.nop.commons.collections.MultiMapCollector;
import io.nop.commons.record.IRecordSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 将一条记录拆分成多条记录，分别由不同的consumer消费
 *
 * @param <R> 原始记录类型
 * @param <T> 拆分后的记录类型
 */
public class SplitBatchConsumer<R, T> implements IBatchConsumer<R, IBatchChunkContext>, IBatchTaskListener {
    static final Logger LOG = LoggerFactory.getLogger(SplitBatchConsumer.class);

    private final IRecordSplitter<R, T> splitter;
    private final Function<String, IBatchConsumer<T, IBatchChunkContext>> consumerProvider;

    private Map<String, IBatchConsumer<T, IBatchChunkContext>> activeConsumers = new HashMap<>();

    public SplitBatchConsumer(IRecordSplitter<R, T> splitter,
                              Function<String, IBatchConsumer<T, IBatchChunkContext>> consumerProvider) {
        this.splitter = splitter;
        this.consumerProvider = consumerProvider;
    }

    @Override
    public void onTaskBegin(IBatchTaskContext context) {
        activeConsumers.clear();
    }

    @Override
    public synchronized void onTaskEnd(Throwable exception, IBatchTaskContext context) {
        Throwable e = null;
        for (IBatchConsumer<T, IBatchChunkContext> consumer : activeConsumers.values()) {
            if (consumer instanceof IBatchTaskListener) {
                try {
                    ((IBatchTaskListener) consumer).onTaskEnd(exception, context);
                } catch (Exception err) {
                    LOG.error("nop.err.batch.consumer-onTaskEnd-fail", err);
                    e = err;
                }
            }
        }
        activeConsumers.clear();

        if (e != null)
            throw NopException.adapt(e);
    }

    @Override
    public void consume(List<R> items, IBatchChunkContext context) {
        MultiMapCollector<String, T> collector = new MultiMapCollector<>();
        splitter.splitMulti(items, collector);
        Map<String, List<T>> map = collector.getResultMap();
        for (Map.Entry<String, List<T>> entry : map.entrySet()) {
            IBatchConsumer<T, IBatchChunkContext> consumer = getConsumer(entry.getKey(), context);
            if (consumer != null) {
                consumer.consume(entry.getValue(), context);
            }
        }
    }

    protected synchronized IBatchConsumer<T, IBatchChunkContext> getConsumer(String tag, IBatchChunkContext context) {
        IBatchConsumer<T, IBatchChunkContext> consumer = activeConsumers.get(tag);
        if (consumer != null)
            return consumer;

        consumer = consumerProvider.apply(tag);
        if (consumer != null) {
            if (consumer instanceof IBatchTaskListener) {
                ((IBatchTaskListener) consumer).onTaskBegin(context.getTaskContext());
            }
            activeConsumers.put(tag, consumer);
        }
        return consumer;
    }
}
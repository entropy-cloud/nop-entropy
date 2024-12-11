/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.consumer;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;
import io.nop.batch.core.IBatchRecordHistoryStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WithHistoryBatchConsumer<R> implements IBatchConsumer<R> {
    private final IBatchRecordHistoryStore<R> historyStore;
    private final IBatchConsumer<R> consumer;
    private final IBatchConsumer<R> historyConsumer;

    public WithHistoryBatchConsumer(IBatchRecordHistoryStore<R> historyStore,
                                    IBatchConsumer<R> consumer,
                                    IBatchConsumer<R> historyConsumer) {
        this.historyStore = historyStore;
        this.consumer = consumer;
        this.historyConsumer = historyConsumer;
    }

    @Override
    public void consume(Collection<R> items, IBatchChunkContext context) {
        Collection<R> filtered = historyStore.filterProcessed(items, context);
        if (!filtered.isEmpty()) {
            if (filtered.size() != items.size()) {
                if (historyConsumer != null) {
                    List<R> history = new ArrayList<>();
                    for (R item : items) {
                        if (!filtered.contains(item)) {
                            context.addCompletedItem(item);
                            history.add(item);
                        }
                    }
                    historyConsumer.consume(history, context);
                } else {
                    for (R item : items) {
                        if (!filtered.contains(item)) {
                            context.addCompletedItem(item);
                        }
                    }
                }
            }

            try {
                consumer.consume(filtered, context);
            } catch (Exception e) {
                historyStore.saveProcessed(filtered, e, context);
                throw NopException.adapt(e);
            }
            historyStore.saveProcessed(filtered, null, context);
            context.addCompletedItems(filtered);
        } else {
            context.addCompletedItems(items);
        }
    }
}

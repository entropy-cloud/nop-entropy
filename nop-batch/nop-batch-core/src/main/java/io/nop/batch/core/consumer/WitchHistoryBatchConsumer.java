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
import io.nop.batch.core.IBatchConsumer;
import io.nop.batch.core.IBatchRecordHistoryStore;

import java.util.List;

public class WitchHistoryBatchConsumer<R> implements IBatchConsumer<R, IBatchChunkContext> {
    private final IBatchRecordHistoryStore<R> historyStore;
    private final IBatchConsumer<R, IBatchChunkContext> consumer;

    public WitchHistoryBatchConsumer(IBatchRecordHistoryStore<R> historyStore, IBatchConsumer<R, IBatchChunkContext> consumer) {
        this.historyStore = historyStore;
        this.consumer = consumer;
    }

    @Override
    public void consume(List<R> items, IBatchChunkContext context) {
        List<R> filtered = historyStore.filterProcessed(items, context);
        if (!filtered.isEmpty()) {
            for (R item : items) {
                if (!filtered.contains(item)) {
                    context.addCompletedItem(item);
                }
            }

            try {
                consumer.consume(filtered, context);
            } catch (Exception e) {
                historyStore.saveProcessed(filtered, e, context);
                throw NopException.adapt(e);
            }
            historyStore.saveProcessed(filtered, null, context);
        } else {
            filtered.forEach(context::addCompletedItem);
        }
    }
}

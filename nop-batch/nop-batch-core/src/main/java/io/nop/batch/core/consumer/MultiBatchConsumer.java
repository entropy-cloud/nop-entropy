/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.consumer;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;

import java.util.Collection;
import java.util.List;

public final class MultiBatchConsumer<R> implements IBatchConsumer<R> {
    private final List<IBatchConsumer<R>> list;

    public MultiBatchConsumer(List<IBatchConsumer<R>> list) {
        this.list = list;
    }

    public List<IBatchConsumer<R>> getConsumers() {
        return list;
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public int size() {
        return list.size();
    }

    public IBatchConsumer<R> first() {
        return list.get(0);
    }

    @Override
    public void consume(Collection<R> items, IBatchChunkContext context) {
        for (IBatchConsumer<R> consumer : list) {
            consumer.consume(items, context);
        }
    }
}

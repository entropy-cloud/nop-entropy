/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.consumer;

import io.nop.batch.core.IBatchConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class EmptyBatchConsumer<R, C> implements IBatchConsumer<R, C> {
    static final Logger LOG = LoggerFactory.getLogger(EmptyBatchConsumer.class);

    private static final EmptyBatchConsumer<Object, Object> INSTANCE = new EmptyBatchConsumer<>();

    public static <R, C> EmptyBatchConsumer<R, C> instance() {
        return (EmptyBatchConsumer) INSTANCE;
    }

    @Override
    public void consume(List<R> items, C context) {
        LOG.debug("batch.consumer.ignore:items={}", items);
    }
}
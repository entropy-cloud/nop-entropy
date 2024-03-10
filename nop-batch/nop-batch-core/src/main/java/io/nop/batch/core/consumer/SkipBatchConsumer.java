/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.consumer;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.BatchSkipPolicy;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumer;
import io.nop.batch.core.IBatchTaskMetrics;
import io.nop.batch.core.exceptions.BatchCancelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 消费失败之后允许忽略skipCount条记录。
 */
public class SkipBatchConsumer<R> implements IBatchConsumer<R, IBatchChunkContext> {
    static final Logger LOG = LoggerFactory.getLogger(SkipBatchConsumer.class);

    private final IBatchConsumer<R, IBatchChunkContext> consumer;
    private final BatchSkipPolicy skipPolicy;

    public SkipBatchConsumer(IBatchConsumer<R, IBatchChunkContext> consumer, BatchSkipPolicy skipPolicy) {
        this.consumer = consumer;
        this.skipPolicy = skipPolicy;
    }

    @Override
    public void consume(List<R> items, IBatchChunkContext context) {
        IBatchTaskMetrics metrics = context.getTaskContext().getMetrics();
        try {
            consumer.consume(items, context);
        } catch (BatchCancelException e) {
            throw e;
        } catch (Throwable e) {
            if (skipPolicy.shouldSkip(e, context.getTaskContext().getSkipItemCount())) {
                int count = context.getChunkItems().size() - context.getCompletedItemCount();
                LOG.info("nop.batch.skip-error:skipCount={},totalSkipCount={}", count,
                        context.getTaskContext().getSkipItemCount(), e);

                if (metrics != null) {
                    metrics.skipError(count);
                }
                context.getTaskContext().incSkipItemCount(count);
            } else {
                throw NopException.adapt(e);
            }
        }
    }
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.consumer;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;
import io.nop.batch.core.IBatchRecordSnapshotBuilder;
import io.nop.batch.core.IBatchTaskMetrics;
import io.nop.batch.core.exceptions.BatchCancelException;
import io.nop.commons.concurrent.thread.ThreadHelper;
import io.nop.commons.util.retry.IRetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 消费失败之后可以重试retryCount次。如果设置了retryOneByOne，则重试的时候放弃批处理，进行逐条重试。
 */
public class RetryBatchConsumer<R> implements IBatchConsumer<R> {
    static final Logger LOG = LoggerFactory.getLogger(RetryBatchConsumer.class);

    private final IBatchConsumer<R> consumer;
    private final IRetryPolicy<IBatchChunkContext> retryPolicy;
    private final boolean retryOneByOne;
    private final boolean singleMode;
    private final IBatchRecordSnapshotBuilder<R> snapshotBuilder;

    public RetryBatchConsumer(IBatchConsumer<R> consumer, IRetryPolicy<IBatchChunkContext> retryPolicy,
                              boolean retryOneByOne, boolean singleMode,
                              IBatchRecordSnapshotBuilder<R> snapshotBuilder) {
        this.consumer = Guard.notNull(consumer, "consumer");
        this.retryPolicy = retryPolicy;
        this.retryOneByOne = retryOneByOne;
        this.singleMode = singleMode;
        this.snapshotBuilder = snapshotBuilder;
    }

    @Override
    public void consume(List<R> items, IBatchChunkContext context) {
        IBatchRecordSnapshotBuilder.ISnapshot<R> snapshot =
                snapshotBuilder == null ? null : snapshotBuilder.buildSnapshot(items, context);
        try {
            if (singleMode) {
                consumeSingle(items, context);
            } else {
                consumer.consume(items, context);
                context.addCompletedItems(items);
            }
        } catch (BatchCancelException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("nop.err.batch.consume-fail", e);

            // 如果没有设置重试策略，则表示不允许重试
            if (retryPolicy == null)
                throw NopException.adapt(e);

            // singleMode情况下有可能部分记录已经被处理，不需要被重试
            if (context.getCompletedItemCount() > 0) {
                items = new ArrayList<>(items);
                items.removeAll(context.getCompletedItems());
            }

            retryConsume(e, items, snapshot, context);
        }
    }

    void consumeSingle(List<R> items, IBatchChunkContext context) {
        context.setSingleMode(true);

        for (R item : items) {
            List<R> single = Collections.singletonList(item);
            consumer.consume(single, context);
            context.addCompletedItem(item);
        }
    }

    void retryConsume(Throwable exception, List<R> items, IBatchRecordSnapshotBuilder.ISnapshot<R> snapshot,
                      IBatchChunkContext context) {
        int retryCount = 0;
        Throwable fatalError = null;

        do {
            context.incRetryCount();

            long delay = retryPolicy.getRetryDelay(exception, retryCount, context);
            if (delay < 0) {
                throw NopException.adapt(exception);
            }

            if (delay > 0) {
                ThreadHelper.sleep(delay);
            }

            try {
                List<R> restoredItems = restoreItems(snapshot, items, context);
                RetryOnceResult result = retryConsumeOnce(retryCount, restoredItems, context);
                if (result == null) {
                    // 返回null表示全部items被成功处理
                    fatalError = null;
                    break;
                }

                // 以最近一次的异常为准。此前的异常在重试过程中可能已经被处理
                if (result.fatalError != null)
                    fatalError = result.fatalError;

                exception = result.retryException;
                items = result.retryItems;
                if (items.isEmpty())
                    break;

            } catch (BatchCancelException e) {
                throw e;
            } catch (Throwable e) {
                exception = e;
                LOG.error("nop.err.batch.retry-consume-fail:retryCount={}", retryCount, e);
            }

            retryCount++;
        } while (true);

        if (fatalError != null)
            throw NopException.adapt(fatalError);
    }

    List<R> restoreItems(IBatchRecordSnapshotBuilder.ISnapshot<R> snapshot, List<R> items, IBatchChunkContext chunkContext) {
        if (snapshot == null)
            return items;
        return snapshot.restore(items, chunkContext);
    }

    class RetryOnceResult {
        Throwable fatalError;
        R fatalItem;
        Throwable retryException;
        List<R> retryItems;
    }

    RetryOnceResult retryConsumeOnce(int retryCount, List<R> items, IBatchChunkContext context) {
        IBatchTaskMetrics metrics = context.getTaskContext().getMetrics();
        if (metrics != null)
            metrics.retry(items.size());

        context.getTaskContext().incRetryItemCount(items.size());

        // 放弃批处理，逐个重试
        if (retryOneByOne) {
            return retryConsumeOneByOne(retryCount, items, context);
        } else {
            try {
                consumer.consume(items, context);
            } catch (BatchCancelException e) {
                throw e;
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
            return null;
        }
    }

    RetryOnceResult retryConsumeOneByOne(int retryCount, List<R> items, IBatchChunkContext context) {
        context.setSingleMode(true);
        List<R> retryItems = new ArrayList<>();

        Throwable retryException = null;
        Throwable fatalError = null;
        R fatalItem = null;

        for (R item : items) {
            List<R> single = Collections.singletonList(item);


            try {
                consumer.consume(single, context);
                context.addCompletedItem(item);
            } catch (BatchCancelException e) {
                throw e;
            } catch (Exception e) {
                LOG.error("nop.err.batch.retry-consume-one-fail:item={},retryCount={}", item, retryCount, e);

                if (retryPolicy.getRetryDelay(e, retryCount + 1, context) >= 0) {
                    // 如果item可重试
                    retryItems.add(item);
                    retryException = e;
                } else {
                    if (fatalError == null) {
                        fatalError = e;
                        fatalItem = item;
                    }
                }
            }
        }

        if (retryException == null && fatalError == null)
            return null;

        RetryOnceResult result = new RetryOnceResult();
        result.fatalError = fatalError;
        result.retryException = retryException;
        result.retryItems = retryItems;
        result.fatalItem = fatalItem;
        return result;
    }
}
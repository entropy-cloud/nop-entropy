/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import io.nop.core.context.IExecutionContext;

import java.util.List;
import java.util.Set;

/**
 * 批处理的一个执行单元。例如100条记录组成一个chunk，一个chunk全部执行完毕之后才提交一次，而不是每处理一条记录就提交一次事务。
 */
public interface IBatchChunkContext extends IExecutionContext {
    <T> List<T> getChunkItems();

    <T> void setChunkItems(List<T> items);

    /**
     * 当多线程执行时，这里对应线程的顺序编号，范围在[0,concurrency)之内。可以用于内部数据分区时的依据
     */
    int getThreadIndex();

    void setThreadIndex(int threadIndex);

    int getConcurrency();

    void setConcurrency(int concurrency);

    IBatchTaskContext getTaskContext();

    <T> Set<T> getCompletedItems();

    default int getCompletedItemCount() {
        Set<?> items = getCompletedItems();
        return items == null ? 0 : items.size();
    }

    <T> void addCompletedItem(T item);

    /**
     * 第一次执行时retryCount=0。重试执行时retryCount从1开始，不断递增
     */
    int getRetryCount();

    void setRetryCount(int retryCount);

    default void incRetryCount() {
        setRetryCount(getRetryCount() + 1);
    }

    /**
     * 是否是重试执行。第一次执行时retrying=false，如果chunk的第一次执行失败，逐条重试的时候retrying为true。
     */
    default boolean isRetrying() {
        return getRetryCount() > 0;
    }

    boolean isSingleMode();

    void setSingleMode(boolean singleMode);
}
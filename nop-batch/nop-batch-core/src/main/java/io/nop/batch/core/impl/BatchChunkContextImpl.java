/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core.impl;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.core.context.ExecutionContextImpl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BatchChunkContextImpl extends ExecutionContextImpl implements IBatchChunkContext {
    private final IBatchTaskContext context;
    private List<?> chunkItems;
    private Set<Object> completedItems;
    private int retryCount;
    private boolean singleMode = true;
    private int threadIndex;
    private int concurrency;

    public BatchChunkContextImpl(IBatchTaskContext context) {
        this.context = context;
    }

    @Override
    public int getThreadIndex() {
        return threadIndex;
    }

    @Override
    public void setThreadIndex(int threadIndex) {
        this.threadIndex = threadIndex;
    }

    @Override
    public int getConcurrency() {
        return concurrency;
    }

    @Override
    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    @Override
    public <T> List<T> getChunkItems() {
        return (List) chunkItems;
    }

    @Override
    public <T> void setChunkItems(List<T> items) {
        chunkItems = items;
    }

    @Override
    public IBatchTaskContext getTaskContext() {
        return context;
    }

    @Override
    public <T> Set<T> getCompletedItems() {
        return (Set<T>) completedItems;
    }

    @Override
    public <T> void addCompletedItem(T item) {
        if (completedItems == null)
            completedItems = new HashSet<>();
        completedItems.add(item);
    }

    @Override
    public int getRetryCount() {
        return retryCount;
    }

    @Override
    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    @Override
    public boolean isSingleMode() {
        return singleMode;
    }

    public void setSingleMode(boolean singleMode) {
        this.singleMode = singleMode;
    }
}
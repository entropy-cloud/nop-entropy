/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.impl;

import io.nop.batch.core.BatchConstants;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.core.context.ExecutionContextImpl;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class BatchChunkContextImpl extends ExecutionContextImpl implements IBatchChunkContext {
    private final IBatchTaskContext context;
    private List<?> chunkItems;
    private Set<Object> completedItems;
    private int retryCount;
    private int loadRetryCount;
    private boolean singleMode = true;
    private int threadIndex;
    private int concurrency;
    private int processCount;

    private CountDownLatch latch;

    public BatchChunkContextImpl(IBatchTaskContext context) {
        super(context.getEvalScope());
        this.context = context;
        this.getEvalScope().setLocalValue(BatchConstants.VAR_BATCH_CHUNK_CTX, this);
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
    public <T> void addCompletedItems(Collection<T> items) {
        if (completedItems == null)
            completedItems = new HashSet<>();
        completedItems.addAll(items);
    }

    @Override
    public int getLoadRetryCount() {
        return loadRetryCount;
    }

    @Override
    public void setLoadRetryCount(int loadRetryCount) {
        this.loadRetryCount = loadRetryCount;
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
    public int getProcessCount() {
        return processCount;
    }

    @Override
    public void setProcessCount(int processCount) {
        this.processCount = processCount;
    }

    @Override
    public boolean isSingleMode() {
        return singleMode;
    }

    public void setSingleMode(boolean singleMode) {
        this.singleMode = singleMode;
    }

    @Override
    public void initChunkLatch(CountDownLatch latch) {
        this.latch = latch;
    }

    public CountDownLatch getChunkLatch() {
        return latch;
    }

    @Override
    public void countDown() {
        latch.countDown();
    }
}
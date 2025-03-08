/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.impl;

import io.nop.api.core.beans.IntRangeBean;
import io.nop.batch.core.BatchConstants;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.IBatchTaskMetrics;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.MapCache;
import io.nop.core.CoreConstants;
import io.nop.core.context.ExecutionContextImpl;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.utils.IVarSet;
import io.nop.core.utils.MapVarSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BatchTaskContextImpl extends ExecutionContextImpl implements IBatchTaskContext {
    static final Logger LOG = LoggerFactory.getLogger(BatchTaskContextImpl.class);

    private final IServiceContext serviceContext;
    private ICache<Object, Object> cache;

    private String taskName;
    private Long taskVersion;
    private String taskId;
    private String taskKey;
    private Map<String, Object> params;
    private IVarSet persistVars = new MapVarSet();
    private IntRangeBean partitionRange;
    private boolean recoverMode;
    private IBatchTaskMetrics metrics;
    private Boolean allowStartIfComplete;
    private int startLimit;
    private String flowStepId;
    private String flowId;

    private final AtomicLong skipItemCount = new AtomicLong();
    private final AtomicLong completeItemCount = new AtomicLong();
    private final AtomicLong processItemCount = new AtomicLong();
    private final AtomicLong retryItemCount = new AtomicLong();
    private final AtomicLong loadRetryCount = new AtomicLong();
    private final AtomicLong loadSkipCount = new AtomicLong();
    private final AtomicLong errorCount = new AtomicLong();

    private volatile long completedIndex;

    private List<Runnable> onTaskBegins;
    private List<Consumer<Throwable>> onTaskEnds;
    private final List<Consumer<IBatchChunkContext>> onChunkBegins = new CopyOnWriteArrayList<>();
    private final List<Consumer<IBatchChunkContext>> onBeforeChunkEnds = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<IBatchChunkContext, Throwable>> onChunkEnds = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<Collection<?>, IBatchChunkContext>> onChunkTryBegin = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<IBatchChunkContext, Throwable>> onChunkTryEnd = new CopyOnWriteArrayList<>();

    private final List<BiConsumer<Integer, IBatchChunkContext>> onLoadBegins = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<IBatchChunkContext, Throwable>> onLoadEnds = new CopyOnWriteArrayList<>();

    private final List<BiConsumer<Collection<?>, IBatchChunkContext>> onConsumeBegin = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<IBatchChunkContext, Throwable>> onConsumeEnd = new CopyOnWriteArrayList<>();

    public BatchTaskContextImpl(IServiceContext svcCtx, IEvalScope scope) {
        super(scope);
        this.serviceContext = svcCtx;
        getEvalScope().setLocalValue(CoreConstants.VAR_SVC_CTX, svcCtx);
        getEvalScope().setLocalValue(BatchConstants.VAR_BATCH_TASK_CTX, this);
    }

    public BatchTaskContextImpl(IServiceContext svcCtx) {
        this(svcCtx, svcCtx == null ? null : svcCtx.getEvalScope());
    }

    public BatchTaskContextImpl() {
        this(null);
    }

    @Override
    public IServiceContext getServiceContext() {
        return serviceContext;
    }

    @Override
    public synchronized ICache<Object, Object> getCache() {
        ICache<Object, Object> cache = this.cache;
        if (cache == null) {
            cache = serviceContext == null ? null : serviceContext.getCache();
            if (cache == null)
                cache = new MapCache<>("batch-task-cache", true);
            this.cache = cache;
        }
        return cache;
    }

    @Override
    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    @Override
    public Long getTaskVersion() {
        return taskVersion;
    }

    @Override
    public void setTaskVersion(Long taskVersion) {
        this.taskVersion = taskVersion;
    }

    @Override
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    @Override
    public String getTaskKey() {
        return taskKey;
    }

    @Override
    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    @Override
    public String getFlowStepId() {
        return flowStepId;
    }

    @Override
    public void setFlowStepId(String flowStepId) {
        this.flowStepId = flowStepId;
    }

    @Override
    public String getFlowId() {
        return flowId;
    }

    @Override
    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    @Override
    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    @Override
    public IVarSet getPersistVars() {
        return persistVars;
    }

    public void setPersistVars(IVarSet vars) {
        this.persistVars = vars;
    }

    @Override
    public IntRangeBean getPartitionRange() {
        return partitionRange;
    }

    @Override
    public void setPartitionRange(IntRangeBean partitionRange) {
        this.partitionRange = partitionRange;
    }

    @Override
    public boolean isRecoverMode() {
        return recoverMode;
    }

    @Override
    public void setRecoverMode(boolean recoverMode) {
        this.recoverMode = recoverMode;
    }

    @Override
    public IBatchChunkContext newChunkContext() {
        return new BatchChunkContextImpl(this);
    }

    @Override
    public IBatchTaskMetrics getMetrics() {
        return metrics;
    }

    @Override
    public void setMetrics(IBatchTaskMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public Boolean getAllowStartIfComplete() {
        return allowStartIfComplete;
    }

    @Override
    public void setAllowStartIfComplete(Boolean allowStartIfComplete) {
        this.allowStartIfComplete = allowStartIfComplete;
    }

    @Override
    public int getStartLimit() {
        return startLimit;
    }

    @Override
    public void setStartLimit(int startLimit) {
        this.startLimit = startLimit;
    }

    @Override
    public long getSkipItemCount() {
        return skipItemCount.get();
    }

    @Override
    public void setSkipItemCount(long count) {
        this.skipItemCount.set(count);
    }

    @Override
    public void incSkipItemCount(int count) {
        skipItemCount.addAndGet(count);
    }

    @Override
    public long getCompletedIndex() {
        return completedIndex;
    }

    @Override
    public void setCompletedIndex(long completedIndex) {
        this.completedIndex = completedIndex;
    }

    @Override
    public long getCompleteItemCount() {
        return completeItemCount.get();
    }

    @Override
    public void setCompleteItemCount(long count) {
        completeItemCount.set(count);
    }

    @Override
    public void incCompleteItemCount(int count) {
        completeItemCount.addAndGet(count);
    }

    @Override
    public long getProcessItemCount() {
        return processItemCount.get();
    }

    @Override
    public void setProcessItemCount(long processCount) {
        processItemCount.set(processCount);
    }

    @Override
    public void incProcessItemCount(int count) {
        processItemCount.addAndGet(count);
    }

    @Override
    public long getErrorCount() {
        return errorCount.get();
    }

    @Override
    public void setErrorCount(long errorCount) {
        this.errorCount.set(errorCount);
    }

    @Override
    public void incErrorCount(int count) {
        this.errorCount.addAndGet(count);
    }

    @Override
    public long getRetryItemCount() {
        return retryItemCount.get();
    }

    @Override
    public void incRetryItemCount(int count) {
        retryItemCount.addAndGet(count);
    }

    @Override
    public void setRetryItemCount(long count) {
        retryItemCount.set(count);
    }

    @Override
    public void onTaskBegin(Runnable action) {
        synchronized (this) {
            if (isDone()) {
                throw new IllegalStateException("nop.err.execution-already-completed");
            }

            if (this.onTaskBegins == null)
                this.onTaskBegins = new ArrayList<>();
            onTaskBegins.add(action);
        }
    }

    @Override
    public void onChunkBegin(Consumer<IBatchChunkContext> action) {
        synchronized (this) {
            if (isDone()) {
                throw new IllegalStateException("nop.err.execution-already-completed");
            }
            onChunkBegins.add(action);
        }
    }

    @Override
    public void onBeforeChunkEnd(Consumer<IBatchChunkContext> action) {
        synchronized (this) {
            if (isDone()) {
                throw new IllegalStateException("nop.err.execution-already-completed");
            }
            onBeforeChunkEnds.add(action);
        }
    }


    @Override
    public void onChunkEnd(BiConsumer<IBatchChunkContext, Throwable> action) {
        synchronized (this) {
            if (isDone()) {
                throw new IllegalStateException("nop.err.execution-already-completed");
            }
            onChunkEnds.add(action);
        }
    }

    @Override
    public void onChunkTryBegin(BiConsumer<Collection<?>, IBatchChunkContext> action) {
        synchronized (this) {
            if (isDone()) {
                throw new IllegalStateException("nop.err.execution-already-completed");
            }
            onChunkTryBegin.add(action);
        }
    }

    @Override
    public void onChunkTryEnd(BiConsumer<IBatchChunkContext, Throwable> action) {
        synchronized (this) {
            if (isDone()) {
                throw new IllegalStateException("nop.err.execution-already-completed");
            }
            onChunkTryEnd.add(action);
        }
    }

    @Override
    public void onConsumeBegin(BiConsumer<Collection<?>, IBatchChunkContext> action) {
        synchronized (this) {
            if (isDone()) {
                throw new IllegalStateException("nop.err.execution-already-completed");
            }
            onConsumeBegin.add(action);
        }
    }

    @Override
    public void onConsumeEnd(BiConsumer<IBatchChunkContext, Throwable> action) {
        synchronized (this) {
            if (isDone()) {
                throw new IllegalStateException("nop.err.execution-already-completed");
            }
            onChunkTryEnd.add(action);
        }
    }

    @Override
    public void onLoadBegin(BiConsumer<Integer, IBatchChunkContext> action) {
        synchronized (this) {
            if (isDone()) {
                throw new IllegalStateException("nop.err.execution-already-completed");
            }
            onLoadBegins.add(action);
        }
    }

    @Override
    public void onLoadEnd(BiConsumer<IBatchChunkContext, Throwable> action) {
        synchronized (this) {
            if (isDone()) {
                throw new IllegalStateException("nop.err.execution-already-completed");
            }
            onLoadEnds.add(action);
        }
    }

    @Override
    public void fireLoadBegin(int batchSize, IBatchChunkContext chunkContext) {
        List<BiConsumer<Integer, IBatchChunkContext>> callbacks = this.onLoadBegins;

        if (callbacks != null) {
            for (BiConsumer<Integer, IBatchChunkContext> callback : callbacks) {
                callback.accept(batchSize, chunkContext);
            }
        }
    }

    @Override
    public void fireLoadEnd(IBatchChunkContext chunkContext, Throwable err) {
        List<BiConsumer<IBatchChunkContext, Throwable>> callbacks = this.onLoadEnds;

        if (callbacks != null) {
            for (BiConsumer<IBatchChunkContext, Throwable> callback : callbacks) {
                callback.accept(chunkContext, err);
            }
        }
    }

    @Override
    public void fireTaskBegin() {
        List<Runnable> callbacks;
        synchronized (this) {
            callbacks = this.onTaskBegins;
            if (callbacks != null)
                this.onTaskBegins = null;
        }
        if (callbacks != null) {
            for (Runnable callback : callbacks) {
                callback.run();
            }
        }
    }

    @Override
    public void fireBeforeChunkEnd(IBatchChunkContext chunkContext) {
        List<Consumer<IBatchChunkContext>> callbacks = this.onBeforeChunkEnds;

        if (callbacks != null) {
            for (Consumer<IBatchChunkContext> callback : callbacks) {
                callback.accept(chunkContext);
            }
        }
    }

    @Override
    public void fireChunkBegin(IBatchChunkContext chunkContext) {
        List<Consumer<IBatchChunkContext>> callbacks = this.onChunkBegins;

        if (callbacks != null) {
            for (Consumer<IBatchChunkContext> callback : callbacks) {
                callback.accept(chunkContext);
            }
        }
    }

    @Override
    public void fireChunkEnd(IBatchChunkContext chunkContext, Throwable err) {
        List<BiConsumer<IBatchChunkContext, Throwable>> callbacks = this.onChunkEnds;
        if (callbacks != null) {
            for (BiConsumer<IBatchChunkContext, Throwable> callback : callbacks) {
                try {
                    callback.accept(chunkContext, err);
                } catch (Exception e) {
                    LOG.error("nop.err.core.execution-after-chunk-end-callback-fail", e);
                }
            }
        }
    }

    @Override
    public void fireChunkTryBegin(Collection<?> items, IBatchChunkContext chunkContext) {
        List<BiConsumer<Collection<?>, IBatchChunkContext>> callbacks = this.onChunkTryBegin;
        if (callbacks != null) {
            for (BiConsumer<Collection<?>, IBatchChunkContext> callback : callbacks) {
                callback.accept(items, chunkContext);
            }
        }
    }

    @Override
    public void fireChunkTryEnd(IBatchChunkContext chunkContext, Throwable err) {
        List<BiConsumer<IBatchChunkContext, Throwable>> callbacks = this.onChunkTryEnd;
        if (callbacks != null) {
            for (BiConsumer<IBatchChunkContext, Throwable> callback : callbacks) {
                callback.accept(chunkContext, err);
            }
        }
    }


    @Override
    public void fireConsumeBegin(Collection<?> items, IBatchChunkContext chunkContext) {
        List<BiConsumer<Collection<?>, IBatchChunkContext>> callbacks = this.onConsumeBegin;
        if (callbacks != null) {
            for (BiConsumer<Collection<?>, IBatchChunkContext> callback : callbacks) {
                callback.accept(items, chunkContext);
            }
        }
    }

    @Override
    public void fireConsumeEnd(IBatchChunkContext chunkContext, Throwable err) {
        List<BiConsumer<IBatchChunkContext, Throwable>> callbacks = this.onConsumeEnd;
        if (callbacks != null) {
            for (BiConsumer<IBatchChunkContext, Throwable> callback : callbacks) {
                callback.accept(chunkContext, err);
            }
        }
    }
}
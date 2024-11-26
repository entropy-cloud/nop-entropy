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
import io.nop.core.context.ExecutionContextImpl;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.utils.IVarSet;
import io.nop.core.utils.MapVarSet;
import io.nop.xlang.api.XLang;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BatchTaskContextImpl extends ExecutionContextImpl implements IBatchTaskContext {
    static final Logger LOG = LoggerFactory.getLogger(BatchTaskContextImpl.class);

    private final IServiceContext serviceContext;

    private String taskName;
    private Long taskVersion;
    private String taskId;
    private String taskKey;
    private Map<String, Object> params;
    private IVarSet persistVars = new MapVarSet();
    private IntRangeBean partition;
    private boolean recoverMode;
    private IBatchTaskMetrics metrics;

    private final AtomicLong skipItemCount = new AtomicLong();
    private final AtomicLong completeItemCount = new AtomicLong();
    private final AtomicLong processItemCount = new AtomicLong();
    private volatile long completedIndex;

    private List<Runnable> onTaskBegins;
    private List<Consumer<Throwable>> onTaskEnds;
    private final List<Consumer<IBatchChunkContext>> onChunkBegins = new CopyOnWriteArrayList<>();
    private final List<Consumer<IBatchChunkContext>> onBeforeChunkEnds = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<IBatchChunkContext, Throwable>> onChunkEnds = new CopyOnWriteArrayList<>();

    public BatchTaskContextImpl(IServiceContext svcCtx, IEvalScope scope) {
        super(scope);
        this.serviceContext = svcCtx;
        getEvalScope().setLocalValue(BatchConstants.VAR_BATCH_TASK_CTX, this);
    }

    public BatchTaskContextImpl(IServiceContext svcCtx) {
        this(svcCtx, svcCtx == null ? XLang.newEvalScope() : svcCtx.getEvalScope());
    }

    public BatchTaskContextImpl() {
        this(null);
    }

    @Override
    public IServiceContext getServiceContext() {
        return serviceContext;
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
    public IntRangeBean getPartition() {
        return partition;
    }

    @Override
    public void setPartition(IntRangeBean partition) {
        this.partition = partition;
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
    public void fireChunkEnd(Throwable err, IBatchChunkContext chunkContext) {
        List<BiConsumer<IBatchChunkContext, Throwable>> callbacks = this.onChunkEnds;
        if (callbacks != null) {
            for (BiConsumer<IBatchChunkContext, Throwable> callback : callbacks) {
                try {
                    callback.accept(chunkContext, err);
                } catch (Exception e) {
                    LOG.error("nop.err.core.execution-after-complete-callback-fail", e);
                }
            }
        }
    }
}
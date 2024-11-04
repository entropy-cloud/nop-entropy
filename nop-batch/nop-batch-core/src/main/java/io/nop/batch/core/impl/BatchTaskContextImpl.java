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

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class BatchTaskContextImpl extends ExecutionContextImpl implements IBatchTaskContext {
    private final IServiceContext serviceContext;

    private String taskName;
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
}

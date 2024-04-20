/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.impl;

import io.nop.api.core.beans.IntRangeBean;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.IBatchTaskMetrics;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class BatchTaskContextImpl extends ServiceContextImpl implements IBatchTaskContext {
    private String taskName;
    private String taskId;
    private Map<String, Object> params;
    private Map<String, Object> persistVars = new ConcurrentHashMap<>();
    private IntRangeBean partition;
    private boolean recoverMode;
    private IBatchTaskMetrics metrics;
    private final AtomicLong skipItemCount = new AtomicLong();

    public BatchTaskContextImpl() {
    }

    public BatchTaskContextImpl(IServiceContext svcCtx) {

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
    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    @Override
    public Map<String, Object> getPersistVars() {
        return persistVars;
    }

    @Override
    public void setPersistVar(String name, Object value) {
        persistVars.put(name, value);
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
    public void incSkipItemCount(int count) {
        skipItemCount.addAndGet(count);
    }
}

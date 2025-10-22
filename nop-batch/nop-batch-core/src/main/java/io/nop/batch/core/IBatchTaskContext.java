/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import io.nop.api.core.beans.IntRangeBean;
import io.nop.commons.cache.ICache;
import io.nop.core.context.IExecutionContext;
import io.nop.core.context.IServiceContext;
import io.nop.core.utils.IVarSet;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 一个批处理任务对应一个loader + processor + consumer的组合调用
 */
public interface IBatchTaskContext extends IExecutionContext {
    IServiceContext getServiceContext();

    ICache<Object, Object> getCache();

    String getTaskName();

    void setTaskName(String taskName);

    Long getTaskVersion();

    void setTaskVersion(Long taskVersion);

    String getTaskId();

    void setTaskId(String taskId);

    String getTaskKey();

    void setTaskKey(String taskKey);

    String getFlowStepId();

    void setFlowStepId(String flowStepId);

    String getFlowId();

    void setFlowId(String flowId);

    /**
     * 外部传入的只读参数，在任务执行过程中不会被修改
     */
    Map<String, Object> getParams();

    void setParams(Map<String, Object> params);

    default void addParam(String name, Object value) {
        Map<String, Object> params = getParams();
        if (params == null) {
            params = new HashMap<>();
            setParams(params);
        }
        params.put(name, value);
    }

    default Object getParam(String name) {
        Map<String, Object> params = getParams();
        return params == null ? null : params.get(name);
    }

    /**
     * 持久化的状态变量。当批处理任务失败后重试时可以读取上次处理状态
     */
    IVarSet getPersistVars();

    void setPersistVars(IVarSet vars);

    default Object getPersistVar(String name) {
        IVarSet vars = getPersistVars();
        if (vars == null)
            return null;
        return vars.getVar(name);
    }

    default void setPersistVar(String name, Object value) {
        IVarSet vars = getPersistVars();
        vars.setVar(name, value);
    }

    /**
     * 本次任务处理所涉及到的数据分区
     */
    IntRangeBean getPartitionRange();

    void setPartitionRange(IntRangeBean partitionRange);

    boolean isRecoverMode();

    void setRecoverMode(boolean recoverMode);

    IBatchChunkContext newChunkContext();

    IBatchTaskMetrics getMetrics();

    void setMetrics(IBatchTaskMetrics metrics);

    Boolean getAllowStartIfComplete();

    void setAllowStartIfComplete(Boolean allowStartIfComplete);

    int getStartLimit();

    void setStartLimit(int startLimit);

    /**
     * 处理过程中因为出错跳过的记录条目数
     */
    long getSkipItemCount();

    void incSkipItemCount(int count);

    void setSkipItemCount(long count);

    long getRetryItemCount();

    void incRetryItemCount(int count);

    void setRetryItemCount(long count);

    long getCompleteItemCount();

    void setCompleteItemCount(long count);

    void incCompleteItemCount(int count);

    long getCompletedIndex();

    void setCompletedIndex(long index);

    long getProcessItemCount();

    void setProcessItemCount(long processCount);

    void incProcessItemCount(int count);

    long getErrorCount();

    void setErrorCount(long errorCount);

    void incErrorCount(int count);

    long getInsertCount();

    void setInsertCount(long insertCount);

    long getUpdateCount();

    void setUpdateCount(long updateCount);

    long getDeleteCount();

    void setDeleteCount(long deleteCount);

    void incInsertCount(int count);

    void incUpdateCount(int count);

    void incDeleteCount(int count);

    void onTaskBegin(Runnable action);

    void onChunkBegin(Consumer<IBatchChunkContext> action);

    void onBeforeChunkEnd(Consumer<IBatchChunkContext> action);

    void onChunkEnd(BiConsumer<IBatchChunkContext, Throwable> action);

    void onChunkTryBegin(BiConsumer<Collection<?>, IBatchChunkContext> action);

    void onChunkTryEnd(BiConsumer<IBatchChunkContext, Throwable> action);

    void onLoadBegin(BiConsumer<Integer, IBatchChunkContext> action);

    void onLoadEnd(BiConsumer<IBatchChunkContext, Throwable> action);

    void onConsumeBegin(BiConsumer<Collection<?>, IBatchChunkContext> action);

    void onConsumeEnd(BiConsumer<IBatchChunkContext, Throwable> action);


    void fireLoadBegin(int batchSize, IBatchChunkContext chunkContext);

    void fireLoadEnd(IBatchChunkContext chunkContext, Throwable err);

    void fireTaskBegin();

    void fireChunkBegin(IBatchChunkContext chunkContext);

    void fireBeforeChunkEnd(IBatchChunkContext chunkContext);

    void fireChunkEnd(IBatchChunkContext chunkContext, Throwable err);

    void fireChunkTryBegin(Collection<?> items, IBatchChunkContext chunkContext);

    void fireChunkTryEnd(IBatchChunkContext chunkContext, Throwable err);

    void fireConsumeBegin(Collection<?> items, IBatchChunkContext chunkContext);

    void fireConsumeEnd(IBatchChunkContext chunkContext, Throwable err);
}
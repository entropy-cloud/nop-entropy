/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.core.context.IEvalContext;
import io.nop.core.context.IServiceContext;
import io.nop.task.metrics.ITaskFlowMetrics;
import jakarta.annotation.Nonnull;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * <p>1. Task表达单次请求处理过程。request保存请求对象，而response保存结果对象。</p>
 * <p>2. 一个Task包含多个TaskStep。Task可能发起子Task。</p>
 * <p>3. 多个TaskInstance属于同一个jobInstance。</p>
 * <p>
 * 4. TaskContext为多个步骤并发运行时所共享的上下文对象。
 * <p>
 * 5. attributes保存不需要持久化的临时变量，taskVars保存需要持久化的Task级别的状态变量。
 */
public interface ITaskRuntime extends IEvalContext {
    IServiceContext getSvcCtx();

    IContext getContext();

    default String getLocale() {
        IServiceContext ctx = getSvcCtx();
        if (ctx != null) {
            return ctx.getContext().getLocale();
        }
        return ContextProvider.currentLocale();
    }

    default boolean isCancelled() {
        if (getSvcCtx() == null)
            return false;

        return getSvcCtx().isCancelled();
    }

    default void appendOnCancel(Consumer<String> task) {
        if (getSvcCtx() == null)
            return;

        getSvcCtx().appendOnCancel(task);
    }

    default void removeOnCancel(Consumer<String> task) {
        if (getSvcCtx() == null)
            return;
        getSvcCtx().removeOnCancel(task);
    }

    ITaskState getTaskState();

    ITaskFlowMetrics getMetrics();

    /**
     * 分配一个新的runId
     */
    int newRunId();

    default String getTaskName() {
        return getTaskState().getTaskName();
    }

    default long getTaskVersion() {
        return getTaskState().getTaskVersion();
    }

    default String getTaskInstanceId() {
        return getTaskState().getTaskInstanceId();
    }

    default String getJobInstanceId() {
        return getTaskState().getJobInstanceId();
    }

    ITaskRuntime newChildRuntime(ITask task, boolean saveState);

    ITaskFlowManager getTaskManager();

    default Object getTaskVar(String name) {
        Map<String, Object> vars = getTaskVars();
        return vars == null ? null : vars.get(name);
    }

    default boolean hasTaskVar(String name) {
        Map<String, Object> vars = getTaskVars();
        return vars == null ? false : vars.containsKey(name);
    }

    default void setTaskVar(String name, Object value) {
        getTaskState().setTaskVar(name, value);
    }

    default Map<String, Object> getTaskVars() {
        return getTaskState().getTaskVars();
    }

    @Nonnull
    Set<String> getEnabledFlags();

    void setEnabledFlags(Set<String> enabledFlags);

    Object getAttribute(String name);

    void setAttribute(String name, Object value);

    void removeAttribute(String name);

    Set<String> getAttributeKeys();

    Object computeAttributeIfAbsent(String name, Function<String, Object> action);

    IScheduledExecutor getScheduledExecutor();

    ITaskStepRuntime newMainStepRuntime();
}
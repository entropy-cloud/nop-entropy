/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.task;

import io.nop.core.context.IServiceContext;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Map;

/**
 * <p>1. Task表达单次请求处理过程。request保存请求对象，而response保存结果对象。</p>
 * <p>2. 一个Task包含多个TaskStep。Task可能发起子Task。</p>
 * <p>3. 多个TaskInstance属于同一个jobInstance。</p>
 * <p>
 * 4. TaskContext为多个步骤并发运行时所共享的上下文对象。
 * <p>
 * 5. attributes保存不需要持久化的临时变量，taskVars保存需要持久化的Task级别的状态变量。
 */
@ThreadSafe
public interface ITaskContext extends IServiceContext {

    ITaskState getTaskState();

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

    ITaskContext newChildContext(String taskName, long taskVersion);

    ITaskStateStore getStateStore();

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

    void saveState(ITaskStepState state);

    ITaskStepState loadState(String stepId, String runId);
}
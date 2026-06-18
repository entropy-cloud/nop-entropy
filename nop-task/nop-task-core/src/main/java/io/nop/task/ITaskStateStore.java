/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;

public interface ITaskStateStore {
    boolean isSupportPersist();

    ITaskStepState newMainStepState(ITaskState taskState);

    /**
     * plan 263: 按持久化状态恢复 mainStep envelope 状态（对称 {@link #newMainStepState} 创建路径与
     * {@link #loadStepState} 子 step 加载路径）。
     *
     * <p>resume（recoverMode=true）路径下，{@code TaskRuntimeImpl.newMainStepRuntime} 优先调用本方法尝试加载
     * 已持久化的 mainStep 状态（stepPath={@link TaskConstants#MAIN_STEP_NAME}=`@main`，含 composite mainStep 执行中
     * 经 {@code saveState} 持久化的 {@code bodyStepIndex} 控制流位置）；命中则用之使 composite mainStep 从中间位置续跑，
     * 未命中（无持久化行 / 非持久化 store）返回 null 使调用方回退 {@link #newMainStepState}（fresh ACTIVE，零回归）。
     *
     * <p>默认返回 null（向后兼容：in-memory / snapshot 等非持久化 store 继承默认即恒回退 fresh，
     * 与既有 {@code loadStepState} 默认行为对称）。持久化 store（如 {@code DaoTaskStateStore}）override 本方法。
     */
    default ITaskStepState loadMainStepState(ITaskState taskState, ITaskRuntime taskRt) {
        return null;
    }

    ITaskStepState newStepState(ITaskStepState parentState, String stepName, String stepType, ITaskRuntime taskRt);

    ITaskStepState loadStepState(ITaskStepState parentState, String stepName, String stepType, ITaskRuntime taskRt);

    void saveStepState(ITaskStepRuntime stepRt);

    ITaskState newTaskState(String taskName, long taskVersion, ITaskRuntime taskRt);

    ITaskState loadTaskState(String taskInstanceId, ITaskRuntime taskRt);

    void saveTaskState(ITaskRuntime taskRt);
}
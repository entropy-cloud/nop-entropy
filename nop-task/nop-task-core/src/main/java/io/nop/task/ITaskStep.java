/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;

import io.nop.api.core.util.ICancelToken;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.core.type.IGenericType;
import io.nop.task.model.ITaskInputModel;
import io.nop.task.model.ITaskOutputModel;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.Set;

/**
 * 相当于是一种函数定义，支持多输入和多输出。
 */
public interface ITaskStep extends ISourceLocationGetter {
    /**
     * 步骤类型
     */
    String getStepType();

    Set<String> getPersistVars();

    /**
     * 步骤执行所需要的输入变量
     */
    List<? extends ITaskInputModel> getInputs();

    /**
     * 除了返回值之外其他可选的输出变量
     */
    List<? extends ITaskOutputModel> getOutputs();

    /**
     * 直接通过返回值返回的数据类型
     */
    IGenericType getReturnType();

    /**
     * 具体的执行动作
     *
     * @param stepState   步骤执行过程中所有内部状态都保存到stepState中，基于它可以实现断点重启
     * @param outputNames 指定要选择性的生成哪些输出变量。生成的结果值保存在stepState.evalScope中
     * @param cancelToken 用于取消执行的消息接口，它是线程安全的
     * @param taskRt      整个任务执行的上下文对象
     * @return 可以返回同步或者异步对象，并动态决定下一个执行步骤。如果返回的结果值是CompletionStage，则外部调用者会自动等待异步执行完毕，
     * 在此过程中可以通过cancelToken来取消异步执行。
     */
    @Nonnull
    TaskStepResult execute(ITaskStepState stepState, Set<String> outputNames,
                           ICancelToken cancelToken, ITaskRuntime taskRt);
}
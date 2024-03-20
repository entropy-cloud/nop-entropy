/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;

import io.nop.api.core.util.ISourceLocationGetter;
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
     * 步骤执行会返回Map，这里对应Map中的数据类型
     */
    List<? extends ITaskOutputModel> getOutputs();

    /**
     * 具体的执行动作
     *
     * @param stepRt 步骤执行过程中所有内部状态都保存到stepState中，基于它可以实现断点重启
     * @return 可以返回同步或者异步对象，并动态决定下一个执行步骤。如果返回的结果值是CompletionStage，则外部调用者会自动等待异步执行完毕，
     * 在此过程中可以通过cancelToken来取消异步执行。
     */
    @Nonnull
    TaskStepResult execute(ITaskStepRuntime stepRt);
}
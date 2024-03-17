/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;

/**
 * TaskStep包含了状态恢复和条件判断等通用逻辑，ITaskStepExecution提供专属于该步骤的逻辑
 */
public interface ITaskStepExecution {

    default void prepareState(ITaskStep step, ITaskStepState state, ITaskRuntime taskRt) {

    }

    /**
     * 可以通过返回值指定下一步骤ID。步骤ID只能是同级步骤，或者系统内置的步骤ID
     */
    TaskStepResult execute(ITaskStep step, ITaskStepState state,
                           ITaskRuntime taskRt);
}
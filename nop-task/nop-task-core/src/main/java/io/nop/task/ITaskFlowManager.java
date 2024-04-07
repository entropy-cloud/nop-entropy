/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;


import io.nop.core.context.IServiceContext;

public interface ITaskFlowManager {

    ITaskRuntime newTaskRuntime(ITask task, boolean saveState, IServiceContext svcCtx);

    ITaskRuntime getTaskRuntime(String taskInstanceId, IServiceContext svcCtx);

    ITask getTask(String taskName, long taskVersion);

    ITaskStepLib getTaskStepLib(String libName, long libVersion);
}
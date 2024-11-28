/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;


import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.task.model.TaskFlowModel;

public interface ITaskFlowManager {

    ITaskRuntime newTaskRuntime(ITask task, boolean saveState, IServiceContext svcCtx, IEvalScope scope);

    ITaskRuntime getTaskRuntime(String taskInstanceId, IServiceContext svcCtx, IEvalScope scope);

    default ITaskRuntime newTaskRuntime(ITask task, boolean saveState, IServiceContext svcCtx) {
        return newTaskRuntime(task, saveState, svcCtx, null);
    }

    default ITaskRuntime getTaskRuntime(String taskInstanceId, IServiceContext svcCtx) {
        return getTaskRuntime(taskInstanceId, svcCtx, null);
    }

    ITask getTask(String taskName, long taskVersion);

    ITask loadTask(IResource resource);

    default ITask loadTaskFromPath(String path) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        return loadTask(resource);
    }

    ITaskStepLib getTaskStepLib(String libName, long libVersion);

    TaskFlowModel getTaskFlowModel(String taskName, long taskVersion);
}
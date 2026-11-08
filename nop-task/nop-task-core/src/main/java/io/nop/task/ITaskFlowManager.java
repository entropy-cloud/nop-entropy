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

    ITask parseTask(IResource resource);

    ITask loadTaskFromPath(String path);

    ITaskStepLib getTaskStepLib(String libName, long libVersion);

    ITaskStepLib loadTaskStepLibFromPath(String path);

    TaskFlowModel getTaskFlowModel(String taskName, long taskVersion);

    /**
     * W-13（CRA 8.49 R4-09，1.5-C7-014 裁定 B）：执行级 fencing——同一 taskInstanceId 的在途
     * 执行互斥。返回 true=claim 成功；false=已有在途执行（并发第二者须 typed 快速失败）。
     *
     * <p>进程内在途注册表形态：注册表生命周期=管理实例生命周期，无跨重启残留面。
     */
    boolean tryBeginExecution(String taskInstanceId);

    /**
     * W-13（裁定 B）：释放执行 fence。必须 Throwable-safe 调用（try/finally 或
     * catch(Throwable) release+rethrow），否则同步 Error 逃逸会使 fence 进程内永久泄漏。
     */
    void endExecution(String taskInstanceId);
}

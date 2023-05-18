/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.core.monitor;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.api.core.util.ProcessResult;

import java.util.function.Consumer;

public interface IRpcTaskStatusStore {
    void fetchActiveTasks(Consumer<RpcTask> processor);

    void saveTask(RpcTask task);

    /**
     * 保存任务状态
     */
    ProcessResult saveTaskStatus(RpcTask task, ApiResponse<TaskStatusBean> status, Throwable exception);
}
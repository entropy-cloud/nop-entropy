/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.api;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.task.TaskStatusBean;

import java.util.concurrent.CompletionStage;

/**
 * 向管理端主动汇报当前任务的执行状态
 */
public interface IRpcStatusReporter {
    CompletionStage<Void> reportStatusAsync(ApiRequest<TaskStatusBean> request);
}
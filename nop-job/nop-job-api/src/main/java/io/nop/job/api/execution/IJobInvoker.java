/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.api.execution;

import java.util.concurrent.CompletionStage;

/**
 * Job的每次触发时刻都调用IJobInvoker来执行单次调用
 */
public interface IJobInvoker {

    /**
     * job的单次运行。具体invoker可以是执行命令行脚本、调用远程服务或者调用内存中的bean等，Job调度框架本身对此没有任何假定。
     *
     * @param jobCtx 包含jobName, jobParams等参数
     * @return 异步执行的返回结果
     */
    CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx);

    /**
     * 取消当前正在执行的运行实例
     *
     * @param jobCtx 包含jobInstanceId等参数
     */
    CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx);
}
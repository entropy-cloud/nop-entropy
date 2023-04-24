/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.job.api;

import io.nop.api.core.util.ICancelToken;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Job的每次触发时刻都调用IJobInvoker来执行单次调用。 此接口支持异常恢复机制。当任务执行过程中异常中断时（例如调度器失效或者执行器宕机），数据库中的持久化状态记录会保持EXECUTING状态。
 * 调度器重新调度该任务时，则会执行recover过程，此时传入的triggerState的recoverMode属性设置为true。
 */
public interface IJobInvoker {

    /**
     * job的单次运行。具体invoker可以是执行命令行脚本、调用远程服务或者调用内存中的bean等，Job调度框架本身对此没有任何假定。
     *
     * @param jobName     job的唯一标识
     * @param jobParams   传递给job的参数，需要支持JSON序列化。
     * @param state       当前调度器的状态。invoker内部可以利用state的attribute来保存一些在多次调用之间共享的扩展信息。
     * @param cancelToken 通过cancelToken传递任务是否已经被取消的信息
     * @return 异步执行的返回结果
     */
    CompletionStage<TriggerFireResult> invokeAsync(String jobName, Map<String, Object> jobParams, ITriggerState state,
                                                   ICancelToken cancelToken);
}
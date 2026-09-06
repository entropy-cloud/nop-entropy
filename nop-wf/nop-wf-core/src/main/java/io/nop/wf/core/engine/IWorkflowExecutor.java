/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core.engine;

import io.nop.core.context.IServiceContext;
import io.nop.wf.api.WfReference;
import io.nop.wf.core.IWorkflow;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * 可以控制针对每个工作流实例的动作按顺序执行
 * <p>
 * 并发语义：缺省实现 {@link DefaultWorkflowExecutor} 对同一 wfId 的并发调用不做互斥——每次调用
 * 各自构造独立的 WorkflowImpl 内存状态（steps 缓存、命令队列等），写竞争由 ORM 层
 * nop_wf_instance/nop_wf_step_instance 的乐观锁（versionProp）在提交时拦截，冲突方以乐观锁异常收场。
 * 若业务需要同一实例严格串行，可提供按 wfId 分段的锁实现替换本接口的 bean。
 */
public interface IWorkflowExecutor {
    <T> CompletionStage<T> execute(WfReference wfRef, IServiceContext ctx,
                                   Function<IWorkflow, T> task);
}
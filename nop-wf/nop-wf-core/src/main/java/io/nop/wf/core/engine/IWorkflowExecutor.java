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
 */
public interface IWorkflowExecutor {
    <T> CompletionStage<T> execute(WfReference wfRef, IServiceContext ctx,
                                   Function<IWorkflow, T> task);
}
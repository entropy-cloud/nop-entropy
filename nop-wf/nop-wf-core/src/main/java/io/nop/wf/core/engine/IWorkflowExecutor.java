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
                                   Function<IWorkflow, CompletionStage<T>> task);
}
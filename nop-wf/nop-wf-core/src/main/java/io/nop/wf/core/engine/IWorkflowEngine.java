/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.engine;

import io.nop.core.context.IServiceContext;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.core.WorkflowTransitionTarget;
import io.nop.wf.core.impl.IWorkflowImplementor;
import io.nop.wf.core.impl.IWorkflowStepImplementor;
import io.nop.wf.core.model.IWorkflowActionModel;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IWorkflowEngine {
    IWfActor resolveActor(String actorType, String actorId, String deptId);

    IWfActor resolveUser(String userId);

    IWfActor getManager(IWfActor actor, int upLevel);

    IWfActor getDeptManager(IWfActor actor, int upLevel);

    void logError(IWorkflowImplementor wf, String stepName, String actionName, Throwable e);

    void save(IWorkflowImplementor wf, IServiceContext ctx);

    boolean isAllowStart(IWorkflowImplementor wf, IServiceContext ctx);

    void start(IWorkflowImplementor wf, Map<String, Object> args, IServiceContext ctx);

    void suspend(IWorkflowImplementor wf, Map<String, Object> args, IServiceContext ctx);

    void resume(IWorkflowImplementor wf, Map<String, Object> args, IServiceContext ctx);

    void remove(IWorkflowImplementor wf, Map<String, Object> args, IServiceContext ctx);

    void kill(IWorkflowImplementor wf, Map<String, Object> args, IServiceContext ctx);

    void turnSignalOn(IWorkflowImplementor wf, Set<String> signals, IServiceContext ctx);

    void turnSignalOff(IWorkflowImplementor wf, Set<String> signals, IServiceContext ctx);

    boolean runAutoTransitions(IWorkflowImplementor wf, IServiceContext ctx);

    void changeActor(IWorkflowStepImplementor step, IWfActor actor, IServiceContext ctx);

    void changeOwner(IWorkflowStepImplementor step, String ownerId, IServiceContext ctx);

    void triggerStepEvent(IWorkflowStepImplementor step, String eventName, IServiceContext ctx);

    void killStep(IWorkflowStepImplementor step, Map<String, Object> args, IServiceContext ctx);

    boolean triggerTransition(IWorkflowStepImplementor step, Map<String, Object> args, IServiceContext ctx);

    void notifySubFlowEnd(IWorkflowStepImplementor step, int status, Map<String, Object> args, IServiceContext ctx);

    List<? extends IWorkflowActionModel> getAllowedActions(IWorkflowStepImplementor step, IServiceContext ctx);

    Object invokeAction(IWorkflowStepImplementor step,
                        String actionName, Map<String, Object> args, IServiceContext ctx);

    List<WorkflowTransitionTarget> getTransitionTargetsForAction(IWorkflowStepImplementor step,
                                                                 String actionName, IServiceContext ctx);

    void transitTo(IWorkflowStepImplementor step, String stepName, Map<String, Object> args, IServiceContext ctx);

    List<? extends IWorkflowStepImplementor> getJoinWaitSteps(IWorkflowStepImplementor step, IWfRuntime wfRt);
}
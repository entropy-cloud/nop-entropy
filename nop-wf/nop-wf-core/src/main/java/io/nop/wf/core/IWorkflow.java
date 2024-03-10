/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core;

import io.nop.core.context.IServiceContext;
import io.nop.wf.api.WfReference;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.core.model.IWorkflowModel;
import io.nop.wf.core.model.IWorkflowStepModel;
import io.nop.wf.core.store.IWorkflowRecord;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作流对外暴露的核心接口。客户程序与工作流引擎的交互全部通过此接口进行
 */
public interface IWorkflow {

    /**
     * 获取工作流模型对象
     */
    IWorkflowModel getModel();

    IWorkflowRecord getRecord();

    String getWfId();

    default String getWfName() {
        return getRecord().getWfName();
    }

    default Long getWfVersion() {
        return getRecord().getWfVersion();
    }

    default int getWfStatus() {
        return getRecord().getStatus();
    }

    default WfReference getWfReference() {
        return new WfReference(getWfName(), getWfVersion(), getWfId());
    }

    default boolean isStarted() {
        return getWfStatus() > NopWfCoreConstants.WF_STATUS_CREATED;
    }

    default boolean isEnded() {
        return getWfStatus() >= NopWfCoreConstants.WF_STATUS_HISTORY_BOUND;
    }

    default boolean isActivated() {
        return getWfStatus() >= NopWfCoreConstants.WF_STATUS_ACTIVATED
                && getWfStatus() < NopWfCoreConstants.WF_STATUS_COMPLETED;
    }

    default boolean isSuspended() {
        return getWfStatus() == NopWfCoreConstants.WF_STATUS_SUSPENDED;
    }

    /**
     * 根据步骤实例id获取到步骤对象
     *
     * @param stepId 步骤实例id
     * @return 工作流步骤对象
     */
    IWorkflowStep getStepById(String stepId);

    /**
     * 根据步骤定义id获取到最近一次步骤执行对应的实例对象
     */
    IWorkflowStep getLatestStepByName(String stepName);

    default IWorkflowStep getLatestStartStep() {
        IWorkflowStepModel startStepModel = getModel().getStartStep();
        return getLatestStepByName(startStepModel.getName());
    }

    IWfActor getManagerActor();

    IWfActor getStarter();

    /**
     * 获取指定步骤的历史步骤列表
     *
     * @param stepName 指定工作流步骤定义id
     * @return 按照事件顺序从前到后排列的步骤列表
     */
    List<? extends IWorkflowStep> getStepsByName(String stepName, boolean includeHistory);

    default List<? extends IWorkflowStep> getStepsByName(String stepName) {
        return getStepsByName(stepName, true);
    }

    List<? extends IWorkflowStep> getActivatedSteps();

    List<? extends IWorkflowStep> getWaitingSteps();

    List<? extends IWorkflowStep> getSteps(boolean includeHistory);

    /**
     * 重新从数据库中加载
     */
    void reload();

    /**
     * 获取跳转到指定步骤时的actor设置
     *
     * @param stepName 准备跳转到此步骤
     * @param ctx      服务上下文
     * @return 目标步骤的actor设置
     */
    WorkflowTransitionTarget getJumpToTarget(String stepName, IServiceContext ctx);

    String getBizObjName();

    String getBizKey();

    String getBizEntityId();

    Object getBizEntity();

    IWorkflowVarSet getGlobalVars();

    IWorkflowVarSet getOutputVars();

    /**
     * 如果工作流尚未启动，调用save会保存工作流实例（状态为CREATED），以后可以通过start方法来启动流程。
     * 如果工作流实例已经启动，则工作流引擎在步骤迁移时会自动调用save，一般不需要手工调用save操作。
     */
    void save(IServiceContext ctx);

    boolean isAllowStart(IServiceContext ctx);

    void start(Map<String, Object> args, IServiceContext ctx);

    /**
     * 挂起工作流实例，会自动挂起所有正在执行的工作流步骤实例（状态为ACTIVATED）
     *
     * @param ctx 服务上下文
     */
    void suspend(Map<String, Object> args, IServiceContext ctx);

    /**
     * 从挂起的状态恢复执行，会自动把状态为SUSPENDED的步骤实例的状态修改为ACTIVATED
     */
    void resume(Map<String, Object> args, IServiceContext ctx);

    /**
     * 删除流程实例，包括所有的activity以及action历史
     *
     * @param ctx 服务上下文
     */
    void remove(Map<String, Object> args, IServiceContext ctx);

    /**
     * 强制终止工作流实例，并终止所有未结束的工作流步骤
     */
    void kill(Map<String, Object> args, IServiceContext ctx);

    void turnSignalOn(Set<String> signals, IServiceContext ctx);

    void turnSignalOff(Set<String> signals, IServiceContext ctx);

    /**
     * 执行active步骤的transition转换
     *
     * @return 如果有步骤执行了转换，则返回true。如果返回了true，一般需要继续调用runAutoTransitions，直到返回false为止
     */
    boolean runAutoTransitions(IServiceContext ctx);

    boolean isSignalOn(String signal);

    boolean isAllSignalOn(Set<String> signals);

    Set<String> getOnSignals();

    IWfActor resolveActor(String actorType, String actorId, String actorDeptId);

    IWfActor resolveUser(String userId);

    IWfActor getManager(IWfActor actor, int upLevel);

    IWfActor getDeptManager(IWfActor actor, int upLevel);
}
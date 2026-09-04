/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.service;

import io.nop.api.core.context.ContextProvider;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.wf.api.actor.WfActorAndOwner;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.engine.WorkflowEngineImpl;
import io.nop.wf.core.impl.WorkflowManagerImpl;
import io.nop.wf.core.model.WfModel;
import io.nop.wf.core.model.WfStepModel;
import io.nop.wf.service.mock.MockWfActorResolver;
import io.nop.wf.service.mock.MockWorkflowStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工作流引擎核心缺陷的回归测试：joinGroup持久化、source重复执行、signal提前激活、
 * 显式rejectSteps、模型分析器出边清空等
 */
public class TestWorkflowEngineRegression extends BaseTestCase {

    private static final AtomicInteger SOURCE_RUN_COUNT = new AtomicInteger();

    public static void incSourceRunCount() {
        SOURCE_RUN_COUNT.incrementAndGet();
    }

    WorkflowManagerImpl workflowManager;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        workflowManager = new WorkflowManagerImpl();

        WorkflowEngineImpl engine = new WorkflowEngineImpl();
        engine.setWfActorResolver(new MockWfActorResolver());
        workflowManager.setWorkflowEngine(engine);
        MockWorkflowStore store = new MockWorkflowStore();
        workflowManager.setWorkflowStore(store);

        workflowManager.init();
        ContextProvider.getOrCreateContext().setUserId("1");
    }

    /**
     * 配置了join-group-expr的join步骤：两条入边必须合并到同一个join步骤实例。
     * 此前joinGroup从未持久化到步骤记录，getNextJoinStepRecord按计算值与存储值(null)比较永远不匹配，
     * 每条入边都新建join实例，join后的步骤被执行N次
     */
    @Test
    public void testJoinGroupExprMergesToSingleInstance() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/joinGroupExpr", 1L);
        workflow.start(null, context);
        IWorkflowStep startStep = workflow.getLatestStartStep();
        startStep.invokeAction("sh", null, context);

        List<? extends IWorkflowStep> activeSteps = workflow.getActivatedSteps();
        assertEquals(2, activeSteps.size());
        IWorkflowStep ysh1 = null;
        IWorkflowStep ysh2 = null;
        for (IWorkflowStep step : activeSteps) {
            if ("ysh1".equals(step.getStepName())) {
                ysh1 = step;
            } else if ("ysh2".equals(step.getStepName())) {
                ysh2 = step;
            }
        }
        assertNotNull(ysh1);
        assertNotNull(ysh2);

        ysh1.invokeAction("sp1", null, context);
        workflow.runAutoTransitions(context);

        // 第一条入边创建join实例，且joinGroup计算值必须持久化到记录上
        List<? extends IWorkflowStep> joinSteps = workflow.getStepsByName("join");
        assertEquals(1, joinSteps.size());
        assertEquals("g1", joinSteps.get(0).getRecord().getJoinGroup());

        ysh2.invokeAction("sp2", null, context);
        workflow.runAutoTransitions(context);

        // 第二条入边复用已存在的join实例而不是新建
        assertEquals(1, workflow.getStepsByName("join").size());
        assertTrue(workflow.isEnded());
    }

    /**
     * 迁移条件不满足而停留在EXECUTED状态的步骤，source不允许在后续自动迁移轮次中被重复执行。
     * 此前条件为 status <= EXECUTED，EXECUTED步骤每轮都重新执行source（重复副作用）
     */
    @Test
    public void testSourceNotReexecutedWhenTransitionBlocked() {
        SOURCE_RUN_COUNT.set(0);

        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/sourceBlocked", 1L);
        workflow.start(null, context);
        IWorkflowStep startStep = workflow.getLatestStartStep();
        startStep.invokeAction("sh", null, context);

        // 多轮自动迁移，onAppStates永不满足，source只能执行一次
        workflow.runAutoTransitions(context);
        workflow.runAutoTransitions(context);
        workflow.runAutoTransitions(context);

        assertEquals(1, SOURCE_RUN_COUNT.get());

        IWorkflowStep autoStep = workflow.getLatestStepByName("auto-step");
        assertEquals(NopWfCoreConstants.WF_STEP_STATUS_EXECUTED, autoStep.getRecord().getStatus());
        assertFalse(workflow.isEnded());
    }

    /**
     * SEQ_GROUP（串签）中处于WAITING的成员步骤不允许被无关的signal事件提前激活，
     * 顺序推进只由ExecGroupSupport.activateNextSeqStep驱动。
     * 此前任意signalWf调用会把所有WAITING步骤激活，串签退化为并行
     */
    @Test
    public void testSignalDoesNotActivateSeqGroupWaitingSteps() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/execGroupSeq", 1L);
        workflow.start(null, context);
        IWorkflowStep startStep = workflow.getLatestStartStep();
        startStep.invokeAction("sh", null, context);

        assertEquals(1, workflow.getActivatedSteps().size());
        assertEquals(2, workflow.getWaitingSteps().size());

        // 与串签步骤完全无关的信号
        workflow.turnSignalOn(Set.of("unrelated-signal"), context);

        assertEquals(1, workflow.getActivatedSteps().size(),
                "seq-group waiting steps should not be activated by unrelated signals");
        assertEquals(2, workflow.getWaitingSteps().size());
    }

    /**
     * 转办后进入WAITING的原步骤（exitCurrentStep=false）不允许被无关signal事件激活，
     * 否则会出现新旧办理人同时办理。此类步骤moveStepToWaiting时已记录finishTime
     */
    @Test
    public void testSignalDoesNotActivateTransferWaitingStep() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/reject", 1L);
        workflow.start(null, context);
        IWorkflowStep startStep = workflow.getLatestStartStep();
        startStep.invokeAction("sh", null, context);

        context.getContext().setUserId("2");
        IWorkflowStep ysh = workflow.getLatestStepByName("ysh");
        assertNotNull(ysh);

        WfActorAndOwner actorAndOwner = new WfActorAndOwner();
        actorAndOwner.setActorId("3");
        actorAndOwner.setActorType("user");
        IWorkflowStep nextStep = ysh.transferToActor(actorAndOwner, false, context);
        assertTrue(ysh.isWaiting());

        workflow.turnSignalOn(Set.of("unrelated-signal"), context);

        assertTrue(ysh.isWaiting(), "transferred-from step should stay waiting");
        List<? extends IWorkflowStep> activated = workflow.getActivatedSteps();
        assertEquals(1, activated.size());
        assertEquals(nextStep.getStepId(), activated.get(0).getStepId());
    }

    /**
     * 显式指定rejectSteps（按步骤名）的驳回：驳回目标按步骤名解析并重建该步骤。
     * 此前实现把同一个字符串既当【步骤实例ID】查找、又当【模型名】做祖先校验，
     * 两种口径不可能同时满足，任何输入下都抛ERR_WF_UNKNOWN_STEP
     */
    @Test
    public void testRejectToExplicitStepName() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/reject", 1L);
        workflow.start(null, context);
        IWorkflowStep startStep = workflow.getLatestStartStep();
        startStep.invokeAction("sh", null, context);

        context.getContext().setUserId("2");
        IWorkflowStep ysh = workflow.getLatestStepByName("ysh");
        ysh.invokeAction("sp", null, context);

        IWorkflowStep ysp = workflow.getLatestStepByName("ysp");
        assertNotNull(ysp);

        Map<String, Object> args = new HashMap<>();
        args.put(NopWfCoreConstants.VAR_REJECT_STEPS, "wf-start");
        ysp.invokeAction("_rejectAction", args, context);

        List<? extends IWorkflowStep> activeSteps = workflow.getSteps(false);
        assertEquals(1, activeSteps.size());
        assertEquals("wf-start", activeSteps.get(0).getStepName());
    }

    /**
     * 模型分析器不得清空起始步骤的出边信息。
     * 此前initTransitionFromSteps中对无入边步骤误写setTransitionToSteps(emptyList)，
     * 清掉了buildDag已经填好的transitionToSteps
     */
    @Test
    public void testStartStepTransitionToStepsNotCleared() {
        IWorkflow workflow = workflowManager.newWorkflow("test/reject", 1L);
        WfModel model = (WfModel) workflow.getModel();

        WfStepModel startModel = model.getStep("wf-start");
        assertNotNull(startModel.getTransitionToSteps());
        assertFalse(startModel.getTransitionToSteps().isEmpty(),
                "start step's outgoing transitions must not be cleared by analyzer");
        assertNotNull(startModel.getTransitionFromSteps());
        assertTrue(startModel.getTransitionFromSteps().isEmpty());
    }
}

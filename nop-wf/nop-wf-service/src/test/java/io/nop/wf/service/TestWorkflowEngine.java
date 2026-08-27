/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.service;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.WorkflowTransitionTarget;
import io.nop.wf.core.engine.WorkflowEngineImpl;
import io.nop.wf.core.impl.WorkflowManagerImpl;
import io.nop.wf.core.model.IWorkflowActionModel;
import io.nop.wf.core.model.IWorkflowModel;
import io.nop.wf.core.model.IWorkflowStepModel;
import io.nop.wf.core.model.WfStepModel;
import io.nop.wf.core.store.IWorkflowRecord;
import io.nop.wf.core.store.beans.WorkflowStepRecordBean;
import io.nop.wf.service.mock.MockWfActorResolver;
import io.nop.wf.service.mock.MockWorkflowStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_TRANSITION_TARGET_STEPS_NOT_MATCH;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_USER_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestWorkflowEngine extends BaseTestCase {
    WorkflowManagerImpl workflowManager;
    //MockWorkflowStore store;

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
     * 创建工作流时不指定wfVersion，此时会使用最新的版本
     */
    @Test
    public void testEmptyVersion() {
        IWorkflow workflow = workflowManager.newWorkflow("test/testParser", null);
        assertNotNull(workflow);
    }

    @Test
    public void testWorkflowInvoke() {
        IServiceContext context = new ServiceContextImpl();

        IWorkflow workflow = workflowManager.newWorkflow("test/testBasic", 1L);
        workflow.start(null, context);

        assertTrue(workflow.isStarted());
        assertTrue(!workflow.isEnded());

        assertNotNull(workflow.getWfId());

        IWorkflowStep step = workflow.getStepsByName("wf-start").get(0);
        assertEquals("user", step.getActor().getActorType());
        assertEquals("1", step.getActor().getActorId());

        List<WorkflowTransitionTarget> targets = step.getTransitionTargetsForAction("action0", context);
        assertEquals(1, targets.size());
        assertNotNull(targets.get(0).getStepName());
        assertNotNull(targets.get(0).getStepDisplayName());
        step.invokeAction("action0", null, context);

        workflow.runAutoTransitions(context);

        assertTrue(workflow.isEnded());
        assertTrue(workflow.isStarted());
        assertFalse(workflow.isSuspended());
        assertFalse(workflow.isActivated());
    }

    @Test
    public void testWorkflowState() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/testBasic", 1L);

        IWorkflowRecord wfRecord = workflow.getRecord();
        String bizEntityId = "1";
        wfRecord.setBizObjId(bizEntityId);
        wfRecord.setBizObjName(NopAuthUser.class.getName());
        Map<String, Object> vars = new HashMap<>();

        workflow.start(vars, context);
        IWorkflowStep step = workflow.getStepsByName("wf-start").get(0);
        invokeAction(step, "action0", null, null, null, context);
        assertTrue(workflow.runAutoTransitions(context));

        assertTrue(workflow.isEnded());
        assertEquals(NopWfCoreConstants.WF_STEP_STATUS_COMPLETED, workflow.getWfStatus());
    }

    @Test
    public void testJoin() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/join", 1L);
        workflow.start(null, context);
        IWorkflowStep startStep = workflow.getLatestStartStep();
        startStep.invokeAction("sh", null, context);

        List<? extends IWorkflowStep> activeSteps = workflow.getActivatedSteps();
        assertEquals(2, activeSteps.size());
        IWorkflowStep ysh1 = null;
        IWorkflowStep ysh2 = null;
        for (IWorkflowStep activeStep : activeSteps) {
            if (activeStep.getStepName().equals("ysh1")) {
                ysh1 = activeStep;
            } else {
                ysh2 = activeStep;
            }
        }

        try {
            ysh1.invokeAction("sp2", null, context);
            assertTrue(false);
        } catch (NopException e) {
            assertEquals(ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP.getErrorCode(), e.getErrorCode());
        }

        ysh1.invokeAction("sp1", null, context);
        workflow.runAutoTransitions(context);
        assertFalse(workflow.isEnded());

        try {
            ysh1.invokeAction("sp1", null, context);
            assertTrue(false);
        } catch (NopException e) {
            assertEquals(ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS.getErrorCode(), e.getErrorCode());
        }

        ysh2.invokeAction("sp2", null, context);
        assertTrue(workflow.runAutoTransitions(context));

        assertTrue(workflow.isEnded());
    }

    void invokeAction(IWorkflowStep step, String actionId, String toStepId, String actorType, String actorId,
                      IServiceContext context) {
        Map<String, Object> args = new HashMap<>();
        if (actorType != null && toStepId != null) {
            Map<String, Object> stepActors = new HashMap<>();
            stepActors.put(toStepId, newActor(actorType, actorId));
            args.put(NopWfCoreConstants.VAR_SELECTED_STEP_ACTORS, stepActors);
        }

        if (toStepId != null) {
            args.put(NopWfCoreConstants.VAR_TARGET_STEPS, toStepId);
        }

        step.invokeAction(actionId, args, context);
    }

    List<Map<String, Object>> newActor(String actorType, String actorId) {
        Map<String, Object> actor = new HashMap<>();
        actor.put("actorType", actorType);
        actor.put("actorId", actorId);
        return Collections.singletonList(actor);
    }

    /**
     * 会签：会生成多个步骤，且该步骤的后续执行为单个步骤,\.
     * 会签生成：
     * 1. join步骤改为普通步骤
     * wf-start->sh->join->sp->  hq->    ysp->end
     * user,1        user,1,2    user,2   user,1
     */
    @Test
    public void testCosign() {
        IServiceContext context = new ServiceContextImpl();
        String userId = context.getContext().getUserId();

        IWorkflow workflow = workflowManager.newWorkflow("test/cosign", 1L);
        workflow.start(null, context);
        assertEquals(userId, workflow.getRecord().getStarterId());
        IWorkflowStep startStep = workflow.getLatestStartStep();
        invokeAction(startStep, "sh", null, null, null, context);
        List<? extends IWorkflowStep> activeSteps = workflow.getActivatedSteps();
        IWorkflowStep step1 = null, step2 = null;
        for (IWorkflowStep step : activeSteps) {
            if ("1".equals(step.getRecord().getActorId())) {
                step1 = step;
            } else {
                step2 = step;
            }
        }
        assertEquals(2, activeSteps.size());
        invokeAction(step1, "sp", null, null, null, context);
        activeSteps = workflow.getWaitingSteps();
        assertEquals(1, activeSteps.size());
        IWorkflowStep genHqStep = activeSteps.get(0);

        assertEquals(NopWfCoreConstants.WF_STEP_STATUS_WAITING, genHqStep.getRecord().getStatus());

        context.getContext().setUserId("2");
        invokeAction(step2, "sp", null, null, null, context);
        List<? extends IWorkflowStep> steps = workflow.getStepsByName("join_join_");
        assertEquals(1, steps.size());
        workflow.runAutoTransitions(context);
        assertTrue(workflow.isEnded());
    }

    /**
     * 会签：会生成多个步骤，且该步骤的后续执行为单个步骤,\.
     * 会签生成：
     * 1. join步骤改为普通步骤
     * wf-start->sh->join->sp->  hq->    ysp->end
     * user,1        user,1,2    user,2   user,1
     */
    @Test
    public void testCosign1() {
        IServiceContext context = new ServiceContextImpl();
        String userId = context.getContext().getUserId();
        IWorkflow workflow = workflowManager.newWorkflow("test/cosign1", 1L);
        workflow.start(null, context);
        assertEquals(userId, workflow.getRecord().getStarterId());
        IWorkflowStep startStep = workflow.getLatestStartStep();
        invokeAction(startStep, "sh", null, null, null, context);
        List<? extends IWorkflowStep> activeSteps = workflow.getActivatedSteps();
        IWorkflowStep step1 = null, step2 = null;
        for (IWorkflowStep step : activeSteps) {
            if ("1".equals(step.getRecord().getActorId())) {
                step1 = step;
            } else {
                step2 = step;
            }
        }
        assertEquals(2, activeSteps.size());
        invokeAction(step1, "sp", null, "user", "1", context);
        activeSteps = workflow.getWaitingSteps();
        assertEquals(1, activeSteps.size());
        IWorkflowStep genHqStep = activeSteps.get(0);

        assertEquals(NopWfCoreConstants.WF_STEP_STATUS_WAITING, genHqStep.getRecord().getStatus());

        context.getContext().setUserId("2");
        invokeAction(step2, "sp", null, "user", "1", context);
        List<? extends IWorkflowStep> steps = workflow.getStepsByName("join_join_");
        assertEquals(1, steps.size());
        workflow.runAutoTransitions(context);
        workflow.runAutoTransitions(context);
        assertTrue(workflow.isEnded());
    }


    @Test
    public void testSimpleJoin() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/simpleJoin", 1L);
        workflow.start(null, context);
        IWorkflowStep startStep = workflow.getLatestStartStep();
        invokeAction(startStep, "sh", null, null, null, context);
        List<? extends IWorkflowStep> yshs = workflow.getSteps(false);
        IWorkflowStep ysh1 = null, ysh2 = null;
        for (IWorkflowStep ysh : yshs) {
            if ("1".equals(ysh.getRecord().getActorId())) {
                ysh1 = ysh;
            } else {
                ysh2 = ysh;
            }
        }


        invokeAction(ysh1, "sp", "ysp", "user", "1", context);
        IWorkflowStep ysp = workflow.getLatestStepByName("ysp");
        assertEquals(NopWfCoreConstants.WF_STEP_STATUS_WAITING, ysp.getRecord().getStatus());
        context.getContext().setUserId("2");
        invokeAction(ysh2, "sp", "ysp", "user", "2", context);

        assertTrue(workflow.getLatestStepByName("ysp").isActivated());
        assertTrue(!workflow.isEnded());
    }

    @Test
    public void testFlow() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/flow", 1L);
        workflow.start(null, context);
        assertTrue(workflow.runAutoTransitions(context));

        IWorkflowStep step = workflow.getWaitingSteps().get(0);
        assertTrue(step.isFlowType());
        assertNotNull(step.getRecord().getSubWfName());

        assertNotNull(step.getRecord().getSubWfId());
        IWorkflow subFlow = workflowManager.getWorkflow(step.getRecord().getSubWfId());

        IWorkflowStep subStartStep = subFlow.getActivatedSteps().get(0);

//        subFlow.start(XLang.newEvalScope());
        subStartStep.invokeAction("action0", null, context);

        subFlow.runAutoTransitions(context);

        assertTrue(subFlow.isEnded());

        workflow.runAutoTransitions(context);

        assertTrue(workflow.isEnded());
    }

    @Test
    public void testCurrentStep() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/testBasic", 1L);
        workflow.start(null, context);
        IWorkflowStep step = workflow.getLatestStartStep();
        //OrmWorkflowRecordStore recordStore = null;
        List<? extends IWorkflowStep> activeSteps = workflow.getSteps(false);
        IWorkflowStep startStep = activeSteps.get(0);
        invokeAction(startStep, "action0", null, null, null, context);
        assertTrue(workflow.runAutoTransitions(context));
        ;
        assertTrue(workflow.isEnded());
    }

    @Test
    public void testInvokableActions() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/join", 1L);
        workflow.start(null, context);
        IWorkflowStep step = workflow.getStepsByName("wf-start").get(0);

        List<? extends IWorkflowActionModel> actions = step.getAllowedActions(context);
    }

    @Test
    public void testMultiTransition() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/multiTransition", 1L);
        workflow.start(null, context);
        while (workflow.runAutoTransitions(context)) ;

        assertTrue(workflow.isEnded());
    }

    @Test
    public void testTransitionTarget() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/testActor", 1L);
        workflow.start(null, context);
        IWorkflowStep step = workflow.getLatestStartStep();
        List<? extends IWorkflowStep> activeSteps = workflow.getSteps(false);
        IWorkflowStep startStep = activeSteps.get(0);
        List<WorkflowTransitionTarget> list = startStep.getTransitionTargetsForAction("sp", context);
        WorkflowTransitionTarget target = list.get(0);
        target.getStepName();
        assertEquals(1, list.size());
    }

    @Test
    public void testActor() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/testActor", 1L);
        workflow.start(null, context);
        IWorkflowStep step = workflow.getLatestStartStep();
        List<? extends IWorkflowStep> activeSteps = workflow.getSteps(false);
        IWorkflowStep startStep = activeSteps.get(0);
        invokeAction(startStep, "sp", "wf-end", "role", "admin", context);
        assertTrue(workflow.isEnded());
    }

    @Test
    public void testNoAction() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/noaction", 1L);
        workflow.start(null, context);
        assertTrue(workflow.runAutoTransitions(context));
        assertTrue(workflow.runAutoTransitions(context));
        assertTrue(workflow.isEnded());

    }

    @Test
    public void testSplitTypeAnd() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/split_and", 1L);
        workflow.start(null, context);
        IWorkflowStep startStep = workflow.getLatestStepByName("wf-start");

        List<WorkflowTransitionTarget> targets = startStep.getTransitionTargetsForAction("action0", context);

        invokeAction(startStep, "action0", null, null, null, context);
        assertTrue(workflow.runAutoTransitions(context));

        assertTrue(workflow.isEnded());
    }

    @Test
    public void testSplitTypeOr() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/split_or", 1L);
        workflow.start(null, context);
        IWorkflowStep step = workflow.getLatestStepByName("wf-start");

        List<WorkflowTransitionTarget> targets = step.getTransitionTargetsForAction("action0", context);

        invokeAction(step, "action0", "end0", "user", "2", context);

        workflow.runAutoTransitions(context);
        assertTrue(workflow.isEnded());
    }

    @Test
    public void testReject() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/reject", 1L);
        workflow.start(null, context);
        IWorkflowStep step = workflow.getLatestStepByName("wf-start");

        invokeAction(step, "sh", null, null, null, context);

        IWorkflowStep ysh = workflow.getLatestStepByName("ysh");
        assertNotNull(ysh);
        List<? extends IWorkflowActionModel> actions = ysh.getAllowedActions(context);
        assertEquals(2, actions.size());
        IWorkflowActionModel rejectAction = null;
        for (IWorkflowActionModel action : actions) {
            if (action.getName().equals("_rejectAction")) {
                rejectAction = action;
            }
        }
        assertNotNull(rejectAction);

        context.getContext().setUserId("2");
        ysh.invokeAction("_rejectAction", null, context);

        List<? extends IWorkflowStep> activeSteps = workflow.getSteps(false);
        assertEquals(1, activeSteps.size());
        assertEquals("wf-start", activeSteps.get(0).getStepName());
    }

    @Test
    public void testWithdraw() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/withdraw", 1L);
        workflow.start(null, context);
        IWorkflowStep startStep = workflow.getLatestStartStep();
        assertTrue(startStep.getModel().isAllowWithdraw());
        invokeAction(startStep, "sh", null, null, null, context);
        IWorkflowStep ysh = workflow.getLatestStepByName("ysh");
        assertNotNull(ysh);
        IWorkflowStep prevStep = ysh.getPrevSteps().get(0);
        assertEquals("wf-start", prevStep.getStepName());
        IWorkflowActionModel withdrawAction = prevStep.getAllowedActions(context).get(0);
        invokeAction(prevStep, withdrawAction.getName(), null, null, null, context);
        assertEquals(1, workflow.getActivatedSteps().size());
        assertNotNull(workflow.getActivatedSteps().get(0).getActor().getActorId());
    }

    @Test
    public void testDynamicActor() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/dynamicActor", null);
        workflow.start(null, context);
        IWorkflowStep startStep = workflow.getLatestStepByName("wf-start");
        invokeAction(startStep, "sh", null, null, null, context);
        workflow.runAutoTransitions(context);

        IWorkflowStep endStep = workflow.getLatestStepByName("wf-end");
        assertEquals("1", endStep.getActor().getActorId());
        assertEquals("user", endStep.getActor().getActorType());
        assertTrue(workflow.isEnded());
    }

    /**
     * 空步骤
     */
    @Test
    public void testEmptyStep() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/emptyStep", null);
        workflow.start(null, context);
        assertTrue(workflow.runAutoTransitions(context));

        List<? extends IWorkflowStep> steps = workflow.getActivatedSteps();
        assertEquals(2, steps.size());
        IWorkflowStep cyStep = null, startStep = null;
        for (IWorkflowStep step : steps) {
            if (step.getStepName().equals("cyStart")) {
                cyStep = step;
            } else {
                startStep = step;
            }
        }
        invokeAction(cyStep, "cy", null, "test/user", "1", context);
        assertTrue(!workflow.isEnded());
        invokeAction(startStep, "sh", null, "user", "1", context);
        assertTrue(workflow.runAutoTransitions(context));
        assertTrue(workflow.isEnded());
    }

    /**
     * 循环步骤
     * start -> mainStart(step) -> sh -> ysh(step)->end(step)
     * -> cyStart(step) -> cysh -> kcy(step) -> cy -> kcy(step)...
     */
    @Test
    public void testLoop() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/loop", null);
        workflow.start(null, context);
        workflow.runAutoTransitions(context);

        IWorkflowStep mainStart = null, cyStart = null;
        for (IWorkflowStep step : workflow.getActivatedSteps()) {
            if (step.getStepName().equals("mainStart")) {
                mainStart = step;
            } else {
                cyStart = step;
            }
        }
        invokeAction(mainStart, "sh", null, "user", "1", context);
        workflow.runAutoTransitions(context);

        assertTrue(workflow.isEnded());
        assertTrue(!cyStart.isActivated());
    }

    @Test
    public void testToAssign() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/assign", null);
        workflow.start(null, context);
        List<? extends IWorkflowStep> steps = workflow.getActivatedSteps();
        assertEquals(1, steps.size());
        IWorkflowStep step0 = steps.get(0);
        invokeAction(step0, "action0", "step5", "user", "1", context);
        assertTrue(workflow.runAutoTransitions(context));
        assertTrue(workflow.isEnded());
    }

    @Test
    public void testToAssignAnd() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/assignAnd", null);
        workflow.start(null, context);
        IWorkflowStep startStep = workflow.getActivatedSteps().get(0);
        invokeAction(startStep, "sh", "ysp", "user", "1", context);
        workflow.runAutoTransitions(context);

        IWorkflowStep ysp = workflow.getLatestStepByName("ysp");
        invokeAction(ysp, "end", null, null, null, context);
        assertTrue(workflow.isEnded());
    }

    /**
     * 通过to-assign转换动态跳转步骤
     */
    @Test
    public void testToAssign1() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/assign", 1L);
        workflow.start(null, context);
        List<? extends IWorkflowStep> steps = workflow.getActivatedSteps();
        assertEquals(1, steps.size());
        IWorkflowStep step0 = steps.get(0);
        assertEquals("1", step0.getActor().getActorId());
        invokeAction(step0, "action0", "step0", "user", "1", context);
        steps = workflow.getActivatedSteps();
        assertEquals(1, steps.size());
        invokeAction(steps.get(0), "action0", "step3", "user", "1", context);
        steps = workflow.getActivatedSteps();
        assertEquals(1, steps.size());
        invokeAction(steps.get(0), "action3", "step0", "user", "1", context);
        steps = workflow.getActivatedSteps();
        assertEquals(1, steps.size());
        invokeAction(steps.get(0), "action0", "step5", "user", "1", context);
        workflow.runAutoTransitions(context);
        assertTrue(workflow.isEnded());
    }

    @Test
    public void testStartStepNoAssignment() {
        IServiceContext context = new ServiceContextImpl();

        IWorkflow workflow = workflowManager.newWorkflow("test/startNoAssign", null);
        workflow.start(null, context);
    }

    /**
     * 指定目标步骤的驳回：rejectSteps 参数语义是步骤名。
     * 回归 check2 P1：doReject 曾用步骤名调用按 stepId 查询的 getStepById，
     * 指定目标步骤的驳回必然抛 ERR_WF_STEP_INSTANCE_NOT_EXISTS。
     */
    @Test
    public void testRejectToSpecifiedStepByName() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/reject", 1L);
        workflow.start(null, context);
        IWorkflowStep step = workflow.getLatestStepByName("wf-start");
        invokeAction(step, "sh", null, null, null, context);

        IWorkflowStep ysh = workflow.getLatestStepByName("ysh");
        assertNotNull(ysh);

        context.getContext().setUserId("2");
        Map<String, Object> args = new HashMap<>();
        args.put(NopWfCoreConstants.VAR_REJECT_STEPS, "wf-start");
        ysh.invokeAction("_rejectAction", args, context);

        List<? extends IWorkflowStep> activeSteps = workflow.getSteps(false);
        assertEquals(1, activeSteps.size());
        assertEquals("wf-start", activeSteps.get(0).getStepName());
    }

    /**
     * 回归 check2 P1：to-assigned 动作未传/传空 targetSteps 时不得静默完成当前步骤，
     * 必须显式报错，避免流程被意外自动结束。
     */
    @Test
    public void testToAssignedWithoutTargetStepsRejected() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/assign", null);
        workflow.start(null, context);
        IWorkflowStep step0 = workflow.getActivatedSteps().get(0);

        NopException e = assertThrows(NopException.class,
                () -> step0.invokeAction("action0", null, context));
        assertEquals(ERR_WF_TRANSITION_TARGET_STEPS_NOT_MATCH.getErrorCode(), e.getErrorCode());
    }

    /**
     * 回归 check2 P1：joinGroupExpr 分组汇聚。按上游步骤实例的 actorId 分组时，
     * 不同分组应产生不同的 join 步骤实例，且实例的 joinGroup 持久化分组值。
     */
    @Test
    public void testJoinGroupExprSeparatesJoinInstances() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/joinGroup", 1L);
        workflow.start(null, context);
        IWorkflowStep startStep = workflow.getLatestStartStep();
        invokeAction(startStep, "sh", null, null, null, context);

        List<? extends IWorkflowStep> workSteps = workflow.getStepsByName("work", false);
        assertEquals(2, workSteps.size());
        IWorkflowStep work2 = null, work3 = null;
        for (IWorkflowStep s : workSteps) {
            if ("2".equals(s.getRecord().getActorId())) {
                work2 = s;
            } else {
                work3 = s;
            }
        }
        assertNotNull(work2);
        assertNotNull(work3);

        context.getContext().setUserId("2");
        invokeAction(work2, "sp", null, null, null, context);
        context.getContext().setUserId("3");
        invokeAction(work3, "sp", null, null, null, context);

        List<? extends IWorkflowStep> joinSteps = workflow.getStepsByName("join", true);
        assertEquals(2, joinSteps.size());

        Set<String> groups = new HashSet<>();
        for (IWorkflowStep s : joinSteps) {
            groups.add(s.getRecord().getJoinGroup());
        }
        assertEquals(Set.of("2", "3"), groups);
    }

    /**
     * 回归 check2 P2：模型分析后起始步骤的出边列表不被误清空、所有步骤的
     * transitionFromSteps 初始化为空表而非 null（复制粘贴错误）。
     */
    @Test
    public void testAnalyzedModelInitializesTransitionStepLists() {
        IWorkflow workflow = workflowManager.newWorkflow("test/join", 1L);
        IWorkflowModel wfModel = workflow.getModel();

        IWorkflowStepModel startModel = wfModel.getStep("wf-start");
        assertNotNull(startModel);
        assertNotNull(startModel.getTransitionToSteps());
        assertTrue(startModel.getTransitionToSteps().size() >= 2, "start step toSteps must be preserved");

        for (IWorkflowStepModel stepModel : wfModel.getSteps()) {
            assertNotNull(stepModel.getTransitionFromSteps(), "fromSteps of " + stepModel.getName());
            assertNotNull(stepModel.getTransitionToSteps(), "toSteps of " + stepModel.getName());
        }
    }

    /**
     * 回归 check2 P2：步骤 actor 指向已删除用户（resolver 返回 null）时
     * allowCallByUser 必须返回 false 而非 NPE。
     */
    @Test
    public void testAllowCallByUserWithDeletedActorReturnsFalse() {
        WorkflowManagerImpl manager = newManagerWithGhostUser("ghost");
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = manager.newWorkflow("test/reject", 1L);
        workflow.start(null, context);
        IWorkflowStep step = workflow.getLatestStepByName("wf-start");
        assertNotNull(step);

        // 模拟组织数据清理后遗留的步骤实例：无 owner 且 actor 指向已删除用户
        WorkflowStepRecordBean record = (WorkflowStepRecordBean) step.getRecord();
        record.setOwnerId(null);
        record.setActorId("ghost");

        assertFalse(step.allowCallByUser(context));
    }

    /**
     * 回归 check2 P2：changeOwnerId 指向不存在的用户时报 ERR_WF_USER_NOT_EXISTS，
     * 不允许静默清空 owner 使任务失去归属。
     */
    @Test
    public void testChangeOwnerRejectsUnknownUser() {
        WorkflowManagerImpl manager = newManagerWithGhostUser("ghost");
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = manager.newWorkflow("test/reject", 1L);
        workflow.start(null, context);
        IWorkflowStep step = workflow.getLatestStepByName("wf-start");
        assertNotNull(step);

        NopException e = assertThrows(NopException.class, () -> step.changeOwnerId("ghost", context));
        assertEquals(ERR_WF_USER_NOT_EXISTS.getErrorCode(), e.getErrorCode());

        // 存在的用户仍可正常改派
        step.changeOwnerId("2", context);
        assertEquals("2", step.getRecord().getOwnerId());
    }

    /**
     * 回归 check2 P2：startStepName 指向 join 步骤（畸形但 xdef 未禁止）时
     * 启动不得 NPE，应正常创建 join 步骤实例。
     */
    @Test
    public void testStartStepAsJoinStepDoesNotFail() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/joinStart", 1L);
        workflow.start(null, context);

        List<? extends IWorkflowStep> steps = workflow.getStepsByName("join");
        assertEquals(1, steps.size());
    }

    /**
     * 构造指定用户已被删除（resolveUser 返回 null）的引擎环境
     */
    private WorkflowManagerImpl newManagerWithGhostUser(String ghostUserId) {
        WorkflowEngineImpl engine = new WorkflowEngineImpl();
        engine.setWfActorResolver(new MockWfActorResolver() {
            @Override
            public IWfActor resolveUser(String userId) {
                if (userId == null || ghostUserId.equals(userId))
                    return null;
                return super.resolveUser(userId);
            }
        });
        WorkflowManagerImpl manager = new WorkflowManagerImpl();
        manager.setWorkflowEngine(engine);
        manager.setWorkflowStore(new MockWorkflowStore());
        manager.init();
        return manager;
    }

    @Test
    public void testCommonAction() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/commonAction", null);
        workflow.start(null, context);
        IWorkflowStep step = workflow.getActivatedSteps().get(0);
        assertEquals(2, step.getAllowedActions(context).size());
    }

    // ==================== Exec Group 回归测试 ====================

    /**
     * and-group: 3 个 actor 全部 agree 后才 transition
     */
    @Test
    public void testExecGroupAnd() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/execGroupAnd", 1L);
        workflow.start(null, context);
        IWorkflowStep startStep = workflow.getLatestStartStep();
        invokeAction(startStep, "sh", null, null, null, context);

        List<? extends IWorkflowStep> activeSteps = workflow.getActivatedSteps();
        assertEquals(3, activeSteps.size());

        IWorkflowStep step1 = null, step2 = null, step3 = null;
        for (IWorkflowStep step : activeSteps) {
            String actorId = step.getRecord().getActorId();
            if ("1".equals(actorId)) step1 = step;
            else if ("2".equals(actorId)) step2 = step;
            else step3 = step;
        }

        // actor 1 agree: 组未完成，无下游
        context.getContext().setUserId("1");
        invokeAction(step1, "sp", null, null, null, context);
        assertFalse(workflow.isEnded());
        assertEquals(2, workflow.getActivatedSteps().size());

        // actor 2 agree: 组仍未完成，无下游
        context.getContext().setUserId("2");
        invokeAction(step2, "sp", null, null, null, context);
        assertFalse(workflow.isEnded());
        assertEquals(1, workflow.getActivatedSteps().size());

        // actor 3 agree: 组完成，transition to end
        context.getContext().setUserId("3");
        invokeAction(step3, "sp", null, null, null, context);
        workflow.runAutoTransitions(context);
        assertTrue(workflow.isEnded());
    }

    /**
     * or-group: 任意 1 个 actor agree 后，其余 skipped，仅 1 个下游步骤
     */
    @Test
    public void testExecGroupOr() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/execGroupOr", 1L);
        workflow.start(null, context);
        IWorkflowStep startStep = workflow.getLatestStartStep();
        invokeAction(startStep, "sh", null, null, null, context);

        List<? extends IWorkflowStep> activeSteps = workflow.getActivatedSteps();
        assertEquals(3, activeSteps.size());

        // 任意 1 个 agree: 组完成（or），skip 其余 2 个，transition to end
        IWorkflowStep firstStep = activeSteps.get(0);
        context.getContext().setUserId(firstStep.getRecord().getActorId());
        invokeAction(firstStep, "sp", null, null, null, context);
        workflow.runAutoTransitions(context);
        assertTrue(workflow.isEnded());
    }

    /**
     * vote-group: 权重和 >= passPercent(0.5) 后 transition，其余 skipped
     */
    @Test
    public void testExecGroupVote() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/execGroupVote", 1L);
        workflow.start(null, context);
        IWorkflowStep startStep = workflow.getLatestStartStep();
        invokeAction(startStep, "sh", null, null, null, context);

        List<? extends IWorkflowStep> activeSteps = workflow.getActivatedSteps();
        assertEquals(3, activeSteps.size());

        IWorkflowStep step1 = null, step2 = null;
        for (IWorkflowStep step : activeSteps) {
            String actorId = step.getRecord().getActorId();
            if ("1".equals(actorId)) step1 = step;
            else if ("2".equals(actorId)) step2 = step;
        }

        // actor 1 agree: 权重 1/3 < 0.5，组未完成，无下游
        context.getContext().setUserId("1");
        invokeAction(step1, "sp", null, null, null, context);
        assertFalse(workflow.isEnded());

        // actor 2 agree: 权重 2/3 >= 0.5，组完成，skip step3，transition to end
        context.getContext().setUserId("2");
        invokeAction(step2, "sp", null, null, null, context);
        workflow.runAutoTransitions(context);
        assertTrue(workflow.isEnded());
    }

    @Test
    public void testExecGroupVoteSlotOverridesFallback() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/execGroupVoteSlot", 1L);
        workflow.start(null, context);
        IWorkflowStep startStep = workflow.getLatestStartStep();
        invokeAction(startStep, "sh", null, null, null, context);

        List<? extends IWorkflowStep> activeSteps = workflow.getActivatedSteps();
        assertEquals(3, activeSteps.size());

        IWorkflowStep step1 = null, step2 = null, step3 = null;
        for (IWorkflowStep step : activeSteps) {
            String actorId = step.getRecord().getActorId();
            if ("1".equals(actorId)) step1 = step;
            else if ("2".equals(actorId)) step2 = step;
            else if ("3".equals(actorId)) step3 = step;
        }

        context.getContext().setUserId("1");
        invokeAction(step1, "sp", null, null, null, context);
        assertFalse(workflow.isEnded());

        context.getContext().setUserId("2");
        invokeAction(step2, "sp", null, null, null, context);
        assertFalse(workflow.isEnded());
        assertEquals(1, workflow.getActivatedSteps().size());

        context.getContext().setUserId("3");
        invokeAction(step3, "sp", null, null, null, context);
        workflow.runAutoTransitions(context);
        assertTrue(workflow.isEnded());
    }

    @Test
    public void testExecGroupVoteRejectSlot() {
        IServiceContext context = new ServiceContextImpl();
        WfStepModel reviewModel = (WfStepModel) workflowManager.getWorkflowModel("test/execGroupVoteRejectRuntime", 1L).getStep("review");
        IEvalPredicate original = reviewModel.getCheckExecGroupReject();
        reviewModel.setCheckExecGroupReject(ctx -> {
            IWorkflow workflow = ((io.nop.wf.core.engine.IWfRuntime) ctx).getWf();
            long rejected = workflow.getStepsByName("review", true).stream().filter(IWorkflowStep::isRejected).count();
            return rejected >= 1;
        });

        IWorkflow workflow = workflowManager.newWorkflow("test/execGroupVoteRejectRuntime", 1L);
        workflow.start(null, context);
        IWorkflowStep startStep = workflow.getLatestStartStep();
        invokeAction(startStep, "sh", null, null, null, context);

        List<? extends IWorkflowStep> activeSteps = workflow.getActivatedSteps();
        assertEquals(3, activeSteps.size());

        IWorkflowStep step1 = null, step2 = null;
        for (IWorkflowStep step : activeSteps) {
            String actorId = step.getRecord().getActorId();
            if ("1".equals(actorId)) {
                step1 = step;
            } else if ("2".equals(actorId)) {
                step2 = step;
            }
        }

        context.getContext().setUserId("1");
        invokeAction(step1, "_rejectAction", null, null, null, context);
        workflow.runAutoTransitions(context);
        assertFalse(workflow.isEnded());
        assertNotNull(workflow.getLatestStepByName("wf-start"));

        reviewModel.setCheckExecGroupReject(original);
    }

    /**
     * seq-group: 3 个 actor 按 execOrder 依次激活
     */
    @Test
    public void testExecGroupSeq() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/execGroupSeq", 1L);
        workflow.start(null, context);
        IWorkflowStep startStep = workflow.getLatestStartStep();
        invokeAction(startStep, "sh", null, null, null, context);

        // 仅 execOrder 最小的步骤 ACTIVATED，其余 WAITING
        assertEquals(1, workflow.getActivatedSteps().size());
        assertEquals(2, workflow.getWaitingSteps().size());

        IWorkflowStep step1 = workflow.getActivatedSteps().get(0);

        // step1 agree: exit，激活下一个 WAITING 步骤，无下游（组未完成）
        context.getContext().setUserId(step1.getRecord().getActorId());
        invokeAction(step1, "sp", null, null, null, context);
        assertFalse(workflow.isEnded());
        assertEquals(1, workflow.getActivatedSteps().size());
        assertEquals(1, workflow.getWaitingSteps().size());

        IWorkflowStep step2 = workflow.getActivatedSteps().get(0);

        // step2 agree: exit，激活下一个 WAITING 步骤，无下游
        context.getContext().setUserId(step2.getRecord().getActorId());
        invokeAction(step2, "sp", null, null, null, context);
        assertFalse(workflow.isEnded());
        assertEquals(1, workflow.getActivatedSteps().size());
        assertEquals(0, workflow.getWaitingSteps().size());

        IWorkflowStep step3 = workflow.getActivatedSteps().get(0);

        // step3 agree: 组完成，transition to end
        context.getContext().setUserId(step3.getRecord().getActorId());
        invokeAction(step3, "sp", null, null, null, context);
        workflow.runAutoTransitions(context);
        assertTrue(workflow.isEnded());
    }
}

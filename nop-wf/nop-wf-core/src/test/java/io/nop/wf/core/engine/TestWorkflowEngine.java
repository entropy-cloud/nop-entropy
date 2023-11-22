/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.engine;

import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.WorkflowTransitionTarget;
import io.nop.wf.core.engine.mock.MockWfActorResolver;
import io.nop.wf.core.engine.mock.MockWorkflowStore;
import io.nop.wf.core.impl.WorkflowManagerImpl;
import io.nop.wf.core.model.IWorkflowActionModel;
import io.nop.wf.core.store.IWorkflowRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled
public class TestWorkflowEngine extends BaseTestCase {
    WorkflowManagerImpl workflowManager;
    MockWorkflowStore store;

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
        //engine.setWorkflowCoordinator();
        workflowManager.setWorkflowEngine(engine);
        store = new MockWorkflowStore();
        workflowManager.setWorkflowStore(store);
        // workflowManager.setWorkflowModelStore(new ResourceWorkflowModelStore());

        workflowManager.init();
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
        assertEquals("actor1", step.getActor().getActorId());

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
        wfRecord.setBizObjName("test.io.entropy.wf.entity.WfEntity");
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
        ysh1.invokeAction("sp1", null, context);
        assertFalse(workflow.isEnded());
        ysh1.invokeAction("sp2", null, context);
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
        context.getContext().setUserId("userId");

        IWorkflow workflow = workflowManager.newWorkflow("test/cosign", 1L);
        workflow.start(null, context);
        assertEquals("userId", workflow.getRecord().getStarterId());
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
        context.getContext().setUserId("userId");
        IWorkflow workflow = workflowManager.newWorkflow("test/cosign1", 1L);
        workflow.start(null, context);
        assertEquals("userId", workflow.getRecord().getStarterId());
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

        invokeAction(step, "action0", "end0", "user", "actor2", context);

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
        invokeAction(startStep, "sh", "ysh", "user", "1", context);
        invokeAction(startStep, "sh", "ysp", "user", "1", context);
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

    @Test
    public void testCommonAction() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/commonAction", null);
        workflow.start(null, context);
        IWorkflowStep step = workflow.getActivatedSteps().get(0);
        assertEquals(2, step.getAllowedActions(context).size());
    }
}

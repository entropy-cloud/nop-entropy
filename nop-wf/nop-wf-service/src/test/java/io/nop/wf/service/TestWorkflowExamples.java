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
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.engine.WorkflowEngineImpl;
import io.nop.wf.core.impl.WorkflowManagerImpl;
import io.nop.wf.service.mock.EnhancedMockWfActorResolver;
import io.nop.wf.service.mock.MockWorkflowStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 覆盖 nop/wf/examples 目录下全部审批流示例的单元测试。使用 EnhancedMockWfActorResolver 支持
 * StarterManager/StarterDeptManager/role 等 actor，MockWorkflowStore 提供内存存储。
 */
public class TestWorkflowExamples extends BaseTestCase {
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
        engine.setWfActorResolver(new EnhancedMockWfActorResolver());
        engine.setUserDelegateService(new io.nop.api.core.auth.IUserDelegateService() {
            @Override
            public boolean canDelegate(String userId, String ownerId, String scope) {
                return false;
            }
            @Override
            public java.util.Set<String> getDelegateOwnerIds(String userId, String scope) {
                return java.util.Collections.emptySet();
            }
        });
        workflowManager.setWorkflowEngine(engine);
        MockWorkflowStore store = new MockWorkflowStore();
        workflowManager.setWorkflowStore(store);

        workflowManager.init();
        ContextProvider.getOrCreateContext().setUserId("1");
    }

    // ==================== Helper Methods ====================

    protected IServiceContext newContext(String userId) {
        IServiceContext context = new ServiceContextImpl();
        context.getContext().setUserId(userId);
        return context;
    }

    protected IWorkflow startExample(String wfName, String starterId, Map<String, Object> args) {
        IServiceContext context = newContext(starterId);
        IWorkflow workflow = workflowManager.newWorkflow(wfName, 1L);
        workflow.start(args, context);
        return workflow;
    }

    protected IWorkflowStep findActiveStep(IWorkflow wf, String stepName) {
        List<? extends IWorkflowStep> steps = wf.getActivatedSteps();
        for (IWorkflowStep step : steps) {
            if (step.getStepName().equals(stepName))
                return step;
        }
        return null;
    }

    protected IWorkflowStep requireActiveStep(IWorkflow wf, String stepName) {
        IWorkflowStep step = findActiveStep(wf, stepName);
        assertNotNull(step, "active step not found: " + stepName);
        return step;
    }

    protected void invokeAction(IWorkflow wf, String stepName, String userId, String action) {
        IServiceContext context = newContext(userId);
        invokeAction(wf, stepName, userId, action, context);
    }

    protected void invokeAction(IWorkflow wf, String stepName, String userId, String action, IServiceContext context) {
        context.getContext().setUserId(userId);
        IWorkflowStep step = findActiveStep(wf, stepName);
        assertNotNull(step, "active step not found: " + stepName + " for action " + action);
        step.invokeAction(action, null, context);
        wf.runAutoTransitions(context);
    }

    /**
     * 按步骤的 actor 查找激活步骤（用于 execGroup 等同名多步骤场景），返回第一个匹配 userId 的步骤。
     */
    protected IWorkflowStep findActiveStepByActor(IWorkflow wf, String userId) {
        for (IWorkflowStep step : wf.getActivatedSteps()) {
            if (userId.equals(step.getRecord().getActorId()))
                return step;
        }
        return null;
    }

    // ==================== Phase 2: 基础流程示例 ====================

    /**
     * simple-approval: submit → manager-approval(mgr1) → end。
     * 验证基本审批路径 + reject 后 submit 重新激活。
     */
    @Test
    public void testSimpleApproval() {
        IWorkflow wf = startExample("examples/simple-approval", "1", new HashMap<>());

        // submit agree → manager-approval(mgr1) 激活
        invokeAction(wf, "submit", "1", "agree");
        IWorkflowStep mgrStep = findActiveStep(wf, "manager-approval");
        assertNotNull(mgrStep, "manager-approval should be activated after submit agree");
        assertEquals("mgr1", mgrStep.getRecord().getActorId());
        assertFalse(wf.isEnded());

        // manager-approval reject → submit 重新激活
        invokeAction(wf, "manager-approval", "mgr1", "reject");
        IWorkflowStep submitAgain = findActiveStep(wf, "submit");
        assertNotNull(submitAgain, "submit should be re-activated after manager reject");
        assertFalse(wf.isEnded());

        // submit 重新 agree → manager-approval 重新激活
        invokeAction(wf, "submit", "1", "agree");
        assertNotNull(findActiveStep(wf, "manager-approval"), "manager-approval re-activated");

        // manager-approval agree → ended
        invokeAction(wf, "manager-approval", "mgr1", "agree");
        assertTrue(wf.isEnded());
    }

    /**
     * cc-notify: submit → approval(mgr1) → cc-notify(role hr-staff) → end。
     * 验证 cc 步骤只能 confirm 不能 agree。
     */
    @Test
    public void testCcNotify() {
        IWorkflow wf = startExample("examples/cc-notify", "1", new HashMap<>());

        // submit agree → approval(mgr1) 激活
        invokeAction(wf, "submit", "1", "agree");
        assertNotNull(findActiveStep(wf, "approval"));

        // approval agree → cc-notify(role hr-staff → user hr-staff) 激活
        invokeAction(wf, "approval", "mgr1", "agree");
        IWorkflowStep ccStep = findActiveStep(wf, "cc-notify");
        assertNotNull(ccStep, "cc-notify should be activated after approval agree");
        assertEquals("hr-staff", ccStep.getRecord().getActorId());
        assertFalse(wf.isEnded());

        // cc-notify 调用 agree 应失败（cc 步骤不能 agree）
        try {
            invokeAction(wf, "cc-notify", "hr-staff", "agree");
            assertFalse(true, "agree should not be allowed on cc step");
        } catch (Exception e) {
            // expected: WhenAllowAgree returns false for cc step
        }
        assertFalse(wf.isEnded());

        // cc-notify 调用 confirm 成功 → ended
        invokeAction(wf, "cc-notify", "hr-staff", "confirm");
        assertTrue(wf.isEnded());
    }

    // ==================== Phase 3: Exec Group 示例 ====================

    /**
     * 在激活步骤中按 actorId 查找（用于 execGroup 同名多步骤场景）。
     */
    protected IWorkflowStep findActiveStepByActorId(IWorkflow wf, String actorId) {
        for (IWorkflowStep step : wf.getActivatedSteps()) {
            if (actorId.equals(step.getRecord().getActorId()))
                return step;
        }
        return null;
    }

    protected void actionAs(IWorkflow wf, String actorId, String action) {
        IServiceContext context = newContext(actorId);
        IWorkflowStep step = findActiveStepByActorId(wf, actorId);
        assertNotNull(step, "no active step for actor: " + actorId);
        step.invokeAction(action, null, context);
        wf.runAutoTransitions(context);
    }

    protected void agreeAs(IWorkflow wf, String actorId) {
        actionAs(wf, actorId, "agree");
    }

    /**
     * countersign: and-group 全部通过后才 transition。
     */
    @Test
    public void testCountersign() {
        IWorkflow wf = startExample("examples/countersign", "1", new HashMap<>());

        // submit agree → countersign-review(and-group, 3 user) 全部 ACTIVATED
        invokeAction(wf, "submit", "1", "agree");
        assertEquals(3, wf.getActivatedSteps().size());

        // 1 个 agree → 仍 2 active，未 ended
        actionAs(wf, "finance-manager", "review-agree");
        assertFalse(wf.isEnded());
        assertEquals(2, wf.getActivatedSteps().size());

        // 2 个 agree → 仍 1 active，未 ended
        actionAs(wf, "hr-manager", "review-agree");
        assertFalse(wf.isEnded());
        assertEquals(1, wf.getActivatedSteps().size());

        // 3 个 agree → and-group 完成，transition 到 confirm(deptMgr0)
        actionAs(wf, "legal-manager", "review-agree");
        IWorkflowStep confirmStep = findActiveStep(wf, "confirm");
        assertNotNull(confirmStep, "confirm step should activate after and-group complete");
        assertEquals("deptMgr0", confirmStep.getRecord().getActorId());

        // confirm agree → ended
        agreeAs(wf, "deptMgr0");
        assertTrue(wf.isEnded());
    }

    /**
     * vote-sign: vote-group 权重和 >= passWeight(50) 后 transition。
     */
    @Test
    public void testVoteSign() {
        IWorkflow wf = startExample("examples/vote-sign", "1", new HashMap<>());

        // submit agree → vote-review(vote-group) 全部 ACTIVATED
        invokeAction(wf, "submit", "1", "agree");
        assertEquals(3, wf.getActivatedSteps().size());

        // ceo(30) agree → 30 < 50，未通过
        actionAs(wf, "test001", "vote-agree");
        assertFalse(wf.isEnded());

        // ceo+director1(65) agree → 65 >= 50，通过，director2 skipped，transition 到 cc-notify
        actionAs(wf, "test002", "vote-agree");
        IWorkflowStep ccStep = findActiveStep(wf, "cc-notify");
        assertNotNull(ccStep, "cc-notify should activate after vote passes");

        // cc-notify(test005) confirm → ended
        actionAs(wf, "test005", "confirm");
        assertTrue(wf.isEnded());
    }

    /**
     * or-sign: or-group 任一 agree 后 skip 其余。
     */
    @Test
    public void testOrSign() {
        IWorkflow wf = startExample("examples/or-sign", "1", new HashMap<>());

        // submit agree → or-sign-review(or-group, test001/test003) 全部 ACTIVATED
        invokeAction(wf, "submit", "1", "agree");
        assertEquals(2, wf.getActivatedSteps().size());

        // 1 个 agree → 组完成，另 1 个 skipped，transition 到 cc-hr
        actionAs(wf, "test001", "or-agree");
        IWorkflowStep ccStep = findActiveStep(wf, "cc-hr");
        assertNotNull(ccStep, "cc-hr should activate after or-group passes");

        // cc-hr(test002) confirm → ended
        actionAs(wf, "test002", "confirm");
        assertTrue(wf.isEnded());
    }

    /**
     * sequential-approval: seq-group 按 execOrder 依次激活。
     */
    @Test
    public void testSequentialApproval() {
        IWorkflow wf = startExample("examples/sequential-approval", "1", new HashMap<>());

        // submit agree → sequential-review(seq-group, mgr1/mgr2/mgr3)
        invokeAction(wf, "submit", "1", "agree");
        // 仅 mgr1 ACTIVATED，mgr2/mgr3 WAITING
        assertEquals(1, wf.getActivatedSteps().size());
        assertNotNull(findActiveStepByActorId(wf, "mgr1"));

        // mgr1 agree → 激活 mgr2
        actionAs(wf, "mgr1", "seq-agree");
        assertFalse(wf.isEnded());
        assertEquals(1, wf.getActivatedSteps().size());
        assertNotNull(findActiveStepByActorId(wf, "mgr2"));

        // mgr2 agree → 激活 mgr3
        actionAs(wf, "mgr2", "seq-agree");
        assertFalse(wf.isEnded());
        assertEquals(1, wf.getActivatedSteps().size());
        assertNotNull(findActiveStepByActorId(wf, "mgr3"));

        // mgr3 agree → 组完成，transition to end
        actionAs(wf, "mgr3", "seq-agree");
        assertTrue(wf.isEnded());
    }

    // ==================== Phase 4: 分支示例 ====================

    /**
     * conditional-branch: 根据 leaveDays 走不同路径（依赖 Phase 0 persist 修复）。
     */
    @Test
    public void testConditionalBranch() {
        // leaveDays=5(>3)：submit → manager-approval(mgr1) → hr-approval(hr-manager) → end
        Map<String, Object> args5 = new HashMap<>();
        args5.put("leaveDays", 5);
        IWorkflow wf = startExample("examples/conditional-branch", "1", args5);

        invokeAction(wf, "submit", "1", "agree");
        assertNotNull(findActiveStep(wf, "manager-approval"));
        assertEquals("mgr1", findActiveStep(wf, "manager-approval").getRecord().getActorId());

        invokeAction(wf, "manager-approval", "mgr1", "agree");
        IWorkflowStep hrStep = findActiveStep(wf, "hr-approval");
        assertNotNull(hrStep);
        assertEquals("hr-manager", hrStep.getRecord().getActorId());

        invokeAction(wf, "hr-approval", "hr-manager", "agree");
        assertTrue(wf.isEnded());

        // leaveDays=2(<=3)：submit → hr-approval 直接（跳过 manager）
        Map<String, Object> args2 = new HashMap<>();
        args2.put("leaveDays", 2);
        IWorkflow wf2 = startExample("examples/conditional-branch", "1", args2);
        invokeAction(wf2, "submit", "1", "agree");
        // manager-approval 不应激活
        assertNull(findActiveStep(wf2, "manager-approval"));
        assertNotNull(findActiveStep(wf2, "hr-approval"));

        invokeAction(wf2, "hr-approval", "hr-manager", "agree");
        assertTrue(wf2.isEnded());
    }

    /**
     * parallel-branch: split-and 并行 + joinType=and 等待全部完成（依赖 Phase 0 confirm→agree 修复）。
     */
    @Test
    public void testParallelBranch() {
        IWorkflow wf = startExample("examples/parallel-branch", "1", new HashMap<>());

        // submit agree → tech-review(test003) + finance-review(test002) 同时 ACTIVATED
        invokeAction(wf, "submit", "1", "agree");
        assertEquals(2, wf.getActivatedSteps().size());
        assertNotNull(findActiveStep(wf, "tech-review"));
        assertNotNull(findActiveStep(wf, "finance-review"));

        // 只完成 tech-review → merge 仍 WAITING
        agreeAs(wf, "test003");
        assertNull(findActiveStep(wf, "merge"));
        assertFalse(wf.isEnded());

        // 完成 finance-review → merge ACTIVATED
        agreeAs(wf, "test002");
        IWorkflowStep mergeStep = findActiveStep(wf, "merge");
        assertNotNull(mergeStep, "merge should activate after both reviews complete");
        assertEquals("test001", mergeStep.getRecord().getActorId());

        // merge agree（Phase 0 修复 confirm→agree）→ ended
        agreeAs(wf, "test001");
        assertTrue(wf.isEnded());
    }

    /**
     * inclusive-branch: 包容分支 + join（依赖 Phase 0 persist 修复）。
     */
    @Test
    public void testInclusiveBranch() {
        // riskLevel=8：security(>7) + compliance(>5) 同时激活
        Map<String, Object> args8 = new HashMap<>();
        args8.put("riskLevel", 8);
        IWorkflow wf = startExample("examples/inclusive-branch", "1", args8);

        invokeAction(wf, "submit", "1", "agree");
        assertEquals(2, wf.getActivatedSteps().size());
        assertNotNull(findActiveStep(wf, "security-review"));
        assertNotNull(findActiveStep(wf, "compliance-review"));

        // 只完成 security → final-review 仍 WAITING（join-and）
        agreeAs(wf, "test001");
        assertNull(findActiveStep(wf, "final-review"));

        // 完成 compliance → final-review ACTIVATED
        agreeAs(wf, "test002");
        IWorkflowStep finalStep = findActiveStep(wf, "final-review");
        assertNotNull(finalStep);
        assertEquals("test003", finalStep.getRecord().getActorId());

        agreeAs(wf, "test003");
        assertTrue(wf.isEnded());

        // riskLevel=3：仅 final-review 激活（跳过中间步骤）
        Map<String, Object> args3 = new HashMap<>();
        args3.put("riskLevel", 3);
        IWorkflow wf2 = startExample("examples/inclusive-branch", "1", args3);
        invokeAction(wf2, "submit", "1", "agree");
        assertNull(findActiveStep(wf2, "security-review"));
        assertNull(findActiveStep(wf2, "compliance-review"));
        assertNotNull(findActiveStep(wf2, "final-review"));
        agreeAs(wf2, "test003");
        assertTrue(wf2.isEnded());
    }

    // ==================== Phase 5: 高级示例 ====================

    /**
     * reject-withdraw: reject-to-start 驳回到发起人后 submit 重新激活。
     */
    @Test
    public void testRejectWithdraw() {
        IWorkflow wf = startExample("examples/reject-withdraw", "1", new HashMap<>());

        // submit → dept-approval(deptMgr0)
        invokeAction(wf, "submit", "1", "agree");
        IWorkflowStep deptStep = findActiveStep(wf, "dept-approval");
        assertNotNull(deptStep);
        assertEquals("deptMgr0", deptStep.getRecord().getActorId());

        // dept-approval reject-to-start → submit 重新激活
        actionAs(wf, "deptMgr0", "reject-to-start");
        IWorkflowStep submitAgain = findActiveStep(wf, "submit");
        assertNotNull(submitAgain, "submit should be re-activated after reject");
        assertFalse(wf.isEnded());

        // submit 重新 agree → dept-approval 重新激活
        invokeAction(wf, "submit", "1", "agree");
        assertNotNull(findActiveStep(wf, "dept-approval"));

        // dept-approval agree → finance-approval(role finance-manager → user finance-manager)
        agreeAs(wf, "deptMgr0");
        IWorkflowStep finStep = findActiveStep(wf, "finance-approval");
        assertNotNull(finStep);
        assertEquals("finance-manager", finStep.getRecord().getActorId());

        // finance-approval agree → ended
        agreeAs(wf, "finance-manager");
        assertTrue(wf.isEnded());
    }

    /**
     * transfer-delegate: 基本审批路径 + 转办 actor 变更。
     */
    @Test
    public void testTransferDelegate() {
        IWorkflow wf = startExample("examples/transfer-delegate", "1", new HashMap<>());

        // submit → approval(mgr1)
        invokeAction(wf, "submit", "1", "agree");
        IWorkflowStep apprStep = findActiveStep(wf, "approval");
        assertNotNull(apprStep);
        assertEquals("mgr1", apprStep.getRecord().getActorId());

        // approval agree → cc-result(test001) confirm → ended
        agreeAs(wf, "mgr1");
        IWorkflowStep ccStep = findActiveStep(wf, "cc-result");
        assertNotNull(ccStep);
        assertEquals("test001", ccStep.getRecord().getActorId());

        actionAs(wf, "test001", "confirm");
        assertTrue(wf.isEnded());
    }

    /**
     * subprocess: 子流程启动/结束/父流程恢复。
     */
    @Test
    public void testSubprocess() {
        Map<String, Object> args = new HashMap<>();
        args.put("employeeId", "emp001");
        args.put("handoverItems", "laptop, documents");
        IWorkflow wf = startExample("examples/subprocess", "1", args);

        // submit agree → hr-approval(hr-manager) 激活
        invokeAction(wf, "submit", "1", "agree");
        IWorkflowStep hrStep = findActiveStep(wf, "hr-approval");
        assertNotNull(hrStep);
        assertEquals("hr-manager", hrStep.getRecord().getActorId());

        // hr-approval agree → handover-subflow 子流程启动，父步骤 WAITING
        agreeAs(wf, "hr-manager");
        List<? extends IWorkflowStep> waitingSteps = wf.getWaitingSteps();
        assertFalse(waitingSteps.isEmpty(), "parent should have waiting step for sub-flow");

        // 找到 sub-flow 步骤
        IWorkflowStep subFlowStep = waitingSteps.stream()
                .filter(s -> "handover-subflow".equals(s.getStepName()))
                .findFirst().orElse(null);
        assertNotNull(subFlowStep, "handover-subflow step should be WAITING");
        assertNotNull(subFlowStep.getRecord().getSubWfId(), "sub-flow should have subWfId");

        // 驱动子流程
        IWorkflow subFlow = workflowManager.getWorkflow(subFlowStep.getRecord().getSubWfId());
        assertNotNull(subFlow);
        // receiver-confirm → sender-confirm → 子流程结束
        IWorkflowStep receiverStep = subFlow.getActivatedSteps().get(0);
        String receiverActorId = receiverStep.getRecord().getActorId();
        actionAs(subFlow, receiverActorId, "agree");

        IWorkflowStep senderStep = subFlow.getActivatedSteps().get(0);
        String senderActorId = senderStep.getRecord().getActorId();
        actionAs(subFlow, senderActorId, "agree");
        assertTrue(subFlow.isEnded(), "sub-flow should be ended");

        // 父流程恢复 → manager-confirm(mgr1)
        IServiceContext parentCtx = newContext("1");
        wf.runAutoTransitions(parentCtx);
        IWorkflowStep mgrStep = findActiveStep(wf, "manager-confirm");
        assertNotNull(mgrStep, "manager-confirm should activate after sub-flow ends");
        assertEquals("mgr1", mgrStep.getRecord().getActorId());

        // manager-confirm agree → ended
        agreeAs(wf, "mgr1");
        assertTrue(wf.isEnded());
    }

    /**
     * timeout-auto: dueTime 设置 + 正常审批路径。
     */
    @Test
    public void testTimeoutAuto() {
        IWorkflow wf = startExample("examples/timeout-auto", "1", new HashMap<>());

        // submit agree → timed-approval(mgr1) 激活
        invokeAction(wf, "submit", "1", "agree");
        IWorkflowStep timedStep = findActiveStep(wf, "timed-approval");
        assertNotNull(timedStep);
        assertEquals("mgr1", timedStep.getRecord().getActorId());

        // 验证 dueTime 被 due-time-expr 正确设置
        assertNotNull(timedStep.getRecord().getDueTime(), "dueTime should be set by due-time-expr");

        // timed-approval agree → manager-approval(mgr2)
        agreeAs(wf, "mgr1");
        IWorkflowStep mgrStep = findActiveStep(wf, "manager-approval");
        assertNotNull(mgrStep);
        assertEquals("mgr2", mgrStep.getRecord().getActorId());

        // manager-approval agree → ended
        agreeAs(wf, "mgr2");
        assertTrue(wf.isEnded());
    }

    /**
     * comprehensive-leave: 3 条条件路径 + or-group + persist。
     */
    @Test
    public void testComprehensiveLeave() {
        // leaveDays=2(<=3)：submit → hr-review → cc-attendance → end
        Map<String, Object> args2 = new HashMap<>();
        args2.put("leaveDays", 2);
        IWorkflow wf2 = startExample("examples/comprehensive-leave", "1", args2);
        invokeAction(wf2, "submit", "1", "agree");
        assertNull(findActiveStep(wf2, "dept-approval"));
        assertNotNull(findActiveStep(wf2, "hr-review"));
        agreeAs(wf2, "hr-manager");
        actionAs(wf2, "attendance-admin", "confirm");
        assertTrue(wf2.isEnded());

        // leaveDays=5(3<days<=7)：submit → dept-approval(deptMgr0) → hr-review → cc-attendance → end
        Map<String, Object> args5 = new HashMap<>();
        args5.put("leaveDays", 5);
        IWorkflow wf5 = startExample("examples/comprehensive-leave", "1", args5);
        invokeAction(wf5, "submit", "1", "agree");
        assertNotNull(findActiveStep(wf5, "dept-approval"));
        assertNull(findActiveStep(wf5, "vp-approval"));
        actionAs(wf5, "deptMgr0", "agree");
        assertNotNull(findActiveStep(wf5, "hr-review"));
        agreeAs(wf5, "hr-manager");
        actionAs(wf5, "attendance-admin", "confirm");
        assertTrue(wf5.isEnded());

        // leaveDays=8(>7)：submit → dept-approval(deptMgr0) → vp-approval(or-group) → hr-review → cc-attendance → end
        Map<String, Object> args8 = new HashMap<>();
        args8.put("leaveDays", 8);
        IWorkflow wf8 = startExample("examples/comprehensive-leave", "1", args8);
        invokeAction(wf8, "submit", "1", "agree");
        assertNotNull(findActiveStep(wf8, "dept-approval"));
        actionAs(wf8, "deptMgr0", "agree");
        // vp-approval or-group 激活（3 VP）
        assertNotNull(findActiveStep(wf8, "vp-approval"));
        assertEquals(3, wf8.getActivatedSteps().size());
        // 1 个 VP agree 即完成
        actionAs(wf8, "vp-finance", "vp-agree");
        assertNotNull(findActiveStep(wf8, "hr-review"));
        agreeAs(wf8, "hr-manager");
        actionAs(wf8, "attendance-admin", "confirm");
        assertTrue(wf8.isEnded());
    }
}

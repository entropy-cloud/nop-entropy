package io.nop.wf.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.wf.core.impl.WorkflowManagerImpl;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
@NopTestConfig(localDb = true, initDatabaseSchema = true)
public class TestDaoWorkflowEngine extends JunitAutoTestCase {
    @Inject
    WorkflowManagerImpl workflowManager;

    TestWorkflowEngine testCase;

    @BeforeEach
    public void init() {
        testCase = new TestWorkflowEngine();
        testCase.workflowManager = workflowManager;
    }

    /**
     * 创建工作流时不指定wfVersion，此时会使用最新的版本
     */
    @Test
    public void testEmptyVersion() {
        testCase.testEmptyVersion();
    }

    @Test
    public void testWorkflowInvoke() {
        testCase.testWorkflowInvoke();
    }

    @Test
    public void testWorkflowState() {
        testCase.testWorkflowState();
    }

    @Test
    public void testJoin() {
        testCase.testJoin();
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
        testCase.testCosign();
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
        testCase.testCosign1();
    }


    @Test
    public void testSimpleJoin() {
        testCase.testSimpleJoin();
    }

    @Test
    public void testFlow() {
        testCase.testFlow();
    }

    @Test
    public void testCurrentStep() {
        testCase.testCurrentStep();
    }

    @Test
    public void testInvokableActions() {
        testCase.testInvokableActions();
    }

    @Test
    public void testMultiTransition() {
        testCase.testMultiTransition();
    }

    @Test
    public void testTransitionTarget() {
        testCase.testTransitionTarget();
    }

    @Test
    public void testActor() {
        testCase.testActor();
    }

    @Test
    public void testNoAction() {
        testCase.testNoAction();

    }

    @Test
    public void testSplitTypeAnd() {
        testCase.testSplitTypeAnd();
    }

    @Test
    public void testSplitTypeOr() {
        testCase.testSplitTypeOr();
    }

    @Test
    public void testReject() {
        testCase.testReject();
    }

    @Test
    public void testWithdraw() {
        testCase.testWithdraw();
    }

    @Test
    public void testDynamicActor() {
        testCase.testDynamicActor();
    }

    /**
     * 空步骤
     */
    @Test
    public void testEmptyStep() {
        testCase.testEmptyStep();
    }

    /**
     * 循环步骤
     * start -> mainStart(step) -> sh -> ysh(step)->end(step)
     * -> cyStart(step) -> cysh -> kcy(step) -> cy -> kcy(step)...
     */
    @Test
    public void testLoop() {
        testCase.testLoop();
    }

    @Test
    public void testToAssign() {
        testCase.testToAssign();
    }

    @Test
    public void testToAssignAnd() {
        testCase.testToAssignAnd();
    }

    /**
     * 通过to-assign转换动态跳转步骤
     */
    @Test
    public void testToAssign1() {
        testCase.testToAssign1();
    }

    @Test
    public void testStartStepNoAssignment() {
        testCase.testStartStepNoAssignment();
    }

    @Test
    public void testCommonAction() {
        testCase.testCommonAction();
    }
}

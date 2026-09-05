/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.service;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.orm.IOrmTemplate;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.impl.WorkflowManagerImpl;
import io.nop.wf.dao.entity.NopWfInstance;
import io.nop.wf.dao.entity.NopWfStepInstance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_INVALID_STEP_STATUS_TRANSITION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDaoWorkflowEngine extends JunitAutoTestCase {
    @Inject
    WorkflowManagerImpl workflowManager;

    @Inject
    IOrmTemplate ormTemplate;

    TestWorkflowEngine testCase;

    @BeforeEach
    public void init() {
        testCase = new TestWorkflowEngine();
        testCase.workflowManager = workflowManager;

        saveUser("1");
        saveUser("2");
        saveRole("admin");

        ContextProvider.getOrCreateContext().setUserId("1");
        ContextProvider.getOrCreateContext().setUserName("user1");
    }

    protected void saveUser(String userId) {
        DaoTestHelper.saveUser(userId);
    }

    protected void saveRole(String roleId) {
        DaoTestHelper.saveRole(roleId);
    }


    protected void runInSession(Runnable task) {
        ormTemplate.runInSession(task);
    }

    /**
     * 创建工作流时不指定wfVersion，此时会使用最新的版本
     */
    @Test
    public void testEmptyVersion() {
        runInSession(testCase::testEmptyVersion);
    }

    @Test
    public void testWorkflowInvoke() {
        runInSession(testCase::testWorkflowInvoke);
    }

    @Test
    public void testBasicWorkflowState() {
        runInSession(testCase::testWorkflowState);
    }

    @Test
    public void testJoin() {
        runInSession(testCase::testJoin);
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
        runInSession(testCase::testCosign);
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
        runInSession(testCase::testCosign1);
    }

    @Test
    public void testSimpleJoin() {
        runInSession(testCase::testSimpleJoin);
    }

    @Test
    public void testFlow() {
        runInSession(testCase::testFlow);
    }

    @Test
    public void testCurrentStep() {
        runInSession(testCase::testCurrentStep);
    }

    @Test
    public void testInvokableActions() {
        runInSession(testCase::testInvokableActions);
    }

    @Test
    public void testMultiTransition() {
        runInSession(testCase::testMultiTransition);
    }

    @Test
    public void testTransitionTarget() {
        runInSession(testCase::testTransitionTarget);
    }

    @Test
    public void testActor() {
        runInSession(testCase::testActor);
    }

    @Test
    public void testNoAction() {
        runInSession(testCase::testNoAction);
    }

    @Test
    public void testSplitTypeAnd() {
        runInSession(testCase::testSplitTypeAnd);
    }

    @Test
    public void testSplitTypeOr() {
        runInSession(testCase::testSplitTypeOr);
    }

    @Test
    public void testReject() {
        runInSession(testCase::testReject);
    }

    @Test
    public void testWithdraw() {
        runInSession(testCase::testWithdraw);
    }

    @Test
    public void testDynamicActor() {
        runInSession(testCase::testDynamicActor);
    }

    /**
     * 空步骤
     */
    @Test
    public void testEmptyStep() {
        runInSession(testCase::testEmptyStep);
    }

    /**
     * 循环步骤
     * start -> mainStart(step) -> sh -> ysh(step)->end(step)
     * -> cyStart(step) -> cysh -> kcy(step) -> cy -> kcy(step)...
     */
    @Test
    public void testLoop() {
        runInSession(testCase::testLoop);
    }

    @Test
    public void testToAssign() {
        runInSession(testCase::testToAssign);
    }

    @Test
    public void testToAssignAnd() {
        runInSession(testCase::testToAssignAnd);
    }

    /**
     * 通过to-assign转换动态跳转步骤
     */
    @Test
    public void testToAssign1() {
        runInSession(testCase::testToAssign1);
    }

    @Test
    public void testStartStepNoAssignment() {
        runInSession(testCase::testStartStepNoAssignment);
    }

    @Test
    public void testCommonAction() {
        runInSession(testCase::testCommonAction);
    }

    /**
     * setLastOperator(null)清空操作人时必须清lastOperatorName而不是误清lastOperateTime。
     * 此前else分支复制粘贴错误，调用setLastOperateTime(null)
     */
    @Test
    public void testSetLastOperatorNullKeepsOperateTime() {
        runInSession(() -> {
            NopWfInstance instance = new NopWfInstance();
            java.sql.Timestamp operateTime = io.nop.api.core.time.CoreMetrics.currentTimestamp();
            instance.setLastOperatorName("user1");
            instance.setLastOperatorId("1");
            instance.setLastOperateTime(operateTime);

            instance.setLastOperator(null);

            assertNull(instance.getLastOperatorName());
            assertNull(instance.getLastOperatorId());
            assertEquals(operateTime, instance.getLastOperateTime());
        });
    }

    /**
     * DAO实体的步骤history状态回退保护：transitToStatus为裸setStatus时终态可被任意回退
     */
    @Test
    public void testStepInstanceHistoryStatusCannotRevert() {
        runInSession(() -> {
            NopWfStepInstance step = new NopWfStepInstance();
            step.setStepId("step-1");
            step.transitToStatus(NopWfCoreConstants.WF_STEP_STATUS_COMPLETED);

            NopException e = assertThrows(NopException.class,
                    () -> step.transitToStatus(NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED));
            assertEquals(ERR_WF_INVALID_STEP_STATUS_TRANSITION.getErrorCode(), e.getErrorCode());
        });
    }
}

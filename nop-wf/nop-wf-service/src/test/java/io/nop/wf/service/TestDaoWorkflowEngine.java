package io.nop.wf.service;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.context.ContextProvider;
import io.nop.auth.dao.entity.NopAuthRole;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.orm.IOrmTemplate;
import io.nop.wf.core.impl.WorkflowManagerImpl;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@NopTestConfig(localDb = true, initDatabaseSchema = true, disableSnapshot = false)
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
        NopAuthUser user = new NopAuthUser();
        user.setUserName("user_" + userId);
        user.setUserId(userId);
        user.setNickName(user.getUserName());
        user.setPassword("123");
        user.setOpenId(userId);
        user.setUserType(1);
        user.setStatus(1);
        user.setGender(1);
        user.setTenantId("0");

        daoProvider().daoFor(NopAuthUser.class).saveEntity(user);
    }

    protected void saveRole(String roleId) {
        NopAuthRole role = new NopAuthRole();
        role.setRoleId(roleId);
        role.setRoleName(roleId);

        daoProvider().daoFor(NopAuthRole.class).saveEntity(role);
    }


    protected void runInSession(Runnable task) {
        ormTemplate.runInSession(task);
    }

    /**
     * 创建工作流时不指定wfVersion，此时会使用最新的版本
     */
    @EnableSnapshot
    @Test
    public void testEmptyVersion() {
        runInSession(testCase::testEmptyVersion);
    }

    @EnableSnapshot
    @Test
    public void testWorkflowInvoke() {
        runInSession(testCase::testWorkflowInvoke);
    }

    @EnableSnapshot
    @Test
    public void testBasicWorkflowState() {
        runInSession(testCase::testWorkflowState);
    }

    @EnableSnapshot
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
    @EnableSnapshot
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
    @EnableSnapshot
    @Test
    public void testCosign1() {
        runInSession(testCase::testCosign1);
    }

    @EnableSnapshot
    @Test
    public void testSimpleJoin() {
        runInSession(testCase::testSimpleJoin);
    }

    @EnableSnapshot
    @Test
    public void testFlow() {
        runInSession(testCase::testFlow);
    }

    @EnableSnapshot
    @Test
    public void testCurrentStep() {
        runInSession(testCase::testCurrentStep);
    }

    @EnableSnapshot
    @Test
    public void testInvokableActions() {
        runInSession(testCase::testInvokableActions);
    }

    @EnableSnapshot
    @Test
    public void testMultiTransition() {
        runInSession(testCase::testMultiTransition);
    }

    @EnableSnapshot
    @Test
    public void testTransitionTarget() {
        runInSession(testCase::testTransitionTarget);
    }

    @EnableSnapshot
    @Test
    public void testActor() {
        runInSession(testCase::testActor);
    }

    @EnableSnapshot
    @Test
    public void testNoAction() {
        runInSession(testCase::testNoAction);
    }

    @EnableSnapshot
    @Test
    public void testSplitTypeAnd() {
        runInSession(testCase::testSplitTypeAnd);
    }

    @EnableSnapshot
    @Test
    public void testSplitTypeOr() {
        runInSession(testCase::testSplitTypeOr);
    }

    @EnableSnapshot
    @Test
    public void testReject() {
        runInSession(testCase::testReject);
    }

    @EnableSnapshot
    @Test
    public void testWithdraw() {
        runInSession(testCase::testWithdraw);
    }

    @EnableSnapshot
    @Test
    public void testDynamicActor() {
        runInSession(testCase::testDynamicActor);
    }

    /**
     * 空步骤
     */
    @EnableSnapshot
    @Test
    public void testEmptyStep() {
        runInSession(testCase::testEmptyStep);
    }

    /**
     * 循环步骤
     * start -> mainStart(step) -> sh -> ysh(step)->end(step)
     * -> cyStart(step) -> cysh -> kcy(step) -> cy -> kcy(step)...
     */
    @EnableSnapshot
    @Test
    public void testLoop() {
        runInSession(testCase::testLoop);
    }

    @EnableSnapshot
    @Test
    public void testToAssign() {
        runInSession(testCase::testToAssign);
    }

    @EnableSnapshot
    @Test
    public void testToAssignAnd() {
        runInSession(testCase::testToAssignAnd);
    }

    /**
     * 通过to-assign转换动态跳转步骤
     */
    @EnableSnapshot
    @Test
    public void testToAssign1() {
        runInSession(testCase::testToAssign1);
    }

    @EnableSnapshot
    @Test
    public void testStartStepNoAssignment() {
        runInSession(testCase::testStartStepNoAssignment);
    }

    @EnableSnapshot
    @Test
    public void testCommonAction() {
        runInSession(testCase::testCommonAction);
    }
}

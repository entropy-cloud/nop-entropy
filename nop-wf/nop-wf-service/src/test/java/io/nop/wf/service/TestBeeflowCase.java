package io.nop.wf.service;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.auth.dao.entity.NopAuthDept;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.orm.IOrmTemplate;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowManager;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.nop.wf.service.WfTestHelper.invoke;
import static io.nop.wf.service.WfTestHelper.start;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测试类钉钉审批流相关用例
 */
@NopTestConfig(localDb = true, initDatabaseSchema = true)
public class TestBeeflowCase extends JunitAutoTestCase {

    @Inject
    IWorkflowManager workflowManager;

    @Inject
    IOrmTemplate ormTemplate;

    @BeforeEach
    public void init() {
        runInSession(() -> {
            DaoTestHelper.saveRole("manager");

            NopAuthDept deptA = DaoTestHelper.saveDept("a");
            deptA.setManagerId("3");
            deptA.setParentId("b");

            NopAuthDept deptB = DaoTestHelper.saveDept("b");
            deptB.setManagerId("5");

            NopAuthUser user1 = DaoTestHelper.saveUser("1");
            user1.setDeptId("a");
            user1.setManagerId("2");

            NopAuthUser user2 = DaoTestHelper.saveUser("2");
            user2.setDeptId("a");
            user2.setManagerId("3");

            NopAuthUser user3 = DaoTestHelper.saveUser("3");
            user3.setDeptId("a");
            user3.setManagerId("5");

            NopAuthUser user4 = DaoTestHelper.saveUser("4");
            user4.setDeptId("b");

            NopAuthUser user5 = DaoTestHelper.saveUser("5");
            user5.setDeptId("b");

            DaoTestHelper.saveUserRole("5", "manager");
        });
    }

    private void runInSession(Runnable task) {
        ormTemplate.runInSession(task);
    }

    @EnableSnapshot
    @Test
    public void testPaymentApplication() {
        runInSession(() -> {
            IWorkflow wf = workflowManager.newWorkflow("beeflow/payment-application", null);
            start(wf, "1");  // 发送到上级 user_2
            invoke(wf, "2", "agree"); // 发送到部门主管 user_3
            invoke(wf, "3", "agree"); // 抄送用户 user_4
            invoke(wf, "4", "confirm"); // 抄送用户缺省后发给 角色 manager
            invoke(wf, "5", "agree");

            assertTrue(wf.isEnded());
        });
    }

    @Test
    public void testSalaryAdjustment() {
        runInSession(() -> {
            IWorkflow wf = workflowManager.newWorkflow("beeflow/salary-adjustment", null);
            start(wf, "1");  // 根据部门条件发送到 user2
            invoke(wf, "2", "agree"); //
            invoke(wf, "3", "agree"); // 抄送用户 user_4
            invoke(wf, "4", "confirm"); // 抄送用户缺省后发给 角色 manager
            invoke(wf, "5", "agree");
        });
    }
}

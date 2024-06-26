package io.nop.wf.service.flowlong;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.wf.service.AbstractWorkflowTestCase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled
@NopTestConfig(localDb = true, initDatabaseSchema = true, disableSnapshot = false)
public class TestCountersign extends AbstractWorkflowTestCase {

    @Test
    public void test() {
        Map<String, Object> args = new HashMap<>();
        args.put("day", 8);
        args.put("assignee", "test001");

        // 启动流程实例. 发送给001和003
        String wfId = startWorkflow("flowlong/counter-sign", "test001", args);

        // 测试会签审批人001【审批】
        executeTask(wfId, "test001", "k004");

        // 直接指定跳转会发起步骤. 003指定强制跳转到k001步骤
        executeJump(wfId, "test003", "k004", "k001");

        // 执行发起，再次发送给001和003
        executeActiveTasks(wfId, "test001", "k001");

        // 测试会签审批人003【转办，交给 002 审批】
        executeTransferToUser(wfId, "test003", "k004", "test002");

        // 会签审批【转办 002 审批】. 002接收了003转发过来的工作
        executeTask(wfId, "test002", "k004");

        // 测试会签审批人001【委派，交给 003 审批】. 001将工作委派给003，完成后会返回到001
        executeDelegate(wfId, "test001", "k004", "test003");

        // 委派人 001 确认审批
        this.executeTask(wfId, "test001", "k004");

        // 部门经理确认驳回
        this.executeRejectTo(wfId, "test002", "k005", "k001");

        // 这里驳回到发起人，发起人重新发起
        this.executeTask(wfId, "test001", "k001");

        executeTask(wfId, "test001", "k004");

        executeTask(wfId, "test003", "k004");

        executeReject(wfId, "test002", "k005");

        assertEquals(2, getActivatedSteps(wfId).size());
    }
}

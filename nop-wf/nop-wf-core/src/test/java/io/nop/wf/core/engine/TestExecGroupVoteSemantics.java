package io.nop.wf.core.engine;

import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.impl.IWorkflowStepImplementor;
import io.nop.wf.core.model.WfExecGroupType;
import io.nop.wf.core.model.WfStepModel;
import io.nop.wf.core.store.beans.WorkflowStepRecordBean;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5: ExecGroupSupport 执行分组语义测试（会签/投票判定）。
 * 使用动态代理 + 真实 WorkflowStepRecordBean/WfStepModel 状态；接口 default 方法
 * （isCompleted/isWaiting/isExcludeInExecGroup/getExecGroupType 等）通过
 * InvocationHandler.invokeDefault 走产品真实实现，断言的是产品状态机语义而非测试替身逻辑。
 */
public class TestExecGroupVoteSemantics {
    private static final int ST_WAITING = NopWfCoreConstants.WF_STEP_STATUS_WAITING;
    private static final int ST_ACTIVATED = NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED;
    private static final int ST_COMPLETED = NopWfCoreConstants.WF_STEP_STATUS_COMPLETED;
    private static final int ST_REJECTED = NopWfCoreConstants.WF_STEP_STATUS_REJECTED;
    private static final int ST_CANCELLED = NopWfCoreConstants.WF_STEP_STATUS_CANCELLED;

    /**
     * 步骤替身夹具：record/model/members 为可替换的真实对象，
     * 未显式处理的方法全部回落到接口 default 实现（真实状态判定逻辑）。
     */
    private static final class StepFixture {
        final WorkflowStepRecordBean record;
        WfStepModel model;
        List<? extends IWorkflowStep> members = Collections.emptyList();
        final IWorkflowStepImplementor proxy;

        StepFixture(int status, int voteWeight, String execGroup) {
            this.record = new WorkflowStepRecordBean();
            this.record.setStatus(status);
            this.record.setVoteWeight(voteWeight);
            this.record.setExecGroup(execGroup);

            InvocationHandler handler = (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getRecord":
                        return record;
                    case "getModel":
                        return model;
                    case "getStepsInSameExecGroup":
                        return members;
                    default:
                        return InvocationHandler.invokeDefault(proxy, method, args);
                }
            };
            this.proxy = (IWorkflowStepImplementor) Proxy.newProxyInstance(
                    IWorkflowStepImplementor.class.getClassLoader(),
                    new Class[]{IWorkflowStepImplementor.class}, handler);
        }

        StepFixture model(WfExecGroupType groupType, Integer passWeight, Double passPercent) {
            WfStepModel m = new WfStepModel();
            m.setExecGroupType(groupType);
            m.setPassWeight(passWeight);
            m.setPassPercent(passPercent);
            this.model = m;
            return this;
        }

        StepFixture members(IWorkflowStep... all) {
            this.members = Arrays.asList(all);
            return this;
        }
    }

    @Test
    public void testVoteGroupCompletesWhenPassWeightReached() {
        // 当前步骤(语义上视为已完成) + 一个已完成成员，权重和 2 >= passWeight 2
        StepFixture self = new StepFixture(ST_ACTIVATED, 1, "g")
                .model(WfExecGroupType.VOTE_GROUP, 2, null);
        self.members(self.proxy, new StepFixture(ST_COMPLETED, 1, "g").proxy);

        assertTrue(ExecGroupSupport.shouldExecGroupComplete(self.proxy));
    }

    @Test
    public void testVoteGroupIncompleteBelowPassWeight() {
        StepFixture self = new StepFixture(ST_ACTIVATED, 1, "g")
                .model(WfExecGroupType.VOTE_GROUP, 3, null);
        self.members(self.proxy, new StepFixture(ST_COMPLETED, 1, "g").proxy);

        assertFalse(ExecGroupSupport.shouldExecGroupComplete(self.proxy));
    }

    @Test
    public void testVoteGroupDefaultPassPercentHalf() {
        // 未配置 passWeight/passPercent 时缺省 passPercent=0.5
        StepFixture self = new StepFixture(ST_ACTIVATED, 1, "g")
                .model(WfExecGroupType.VOTE_GROUP, null, null);
        self.members(self.proxy,
                new StepFixture(ST_COMPLETED, 1, "g").proxy,
                new StepFixture(ST_WAITING, 1, "g").proxy);
        // 完成权重 2/3 >= 0.5
        assertTrue(ExecGroupSupport.shouldExecGroupComplete(self.proxy));

        self.members(self.proxy,
                new StepFixture(ST_WAITING, 1, "g").proxy,
                new StepFixture(ST_WAITING, 1, "g").proxy);
        // 完成权重 1/3 < 0.5
        assertFalse(ExecGroupSupport.shouldExecGroupComplete(self.proxy));
    }

    @Test
    public void testVoteGroupRejectWhenRemainingCannotReachPassWeight() {
        // 总权重3：当前self 1票拒绝 + REJECTED成员1票拒绝，剩余1 < passWeight 3 → 整组拒绝
        StepFixture self = new StepFixture(ST_ACTIVATED, 1, "g")
                .model(WfExecGroupType.VOTE_GROUP, 3, null);
        self.members(self.proxy,
                new StepFixture(ST_COMPLETED, 1, "g").proxy,
                new StepFixture(ST_REJECTED, 1, "g").proxy);

        assertTrue(ExecGroupSupport.shouldExecGroupReject(self.proxy));
    }

    @Test
    public void testVoteGroupNotRejectedWhileStillPassable() {
        // 总权重3：仅当前1票拒绝，剩余2 >= passWeight 2 → 不整组拒绝
        StepFixture self = new StepFixture(ST_ACTIVATED, 1, "g")
                .model(WfExecGroupType.VOTE_GROUP, 2, null);
        self.members(self.proxy,
                new StepFixture(ST_COMPLETED, 1, "g").proxy,
                new StepFixture(ST_WAITING, 1, "g").proxy);

        assertFalse(ExecGroupSupport.shouldExecGroupReject(self.proxy));
    }

    @Test
    public void testCancelledMembersExcludedFromVote() {
        // passPercent=0.8：排除 CANCELLED 成员后总权重=2（self+completed），完成权重 2/2=1.0 通过；
        // 若未排除则为 2/3≈0.67 < 0.8 不通过，断言结果证明排除语义生效
        StepFixture self = new StepFixture(ST_ACTIVATED, 1, "g")
                .model(WfExecGroupType.VOTE_GROUP, null, 0.8D);
        self.members(self.proxy,
                new StepFixture(ST_CANCELLED, 1, "g").proxy,
                new StepFixture(ST_COMPLETED, 1, "g").proxy);

        assertTrue(ExecGroupSupport.shouldExecGroupComplete(self.proxy));
        // 当前1票拒绝、剩余1票，剩余占比 0.5 < 0.8，已达成的票无法过阈值 → 整组拒绝
        assertTrue(ExecGroupSupport.shouldExecGroupReject(self.proxy));
    }

    @Test
    public void testOrGroupCompletesImmediately() {
        StepFixture self = new StepFixture(ST_ACTIVATED, 1, "g")
                .model(WfExecGroupType.OR_GROUP, null, null);
        self.members(self.proxy, new StepFixture(ST_ACTIVATED, 1, "g").proxy);

        assertTrue(ExecGroupSupport.shouldExecGroupComplete(self.proxy));
    }

    @Test
    public void testAndGroupCompletesWhenNoActiveMembers() {
        StepFixture self = new StepFixture(ST_ACTIVATED, 1, "g")
                .model(WfExecGroupType.AND_GROUP, null, null);
        self.members();
        assertTrue(ExecGroupSupport.shouldExecGroupComplete(self.proxy));

        self.members(self.proxy);
        assertFalse(ExecGroupSupport.shouldExecGroupComplete(self.proxy));
    }

    @Test
    public void testSameExecGroupComparison() {
        StepFixture a = new StepFixture(ST_ACTIVATED, 1, "g1");
        StepFixture b = new StepFixture(ST_ACTIVATED, 1, "g1");
        assertTrue(ExecGroupSupport.isSameExecGroup(a.proxy, b.proxy));

        StepFixture c = new StepFixture(ST_ACTIVATED, 1, "g2");
        assertFalse(ExecGroupSupport.isSameExecGroup(a.proxy, c.proxy));

        // 任一步骤无 execGroup 时不算同组
        StepFixture noGroup = new StepFixture(ST_ACTIVATED, 1, null);
        assertFalse(ExecGroupSupport.isSameExecGroup(noGroup.proxy, a.proxy));
        assertEquals("g1", a.record.getExecGroup());
    }
}

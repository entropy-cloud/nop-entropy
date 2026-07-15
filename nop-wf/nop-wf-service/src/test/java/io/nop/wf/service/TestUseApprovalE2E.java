package io.nop.wf.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.DaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.dao.entity.NopWfApprovableForm;
import io.nop.wf.dao.entity.NopWfApprovableItem;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * use-approval 端到端集成测试：
 * 1. DIRECT 模式：5 个 action 的状态迁移 + 幂等守卫
 * 2. WORKFLOW 模式：submit→wf→listener→approve 全链
 * 3. Anti-Hollow 断言：bizObject operations 实际注册了 5 个 approval mutation
 *
 * 实体 NopWfApprovableItem / NopWfApprovableForm 由 codegen 从 nop-wf.orm.xml 生成，
 * 其 _Xxx.xbiz 经 x:extends 继承 approval-support.xbiz，IBiz 继承 IApprovableBiz。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestUseApprovalE2E extends AbstractWorkflowTestCase {

    @Inject
    IBizObjectManager bizObjectManager;

    // ======================== Anti-Hollow: bizObject operations 注册验证 ========================

    /**
     * Anti-Hollow (a): 断言 NopWfApprovableItem 的 bizObject operations 实际注册了 5 个 approval mutation。
     * 这验证了 codegen 生成的 _NopWfApprovableItem.xbiz 经 x:extends 合并 approval-support.xbiz 后，
     * 5 个 mutation 成功注册到 dispatch 层（真正的运行时风险）。
     */
    @Test
    public void testBizObjectHasFiveApprovalMutations() {
        IBizObject bizObj = bizObjectManager.getBizObject("NopWfApprovableItem");
        assertNotNull(bizObj);

        Collection<GraphQLFieldDefinition> mutations = bizObj.getOperationDefinitions(GraphQLOperationType.mutation);
        assertNotNull(mutations);

        java.util.Set<String> mutationNames = new java.util.HashSet<>();
        for (GraphQLFieldDefinition def : mutations) {
            mutationNames.add(def.getName());
        }

        // Mutation names are registered as {bizObjName}__{actionName}
        String prefix = "NopWfApprovableItem__";
        assertTrue(mutationNames.contains(prefix + "submitForApproval"),
                "submitForApproval mutation must be registered. Actual mutations: " + mutationNames);
        assertTrue(mutationNames.contains(prefix + "approve"),
                "approve mutation must be registered. Actual mutations: " + mutationNames);
        assertTrue(mutationNames.contains(prefix + "reject"),
                "reject mutation must be registered. Actual mutations: " + mutationNames);
        assertTrue(mutationNames.contains(prefix + "withdrawApproval"),
                "withdrawApproval mutation must be registered. Actual mutations: " + mutationNames);
        assertTrue(mutationNames.contains(prefix + "reverseApprove"),
                "reverseApprove mutation must be registered. Actual mutations: " + mutationNames);
    }

    // ======================== DIRECT 模式 ========================

    /**
     * DIRECT 模式状态迁移全路径测试。
     * submitForApproval→approve→reverseApprove→submitForApproval→reject→submitForApproval→withdrawApproval
     */
    @Test
    public void testDirectApproval_stateTransition() {
        final String entityId = run(() -> {
            IEntityDao<NopWfApprovableItem> dao = DaoProvider.instance().dao("NopWfApprovableItem");
            NopWfApprovableItem entity = dao.newEntity();
            entity.setItemName("Direct Test Item");
            entity.setApproveStatus("UNSUBMITTED");
            dao.saveOrUpdateEntity(entity);
            return entity.orm_idString();
        });

        IBizObject bizObj = bizObjectManager.getBizObject("NopWfApprovableItem");

        // submitForApproval: UNSUBMITTED → SUBMITTED
        NopWfApprovableItem result = invokeApprovalAction(bizObj, "submitForApproval", entityId, "test001");
        assertEquals("SUBMITTED", result.getApproveStatus(),
                "submitForApproval should transition UNSUBMITTED→SUBMITTED");

        // approve: SUBMITTED → APPROVED
        result = invokeApprovalAction(bizObj, "approve", entityId, "test001");
        assertEquals("APPROVED", result.getApproveStatus(),
                "approve should transition SUBMITTED→APPROVED");
        assertEquals("test001", result.getApprovedBy(), "approve should set approvedBy");
        assertNotNull(result.getApprovedAt(), "approve should set approvedAt");

        // reverseApprove: APPROVED → SUBMITTED
        result = invokeApprovalAction(bizObj, "reverseApprove", entityId, "test001");
        assertEquals("SUBMITTED", result.getApproveStatus(),
                "reverseApprove should transition APPROVED→SUBMITTED");
        assertNull(result.getApprovedBy(), "reverseApprove should clear approvedBy");

        // reject: SUBMITTED → REJECTED
        result = invokeApprovalAction(bizObj, "reject", entityId, "test001");
        assertEquals("REJECTED", result.getApproveStatus(),
                "reject should transition SUBMITTED→REJECTED");

        // submitForApproval: REJECTED → SUBMITTED (resubmit after rejection)
        result = invokeApprovalAction(bizObj, "submitForApproval", entityId, "test001");
        assertEquals("SUBMITTED", result.getApproveStatus(),
                "submitForApproval should allow REJECTED→SUBMITTED");

        // withdrawApproval: SUBMITTED → UNSUBMITTED
        result = invokeApprovalAction(bizObj, "withdrawApproval", entityId, "test001");
        assertEquals("UNSUBMITTED", result.getApproveStatus(),
                "withdrawApproval should transition SUBMITTED→UNSUBMITTED");
    }

    /**
     * DIRECT 模式幂等守卫测试：非法状态迁移应抛异常。
     */
    @Test
    public void testDirectApproval_idempotencyGuard() {
        final String entityId = run(() -> {
            IEntityDao<NopWfApprovableItem> dao = DaoProvider.instance().dao("NopWfApprovableItem");
            NopWfApprovableItem entity = dao.newEntity();
            entity.setItemName("Guard Test Item");
            entity.setApproveStatus("UNSUBMITTED");
            dao.saveOrUpdateEntity(entity);
            return entity.orm_idString();
        });

        IBizObject bizObj = bizObjectManager.getBizObject("NopWfApprovableItem");

        // submit: UNSUBMITTED → SUBMITTED
        invokeApprovalAction(bizObj, "submitForApproval", entityId, "test001");

        // approve on UNSUBMITTED should fail (entity is now SUBMITTED, but approve from wrong state)
        // Let's first move to APPROVED, then try approve again (should fail)
        invokeApprovalAction(bizObj, "approve", entityId, "test001"); // SUBMITTED → APPROVED

        // approve again on APPROVED should throw (expected SUBMITTED)
        final String id = entityId;
        assertThrows(NopException.class, () -> {
            invokeApprovalAction(bizObj, "approve", id, "test001");
        }, "approve on APPROVED state should throw exception");

        // withdrawApproval on non-SUBMITTED should throw
        assertThrows(NopException.class, () -> {
            invokeApprovalAction(bizObj, "withdrawApproval", id, "test001");
        }, "withdrawApproval on APPROVED state should throw exception");
    }

    // ======================== WORKFLOW 模式 ========================

    /**
     * WORKFLOW 模式全链测试：
     * submitForApproval → 启动 wf + nopFlowId 反写 → 审批步 agree → wf 结束 →
     * listener 回调 approve → approveStatus = APPROVED
     *
     * Anti-Hollow (b): wf 结束事件 listener 运行时实际调用了业务 approve action。
     */
    @Test
    public void testWorkflowApproval_fullChain() {
        // 1. 创建实体
        final String entityId = run(() -> {
            IEntityDao<NopWfApprovableForm> dao = DaoProvider.instance().dao("NopWfApprovableForm");
            NopWfApprovableForm entity = dao.newEntity();
            entity.setFormTitle("Workflow Test Form");
            entity.setApproveStatus("UNSUBMITTED");
            dao.saveOrUpdateEntity(entity);
            return entity.orm_idString();
        });

        IBizObject bizObj = bizObjectManager.getBizObject("NopWfApprovableForm");

        // 2. submitForApproval: UNSUBMITTED → SUBMITTED + 启动 wf
        NopWfApprovableForm formResult = invokeApprovalAction(bizObj, "submitForApproval", entityId, "test001");
        assertEquals("SUBMITTED", formResult.getApproveStatus(),
                "submitForApproval should set approveStatus to SUBMITTED");

        // 3. 验证 nopFlowId 反写
        assertNotNull(formResult.getNopFlowId(),
                "submitForApproval should bind nopFlowId via bizEntityFlowIdProp");
        String wfId = formResult.getNopFlowId();

        // 4. 验证 wf 已启动
        run(() -> {
            IWorkflow wf = workflowManager.getWorkflow(wfId);
            assertNotNull(wf, "Workflow should exist with wfId=" + wfId);
            assertFalse(wf.isEnded(), "Workflow should be active (not ended) after submitForApproval");

            // 5. 执行审批步: agree (oa.xwf 定义的 action)
            IServiceContext stepCtx = newServiceContext("test002");
            executeTask(wfId, "test002", "approve1", step -> {
                step.invokeAction("agree", null, stepCtx);
                step.getWorkflow().runAutoTransitions(stepCtx);
            });
            return null;
        });

        // 6. 验证 wf 已结束 + approveStatus 已由 listener 回调改为 APPROVED
        run(() -> {
            IWorkflow wf = workflowManager.getWorkflow(wfId);
            assertTrue(wf.isEnded(), "Workflow should be ended after approval step completed");

            IEntityDao<NopWfApprovableForm> dao = DaoProvider.instance().dao("NopWfApprovableForm");
            NopWfApprovableForm entity = dao.getEntityById(entityId);
            assertEquals("APPROVED", entity.getApproveStatus(),
                    "After wf end, listener should have called approve action, setting approveStatus to APPROVED");
            return null;
        });
    }

    // ======================== 辅助方法 ========================

    @SuppressWarnings("unchecked")
    private <T> T invokeApprovalAction(IBizObject bizObj, String action, String entityId, String userId) {
        return (T) run(() -> {
            Map<String, Object> request = new HashMap<>();
            request.put("id", entityId);
            return bizObj.invoke(action, request, null, newServiceContext(userId));
        });
    }
}

package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.ActionAuthMeta;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-2.6-005/006 修复回归（plan 2378 R1.12 Phase 1）：
 * NopMetaDataContract / NopMetaTagLabel 的 approve/reject 是 x:extends approval-support.xbiz
 * 的本地覆盖（自带 source），auth 必须在本地覆盖文件中声明——断言对象是经 IBizObjectManager
 * 解析的 merged definition（合并终值），不是源 XML。
 * 同时验证未被覆盖的 submitForApproval 从 approval-support.xbiz 继承 admin auth。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestApprovalAuthMergedDefinition extends JunitBaseTestCase {

    @Inject
    IBizObjectManager bizObjectManager;

    @Test
    public void testMetaDataContractApprovalActionsCarryAdminAuth() {
        IBizObject bizObj = bizObjectManager.getBizObject("NopMetaDataContract");
        assertNotNull(bizObj);

        for (String action : new String[]{"approve", "reject"}) {
            ActionAuthMeta auth = requireMutationAuth(bizObj, "NopMetaDataContract__" + action);
            assertTrue(auth.getRoles() != null && auth.getRoles().contains("admin"),
                    action + " auth roles should contain admin (local override file), actual: "
                            + (auth.getRoles() == null ? "null" : auth.getRoles()));
        }

        // 本地未覆盖的 3 个 approval 动作经 x:extends 从 approval-support.xbiz 继承 auth
        ActionAuthMeta submitAuth = requireMutationAuth(bizObj, "NopMetaDataContract__submitForApproval");
        assertTrue(submitAuth.getRoles() != null && submitAuth.getRoles().contains("admin"),
                "submitForApproval should inherit admin auth from approval-support.xbiz");
    }

    @Test
    public void testMetaTagLabelApprovalActionsCarryAdminAuth() {
        IBizObject bizObj = bizObjectManager.getBizObject("NopMetaTagLabel");
        assertNotNull(bizObj);

        for (String action : new String[]{"approve", "reject"}) {
            ActionAuthMeta auth = requireMutationAuth(bizObj, "NopMetaTagLabel__" + action);
            assertTrue(auth.getRoles() != null && auth.getRoles().contains("admin"),
                    action + " auth roles should contain admin (local override file), actual: "
                            + (auth.getRoles() == null ? "null" : auth.getRoles()));
        }
    }

    /**
     * NopMetaTagLabel 本地未覆盖的 submitForApproval 从 approval-support.xbiz 继承 admin auth
     * （继承链在本地覆盖实体上同样生效）。
     */
    @Test
    public void testMetaTagLabelInheritsSubmitForApprovalAuth() {
        IBizObject bizObj = bizObjectManager.getBizObject("NopMetaTagLabel");
        ActionAuthMeta submitAuth = requireMutationAuth(bizObj, "NopMetaTagLabel__submitForApproval");
        assertTrue(submitAuth.getRoles() != null && submitAuth.getRoles().contains("admin"),
                "submitForApproval should inherit admin auth from approval-support.xbiz");
    }

    private GraphQLFieldDefinition findMutation(IBizObject bizObj, String operationName) {
        for (GraphQLFieldDefinition def : bizObj.getOperationDefinitions(GraphQLOperationType.mutation)) {
            if (operationName.equals(def.getName()))
                return def;
        }
        return null;
    }

    private ActionAuthMeta requireMutationAuth(IBizObject bizObj, String operationName) {
        GraphQLFieldDefinition def = findMutation(bizObj, operationName);
        assertNotNull(def, operationName + " must be registered");
        ActionAuthMeta auth = def.getAuth();
        assertNotNull(auth, operationName + " must carry <auth> after merge (P1-2.6-005/006)");
        return auth;
    }
}

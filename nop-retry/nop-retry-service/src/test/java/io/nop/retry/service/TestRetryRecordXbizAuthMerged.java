package io.nop.retry.service;

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

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-3.8-003 修复回归（plan 2386）：Retry 引擎执行表（NopRetryRecord/NopRetryPolicy/
 * NopRetryAttempt/NopRetryDeadLetter）的 CRUD mutations 携带 roles=admin auth——
 * 表内 serviceName/requestPayload 即执行凭据。引擎内部走 dao 直写，不受 biz 层 auth 影响。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestRetryRecordXbizAuthMerged extends JunitBaseTestCase {

    @Inject
    IBizObjectManager bizObjectManager;

    @Test
    public void testRetryRecordMutationsCarryAdminAuth() {
        assertMutationsAdminOnly("NopRetryRecord");
    }

    @Test
    public void testRetryPolicyMutationsCarryAdminAuth() {
        assertMutationsAdminOnly("NopRetryPolicy");
    }

    @Test
    public void testRetryAttemptMutationsCarryAdminAuth() {
        assertMutationsAdminOnly("NopRetryAttempt");
    }

    @Test
    public void testRetryDeadLetterMutationsCarryAdminAuth() {
        assertMutationsAdminOnly("NopRetryDeadLetter");
    }

    private void assertMutationsAdminOnly(String bizObjName) {
        IBizObject bizObj = bizObjectManager.getBizObject(bizObjName);
        assertNotNull(bizObj, bizObjName + " biz object must be registered");
        String prefix = bizObjName + "__";

        for (String action : new String[]{"save", "update", "delete", "saveAll", "deleteAll"}) {
            GraphQLFieldDefinition def = findMutation(bizObj, prefix + action);
            assertNotNull(def, prefix + action + " must be registered as mutation");

            ActionAuthMeta auth = def.getAuth();
            assertNotNull(auth, prefix + action + " must carry <auth> after merge (P1-3.8-003)");
            assertNotNull(auth.getRoles(), prefix + action + " auth must declare roles");
            assertTrue(auth.getRoles().contains("admin"), prefix + action + " must be admin-only");
        }
    }

    private GraphQLFieldDefinition findMutation(IBizObject bizObj, String name) {
        Collection<GraphQLFieldDefinition> ops = bizObj.getOperationDefinitions(GraphQLOperationType.mutation);
        if (ops == null)
            return null;
        for (GraphQLFieldDefinition def : ops) {
            if (name.equals(def.getName()))
                return def;
        }
        return null;
    }
}

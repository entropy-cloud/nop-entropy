package io.nop.dyn.service.entity;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-2.6-006 修复回归（plan 2378 R1.12 Phase 1）：
 * NopDynModule 的 active_findPage/publish/unpublish 三个纯 xbiz 动作必须在合并终值中携带 auth；
 * 每动作单独声明自身权限（| 是 OR 组语义，跨动作连写会互相提权）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopDynModuleXbizAuthMerged extends JunitBaseTestCase {

    @Inject
    IBizObjectManager bizObjectManager;

    @Test
    public void testActiveFindPageCarriesMirroredPermission() {
        IBizObject bizObj = bizObjectManager.getBizObject("NopDynModule");
        assertNotNull(bizObj);

        ActionAuthMeta auth = requireAuth(bizObj, GraphQLOperationType.query, "NopDynModule__active_findPage");
        assertEquals("NopDynModule:query|NopDynModule:findPage", auth.getPermissions().toString(),
                "active_findPage must declare the mirrored findPage permission set");
        assertNull(auth.getRoles(), "active_findPage declares permissions only");
    }

    @Test
    public void testPublishUnpublishCarryOwnPermission() {
        IBizObject bizObj = bizObjectManager.getBizObject("NopDynModule");

        ActionAuthMeta publishAuth = requireAuth(bizObj, GraphQLOperationType.mutation, "NopDynModule__publish");
        assertTrue(publishAuth.getRoles().contains("admin"), "publish roles should contain admin");
        assertEquals("NopDynModule:publish", publishAuth.getPermissions().toString(),
                "publish must declare only its own permission");

        ActionAuthMeta unpublishAuth = requireAuth(bizObj, GraphQLOperationType.mutation, "NopDynModule__unpublish");
        assertTrue(unpublishAuth.getRoles().contains("admin"), "unpublish roles should contain admin");
        assertEquals("NopDynModule:unpublish", unpublishAuth.getPermissions().toString(),
                "unpublish must declare only its own permission");
    }

    private GraphQLFieldDefinition findOperation(IBizObject bizObj, GraphQLOperationType type, String operationName) {
        for (GraphQLFieldDefinition def : bizObj.getOperationDefinitions(type)) {
            if (operationName.equals(def.getName()))
                return def;
        }
        return null;
    }

    private ActionAuthMeta requireAuth(IBizObject bizObj, GraphQLOperationType type, String operationName) {
        GraphQLFieldDefinition def = findOperation(bizObj, type, operationName);
        assertNotNull(def, operationName + " must be registered");
        ActionAuthMeta auth = def.getAuth();
        assertNotNull(auth, operationName + " must carry <auth> after merge (P1-2.6-006)");
        return auth;
    }
}

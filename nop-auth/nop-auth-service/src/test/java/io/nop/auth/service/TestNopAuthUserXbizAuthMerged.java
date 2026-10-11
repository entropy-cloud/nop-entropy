package io.nop.auth.service;

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

/**
 * P1-2.6-006 修复回归（plan 2378 R1.12 Phase 1）：
 * NopAuthUser.active_findPage 绕过 findPage 权限声明自身权限
 * （镜像 findPage 的 Java 缺省权限串 obj:query|obj:findPage——单动作内 | 分组语义等价）；
 * NopAuthUser_tenant 经 x:extends 继承后终值一致。
 * 测试环境存在 _delta/default 测试覆盖（x:extends="super"，不含 active_findPage），
 * 合并终值仍必须携带 auth。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopAuthUserXbizAuthMerged extends JunitBaseTestCase {

    private static final String EXPECTED_PERMISSIONS = "NopAuthUser:query|NopAuthUser:findPage";

    @Inject
    IBizObjectManager bizObjectManager;

    @Test
    public void testActiveFindPageCarriesAuth() {
        IBizObject bizObj = bizObjectManager.getBizObject("NopAuthUser");
        assertNotNull(bizObj);

        ActionAuthMeta auth = requireQueryAuth(bizObj, "NopAuthUser__active_findPage");
        assertEquals(EXPECTED_PERMISSIONS, auth.getPermissions().toString(),
                "active_findPage must declare the mirrored findPage permission set");
        assertNull(auth.getRoles(), "active_findPage declares permissions only");
    }

    @Test
    public void testTenantInheritsActiveFindPageAuth() {
        IBizObject bizObj = bizObjectManager.getBizObject("NopAuthUser_tenant");
        assertNotNull(bizObj);

        ActionAuthMeta auth = requireQueryAuth(bizObj, "NopAuthUser_tenant__active_findPage");
        assertEquals(EXPECTED_PERMISSIONS, auth.getPermissions().toString(),
                "NopAuthUser_tenant must inherit active_findPage auth via x:extends");
    }

    private GraphQLFieldDefinition findQuery(IBizObject bizObj, String operationName) {
        for (GraphQLFieldDefinition def : bizObj.getOperationDefinitions(GraphQLOperationType.query)) {
            if (operationName.equals(def.getName()))
                return def;
        }
        return null;
    }

    private ActionAuthMeta requireQueryAuth(IBizObject bizObj, String operationName) {
        GraphQLFieldDefinition def = findQuery(bizObj, operationName);
        assertNotNull(def, operationName + " must be registered");
        ActionAuthMeta auth = def.getAuth();
        assertNotNull(auth, operationName + " must carry <auth> after merge (P1-2.6-006)");
        return auth;
    }
}

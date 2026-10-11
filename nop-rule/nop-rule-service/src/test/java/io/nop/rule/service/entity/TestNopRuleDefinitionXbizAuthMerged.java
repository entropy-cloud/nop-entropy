package io.nop.rule.service.entity;

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
 * NopRuleDefinition.getOutputFieldsEditSchema 转发 getOutputFields 取数，
 * 必须镜像被转发动作的缺省权限（单动作内 | 分组与 Java 缺省串同构）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopRuleDefinitionXbizAuthMerged extends JunitBaseTestCase {

    @Inject
    IBizObjectManager bizObjectManager;

    @Test
    public void testGetOutputFieldsEditSchemaCarriesMirroredPermission() {
        IBizObject bizObj = bizObjectManager.getBizObject("NopRuleDefinition");
        assertNotNull(bizObj);

        String operationName = "NopRuleDefinition__getOutputFieldsEditSchema";
        GraphQLFieldDefinition def = null;
        for (GraphQLFieldDefinition candidate : bizObj.getOperationDefinitions(GraphQLOperationType.query)) {
            if (operationName.equals(candidate.getName())) {
                def = candidate;
                break;
            }
        }
        assertNotNull(def, operationName + " must be registered");

        ActionAuthMeta auth = def.getAuth();
        assertNotNull(auth, operationName + " must carry <auth> after merge (P1-2.6-006)");
        assertEquals("NopRuleDefinition:query|NopRuleDefinition:getOutputFields", auth.getPermissions().toString(),
                "getOutputFieldsEditSchema must mirror getOutputFields' default permission set");
        assertNull(auth.getRoles(), "getOutputFieldsEditSchema declares permissions only");
    }
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.model;

import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.parse.GenericTypeParser;
import io.nop.core.type.utils.GenericTypeHelper;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.xmeta.impl.SchemaImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestApiMethodModel extends BaseTestCase {
    @Test
    public void testDerivedMessageNamesForApiXml() {
        ApiMethodModel method = new ApiMethodModel();
        method.setRequestMessage("io.nop.rule.api.beans.RuleRequestBean");
        method.setResponseMessage(GenericTypeHelper.buildRawType("io.nop.rule.api.beans.RuleResultBean"));

        assertEquals("RuleRequestBean", method.getSimpleRequestMessage());
        assertEquals("RuleResultBean", method.getSimpleResponseMessage());
        assertFalse(method.isVoidRequest());
    }

    @Test
    public void testVoidRequestDetection() {
        ApiMethodModel method = new ApiMethodModel();
        method.setRequestMessage("Void");
        method.setResponseMessage(PredefinedGenericTypes.VOID_TYPE);

        assertEquals("Void", method.getSimpleRequestMessage());
        assertEquals("Void", method.getSimpleResponseMessage());
        assertTrue(method.isVoidRequest());
    }

    @Test
    public void testCodegenJavaTypeResolvesStdAlias() {
        ApiMessageFieldModel field = new ApiMessageFieldModel();
        SchemaImpl schema = new SchemaImpl();
        schema.setType(new GenericTypeParser().parseFromText(null, "Timestamp"));
        field.setSchema(schema);

        assertEquals("java.sql.Timestamp", field.getCodegenJavaType());
    }

    @Test
    public void testCodegenJavaTypeSimplifiesJavaLang() {
        ApiMessageFieldModel field = new ApiMessageFieldModel();
        SchemaImpl schema = new SchemaImpl();
        schema.setType(new GenericTypeParser().parseFromText(null, "java.lang.String"));
        field.setSchema(schema);

        assertEquals("String", field.getCodegenJavaType());
    }
}

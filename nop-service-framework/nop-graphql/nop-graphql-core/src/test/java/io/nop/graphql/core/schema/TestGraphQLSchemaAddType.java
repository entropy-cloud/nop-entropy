/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.schema;

import io.nop.api.core.exceptions.NopException;
import io.nop.graphql.core.ast.GraphQLEnumDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * addType重复类型的错误码语义：应报告"类型定义已存在"（duplicate-type-def + typeName参数），
 * 而非复制粘贴自addDirective的"指令定义已存在"（duplicate-directive-def + directiveName）。
 */
public class TestGraphQLSchemaAddType {
    @Test
    public void testDuplicateTypeReportsTypeError() {
        GraphQLSchema schema = new GraphQLSchema();

        GraphQLObjectDefinition typeA = new GraphQLObjectDefinition();
        typeA.setName("MyObj");

        GraphQLObjectDefinition typeB = new GraphQLObjectDefinition();
        typeB.setName("MyObj");

        schema.addType(typeA);
        NopException err = assertThrows(NopException.class, () -> schema.addType(typeB));
        assertEquals("nop.err.graphql.duplicate-type-def", err.getErrorCode());
        assertEquals("MyObj", err.getParam("typeName"));
    }

    @Test
    public void testDuplicateEnumTypeAlsoReportsTypeError() {
        GraphQLSchema schema = new GraphQLSchema();

        GraphQLEnumDefinition enumA = new GraphQLEnumDefinition();
        enumA.setName("MyEnum");

        GraphQLEnumDefinition enumB = new GraphQLEnumDefinition();
        enumB.setName("MyEnum");

        schema.addType(enumA);
        NopException err = assertThrows(NopException.class, () -> schema.addType(enumB));
        assertEquals("nop.err.graphql.duplicate-type-def", err.getErrorCode());
    }
}

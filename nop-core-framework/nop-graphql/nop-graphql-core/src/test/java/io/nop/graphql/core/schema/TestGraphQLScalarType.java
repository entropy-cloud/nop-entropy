/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestGraphQLScalarType {
    @Test
    public void testVoid() {
        GraphQLScalarType scalarType = GraphQLScalarType.fromJavaClass(void.class);
        assertEquals(GraphQLScalarType.Void, scalarType);
    }
}

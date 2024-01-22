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

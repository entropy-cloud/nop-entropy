package io.nop.http.api.utils;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 GraphQLRequestBuilder 的请求构造语义：
 * 操作别名按 index 生成、参数经 variables 传递、selection 输出到查询体、
 * operationType 决定查询前缀。
 */
public class TestGraphQLRequestBuilder {

    @Test
    public void testBuildQueryWithArgAndSelection() {
        GraphQLRequestBuilder builder = GraphQLRequestBuilder.query();
        builder.addOperation("NopAuthUser_findUser")
                .addArg("id", "String", "u1")
                .selection(FieldSelectionBean.fromProp("id", "name"));
        GraphQLRequestBean req = builder.build();

        assertTrue(req.getQuery().startsWith("query("), "query type must prefix the query body");
        assertTrue(req.getQuery().contains("NopAuthUser_findUser_0:NopAuthUser_findUser(id:$id_0)"),
                "operation must be aliased and reference its variable, got: " + req.getQuery());
        assertTrue(req.getQuery().contains("{id name}") || req.getQuery().contains("{id,name}")
                        || req.getQuery().contains("id") && req.getQuery().contains("name"),
                "selection fields must appear in the query body, got: " + req.getQuery());
        assertEquals("u1", req.getVariables().get("id_0"), "arg value must travel in variables");
    }

    @Test
    public void testMultipleOperationsGetDistinctAliases() {
        GraphQLRequestBuilder builder = GraphQLRequestBuilder.query();
        builder.addOperation("getA").addArg("x", "Int", 1);
        builder.addOperation("getB").addArg("y", "Int", 2);
        GraphQLRequestBean req = builder.build();

        String query = req.getQuery();
        assertTrue(query.contains("getA_0:getA(x:$x_0)"), "first alias must be index 0: " + query);
        assertTrue(query.contains("getB_1:getB(y:$y_1)"), "second alias must be index 1: " + query);
        assertEquals(2, req.getVariables().size());
        assertEquals(1, req.getVariables().get("x_0"));
        assertEquals(2, req.getVariables().get("y_1"));
    }

    @Test
    public void testMutationPrefix() {
        GraphQLRequestBuilder builder = GraphQLRequestBuilder.mutation();
        builder.addOperation("NopAuthUser__save").addArg("data", "JSON", Map.of("id", 1));
        GraphQLRequestBean req = builder.build();

        assertTrue(req.getQuery().startsWith("mutation("), "mutation builder must emit mutation prefix");
    }

    @Test
    public void testNoArgsOmitsVariableSection() {
        GraphQLRequestBuilder builder = GraphQLRequestBuilder.query();
        builder.addOperation("ping");
        GraphQLRequestBean req = builder.build();

        assertFalse(!req.getVariables().isEmpty(), "no args means empty variables");
        assertTrue(req.getQuery().startsWith("query{"), "no args must omit variable declarations");
    }
}

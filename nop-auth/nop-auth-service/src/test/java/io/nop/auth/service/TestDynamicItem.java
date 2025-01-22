package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.beans.ApiRequest;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.model.selection.FieldSelectionBeanParser;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NopTestConfig(localDb = true, initDatabaseSchema = true)
public class TestDynamicItem extends JunitBaseTestCase {
    @Inject
    IGraphQLEngine graphQLEngine;


    @Test
    public void testDynamicItem() {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(Map.of("id", "123"));
        request.setSelection(FieldSelectionBeanParser.fromText(null, "name, rows{nickName},rows2{userName}"));

        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(GraphQLOperationType.query,
                "NopAuthUser__testDynamicItem", request);
        Map<String, Object> result = (Map<String, Object>) graphQLEngine.executeRpc(context).get();
        assertEquals("a", result.get("name"));
    }
}

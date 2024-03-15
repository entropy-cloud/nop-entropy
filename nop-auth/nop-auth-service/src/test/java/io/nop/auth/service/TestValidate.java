package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

@NopTestConfig(localDb = true, initDatabaseSchema = true)
public class TestValidate extends JunitAutoTestCase {
    @Inject
    IGraphQLEngine graphQLEngine;

    @EnableSnapshot
    @Test
    public void testValidate() {
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(null, "DemoAuth__hello", new ApiRequest<>());
        ApiResponse<?> response = graphQLEngine.executeRpc(context);
        output("response.json5", response);
    }

    @EnableSnapshot
    @Test
    public void testNonEmpty() {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", "");
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(null, "DemoAuth__hello",
                ApiRequest.build(data));
        ApiResponse<?> response = graphQLEngine.executeRpc(context);
        output("response.json5", response);
    }
}

package io.nop.wf.service;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Map;

@Disabled
@NopTestConfig(localDb = true, initDatabaseSchema = true)
public class TestWorkflowDefinitionBizModel extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @EnableSnapshot
    @Test
    public void testPublish() {
        forceStackTrace();
        ApiRequest<?> request = request("request.json5", Map.class);
        IGraphQLExecutionContext gqlCtx = graphQLEngine.newRpcContext(null,
                "NopWfDefinition__save", request);
        ApiResponse<?> response = graphQLEngine.executeRpc(gqlCtx);
        output("response.json5", response);

        request = request("request2.json5", Map.class);
        gqlCtx = graphQLEngine.newRpcContext(null,
                "NopWfDefinition__publish", request);
        response = graphQLEngine.executeRpc(gqlCtx);
        output("response2.json5", response);
    }
}

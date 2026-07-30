package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestAutoNopMetaSemanticTypeCrud extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Test
    @EnableSnapshot
    public void testSaveAndQuerySemanticType() {
        var saveReq = request("saveSemanticType.json5", Map.class);
        IGraphQLExecutionContext saveCtx = graphQLEngine.newRpcContext(null,
                "NopMetaSemanticType__save", saveReq);
        var saveResp = graphQLEngine.executeRpc(saveCtx);
        output("saveSemanticTypeResponse.json5", saveResp);

        var queryReq = request("querySemanticType.json5", String.class);
        IGraphQLExecutionContext queryCtx = graphQLEngine.newRpcContext(null,
                "NopMetaSemanticType__get", queryReq);
        var queryResp = graphQLEngine.executeRpc(queryCtx);
        output("querySemanticTypeResponse.json5", queryResp);
    }
}

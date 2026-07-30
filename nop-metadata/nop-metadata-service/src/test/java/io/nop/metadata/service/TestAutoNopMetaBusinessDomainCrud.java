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
public class TestAutoNopMetaBusinessDomainCrud extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Test
    @EnableSnapshot
    public void testSaveAndQueryBusinessDomain() {
        var saveReq = request("saveBusinessDomain.json5", Map.class);
        IGraphQLExecutionContext saveCtx = graphQLEngine.newRpcContext(null,
                "NopMetaBusinessDomain__save", saveReq);
        var saveResp = graphQLEngine.executeRpc(saveCtx);
        output("saveBusinessDomainResponse.json5", saveResp);

        var queryReq = request("queryBusinessDomain.json5", String.class);
        IGraphQLExecutionContext queryCtx = graphQLEngine.newRpcContext(null,
                "NopMetaBusinessDomain__get", queryReq);
        var queryResp = graphQLEngine.executeRpc(queryCtx);
        output("queryBusinessDomainResponse.json5", queryResp);
    }
}

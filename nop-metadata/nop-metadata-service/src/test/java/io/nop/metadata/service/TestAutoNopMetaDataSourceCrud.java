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
public class TestAutoNopMetaDataSourceCrud extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Test
    @EnableSnapshot
    public void testSaveAndQueryDataSource() {
        var saveReq = request("saveDataSource.json5", Map.class);
        IGraphQLExecutionContext saveCtx = graphQLEngine.newRpcContext(null,
                "NopMetaDataSource__save", saveReq);
        var saveResp = graphQLEngine.executeRpc(saveCtx);
        output("saveDataSourceResponse.json5", saveResp);

        var queryReq = request("queryDataSource.json5", String.class);
        IGraphQLExecutionContext queryCtx = graphQLEngine.newRpcContext(null,
                "NopMetaDataSource__get", queryReq);
        var queryResp = graphQLEngine.executeRpc(queryCtx);
        output("queryDataSourceResponse.json5", queryResp);
    }
}

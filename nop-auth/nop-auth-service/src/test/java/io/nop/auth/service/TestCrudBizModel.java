package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@NopTestConfig(localDb = true,initDatabaseSchema = OptionalBoolean.TRUE)
public class TestCrudBizModel extends JunitAutoTestCase {
    @Inject
    IGraphQLEngine graphQLEngine;

    @Test
    public void testOrderBy() {
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(null, "NopAuthOpLog__findPage", ApiRequest.build(null));
        graphQLEngine.executeRpc(ctx);
    }
}

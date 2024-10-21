package io.nop.auth.service;


import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.service.audit.AuditServiceImpl;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(initDatabaseSchema = true, localDb = true, disableSnapshot = false)
public class TestXMetaPropDefaultValue extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    AuditServiceImpl auditService;


    @EnableSnapshot
    @Test
    public void testSet() {

        ApiRequest<?> request = input("request.json5", ApiRequest.class);
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(GraphQLOperationType.mutation, "NopAuthUser__save",
                request);
        Object result = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
        output("response.json5", result);
        assertTrue(FutureHelper.waitUntil(() -> auditService.isAllProcessed(), 1000));

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.interrupted();
        }
    }
}

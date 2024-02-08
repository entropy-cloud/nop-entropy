package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.api.core.audit.IAuditService;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.IGraphQLLogger;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = true)
@NopTestProperty(name = "nop.auth.graphql.enable-audit", value = "true")
@NopTestProperty(name = "nop.auth.graphql.audit-mutation-patterns", value = "NopAuthUser__*")
public class TestGraphQLLogger extends JunitAutoTestCase {
    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IAuditService auditService;

    @Inject
    IGraphQLLogger graphQLLogger;

    @EnableSnapshot
    @Test
    public void testAudit() {
        long beginTime = CoreMetrics.currentTimeMillis();
        ApiRequest<?> request = input("request.json5", ApiRequest.class);
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(GraphQLOperationType.mutation, "NopAuthUser__save",
                request);
        ApiResponse<?> result = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
        output("response.json5", result);
        graphQLLogger.onRpcExecute(context, beginTime, result, null);
        assertTrue(FutureHelper.waitUntil(() -> auditService.isAllProcessed(), 10000));
    }
}

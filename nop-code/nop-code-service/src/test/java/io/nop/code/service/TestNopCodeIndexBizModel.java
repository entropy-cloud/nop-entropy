package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestNopCodeIndexBizModel extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Test
    void testIndexDirectory() {
        String testProjectPath = Paths.get("src/test/resources/test-project/src/main/java").toString();

        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", "test-index");
        data.put("directoryPath", testProjectPath);
        data.put("filePattern", "**/*.java");
        request.setData(data);

        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                GraphQLOperationType.mutation, "NopCodeIndex__indexDirectory", request);
        ApiResponse<?> response = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(ctx));

        assertNotNull(response);
        assertTrue(response.isOk());
        Integer count = (Integer) response.getData();
        assertNotNull(count);
        assertEquals(6, count, "Should index exactly 6 Java files in test-project, got " + count);
    }

    @Test
    void testGetStats() {
        String testProjectPath = Paths.get("src/test/resources/test-project/src/main/java").toString();
        ApiRequest<Map<String, Object>> indexRequest = new ApiRequest<>();
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", "stats-test");
        data.put("directoryPath", testProjectPath);
        data.put("filePattern", "**/*.java");
        indexRequest.setData(data);
        IGraphQLExecutionContext indexCtx = graphQLEngine.newRpcContext(
                GraphQLOperationType.mutation, "NopCodeIndex__indexDirectory", indexRequest);
        FutureHelper.syncGet(graphQLEngine.executeRpcAsync(indexCtx));

        ApiRequest<Map<String, Object>> statsRequest = new ApiRequest<>();
        Map<String, Object> statsData = new HashMap<>();
        statsData.put("indexId", "stats-test");
        statsRequest.setData(statsData);
        IGraphQLExecutionContext statsCtx = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, "NopCodeIndex__getStats", statsRequest);
        ApiResponse<?> statsResponse = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(statsCtx));

        assertNotNull(statsResponse);
        assertTrue(statsResponse.isOk());
        assertNotNull(statsResponse.getData());

        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) statsResponse.getData();
        assertEquals("stats-test", stats.get("indexId"));
        int fileCount = ((Number) stats.get("fileCount")).intValue();
        assertTrue(fileCount >= 6,
                "Expected at least 6 files indexed, got " + fileCount);
        int symbolCount = ((Number) stats.get("symbolCount")).intValue();
        assertTrue(symbolCount > 0,
                "Expected some symbols indexed, got " + symbolCount);
    }
}

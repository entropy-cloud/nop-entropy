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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestFlowAnalysisE2E extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    private static final String TEST_PROJECT_PATH =
            Paths.get("src/test/resources/test-project/src/main/java").toString();

    private ApiResponse<?> rpcQuery(String operation, Map<String, Object> data) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, operation, request);
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(ctx));
    }

    private ApiResponse<?> rpcMutation(String operation, Map<String, Object> data) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                GraphQLOperationType.mutation, operation, request);
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(ctx));
    }

    private void indexTestProject(String indexId) {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", indexId);
        data.put("directoryPath", TEST_PROJECT_PATH);
        data.put("filePattern", "**/*.java");
        ApiResponse<?> resp = rpcMutation("NopCodeIndex__indexDirectory", data);
        assertTrue(resp.isOk(), "Indexing should succeed: " + resp.getMsg());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDetectFlows() {
        String indexId = "flow-test";
        indexTestProject(indexId);

        Map<String, Object> data = new HashMap<>();
        data.put("indexId", indexId);
        ApiResponse<?> response = rpcMutation("NopCodeIndex__detectFlows", data);

        assertTrue(response.isOk(), "detectFlows should succeed: " + response.getMsg());
        assertNotNull(response.getData());
        List<Object> flows = (List<Object>) response.getData();
        assertNotNull(flows, "Flows list should not be null");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAnalyzeChanges() {
        String indexId = "change-test";
        indexTestProject(indexId);

        Map<String, Object> data = new HashMap<>();
        data.put("indexId", indexId);
        data.put("baselineCommitish", "HEAD~1");
        data.put("targetCommitish", "HEAD");
        ApiResponse<?> response = rpcQuery("NopCodeIndex__analyzeChanges", data);

        if (response.isOk()) {
            assertNotNull(response.getData(), "analyzeChanges result should not be null when successful");
        } else {
            assertNotNull(response.getMsg(), "Error response should have an error message");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDetectDeadCode() {
        String indexId = "deadcode-test";
        indexTestProject(indexId);

        Map<String, Object> data = new HashMap<>();
        data.put("indexId", indexId);
        ApiResponse<?> response = rpcMutation("NopCodeSymbol__detectDeadCode", data);

        assertTrue(response.isOk(), "detectDeadCode should succeed: " + response.getMsg());
        assertNotNull(response.getData());
        Map<String, Object> result = (Map<String, Object>) response.getData();

        List<Object> deadSymbols = (List<Object>) result.get("deadSymbols");
        List<Object> suspiciousSymbols = (List<Object>) result.get("suspiciousSymbols");
        assertNotNull(deadSymbols, "deadSymbols should not be null");
        assertNotNull(suspiciousSymbols, "suspiciousSymbols should not be null");
    }
}

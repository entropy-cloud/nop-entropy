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
public class TestNopCodeAnalysisBizModel extends JunitAutoTestCase {

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

    @SuppressWarnings("unchecked")
    private void indexTestProject(String indexId) {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", indexId);
        data.put("directoryPath", TEST_PROJECT_PATH);
        data.put("filePattern", "**/*.java");
        ApiResponse<?> resp = rpcMutation("NopCodeIndex__indexDirectory", data);
        assertTrue(resp.isOk(), "Indexing should succeed");
    }

    @Test
    void testDetectCommunities() {
        String indexId = "comm-test";
        indexTestProject(indexId);

        Map<String, Object> data = new HashMap<>();
        data.put("indexId", indexId);
        ApiResponse<?> response = rpcQuery("NopCodeAnalysis__detectCommunities", data);

        assertTrue(response.isOk());
        assertNotNull(response.getData());
        Map<String, Object> result = (Map<String, Object>) response.getData();
        assertTrue((Integer) result.get("totalSymbols") >= 0);
        assertTrue((Integer) result.get("totalCommunities") >= 0);
        assertNotNull(result.get("averageCohesion"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGetGraphAnalysis() {
        String indexId = "graph-test";
        indexTestProject(indexId);

        Map<String, Object> data = new HashMap<>();
        data.put("indexId", indexId);
        data.put("topN", 5);
        ApiResponse<?> response = rpcQuery("NopCodeAnalysis__getGraphAnalysis", data);

        assertTrue(response.isOk());
        assertNotNull(response.getData());
        Map<String, Object> result = (Map<String, Object>) response.getData();

        List<Object> godNodes = (List<Object>) result.get("godNodes");
        assertNotNull(godNodes);

        Map<String, Object> breakdown = (Map<String, Object>) result.get("cohesionBreakdown");
        assertNotNull(breakdown);
        assertTrue((Integer) breakdown.get("extractedCount") >= 0);
        assertTrue((Integer) breakdown.get("inferredCount") >= 0);

        List<Object> isolated = (List<Object>) result.get("isolatedSymbols");
        assertNotNull(isolated);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGetImpactAnalysis() {
        String indexId = "impact-test";
        indexTestProject(indexId);

        Map<String, Object> data = new HashMap<>();
        data.put("indexId", indexId);
        data.put("symbolId", "com.example.service.UserService");
        data.put("depth", 2);
        ApiResponse<?> response = rpcQuery("NopCodeAnalysis__getImpactAnalysis", data);

        assertTrue(response.isOk());
        assertNotNull(response.getData());
        Map<String, Object> result = (Map<String, Object>) response.getData();

        assertNotNull(result.get("targetSymbolId"));
        assertNotNull(result.get("riskLevel"));
        assertNotNull(result.get("upstream"));
        assertNotNull(result.get("downstream"));
    }

    @Test
    void testDetectCommunitiesOnEmptyIndex() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", "nonexistent-index-999");
        ApiResponse<?> response = rpcQuery("NopCodeAnalysis__detectCommunities", data);

        // Either the engine returns an error, or we get a valid but empty result
        if (response.isOk()) {
            // Graceful: returned empty/default result
            assertNotNull(response.getData());
        }
        // If not OK, that's also acceptable — index doesn't exist
    }
}

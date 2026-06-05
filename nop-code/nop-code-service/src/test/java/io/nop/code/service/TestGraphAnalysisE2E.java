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
public class TestGraphAnalysisE2E extends JunitAutoTestCase {

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
    void testGetCriticalNodes() {
        String indexId = "crit-test";
        indexTestProject(indexId);

        Map<String, Object> data = new HashMap<>();
        data.put("indexId", indexId);
        data.put("topN", 5);
        ApiResponse<?> response = rpcQuery("NopCodeIndex__getCriticalNodes", data);

        assertTrue(response.isOk(), "getCriticalNodes should succeed: " + response.getMsg());
        assertNotNull(response.getData());
        Map<String, Object> result = (Map<String, Object>) response.getData();

        List<Object> bridgeNodes = (List<Object>) result.get("bridgeNodes");
        List<Object> hubNodes = (List<Object>) result.get("hubNodes");
        assertNotNull(bridgeNodes, "bridgeNodes should not be null");
        assertNotNull(hubNodes, "hubNodes should not be null");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGetKnowledgeGaps() {
        String indexId = "gap-test";
        indexTestProject(indexId);

        Map<String, Object> data = new HashMap<>();
        data.put("indexId", indexId);
        ApiResponse<?> response = rpcQuery("NopCodeIndex__getKnowledgeGaps", data);

        assertTrue(response.isOk(), "getKnowledgeGaps should succeed: " + response.getMsg());
        assertNotNull(response.getData());
        Map<String, Object> result = (Map<String, Object>) response.getData();

        List<Object> isolated = (List<Object>) result.get("isolatedSymbols");
        List<Object> weakCommunities = (List<Object>) result.get("weakCommunities");
        assertNotNull(isolated, "isolatedSymbols should not be null");
        assertNotNull(weakCommunities, "weakCommunities should not be null");
    }
}

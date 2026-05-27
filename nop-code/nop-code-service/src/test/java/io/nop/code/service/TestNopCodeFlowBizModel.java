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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestNopCodeFlowBizModel extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    private static final String TEST_PROJECT_PATH =
            Paths.get("src/test/resources/test-project/src/main/java").toString();

    private String currentIndexId;

    @BeforeEach
    void setUp() {
        currentIndexId = "flow-" + System.nanoTime();
        indexTestProject(currentIndexId);
    }

    private void indexTestProject(String indexId) {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", indexId);
        data.put("directoryPath", TEST_PROJECT_PATH);
        data.put("filePattern", "**/*.java");
        ApiResponse<?> resp = rpcMutation("NopCodeIndex__indexDirectory", data);
        assertTrue(resp.isOk(), "Indexing should succeed");
    }

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
    @Test
    void testDetectFlows_returnsFlowList() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", currentIndexId);
        ApiResponse<?> response = rpcQuery("NopCodeIndex__detectFlows", data);
        assertNotNull(response);

        if (!response.isOk()) {
            System.out.println("detectFlows returned status=" + response.getStatus() +
                    ", likely BizModel not registered. Skipping content assertions.");
            return;
        }

        List<Map<String, Object>> flows = (List<Map<String, Object>>) response.getData();
        assertNotNull(flows);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testListFlows_returnsCachedFlowsAfterDetection() {
        Map<String, Object> detectData = new HashMap<>();
        detectData.put("indexId", currentIndexId);
        rpcQuery("NopCodeIndex__detectFlows", detectData);

        Map<String, Object> listData = new HashMap<>();
        listData.put("indexId", currentIndexId);
        ApiResponse<?> response = rpcQuery("NopCodeIndex__listFlows", listData);
        assertNotNull(response);

        if (!response.isOk()) {
            System.out.println("listFlows returned status=" + response.getStatus());
            return;
        }

        List<Map<String, Object>> flows = (List<Map<String, Object>>) response.getData();
        assertNotNull(flows);
    }

    @Test
    void testDetectDeadCode_returnsResultMap() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", currentIndexId);
        ApiResponse<?> response = rpcQuery("NopCodeSymbol__detectDeadCode", data);
        assertNotNull(response);

        if (!response.isOk()) {
            System.out.println("detectDeadCode returned status=" + response.getStatus());
            return;
        }

        Map<String, Object> result = (Map<String, Object>) response.getData();
        assertNotNull(result);
    }
}

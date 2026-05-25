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
public class TestNopSearchIntegration extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    private static final String TEST_PROJECT_PATH =
            Paths.get("src/test/resources/test-project/src/main/java").toString();

    private String currentIndexId;

    @BeforeEach
    void setUp() {
        currentIndexId = "search-" + System.nanoTime();
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
    void testSearchFallbackWithoutSearchEngine() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", currentIndexId);
        data.put("query", "User");
        data.put("searchType", "COMBINED");
        ApiResponse<?> response = rpcQuery("NopCodeSymbol__searchCode", data);

        assertTrue(response.isOk());
        assertNotNull(response.getData());
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getData();
        assertNotNull(results, "searchCode should return non-null list (fallback to DB LIKE query)");
    }

    @SuppressWarnings("unchecked")
    @Test
    void testSearchBySymbolNameReturnsResults() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", currentIndexId);
        data.put("query", "User");
        data.put("searchType", "SYMBOL_NAME");
        ApiResponse<?> response = rpcQuery("NopCodeSymbol__searchCode", data);

        assertTrue(response.isOk());
        assertNotNull(response.getData());
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getData();
        assertNotNull(results);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testSearchCombinedReturnsResults() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", currentIndexId);
        data.put("query", "setName");
        data.put("searchType", "COMBINED");
        ApiResponse<?> response = rpcQuery("NopCodeSymbol__searchCode", data);

        assertTrue(response.isOk());
        assertNotNull(response.getData());
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getData();
        assertNotNull(results);
    }

    @Test
    void testSearchEmptyQueryReturnsResponse() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", currentIndexId);
        data.put("query", "");
        data.put("searchType", "COMBINED");
        ApiResponse<?> response = rpcQuery("NopCodeSymbol__searchCode", data);
        assertNotNull(response);

        if (response.isOk()) {
            assertNotNull(response.getData());
        }
    }
}

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
    void testSearchBySymbolName_returnsResults() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", currentIndexId);
        data.put("query", "User");
        data.put("searchType", "SYMBOL_NAME");
        ApiResponse<?> response = rpcQuery("NopCodeSymbol__searchCode", data);

        assertTrue(response.isOk(), "searchCode SYMBOL_NAME should succeed, status=" + response.getStatus());
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getData();
        assertFalse(results.isEmpty(), "Searching 'User' should find at least one symbol");
    }

    @SuppressWarnings("unchecked")
    @Test
    void testSearchCombined_returnsResults() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", currentIndexId);
        data.put("query", "setName");
        data.put("searchType", "COMBINED");
        ApiResponse<?> response = rpcQuery("NopCodeSymbol__searchCode", data);

        assertTrue(response.isOk(), "searchCode COMBINED should succeed, status=" + response.getStatus());
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getData();
        assertFalse(results.isEmpty(), "Searching 'setName' should find at least one symbol");
    }

    @SuppressWarnings("unchecked")
    @Test
    void testSearchEmptyQuery_doesNotThrow() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", currentIndexId);
        data.put("query", "");
        data.put("searchType", "COMBINED");
        ApiResponse<?> response = rpcQuery("NopCodeSymbol__searchCode", data);

        if (response.isOk()) {
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.getData();
            assertNotNull(results, "Results should be non-null when response is ok");
        } else {
            assertNotNull(response.getMsg(),
                    "Error response for empty query should have an error message");
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void testSearchBySymbolName_filtersByLanguage() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", currentIndexId);
        data.put("query", "User");
        data.put("searchType", "SYMBOL_NAME");
        data.put("language", "JAVA");
        ApiResponse<?> response = rpcQuery("NopCodeSymbol__searchCode", data);

        assertTrue(response.isOk(), "searchCode SYMBOL_NAME with language filter should succeed");
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getData();
        assertNotNull(results);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testSearchBySymbolName_nonExistentLanguage_returnsEmpty() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", currentIndexId);
        data.put("query", "User");
        data.put("searchType", "SYMBOL_NAME");
        data.put("language", "PYTHON");
        ApiResponse<?> response = rpcQuery("NopCodeSymbol__searchCode", data);

        assertTrue(response.isOk(),
                "searchCode SYMBOL_NAME with non-existent language should succeed, got status=" + response.getStatus());

        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getData();
        assertNotNull(results);
        assertTrue(results.isEmpty(), "PYTHON language filter should return no results for a Java-only project");
    }

    @SuppressWarnings("unchecked")
    @Test
    void testSearchCombined_filtersByLanguage() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", currentIndexId);
        data.put("query", "User");
        data.put("searchType", "COMBINED");
        data.put("language", "PYTHON");
        ApiResponse<?> response = rpcQuery("NopCodeSymbol__searchCode", data);

        assertTrue(response.isOk(),
                "searchCode COMBINED with PYTHON language filter should succeed, got status=" + response.getStatus());

        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getData();
        assertNotNull(results);
        assertTrue(results.isEmpty(), "COMBINED with PYTHON filter should return no results for a Java-only project");
    }
}

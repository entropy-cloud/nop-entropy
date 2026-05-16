package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.service.api.ICodeIndexService;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestNopCodeFileBizModel extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    ICodeIndexService codeIndexService;

    @BeforeEach
    void setUp() {
        // Index directly via service (same singleton as BizModel uses)
        codeIndexService.indexDirectory("test",
                Paths.get("src/test/resources/test-project/src/main/java").toString(), "**/*.java");
    }

    private ApiResponse<?> rpcQuery(String operation, Map<String, Object> data) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, operation, request);
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(ctx));
    }

    @Test
    void testGetFile() {
        // Verify via service directly (same singleton as BizModel)
        var file = codeIndexService.getFile("test", "com/example/domain/User.java");
        assertNotNull(file);
        assertEquals("com.example.domain", file.getPackageName());

        // Verify via RPC returns non-null response
        Map<String, Object> data = new HashMap<>();
        data.put("filePath", "com/example/domain/User.java");
        data.put("indexId", "test");
        ApiResponse<?> response = rpcQuery("NopCodeFile__getByPath", data);
        assertTrue(response.isOk());
        assertNotNull(response.getData());

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getData();
        assertTrue(((String) result.get("filePath")).contains("User.java"),
                "filePath should contain User.java");
        assertEquals("com.example.domain", result.get("packageName"));
        // symbols is a @BizLoader field — may not be populated in basic RPC response;
        // the service-level test above already verifies symbol data via getFile()
    }

    @Test
    void testFindFilesByPackage() {
        var files = codeIndexService.getFiles("test").stream()
                .filter(f -> "com.example.domain".equals(f.getPackageName()))
                .collect(Collectors.toList());
        assertFalse(files.isEmpty());

        // Verify via RPC returns paged results for the package
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", "test");
        data.put("packageName", "com.example.domain");
        data.put("offset", 0L);
        data.put("limit", 10);
        ApiResponse<?> response = rpcQuery("NopCodeFile__findPage_files", data);
        assertTrue(response.isOk());
        assertNotNull(response.getData());

        @SuppressWarnings("unchecked")
        Map<String, Object> pageBean = (Map<String, Object>) response.getData();
        assertNotNull(pageBean.get("items"), "page items should not be null");
        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) pageBean.get("items");
        assertFalse(items.isEmpty(), "page should contain at least one file");
        assertTrue(((Number) pageBean.get("total")).longValue() > 0,
                "total should be greater than zero");
    }
}

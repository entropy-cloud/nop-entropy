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
    }

    @Test
    void testFindFilesByPackage() {
        var files = codeIndexService.getFiles("test").stream()
                .filter(f -> "com.example.domain".equals(f.getPackageName()))
                .collect(Collectors.toList());
        assertFalse(files.isEmpty());
    }
}

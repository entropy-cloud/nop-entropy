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

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestNopCodeHierarchyQueries extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    ICodeIndexService codeIndexService;

    @BeforeEach
    void setUp() {
        codeIndexService.indexDirectory("test",
                Paths.get("src/test/resources/test-project/src/main/java"), "**/*.java");
    }

    private ApiResponse<?> rpcQuery(String operation, Map<String, Object> data) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, operation, request);
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(ctx));
    }

    @Test
    void testTypeHierarchySuper() {
        Map<String, Object> data = new HashMap<>();
        data.put("qualifiedName", "com.example.domain.User");
        data.put("indexId", "test");
        data.put("direction", "super");
        data.put("maxDepth", 3);
        ApiResponse<?> response = rpcQuery("NopCodeTypeHierarchy__getTypeHierarchy", data);
        assertTrue(response.isOk());
        assertNotNull(response.getData());
    }

    @Test
    void testTypeHierarchySub() {
        Map<String, Object> data = new HashMap<>();
        data.put("qualifiedName", "com.example.domain.BaseEntity");
        data.put("indexId", "test");
        data.put("direction", "sub");
        data.put("maxDepth", 3);
        ApiResponse<?> response = rpcQuery("NopCodeTypeHierarchy__getTypeHierarchy", data);
        assertTrue(response.isOk());
        assertNotNull(response.getData());
    }

    @Test
    void testCallHierarchyOutgoing() {
        Map<String, Object> data = new HashMap<>();
        data.put("qualifiedName", "com.example.service.UserService.changeName");
        data.put("indexId", "test");
        data.put("direction", "outgoing");
        data.put("maxDepth", 2);
        ApiResponse<?> response = rpcQuery("NopCodeCallHierarchy__get", data);
        assertTrue(response.isOk());
        assertNotNull(response.getData());
    }

    @Test
    void testBatchGetOutlines() {
        Map<String, Object> data = new HashMap<>();
        data.put("qualifiedNames", List.of("com.example.domain.User", "com.example.domain.Status"));
        data.put("indexId", "test");
        ApiResponse<?> response = rpcQuery("NopCodeType__batchGetOutlines", data);
        assertTrue(response.isOk());
        assertNotNull(response.getData());
    }
}

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
public class TestNopCodeSymbolBizModel extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    ICodeIndexService codeIndexService;

    @BeforeEach
    void setUp() {
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
    void testFindByQualifiedName() {
        var symbol = codeIndexService.findSymbolByQualifiedName("test", "com.example.domain.User");
        assertNotNull(symbol);
        assertEquals("User", symbol.getName());

        Map<String, Object> data = new HashMap<>();
        data.put("qualifiedName", "com.example.domain.User");
        data.put("indexId", "test");
        ApiResponse<?> response = rpcQuery("NopCodeSymbol__findByQualifiedName", data);
        assertTrue(response.isOk());
    }

    @Test
    void testFindSymbolsWithQuery() {
        var symbols = codeIndexService.findSymbols("test", "User",
                List.of(io.nop.code.core.model.CodeSymbolKind.CLASS), null, 20);
        assertFalse(symbols.isEmpty());
        assertTrue(symbols.stream().anyMatch(s -> "User".equals(s.getName())));
    }

    @Test
    void testFindSymbolsByPackage() {
        var symbols = codeIndexService.findSymbols("test", null, null, "com.example.domain", 100);
        assertFalse(symbols.isEmpty());
        assertTrue(symbols.stream().anyMatch(s -> s.getQualifiedName() != null
                && s.getQualifiedName().startsWith("com.example.domain")));
    }
}

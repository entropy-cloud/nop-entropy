package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.api.dto.IndexStatsDTO;
import io.nop.code.service.api.dto.SymbolDTO;
import io.nop.code.service.entity.NopCodeSymbolBizModel;
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
public class TestPhase1BugFixes extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    ICodeIndexService codeIndexService;

    private static final String TEST_PROJECT_PATH =
            Paths.get("src/test/resources/test-project/src/main/java").toString();

    private String currentIndexId;

    @BeforeEach
    void setUp() {
        currentIndexId = "p1fix-" + System.nanoTime();
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
    void testFix1_CallHierarchyReturnsCallees() {
        Map<String, Object> data = new HashMap<>();
        data.put("qualifiedName", "com.example.service.UserService.changeName");
        data.put("indexId", currentIndexId);
        data.put("direction", "outgoing");
        data.put("maxDepth", 2);
        ApiResponse<?> response = rpcQuery("NopCodeSymbol__getCallHierarchy", data);
        assertTrue(response.isOk());
        assertNotNull(response.getData());

        Map<String, Object> root = (Map<String, Object>) response.getData();
        Map<String, Object> rootSymbol = (Map<String, Object>) root.get("symbol");
        assertNotNull(rootSymbol, "Root node should contain 'symbol' map");
        assertEquals("changeName", rootSymbol.get("name"));
        assertEquals("com.example.service.UserService.changeName", rootSymbol.get("qualifiedName"));

        assertTrue(root.containsKey("callees"), "Response should contain callees field");
        List<Map<String, Object>> callees = (List<Map<String, Object>>) root.get("callees");
        assertNotNull(callees, "callees should not be null after fix");

        if (!callees.isEmpty()) {
            boolean hasCalleeWithName = callees.stream().anyMatch(c -> {
                Map<String, Object> sym = (Map<String, Object>) c.get("symbol");
                return sym != null && sym.get("name") != null;
            });
            assertTrue(hasCalleeWithName, "If callees exist, each should have a symbol with a name");
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void testFix1_CallHierarchyReturnsCallers() {
        Map<String, Object> data = new HashMap<>();
        data.put("qualifiedName", "com.example.domain.User.setName");
        data.put("indexId", currentIndexId);
        data.put("direction", "incoming");
        data.put("maxDepth", 2);
        ApiResponse<?> response = rpcQuery("NopCodeSymbol__getCallHierarchy", data);
        assertTrue(response.isOk());
        assertNotNull(response.getData());

        Map<String, Object> root = (Map<String, Object>) response.getData();
        Map<String, Object> rootSymbol = (Map<String, Object>) root.get("symbol");
        assertNotNull(rootSymbol, "Root node should contain 'symbol' map");
        assertEquals("setName", rootSymbol.get("name"));

        assertTrue(root.containsKey("callers"), "Response should contain callers field");
        List<Map<String, Object>> callers = (List<Map<String, Object>>) root.get("callers");
        assertNotNull(callers, "callers should not be null after fix");
    }

    @SuppressWarnings("unchecked")
    @Test
    void testFix2_UsageTablePopulated() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", currentIndexId);
        data.put("qualifiedName", "com.example.domain.User");
        data.put("kind", "EXTENDS");
        ApiResponse<?> response = rpcQuery("NopCodeSymbol__findReferencedBy", data);
        assertTrue(response.isOk());
        assertNotNull(response.getData());

        List<Map<String, Object>> refs = (List<Map<String, Object>>) response.getData();
        assertNotNull(refs, "findReferencedBy should not return null");
        assertFalse(refs.isEmpty(), "User should be referenced (extends/implements) after usage population fix");
    }

    @SuppressWarnings("unchecked")
    @Test
    void testFix4_SourceCodeNotNull() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", currentIndexId);
        data.put("filePath", "com/example/domain/User.java");
        ApiResponse<?> response = rpcQuery("NopCodeFile__getByPath", data);
        assertTrue(response.isOk());
        assertNotNull(response.getData());

        Map<String, Object> fileResult = (Map<String, Object>) response.getData();
        Object sourceCode = fileResult.get("sourceCode");
        assertNotNull(sourceCode, "sourceCode should not be null after fix");
        assertFalse(sourceCode.toString().isEmpty(), "sourceCode should not be empty");
    }

    @Test
    void testFix5_IndexIdRequiredOnUsages() {
        NopCodeSymbolBizModel bizModel = new NopCodeSymbolBizModel();
        SymbolDTO symbol = new SymbolDTO();
        symbol.setId("test-symbol-id");
        symbol.setName("test");

        List<?> result = bizModel.usages(symbol, null, 10);
        assertTrue(result.isEmpty(), "usages should return empty list when indexId is null");
    }

    @Test
    void testFix5_IndexIdRequiredOnSourceCode() {
        NopCodeSymbolBizModel bizModel = new NopCodeSymbolBizModel();
        SymbolDTO symbol = new SymbolDTO();
        symbol.setId("test-symbol-id");
        symbol.setName("test");

        String result = bizModel.sourceCode(symbol, null, 0, 5);
        assertNull(result, "sourceCode should return null when indexId is null");
    }

    @SuppressWarnings("unchecked")
    @Test
    void testFix6_AnalysisCacheConsistentResults() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", currentIndexId);

        ApiResponse<?> response1 = rpcQuery("NopCodeIndex__detectCommunities", data);
        assertTrue(response1.isOk());
        Map<String, Object> result1 = (Map<String, Object>) response1.getData();
        assertNotNull(result1);

        assertNotNull(result1.get("totalSymbols"), "totalSymbols should not be null");
        int totalSymbols1 = (Integer) result1.get("totalSymbols");
        assertNotNull(result1.get("totalCommunities"), "totalCommunities should not be null");
        int totalCommunities1 = (Integer) result1.get("totalCommunities");

        ApiResponse<?> response2 = rpcQuery("NopCodeIndex__detectCommunities", data);
        assertTrue(response2.isOk());
        Map<String, Object> result2 = (Map<String, Object>) response2.getData();
        assertNotNull(result2);

        assertNotNull(result2.get("totalSymbols"), "totalSymbols should not be null");
        int totalSymbols2 = (Integer) result2.get("totalSymbols");
        assertNotNull(result2.get("totalCommunities"), "totalCommunities should not be null");
        int totalCommunities2 = (Integer) result2.get("totalCommunities");

        assertEquals(totalSymbols1, totalSymbols2,
                "Second detectCommunities should return same totalSymbols (cache hit)");
        assertEquals(totalCommunities1, totalCommunities2,
                "Second detectCommunities should return same totalCommunities (cache hit)");
    }

    @Test
    void testFix7_FileIdStabilityAfterReindex() {
        IndexStatsDTO stats1 = codeIndexService.getIndexStats(currentIndexId);
        assertNotNull(stats1);
        int fileCount1 = stats1.getFileCount();
        int symbolCount1 = stats1.getSymbolCount();
        assertTrue(fileCount1 > 0, "Should have indexed files");
        assertTrue(symbolCount1 > 0, "Should have indexed symbols");

        List<io.nop.code.core.model.CodeSymbol> symbolsBefore =
                codeIndexService.getFileSymbols(currentIndexId, "com/example/domain/User.java");
        assertFalse(symbolsBefore.isEmpty(), "Should have symbols for User.java before re-index");

        codeIndexService.deleteIndex(currentIndexId);
        codeIndexService.indexDirectory(currentIndexId, TEST_PROJECT_PATH, "**/*.java");

        IndexStatsDTO stats2 = codeIndexService.getIndexStats(currentIndexId);
        assertNotNull(stats2);
        assertEquals(fileCount1, stats2.getFileCount(),
                "Re-indexing same project should produce same file count (stable file IDs)");
        assertEquals(symbolCount1, stats2.getSymbolCount(),
                "Re-indexing same project should produce same symbol count (stable file IDs)");

        List<io.nop.code.core.model.CodeSymbol> symbolsAfter =
                codeIndexService.getFileSymbols(currentIndexId, "com/example/domain/User.java");
        assertFalse(symbolsAfter.isEmpty(), "Should have symbols for User.java after re-index");
        assertEquals(symbolsBefore.size(), symbolsAfter.size(),
                "Same number of symbols for User.java after re-index (deterministic IDs)");
    }
}

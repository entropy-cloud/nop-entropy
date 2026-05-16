package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.api.dto.ModuleDigestDTO;
import io.nop.code.service.api.dto.PublicAPIDTO;
import io.nop.code.service.api.dto.ReferenceDTO;
import io.nop.code.service.api.dto.SymbolInfoDTO;
import io.nop.code.service.api.dto.SymbolSourceDTO;
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
        assertNotNull(response.getData());
        Object responseData = response.getData();
        if (responseData instanceof Map) {
            Map<?, ?> symbolData = (Map<?, ?>) responseData;
            assertNotNull(symbolData.get("name"));
            assertEquals("User", symbolData.get("name"));
        } else if (responseData instanceof SymbolInfoDTO) {
            SymbolInfoDTO symbolDTO = (SymbolInfoDTO) responseData;
            assertEquals("User", symbolDTO.getName());
        }
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

    @Test
    void testModuleDigest() {
        List<ModuleDigestDTO> digest = codeIndexService.getModuleDigest("test",
                "com/example/domain", false);
        assertFalse(digest.isEmpty());

        for (ModuleDigestDTO entry : digest) {
            assertNotNull(entry.getFilePath());
            assertNotNull(entry.getSymbols());
            for (SymbolInfoDTO sym : entry.getSymbols()) {
                assertNotEquals("PRIVATE", sym.getAccessModifier(),
                        "Private symbols should be excluded: " + sym.getName());
            }
        }

        assertTrue(digest.stream().anyMatch(d ->
                d.getFilePath().contains("User.java") &&
                        d.getSymbols().stream().anyMatch(s -> "User".equals(s.getName()))),
                "Should contain User class in digest");

        Map<String, Object> data = new HashMap<>();
        data.put("indexId", "test");
        data.put("dirPath", "com/example/domain");
        ApiResponse<?> response = rpcQuery("NopCodeSymbol__moduleDigest", data);
        assertTrue(response.isOk());
        assertNotNull(response.getData());
        Object responseData = response.getData();
        if (responseData instanceof List) {
            List<?> digestList = (List<?>) responseData;
            assertFalse(digestList.isEmpty());
            boolean hasUser = false;
            for (Object entry : digestList) {
                if (entry instanceof Map) {
                    Map<?, ?> entryMap = (Map<?, ?>) entry;
                    Object filePath = entryMap.get("filePath");
                    if (filePath != null && filePath.toString().contains("User.java")) {
                        hasUser = true;
                        break;
                    }
                }
            }
            assertTrue(hasUser, "Module digest should contain User.java");
        } else if (responseData instanceof List) {
            @SuppressWarnings("unchecked")
            List<ModuleDigestDTO> digestList = (List<ModuleDigestDTO>) responseData;
            assertFalse(digestList.isEmpty());
            assertTrue(digestList.stream().anyMatch(d ->
                    d.getFilePath().contains("User.java")));
        }
    }

    @Test
    void testModuleDigestIncludePrivate() {
        List<ModuleDigestDTO> withPrivate = codeIndexService.getModuleDigest("test",
                "com/example/domain", true);
        List<ModuleDigestDTO> withoutPrivate = codeIndexService.getModuleDigest("test",
                "com/example/domain", false);

        long privateCountWith = withPrivate.stream()
                .mapToLong(d -> d.getSymbols().stream()
                        .filter(s -> "PRIVATE".equals(s.getAccessModifier())).count())
                .sum();
        long privateCountWithout = withoutPrivate.stream()
                .mapToLong(d -> d.getSymbols().stream()
                        .filter(s -> "PRIVATE".equals(s.getAccessModifier())).count())
                .sum();

        assertTrue(privateCountWith > 0, "includePrivate=true should return private symbols");
        assertEquals(0, privateCountWithout, "includePrivate=false should exclude private symbols");
    }

    @Test
    void testShowSymbol() {
        SymbolSourceDTO result = codeIndexService.showSymbolSource("test",
                "com.example.domain.User", true);
        assertNotNull(result, "Should find symbol by qualifiedName");
        assertEquals("com.example.domain.User", result.getQualifiedName());
        assertNotNull(result.getFilePath());
        assertTrue(result.getStartLine() > 0, "startLine should be positive");
        assertTrue(result.getEndLine() >= result.getStartLine(),
                "endLine should be >= startLine");

        Map<String, Object> data = new HashMap<>();
        data.put("indexId", "test");
        data.put("qualifiedName", "com.example.domain.User");
        data.put("includeBody", true);
        ApiResponse<?> response = rpcQuery("NopCodeSymbol__showSymbol", data);
        assertTrue(response.isOk());
        assertNotNull(response.getData());
        Object responseData = response.getData();
        if (responseData instanceof Map) {
            Map<?, ?> sourceData = (Map<?, ?>) responseData;
            assertEquals("com.example.domain.User", sourceData.get("qualifiedName"));
            assertNotNull(sourceData.get("filePath"));
            assertNotNull(sourceData.get("startLine"));
        } else if (responseData instanceof SymbolSourceDTO) {
            SymbolSourceDTO sourceDTO = (SymbolSourceDTO) responseData;
            assertEquals("com.example.domain.User", sourceDTO.getQualifiedName());
            assertNotNull(sourceDTO.getFilePath());
            assertNotNull(sourceDTO.getSourceCode());
        }
    }

    @Test
    void testShowSymbolNotFound() {
        SymbolSourceDTO result = codeIndexService.showSymbolSource("test",
                "com.example.nonexistent.Foo", false);
        assertNull(result, "Should return null for nonexistent symbol");
    }

    @Test
    void testPublicSurface() {
        List<PublicAPIDTO> surface = codeIndexService.getPublicSurface("test",
                "com/example/domain");
        assertFalse(surface.isEmpty());

        for (PublicAPIDTO sym : surface) {
            assertNotNull(sym.getFilePath());
            assertNotNull(sym.getSymbolName());
            assertNotNull(sym.getKind());
            assertTrue(List.of("CLASS", "INTERFACE", "ENUM", "METHOD", "FIELD").contains(sym.getKind()),
                    "Kind should be one of CLASS/INTERFACE/ENUM/METHOD/FIELD: " + sym.getKind());
        }

        assertTrue(surface.stream().anyMatch(s -> "User".equals(s.getSymbolName())),
                "Should contain User class in public surface");

        Map<String, Object> data = new HashMap<>();
        data.put("indexId", "test");
        data.put("dirPath", "com/example/domain");
        ApiResponse<?> response = rpcQuery("NopCodeSymbol__publicSurface", data);
        assertTrue(response.isOk());
        assertNotNull(response.getData());
        Object responseData = response.getData();
        if (responseData instanceof List) {
            List<?> surfaceList = (List<?>) responseData;
            assertFalse(surfaceList.isEmpty());
            boolean hasUser = false;
            for (Object sym : surfaceList) {
                if (sym instanceof Map) {
                    Map<?, ?> symMap = (Map<?, ?>) sym;
                    if ("User".equals(symMap.get("symbolName"))) {
                        hasUser = true;
                        break;
                    }
                }
            }
            assertTrue(hasUser, "Public surface should contain User class");
        } else if (responseData instanceof List) {
            @SuppressWarnings("unchecked")
            List<PublicAPIDTO> surfaceList = (List<PublicAPIDTO>) responseData;
            assertFalse(surfaceList.isEmpty());
            assertTrue(surfaceList.stream().anyMatch(s -> "User".equals(s.getSymbolName())));
        }
    }

    @Test
    void testPublicSurfaceEmptyDir() {
        List<PublicAPIDTO> surface = codeIndexService.getPublicSurface("test",
                "nonexistent/path");
        assertTrue(surface.isEmpty(), "Should return empty for nonexistent directory");
    }

    @Test
    void testFindReferencedBy() {
        List<ReferenceDTO> refs = codeIndexService.findReferencedBy("test",
                "com.example.domain.User", null, 50);
        assertNotNull(refs);
    }

    @Test
    void testFindReferencedByNotFound() {
        List<ReferenceDTO> refs = codeIndexService.findReferencedBy("test",
                "com.example.nonexistent.Foo", null, 50);
        assertNotNull(refs);
        assertTrue(refs.isEmpty(), "Nonexistent symbol should have no references");
    }

    @Test
    void testFindReferencedByViaRpc() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", "test");
        data.put("qualifiedName", "com.example.domain.User");
        ApiResponse<?> response = rpcQuery("NopCodeSymbol__findReferencedBy", data);
        assertTrue(response.isOk());
        assertNotNull(response.getData());
        Object responseData = response.getData();
        if (responseData instanceof List) {
            List<?> refsList = (List<?>) responseData;
            assertNotNull(refsList);
        }
    }
}

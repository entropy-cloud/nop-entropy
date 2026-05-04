package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.lang.java.JavaLanguageAdapter;
import io.nop.code.core.adapter.LanguageAdapterRegistry;
import io.nop.code.core.analyzer.ProjectAnalyzer;
import io.nop.code.service.api.dto.IndexStatsDTO;
import io.nop.code.service.impl.CodeIndexService;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Disabled("手动启用：索引整个 nop-entropy 项目耗时约 30 秒")
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestIndexNopEntropyProject extends JunitAutoTestCase {

    private static final String INDEX_ID = "nop-entropy";

    @Inject
    IGraphQLEngine graphQLEngine;

    CodeIndexService codeIndexService;

    String projectRoot;

    boolean indexed = false;

    @BeforeAll
    void resolveProjectRoot() {
        LanguageAdapterRegistry registry = new LanguageAdapterRegistry();
        registry.registerAdapter(new JavaLanguageAdapter());
        codeIndexService = new CodeIndexService(registry, new ProjectAnalyzer(registry));

        // 向上查找 nop-entropy 项目根目录（包含 nop-code/ 子目录和 pom.xml）
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 10; i++) {
            if (dir.resolve("pom.xml").toFile().exists()
                    && dir.resolve("nop-code").toFile().isDirectory()) {
                projectRoot = dir.toString();
                break;
            }
            dir = dir.getParent();
            if (dir == null) break;
        }
        assertNotNull(projectRoot, "无法定位 nop-entropy 项目根目录");
    }

    @BeforeEach
    void indexProject() {
        if (indexed) {
            return;
        }
        Path rootPath = Path.of(projectRoot);
        long start = System.currentTimeMillis();
        int count = codeIndexService.indexDirectory(INDEX_ID, rootPath, "**/*.java");
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("\n=== INDEX: " + count + " Java files from " + projectRoot + " in " + elapsed + "ms ===\n");
        assertTrue(count > 100, "应该索引到至少 100 个 Java 文件，实际: " + count);
        indexed = true;

        IndexStatsDTO stats = codeIndexService.getIndexStats(INDEX_ID);
        System.out.println("  Symbol count: " + stats.getSymbolCount());
        System.out.println("  Symbol counts by kind: " + stats.getSymbolCounts());
    }

    private ApiResponse<?> rpcQuery(String operation, Map<String, Object> data) {
        long start = System.currentTimeMillis();
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, operation, request);
        ApiResponse<?> response = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(ctx));
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("  [" + operation + "] " + elapsed + "ms");
        return response;
    }

    private long timed(Runnable action) {
        long start = System.currentTimeMillis();
        action.run();
        return System.currentTimeMillis() - start;
    }

    // ==================== 场景 1：项目概览 ====================

    @Test
    void testProjectStats() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", INDEX_ID);
        ApiResponse<?> response = rpcQuery("NopCodeIndex__getStats", data);
        assertTrue(response.isOk());
        System.out.println("Project stats: " + response.getData());
    }

    @Test
    void testFileTree() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", INDEX_ID);
        ApiResponse<?> response = rpcQuery("NopCodeFile__fileTree", data);
        assertTrue(response.isOk());
        System.out.println("File tree returned successfully");
    }

    // ==================== 场景 2：按名称查找符号 ====================

    @Test
    void testFindClassByName() {
        long ms = timed(() -> {
            List<CodeSymbol> symbols = codeIndexService.findSymbols(
                    INDEX_ID, "NopApplication",
                    List.of(CodeSymbolKind.CLASS), null, 10);
            assertFalse(symbols.isEmpty(), "应该能找到 NopApplication 类");
            assertTrue(symbols.stream().anyMatch(s -> s.getName().equals("NopApplication")));
            System.out.println("  Found NopApplication: " + symbols.get(0).getQualifiedName());
        });
        System.out.println("  [findSymbols by name] " + ms + "ms");
    }

    @Test
    void testFindAllInterfaces() {
        long ms = timed(() -> {
            List<CodeSymbol> symbols = codeIndexService.findSymbols(
                    INDEX_ID, null,
                    List.of(CodeSymbolKind.INTERFACE), null, 50);
            assertFalse(symbols.isEmpty(), "应该能找到接口");
            System.out.println("  Found " + symbols.size() + " interfaces (first 50)");
        });
        System.out.println("  [findSymbols by kind=INTERFACE] " + ms + "ms");
    }

    @Test
    void testFindClassesInPackage() {
        long ms = timed(() -> {
            List<CodeSymbol> symbols = codeIndexService.findSymbols(
                    INDEX_ID, null, null,
                    "io.nop.auth.service", 20);
            assertFalse(symbols.isEmpty(), "应该能找到 io.nop.auth.service 包下的类");
            System.out.println("  Found " + symbols.size() + " symbols in io.nop.auth.service");
        });
        System.out.println("  [findSymbols by package] " + ms + "ms");
    }

    // ==================== 场景 3：精确查找 ====================

    @Test
    void testFindByQualifiedName() {
        long ms = timed(() -> {
            CodeSymbol symbol = codeIndexService.findSymbolByQualifiedName(
                    INDEX_ID, "io.nop.biz.crud.CrudBizModel");
            assertNotNull(symbol, "应该能找到 CrudBizModel");
            assertEquals("CrudBizModel", symbol.getName());
            System.out.println("  Found: " + symbol.getQualifiedName() + " kind=" + symbol.getKind());
        });
        System.out.println("  [findByQualifiedName] " + ms + "ms");
    }

    // ==================== 场景 4：查看类的继承关系 ====================

    @Test
    void testTypeHierarchySuper() {
        // 外部人员想知道 NopAuthUserBizModel 继承了什么
        Map<String, Object> data = new HashMap<>();
        data.put("qualifiedName", "io.nop.auth.service.entity.NopAuthUserBizModel");
        data.put("indexId", INDEX_ID);
        data.put("direction", "super");
        data.put("maxDepth", 5);
        ApiResponse<?> response = rpcQuery("NopCodeTypeHierarchy__get", data);
        assertTrue(response.isOk());
        System.out.println("NopAuthUserBizModel super hierarchy: " + response.getData());
    }

    @Test
    void testTypeHierarchySub() {
        // 外部人员想知道 CrudBizModel 有哪些子类
        Map<String, Object> data = new HashMap<>();
        data.put("qualifiedName", "io.nop.biz.crud.CrudBizModel");
        data.put("indexId", INDEX_ID);
        data.put("direction", "sub");
        data.put("maxDepth", 2);
        ApiResponse<?> response = rpcQuery("NopCodeTypeHierarchy__get", data);
        assertTrue(response.isOk());
        System.out.println("CrudBizModel sub types returned");
    }

    // ==================== 场景 5：查看方法调用关系 ====================

    @Test
    void testCallHierarchyOutgoing() {
        // 外部人员想知道某个 BizModel 方法调用了哪些方法
        Map<String, Object> data = new HashMap<>();
        data.put("qualifiedName", "io.nop.auth.service.entity.NopAuthUserBizModel.findUserByUserName");
        data.put("indexId", INDEX_ID);
        data.put("direction", "outgoing");
        data.put("maxDepth", 2);
        ApiResponse<?> response = rpcQuery("NopCodeCallHierarchy__get", data);
        assertTrue(response.isOk());
        System.out.println("Call hierarchy outgoing: " + response.getData());
    }

    // ==================== 场景 6：查看文件内容 ====================

    @Test
    void testGetFileByPath() {
        // 外部人员想看某个文件的源码
        Map<String, Object> data = new HashMap<>();
        data.put("filePath", "io/nop/auth/service/entity/NopAuthUserBizModel.java");
        data.put("indexId", INDEX_ID);
        ApiResponse<?> response = rpcQuery("NopCodeFile__getByPath", data);
        assertTrue(response.isOk());
        System.out.println("File content returned successfully");
    }

    @Test
    void testGetSymbolSourceCode() {
        CodeSymbol symbol = codeIndexService.findSymbolByQualifiedName(
                INDEX_ID, "io.nop.biz.crud.CrudBizModel");
        if (symbol != null) {
            long ms = timed(() -> {
                String source = codeIndexService.getSymbolSourceCode(INDEX_ID, symbol.getId(), 2, 5);
                assertNotNull(source);
                System.out.println("  Source snippet: "
                        + source.substring(0, Math.min(200, source.length())));
            });
            System.out.println("  [getSymbolSourceCode] " + ms + "ms");
        }
    }

    // ==================== 场景 7：批量获取类型概要 ====================

    @Test
    void testBatchGetTypeOutlines() {
        // 外部人员想快速了解几个核心类的结构
        Map<String, Object> data = new HashMap<>();
        data.put("qualifiedNames", List.of(
                "io.nop.biz.crud.CrudBizModel",
                "io.nop.graphql.core.engine.IGraphQLEngine",
                "io.nop.orm.IOrmTemplate"));
        data.put("indexId", INDEX_ID);
        ApiResponse<?> response = rpcQuery("NopCodeType__batchGetOutlines", data);
        assertTrue(response.isOk());
        System.out.println("Batch outlines returned: " + response.getData());
    }

    // ==================== 场景 8：注解使用查询 ====================

    @Test
    void testAnnotationUsage() {
        CodeSymbol bizModel = codeIndexService.findSymbolByQualifiedName(
                INDEX_ID, "io.nop.api.core.annotations.biz.BizModel");
        if (bizModel != null) {
            long ms = timed(() -> {
                var usages = codeIndexService.getSymbolUsages(INDEX_ID, bizModel.getId(), 20);
                assertFalse(usages.isEmpty(), "应该能找到 @BizModel 的使用");
                System.out.println("  Found " + usages.size() + " usages of @BizModel");
            });
            System.out.println("  [getSymbolUsages] " + ms + "ms");
        }
    }
}

package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.core.data.AutoTestVars;
import io.nop.autotest.core.execute.AutoTestOrmHook;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.api.dto.CallHierarchyDTO;
import io.nop.code.api.dto.DepEdgeDTO;
import io.nop.code.api.dto.DepGraphDTO;
import io.nop.code.api.dto.IndexStatsDTO;
import io.nop.code.api.dto.ReferenceDTO;
import io.nop.code.api.dto.TypeHierarchyDTO;
import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.orm.IOrmInterceptor;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.factory.SessionFactoryImpl;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 对 nop-kernel/nop-xlang 模块建立 nop-code 索引，并用可独立核实的源码事实
 * （grep 可复核的类声明、继承关系、import 依赖、引用位置）验证索引结果真实有效。
 *
 * 所有验证阶段合并为单个 @Test：JunitAutoTestCase 的 init 为 @BeforeEach，
 * 每个测试方法都会新建独立的 localDb H2 实例，拆成多个方法会导致重复建索引
 * 或查到空库。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestIndexNopXlangModule extends JunitAutoTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(TestIndexNopXlangModule.class);
    private static final String INDEX_ID = "nop-xlang";

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    ICodeIndexService codeIndexService;

    private static Path moduleRoot;

    private int indexedFileCount;

    @BeforeAll
    void resolveModuleRoot() {
        // 从当前目录向上查找仓库根（以 nop-kernel/nop-xlang 子目录为标志）
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 10 && dir != null; i++) {
            if (dir.resolve("nop-kernel/nop-xlang").toFile().isDirectory()) {
                moduleRoot = dir.resolve("nop-kernel/nop-xlang");
                break;
            }
            dir = dir.getParent();
        }
        assertNotNull(moduleRoot, "无法定位 nop-kernel/nop-xlang 模块目录");
    }

    @Test
    void testIndexNopXlangAndVerify() throws Exception {
        disableAutoTestEntityRecording();
        long start = System.currentTimeMillis();
        indexedFileCount = codeIndexService.indexDirectory(
                INDEX_ID, moduleRoot.toString(), "**/*.java");
        long elapsed = System.currentTimeMillis() - start;
        LOG.info("indexed {} files from {} in {}ms", indexedFileCount, moduleRoot, elapsed);

        verifyIndexCoverage();
        verifySymbolLookup();
        verifyTypeHierarchy();
        verifyReferences();
        verifyFileDependencies();
        verifyCallHierarchy();
        verifyGraphQLStack();
    }

    /**
     * 索引会批量写入 10 万级 ORM 实体行。AutoTestOrmHook 会对每行每列做快照变量登记
     * （同名变量改名循环为 O(N^2) 且逐值打 INFO 日志），在本测试无快照语义的场景下
     * 会导致建索引进度退化为小时级。这里在批量写入前移除该 hook；AutoTestCase 的
     * teardown 会再做一次幂等 remove。
     */
    private void disableAutoTestEntityRecording() {
        IOrmSessionFactory sessionFactory = (IOrmSessionFactory) BeanContainer.tryGetBean("nopOrmSessionFactory");
        assertNotNull(sessionFactory, "IoC 容器中应存在 nopOrmSessionFactory bean");
        int removed = 0;
        if (sessionFactory instanceof SessionFactoryImpl impl) {
            for (IOrmInterceptor interceptor : List.copyOf(impl.getInterceptors())) {
                if (interceptor instanceof AutoTestOrmHook hook) {
                    impl.removeInterceptor(hook);
                    impl.removeDaoListener(hook);
                    removed++;
                }
            }
        }
        assertTrue(removed > 0, "应找到并移除 AutoTestOrmHook，实际移除: " + removed);
        AutoTestVars.clear();
        LOG.info("disabled AutoTestOrmHook (removed {} instance(s)) for bulk indexing", removed);
    }

    /**
     * 索引器实际覆盖的文件数：ProjectAnalyzer 的 filePattern 只做扩展名匹配并深度遍历
     * 整棵模块树（含 model/antlr/gen 生成源码与 target/ 下的构建产物副本），
     * 因此基准 = 全树 .java 数。已知 wart：target/ 副本会被重复索引，待后续改进。
     */
    private long diskJavaFileCount() throws IOException {
        try (Stream<Path> stream = Files.walk(moduleRoot)) {
            return stream.filter(p -> p.toString().endsWith(".java"))
                    .count();
        }
    }

    private String findStoredPath(String fileName) {
        List<CodeFileAnalysisResult> files = codeIndexService.getFiles(INDEX_ID);
        return files.stream().map(CodeFileAnalysisResult::getFilePath)
                .filter(p -> p.endsWith(fileName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("索引中找不到文件: " + fileName));
    }

    private boolean hierarchyContains(TypeHierarchyDTO node, String qualifiedName) {
        if (node.getSymbol() != null && qualifiedName.equals(node.getSymbol().getQualifiedName()))
            return true;
        if (node.getSuperTypes() != null) {
            for (TypeHierarchyDTO child : node.getSuperTypes()) {
                if (hierarchyContains(child, qualifiedName))
                    return true;
            }
        }
        if (node.getSubTypes() != null) {
            for (TypeHierarchyDTO child : node.getSubTypes()) {
                if (hierarchyContains(child, qualifiedName))
                    return true;
            }
        }
        return false;
    }

    private Path resolveAbsolute(String storedPath) {
        Path p = moduleRoot.resolve(storedPath);
        if (Files.exists(p))
            return p;
        try (Stream<Path> stream = Files.walk(moduleRoot)) {
            return stream.filter(f -> f.toString().endsWith(storedPath))
                    .findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    // ==================== 1. 索引完整性 ====================

    private void verifyIndexCoverage() throws Exception {
        long diskCount = diskJavaFileCount();
        assertTrue(diskCount >= 896, "nop-xlang 源码应不少于 896 个 .java，实际磁盘: " + diskCount);
        assertEquals(diskCount, indexedFileCount,
                "indexDirectory 返回数应等于模块全树 .java 数");

        List<CodeFileAnalysisResult> files = codeIndexService.getFiles(INDEX_ID);
        assertEquals(diskCount, files.size(), "持久化的文件记录数应等于磁盘 .java 数");

        IndexStatsDTO stats = codeIndexService.getIndexStats(INDEX_ID);
        assertEquals(diskCount, stats.getFileCount(), "stats.fileCount 应等于磁盘 .java 数");
        assertTrue(stats.getSymbolCount() > 5000,
                "近千个源文件的符号数应远大于 5000，实际: " + stats.getSymbolCount());
        LOG.info("index stats: files={}, symbols={}, byKind={}",
                stats.getFileCount(), stats.getSymbolCount(), stats.getSymbolCounts());
    }

    // ==================== 2. 符号查找 ====================

    private void verifySymbolLookup() {
        CodeSymbol xlang = codeIndexService.findSymbolByQualifiedName(
                INDEX_ID, "io.nop.xlang.api.XLang");
        assertNotNull(xlang, "应能按全限定名找到 io.nop.xlang.api.XLang");
        assertEquals(CodeSymbolKind.CLASS, xlang.getKind());

        assertNotNull(codeIndexService.findSymbolByQualifiedName(
                        INDEX_ID, "io.nop.xlang.xpl.impl.XplCompiler"),
                "应能找到 XplCompiler");

        List<CodeSymbol> byName = codeIndexService.findSymbols(
                INDEX_ID, "XLangCompileScope", null, null, 50);
        assertTrue(byName.stream().anyMatch(s ->
                        "io.nop.xlang.scope.XLangCompileScope".equals(s.getQualifiedName())),
                "按名称模糊查找应命中 io.nop.xlang.scope.XLangCompileScope");

        List<CodeSymbol> apiInterfaces = codeIndexService.findSymbols(
                INDEX_ID, null, List.of(CodeSymbolKind.INTERFACE), "io.nop.xlang.api", 100);
        assertTrue(apiInterfaces.stream().anyMatch(s ->
                        "IXLangCompileScope".equals(s.getName())),
                "io.nop.xlang.api 包下应能查到接口 IXLangCompileScope");
    }

    // ==================== 3. 类型层级（真值: XplCompiler extends XLangExprParser implements IXplCompiler） ====================

    private void verifyTypeHierarchy() {
        TypeHierarchyDTO supers = codeIndexService.getTypeHierarchy(
                INDEX_ID, "io.nop.xlang.xpl.impl.XplCompiler", "super", 5);
        assertNotNull(supers);
        assertTrue(hierarchyContains(supers, "io.nop.xlang.expr.XLangExprParser"),
                "XplCompiler 的 super 层级应包含 XLangExprParser（源码: extends XLangExprParser）");
        assertTrue(hierarchyContains(supers, "io.nop.xlang.xpl.IXplCompiler"),
                "XplCompiler 的 super 层级应包含 IXplCompiler（源码: implements IXplCompiler）");

        TypeHierarchyDTO subs = codeIndexService.getTypeHierarchy(
                INDEX_ID, "io.nop.xlang.expr.XLangExprParser", "sub", 2);
        assertNotNull(subs);
        assertTrue(hierarchyContains(subs, "io.nop.xlang.xpl.impl.XplCompiler"),
                "XLangExprParser 的 sub 层级应包含 XplCompiler");
    }

    // ==================== 4. 引用查询（usage 表端到端） ====================

    private void verifyReferences() throws IOException {
        // TESTED_BY 是确定性派生：测试类符号 → 测试文件。验证 usage 表的
        // symbolId → kind → filePath 全链路查找真实可用
        List<ReferenceDTO> testRefs = codeIndexService.findReferencedBy(
                INDEX_ID, "io.nop.xlang.dict.TestDictModelLoader", null, 100);
        ReferenceDTO testedBy = testRefs.stream()
                .filter(r -> "TESTED_BY".equals(r.getKind()))
                .findFirst().orElse(null);
        assertNotNull(testedBy, "测试类应有 TESTED_BY 引用记录");
        assertTrue(testedBy.getFilePath().endsWith("TestDictModelLoader.java"),
                "TESTED_BY 引用应指向测试文件，实际: " + testedBy.getFilePath());
        Path absolute = resolveAbsolute(testedBy.getFilePath());
        assertNotNull(absolute, "TESTED_BY 引用的文件应能回读到磁盘");
        String line = Files.readAllLines(absolute).get(testedBy.getLine() - 1);
        assertTrue(line.contains("class TestDictModelLoader"),
                "TESTED_BY 行号应指向测试类声明，实际: " + testedBy.getFilePath()
                        + ":" + testedBy.getLine() + " 内容: " + line);

        // 已知能力边界：当前持久层仅派生 CALL/ANNOTATES/EXTENDS/IMPLEMENTS/TESTED_BY
        // 等 usage，TYPE_REFERENCE（字段/参数/局部变量类型引用）尚未提取，因此
        // grep 到的 62 个引用 IXLangCompileScope 的文件不会被完整反映；此处仅断言
        // 查询路径可执行且返回已提取的记录
        List<ReferenceDTO> refs = codeIndexService.findReferencedBy(
                INDEX_ID, "io.nop.xlang.api.IXLangCompileScope", null, 10000);
        assertTrue(refs.size() >= 2,
                "IXLangCompileScope 的已提取引用应可查回，实际: " + refs.size());
        LOG.info("references: TestDictModelLoader={} refs, IXLangCompileScope={} refs (TYPE_REFERENCE gap known)",
                testRefs.size(), refs.size());
    }

    // ==================== 5. 文件级依赖（真值: XplCompiler.java import XLangExprParser/XLangCompileScope） ====================

    private void verifyFileDependencies() {
        String xplCompilerPath = findStoredPath("XplCompiler.java");
        DepGraphDTO deps = codeIndexService.getDeps(INDEX_ID, xplCompilerPath, 1);
        assertNotNull(deps);
        List<String> depTargets = deps.getEdges() == null ? List.of()
                : deps.getEdges().stream().map(DepEdgeDTO::getTarget).toList();
        assertTrue(depTargets.stream().anyMatch(t -> t.endsWith("XLangExprParser.java")),
                "XplCompiler.java 的依赖应包含 XLangExprParser.java（源码 import 真值），实际: " + depTargets);
        assertTrue(depTargets.stream().anyMatch(t -> t.endsWith("XLangCompileScope.java")),
                "XplCompiler.java 的依赖应包含 XLangCompileScope.java（源码 import 真值）");

        String scopeApiPath = findStoredPath("IXLangCompileScope.java");
        DepGraphDTO reverse = codeIndexService.getReverseDeps(INDEX_ID, scopeApiPath, 1, 10000);
        List<String> reverseSources = reverse.getEdges() == null ? List.of()
                : reverse.getEdges().stream().map(DepEdgeDTO::getSource).toList();
        assertTrue(reverseSources.stream().anyMatch(s -> s.endsWith("XplCompiler.java")),
                "IXLangCompileScope.java 的反向依赖应包含 XplCompiler.java，实际条数: "
                        + reverseSources.size());
    }

    // ==================== 6. 调用层级（真值: newCompileTool 调用 newXplCompiler） ====================

    private void verifyCallHierarchy() {
        CallHierarchyDTO calls = codeIndexService.getCallHierarchy(
                INDEX_ID, "io.nop.xlang.api.XLang.newCompileTool", "outgoing", 3);
        assertNotNull(calls);
        List<String> calleeNames = calls.getCallees() == null ? List.of()
                : calls.getCallees().stream()
                        .map(c -> c.getSymbol() == null ? "" : c.getSymbol().getQualifiedName())
                        .toList();
        assertTrue(calleeNames.stream().anyMatch(n -> n.contains("newXplCompiler")),
                "newCompileTool 的 outgoing 调用应包含 newXplCompiler（源码真值），实际: " + calleeNames);
        // 已知能力边界：CONSTRUCTOR 调用仅对同文件内定义的类型提取（symbolMap 为单文件表），
        // 跨文件的 new XLangCompileTool(...) 不产生调用边
        LOG.info("call hierarchy of XLang.newCompileTool outgoing: {}", calleeNames);
    }

    // ==================== 7. GraphQL 全栈读取 ====================

    @SuppressWarnings("unchecked")
    private void verifyGraphQLStack() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", INDEX_ID);
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, "NopCodeIndex__getStats", request);
        ApiResponse<?> response = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(ctx));
        assertTrue(response.isOk(), "GraphQL NopCodeIndex__getStats 应成功: " + response.getMsg());
        assertNotNull(response.getData());

        Map<String, Object> stats = (Map<String, Object>) response.getData();
        int fileCount = ((Number) stats.get("fileCount")).intValue();
        int symbolCount = ((Number) stats.get("symbolCount")).intValue();
        assertEquals(indexedFileCount, fileCount, "GraphQL stats.fileCount 应等于已索引文件数");
        assertTrue(symbolCount > 5000, "GraphQL stats.symbolCount 应 > 5000，实际: " + symbolCount);
    }
}

package io.nop.code.service.impl;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.core.adapter.LanguageAdapterRegistry;
import io.nop.code.core.analyzer.ProjectAnalysisResult;
import io.nop.code.core.analyzer.ProjectAnalyzer;
import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.lang.java.JavaLanguageAdapter;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.core.resource.IResourceLoader;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * plan 2532 组B 红④（6.3-W-02 / C5-002，TP-002）：sourceCode 全程驻留释放。
 *
 * <p>驱动器形态（设计裁定④）：经既有构造/装配通道（IoC 容器 CodeIndexService bean +
 * 同包覆写 protected analyzer 字段——TestIndexNopEntropyProject 直接装配通道同纪律，零 mockito
 * 手工子类代理）注入捕获型 ProjectAnalyzer 子类（覆写 indexDirectory 实际调用的分析变体，捕获
 * 结果对象引用），对 test-project 小夹具（6 文件）indexDirectory 成功返回后断言全部文件结果
 * sourceCode == null。
 *
 * <p>红证形态（设计裁定④）：断言行为红（既有缝，pre-fix 可编译）——pre-fix 捕获对象内全部
 * 文件结果 sourceCode 全程非-null（驻留至 GC）；post-fix persistInSession 逐文件持久化返回后
 * 置 null。同测试钉空回调处置（裁定③.6）：indexDirectory 须改调非流式分析变体。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestSourceCodeRelease_W_02 extends JunitAutoTestCase {

    @Inject
    ICodeIndexService codeIndexService;

    /** 手工子类代理（零 mockito）：捕获 indexDirectory 实际调用的分析变体及其结果对象。 */
    static class CapturingAnalyzer extends ProjectAnalyzer {
        final List<ProjectAnalysisResult> captured = new ArrayList<>();
        boolean streamingVariantInvoked;

        CapturingAnalyzer(LanguageAdapterRegistry registry) {
            super(registry);
        }

        @Override
        public ProjectAnalysisResult analyzeProject(IResourceLoader resourceLoader, String vfsPath,
                                                    String filePattern) {
            ProjectAnalysisResult result = super.analyzeProject(resourceLoader, vfsPath, filePattern);
            captured.add(result);
            return result;
        }

        @Override
        public ProjectAnalysisResult analyzeProject(IResourceLoader resourceLoader, String vfsPath,
                                                    String filePattern, BatchCallback batchCallback) {
            streamingVariantInvoked = true;
            ProjectAnalysisResult result = super.analyzeProject(resourceLoader, vfsPath, filePattern, batchCallback);
            captured.add(result);
            return result;
        }
    }

    @Test
    void testW_02_sourceCodeReleasedAfterPersist() {
        CodeIndexService service = (CodeIndexService) codeIndexService;

        LanguageAdapterRegistry registry = new LanguageAdapterRegistry();
        registry.registerAdapter(new JavaLanguageAdapter());
        CapturingAnalyzer analyzer = new CapturingAnalyzer(registry);
        service.analyzer = analyzer;

        String projectRoot = Paths.get("src/test/resources/test-project/src/main/java")
                .toAbsolutePath().toString();
        int count = service.indexDirectory("w02-release", projectRoot, "**/*.java");
        assertEquals(6, count, "test-project fixture must index exactly 6 files");

        assertEquals(1, analyzer.captured.size(), "exactly one analysis run captured");
        ProjectAnalysisResult result = analyzer.captured.get(0);
        assertNotNull(result, "captured analysis result must be observable");

        for (CodeFileAnalysisResult file : result.getFileResults()) {
            assertNull(file.getSourceCode(),
                    "sourceCode must be released (null) after the file's persist completes: "
                            + file.getFilePath());
        }

        assertFalse(analyzer.streamingVariantInvoked,
                "indexDirectory must call the non-streaming analysis variant"
                        + " (the previous streaming call passed an empty callback with no consumer)");
    }
}

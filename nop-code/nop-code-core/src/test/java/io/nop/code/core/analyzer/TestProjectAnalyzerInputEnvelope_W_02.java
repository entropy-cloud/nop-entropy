package io.nop.code.core.analyzer;

import io.nop.code.core.adapter.LanguageAdapterRegistry;
import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.core.model.CodeLanguage;
import io.nop.commons.util.FileHelper;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * plan 2532 组B 红③（6.3-W-02 / C5-002，TP-002 主面）：ProjectAnalyzer 死常量激活——
 * 输入面包络守卫（超限文件 skip + 计数超限停止遍历 + truncated 被动标志 + WARN）。
 *
 * <p>TP-002 原文「注入小阈值」——本驱动器注入小阈值（大小上限=64 字节 / 计数上限=1）+ temp 目录
 * 夹具（多文件、含超限文件），断言：超限文件结果缺席、计数截断生效、截断标志置位。
 *
 * <p>红证形态（设计裁定④）：pre-fix 编译期构造红——注入 setter（{@code setMaxFileSizeBytes} /
 * {@code setMaxFileCount}）与标志访问器（{@code ProjectAnalysisResult.isTruncated}）均不存在；
 * post-fix 语义红 = 超限文件被分析或标志未置位。
 */
class TestProjectAnalyzerInputEnvelope_W_02 {

    @TempDir
    Path tempDir;

    private LanguageAdapterRegistry registry;

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private String vfsPath() {
        return FileHelper.getFileUrl(tempDir.toFile());
    }

    @BeforeEach
    void setUp() {
        registry = new LanguageAdapterRegistry();
        registry.registerAdapter(new ILanguageAdapter() {
            private final ICodeFileAnalyzer fileAnalyzer = new ICodeFileAnalyzer() {
                @Override
                public CodeLanguage getLanguage() {
                    return CodeLanguage.JAVA;
                }

                @Override
                public CodeFileAnalysisResult analyze(String filePath, String sourceCode) {
                    CodeFileAnalysisResult result = new CodeFileAnalysisResult();
                    result.setFilePath(filePath);
                    result.setLanguage(CodeLanguage.JAVA);
                    result.setLineCount(sourceCode.split("\n").length);
                    return result;
                }

                @Override
                public List<String> getFileExtensions() {
                    return Collections.singletonList(".java");
                }
            };

            @Override
            public CodeLanguage getLanguage() {
                return CodeLanguage.JAVA;
            }

            @Override
            public ICodeFileAnalyzer getFileAnalyzer() {
                return fileAnalyzer;
            }

            @Override
            public List<String> getFileExtensions() {
                return Collections.singletonList(".java");
            }

            @Override
            public List<String> getExcludePatterns() {
                return Collections.emptyList();
            }
        });
    }

    @Test
    void testW_02_oversizedFileSkippedAndFlagged() throws IOException {
        Files.writeString(tempDir.resolve("Small.java"), "public class Small {}");
        // > 64-byte injected cap: a padded class body well beyond the threshold
        StringBuilder big = new StringBuilder("public class Big {");
        for (int i = 0; i < 20; i++) {
            big.append("\n    // padding line to exceed the injected max file size threshold ");
        }
        big.append("\n}");
        Files.writeString(tempDir.resolve("Big.java"), big.toString());

        ProjectAnalyzer analyzer = new ProjectAnalyzer(registry);
        analyzer.setMaxFileSizeBytes(64);

        ProjectAnalysisResult result = analyzer.analyzeProject(vfsPath());

        List<String> paths = result.getFileResults().stream()
                .map(CodeFileAnalysisResult::getFilePath).collect(Collectors.toList());
        assertTrue(paths.stream().anyMatch(p -> p.endsWith("Small.java")),
                "file within the size cap must still be analyzed");
        assertFalse(paths.stream().anyMatch(p -> p.endsWith("Big.java")),
                "oversized file must be skipped (WARN + truncated), not read into the heap");
        assertTrue(result.isTruncated(),
                "input-envelope truncation flag must be set when an oversized file is skipped");
    }

    @Test
    void testW_02_fileCountCapStopsTraversalAndFlags() throws IOException {
        Files.writeString(tempDir.resolve("A.java"), "public class A {}");
        Files.writeString(tempDir.resolve("B.java"), "public class B {}");
        Files.writeString(tempDir.resolve("C.java"), "public class C {}");

        ProjectAnalyzer analyzer = new ProjectAnalyzer(registry);
        analyzer.setMaxFileCount(1);

        ProjectAnalysisResult result = analyzer.analyzeProject(vfsPath());

        assertEquals(1, result.getFileResults().size(),
                "file-count cap must stop traversal at the injected count");
        assertTrue(result.isTruncated(),
                "input-envelope truncation flag must be set when the count cap stops traversal");
    }

    @Test
    void testW_02_belowThresholdsRunIsCompleteAndUnflagged() throws IOException {
        Files.writeString(tempDir.resolve("Ok.java"), "public class Ok {}");

        ProjectAnalyzer analyzer = new ProjectAnalyzer(registry);
        analyzer.setMaxFileSizeBytes(1024);
        analyzer.setMaxFileCount(10);

        ProjectAnalysisResult result = analyzer.analyzeProject(vfsPath());

        assertFalse(result.isTruncated(), "no truncation below the injected thresholds");
        assertEquals(1, result.getFileResults().size(), "all files analyzed below the thresholds");
    }
}

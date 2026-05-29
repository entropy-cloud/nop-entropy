package io.nop.code.core.analyzer;

import io.nop.code.core.adapter.LanguageAdapterRegistry;
import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.core.model.CodeLanguage;
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

class TestProjectAnalyzerFileFilter {

    @TempDir
    Path tempDir;

    private LanguageAdapterRegistry registry;
    private ProjectAnalyzer analyzer;

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
        analyzer = new ProjectAnalyzer(registry);
    }

    @Test
    void testJavaFilePatternFiltersCorrectly() throws IOException {
        Files.writeString(tempDir.resolve("Foo.java"), "public class Foo {}");
        Files.writeString(tempDir.resolve("image.png"), "PNG binary data");
        Files.writeString(tempDir.resolve("lib.jar"), "JAR binary data");
        Files.writeString(tempDir.resolve("data.json"), "{}");

        var result = analyzer.analyzeProject(tempDir);

        assertEquals(1, result.getFileResults().size());
        assertTrue(result.getFileResults().get(0).getFilePath().endsWith("Foo.java"));
    }

    @Test
    void testOnlyRegisteredExtensionsAnalyzed() throws IOException {
        Files.writeString(tempDir.resolve("Foo.java"), "public class Foo {}");
        Files.writeString(tempDir.resolve("data.json"), "{}");

        var result = analyzer.analyzeProject(tempDir);

        assertEquals(1, result.getFileResults().size());
        assertTrue(result.getFileResults().get(0).getFilePath().endsWith("Foo.java"));
    }

    @Test
    void testExcludePatternDoesNotSubstringMatch() throws IOException {
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
                return Collections.singletonList("**/build/**");
            }
        });
        analyzer = new ProjectAnalyzer(registry);

        Path buildDir = tempDir.resolve("foo").resolve("build");
        Files.createDirectories(buildDir);
        Files.writeString(buildDir.resolve("Bar.java"), "public class Bar {}");

        Path rebuildDir = tempDir.resolve("rebuild").resolve("scripts");
        Files.createDirectories(rebuildDir);
        Files.writeString(rebuildDir.resolve("Baz.java"), "public class Baz {}");

        Files.writeString(tempDir.resolve("Top.java"), "public class Top {}");

        var result = analyzer.analyzeProject(tempDir);

        List<String> paths = result.getFileResults().stream()
                .map(CodeFileAnalysisResult::getFilePath).collect(Collectors.toList());
        assertTrue(paths.stream().anyMatch(p -> p.contains("Baz.java")),
                "rebuild/scripts/Baz.java should NOT be excluded (substring mismatch)");
        assertFalse(paths.stream().anyMatch(p -> p.contains("Bar.java")),
                "foo/build/Bar.java should be excluded");
        assertTrue(paths.stream().anyMatch(p -> p.endsWith("Top.java")),
                "Top.java should not be excluded");
    }
}

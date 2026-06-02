package io.nop.code.core.analyzer;

import io.nop.code.core.adapter.LanguageAdapterRegistry;
import io.nop.code.core.incremental.InMemoryFingerprintStore;
import io.nop.code.core.model.*;
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

import static org.junit.jupiter.api.Assertions.*;

class TestProjectAnalyzerWithStore {

    @TempDir
    Path tempDir;

    private LanguageAdapterRegistry registry;
    private ProjectAnalyzer analyzer;
    private InMemoryFingerprintStore fingerprintStore;

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        registry = new LanguageAdapterRegistry();
        registry.registerAdapter(createMockAdapter());
        analyzer = new ProjectAnalyzer(registry);
        fingerprintStore = new InMemoryFingerprintStore();
    }

    private String vfsPath() {
        return "file:" + tempDir.toAbsolutePath();
    }

    private ILanguageAdapter createMockAdapter() {
        final ICodeFileAnalyzer fileAnalyzer = new ICodeFileAnalyzer() {
            @Override
            public CodeLanguage getLanguage() {
                return CodeLanguage.JAVA;
            }

            @Override
            public CodeFileAnalysisResult analyze(String filePath, String sourceCode) {
                String className = extractClassName(sourceCode);

                CodeFileAnalysisResult result = new CodeFileAnalysisResult();
                result.setFilePath(filePath);
                result.setLanguage(CodeLanguage.JAVA);
                result.setSourceCode(sourceCode);
                result.setLineCount(sourceCode.split("\n").length);

                if (className != null) {
                    CodeSymbol symbol = new CodeSymbol();
                    symbol.setId(className);
                    symbol.setQualifiedName(className);
                    symbol.setName(className);
                    symbol.setKind(CodeSymbolKind.CLASS);
                    result.setSymbols(Collections.singletonList(symbol));
                } else {
                    result.setSymbols(Collections.emptyList());
                }

                result.setCalls(Collections.emptyList());
                return result;
            }

            @Override
            public List<String> getFileExtensions() {
                return Collections.singletonList(".java");
            }

            private String extractClassName(String source) {
                int idx = source.indexOf("class ");
                if (idx < 0) return null;
                String rest = source.substring(idx + "class ".length()).trim();
                int space = rest.indexOf(' ');
                int brace = rest.indexOf('{');
                int end = -1;
                if (space >= 0 && brace >= 0) end = Math.min(space, brace);
                else if (space >= 0) end = space;
                else if (brace >= 0) end = brace;
                if (end < 0) return null;
                return rest.substring(0, end).trim();
            }
        };

        return new ILanguageAdapter() {
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
        };
    }

    @Test
    void testIncrementalWithStoreDetectsChanges() throws IOException, InterruptedException {
        Files.writeString(tempDir.resolve("Foo.java"), "public class Foo { }");

        ProjectAnalysisResult first =
                analyzer.analyzeIncremental(vfsPath(), "test-idx", fingerprintStore);
        assertNotNull(first);

        Thread.sleep(50);
        Files.writeString(tempDir.resolve("Foo.java"), "public class Foo { int x; }");

        ProjectAnalysisResult second =
                analyzer.analyzeIncremental(vfsPath(), "test-idx", fingerprintStore);
        assertNotNull(second);
        assertEquals(1, second.getFileResults().size());
    }

    @Test
    void testIncrementalWithStoreNoChanges() throws IOException {
        Files.writeString(tempDir.resolve("Foo.java"), "public class Foo { }");
        Files.writeString(tempDir.resolve("Bar.java"), "public class Bar { }");

        ProjectAnalysisResult first =
                analyzer.analyzeIncremental(vfsPath(), "test-idx", fingerprintStore);
        assertNotNull(first);

        ProjectAnalysisResult second =
                analyzer.analyzeIncremental(vfsPath(), "test-idx", fingerprintStore);
        assertNotNull(second);
        assertEquals(2, second.getFileResults().size());
    }

    @Test
    void testIncrementalWithStoreMixedAddModifyDelete() throws IOException, InterruptedException {
        Files.writeString(tempDir.resolve("Foo.java"), "public class Foo { }");
        Files.writeString(tempDir.resolve("Bar.java"), "public class Bar { }");

        ProjectAnalysisResult first =
                analyzer.analyzeIncremental(vfsPath(), "test-idx", fingerprintStore);
        assertNotNull(first);

        Thread.sleep(50);
        Files.writeString(tempDir.resolve("Foo.java"), "public class Foo { int x; }");
        Files.delete(tempDir.resolve("Bar.java"));
        Files.writeString(tempDir.resolve("Baz.java"), "public class Baz { }");

        ProjectAnalysisResult second =
                analyzer.analyzeIncremental(vfsPath(), "test-idx", fingerprintStore);
        assertNotNull(second);
        assertEquals(2, second.getFileResults().size());
        assertNotNull(second.findSymbol("Baz"));
    }
}

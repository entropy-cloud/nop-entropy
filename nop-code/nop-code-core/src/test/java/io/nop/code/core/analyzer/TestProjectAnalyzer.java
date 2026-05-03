package io.nop.code.core.analyzer;

import io.nop.code.core.adapter.LanguageAdapterRegistry;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProjectAnalyzer file scanning and symbol collection.
 */
class TestProjectAnalyzer {

    @TempDir
    Path tempDir;

    private ICodeFileAnalyzer createMockAnalyzer() {
        return new ICodeFileAnalyzer() {
            @Override
            public CodeLanguage getLanguage() {
                return CodeLanguage.JAVA;
            }

            @Override
            public CodeFileAnalysisResult analyze(String filePath, String sourceCode) {
                // Extract class name from "public class Foo {}"
                String className = extractClassName(sourceCode);

                CodeFileAnalysisResult result = new CodeFileAnalysisResult();
                result.setFilePath(filePath);
                result.setLanguage(CodeLanguage.JAVA);
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
    }

    private ILanguageAdapter createMockAdapter() {
        final ICodeFileAnalyzer analyzer = createMockAnalyzer();
        return new ILanguageAdapter() {
            @Override
            public CodeLanguage getLanguage() {
                return CodeLanguage.JAVA;
            }

            @Override
            public ICodeFileAnalyzer getFileAnalyzer() {
                return analyzer;
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
    void testAnalyzeProjectWithTwoFiles() throws IOException {
        // Create 2 Java files
        Path fooFile = tempDir.resolve("Foo.java");
        Files.writeString(fooFile, "public class Foo {}");

        Path barFile = tempDir.resolve("Bar.java");
        Files.writeString(barFile, "public class Bar {}");

        // Setup registry with mock adapter
        LanguageAdapterRegistry registry = new LanguageAdapterRegistry();
        registry.registerAdapter(createMockAdapter());

        // Run analysis
        ProjectAnalyzer analyzer = new ProjectAnalyzer(registry);
        ProjectAnalyzer.ProjectAnalysisResult result = analyzer.analyzeProject(tempDir);

        // fileResults contains both files
        assertEquals(2, result.getFileResults().size());
        assertTrue(result.getFileResults().stream()
                .anyMatch(r -> r.getFilePath().equals("Foo.java")));
        assertTrue(result.getFileResults().stream()
                .anyMatch(r -> r.getFilePath().equals("Bar.java")));

        // globalSymbolTable is not empty
        assertFalse(result.getGlobalSymbolTable().getAll().isEmpty());

        // stats.totalFiles == 2
        assertEquals(2, result.getStats().getTotalFiles());
    }

    @Test
    void testStatsTotalSymbols() throws IOException {
        Path fooFile = tempDir.resolve("Foo.java");
        Files.writeString(fooFile, "public class Foo {}");

        Path barFile = tempDir.resolve("Bar.java");
        Files.writeString(barFile, "public class Bar {}");

        LanguageAdapterRegistry registry = new LanguageAdapterRegistry();
        registry.registerAdapter(createMockAdapter());

        ProjectAnalyzer analyzer = new ProjectAnalyzer(registry);
        ProjectAnalyzer.ProjectAnalysisResult result = analyzer.analyzeProject(tempDir);

        assertEquals(2, result.getStats().getTotalSymbols());
    }

    @Test
    void testEmptyProjectReturnsEmpty() throws IOException {
        LanguageAdapterRegistry registry = new LanguageAdapterRegistry();
        registry.registerAdapter(createMockAdapter());

        ProjectAnalyzer analyzer = new ProjectAnalyzer(registry);
        ProjectAnalyzer.ProjectAnalysisResult result = analyzer.analyzeProject(tempDir);

        assertEquals(0, result.getFileResults().size());
        assertEquals(0, result.getStats().getTotalFiles());
        assertEquals(0, result.getStats().getTotalSymbols());
    }

    @Test
    void testFindSymbolByQualifiedName() throws IOException {
        Path fooFile = tempDir.resolve("Foo.java");
        Files.writeString(fooFile, "public class Foo {}");

        LanguageAdapterRegistry registry = new LanguageAdapterRegistry();
        registry.registerAdapter(createMockAdapter());

        ProjectAnalyzer analyzer = new ProjectAnalyzer(registry);
        ProjectAnalyzer.ProjectAnalysisResult result = analyzer.analyzeProject(tempDir);

        CodeSymbol found = result.findSymbol("Foo");
        assertNotNull(found);
        assertEquals("Foo", found.getName());
        assertEquals(CodeSymbolKind.CLASS, found.getKind());
    }

    @Test
    void testBuildCallGraph() throws IOException {
        Path fooFile = tempDir.resolve("Foo.java");
        Files.writeString(fooFile, "public class Foo {}");

        LanguageAdapterRegistry registry = new LanguageAdapterRegistry();
        registry.registerAdapter(createMockAdapter());

        ProjectAnalyzer analyzer = new ProjectAnalyzer(registry);
        ProjectAnalyzer.ProjectAnalysisResult result = analyzer.analyzeProject(tempDir);

        // Build call graph (should be empty since mock has no calls)
        CallGraph callGraph = result.buildCallGraph();
        assertNotNull(callGraph);
        assertTrue(callGraph.getAllNodeIds().isEmpty());
    }

    @Test
    void testProgressCallback() throws IOException {
        Path fooFile = tempDir.resolve("Foo.java");
        Files.writeString(fooFile, "public class Foo {}");

        Path barFile = tempDir.resolve("Bar.java");
        Files.writeString(barFile, "public class Bar {}");

        LanguageAdapterRegistry registry = new LanguageAdapterRegistry();
        registry.registerAdapter(createMockAdapter());

        ProjectAnalyzer analyzer = new ProjectAnalyzer(registry);

        final int[] callbackCount = {0};
        ProjectAnalyzer.ProgressCallback callback = (current, total, message) -> {
            callbackCount[0]++;
        };

        ProjectAnalyzer.ProjectAnalysisResult result =
                analyzer.analyzeProject(tempDir, callback);

        assertTrue(callbackCount[0] > 0, "Progress callback should have been called");
    }
}

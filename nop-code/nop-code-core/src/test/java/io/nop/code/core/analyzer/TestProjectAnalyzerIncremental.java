package io.nop.code.core.analyzer;

import io.nop.code.core.adapter.LanguageAdapterRegistry;
import io.nop.code.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProjectAnalyzer.analyzeIncremental() — both overloads.
 */
class TestProjectAnalyzerIncremental {

    @TempDir
    Path tempDir;

    private LanguageAdapterRegistry registry;
    private ProjectAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        registry = new LanguageAdapterRegistry();
        registry.registerAdapter(createMockAdapter());
        analyzer = new ProjectAnalyzer(registry);
    }

    // ------------------------------------------------------------------ helpers

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

    /**
     * Writes sample .java files into the given directory.
     */
    private void createTestProject(Path dir) throws IOException {
        Files.writeString(dir.resolve("Foo.java"), "public class Foo { }");
        Files.writeString(dir.resolve("Bar.java"), "public class Bar { }");
    }

    // ------------------------------------------------------------------ tests

    @Test
    void testAnalyzeIncrementalFromScratch() throws IOException {
        createTestProject(tempDir);

        ProjectAnalyzer.ProjectAnalysisResult result =
                analyzer.analyzeIncremental(tempDir, (ProjectAnalyzer.ProjectAnalysisResult) null);

        assertNotNull(result);
        assertEquals(2, result.getFileResults().size());
        assertEquals(2, result.getStats().getTotalFiles());
        assertEquals(2, result.getStats().getTotalSymbols());
        assertNotNull(result.findSymbol("Foo"));
        assertNotNull(result.findSymbol("Bar"));
    }

    @Test
    void testAnalyzeIncrementalNoChanges() throws IOException {
        createTestProject(tempDir);

        ProjectAnalyzer.ProjectAnalysisResult first = analyzer.analyzeProject(tempDir);
        assertEquals(2, first.getFileResults().size());

        ProjectAnalyzer.ProjectAnalysisResult second =
                analyzer.analyzeIncremental(tempDir, first);

        assertNotNull(second);
        assertEquals(2, second.getFileResults().size());
        assertEquals(2, second.getStats().getTotalFiles());
        assertEquals(2, second.getStats().getTotalSymbols());
        assertNotNull(second.findSymbol("Foo"));
        assertNotNull(second.findSymbol("Bar"));
    }

    @Test
    void testAnalyzeIncrementalWithModification() throws IOException, InterruptedException {
        createTestProject(tempDir);

        ProjectAnalyzer.ProjectAnalysisResult first = analyzer.analyzeProject(tempDir);
        assertEquals(2, first.getFileResults().size());

        Thread.sleep(50);
        Files.writeString(tempDir.resolve("Foo.java"), "public class Foo { int x; }");

        ProjectAnalyzer.ProjectAnalysisResult second =
                analyzer.analyzeIncremental(tempDir, first);

        assertNotNull(second);
        assertEquals(2, second.getFileResults().size());
        assertEquals(2, second.getStats().getTotalFiles());
        assertNotNull(second.findSymbol("Foo"));
        assertNotNull(second.findSymbol("Bar"));
    }

    @Test
    void testAnalyzeIncrementalWithAddition() throws IOException {
        Files.writeString(tempDir.resolve("Foo.java"), "public class Foo { }");

        ProjectAnalyzer.ProjectAnalysisResult first = analyzer.analyzeProject(tempDir);
        assertEquals(1, first.getFileResults().size());
        assertNotNull(first.findSymbol("Foo"));

        Files.writeString(tempDir.resolve("Baz.java"), "public class Baz { }");

        ProjectAnalyzer.ProjectAnalysisResult second =
                analyzer.analyzeIncremental(tempDir, first);

        assertNotNull(second);
        assertEquals(2, second.getFileResults().size());
        assertEquals(2, second.getStats().getTotalFiles());
        assertNotNull(second.findSymbol("Foo"));
        assertNotNull(second.findSymbol("Baz"));
    }

    @Test
    void testAnalyzeIncrementalWithDeletion() throws IOException {
        createTestProject(tempDir);

        ProjectAnalyzer.ProjectAnalysisResult first = analyzer.analyzeProject(tempDir);
        assertEquals(2, first.getFileResults().size());

        Files.delete(tempDir.resolve("Bar.java"));

        ProjectAnalyzer.ProjectAnalysisResult second =
                analyzer.analyzeIncremental(tempDir, first);

        assertNotNull(second);
        assertEquals(1, second.getFileResults().size());
        assertEquals(1, second.getStats().getTotalFiles());
        assertNotNull(second.findSymbol("Foo"));
        assertNull(second.findSymbol("Bar"));
    }

    @Test
    void testAnalyzeIncrementalWithManifestFile() throws IOException {
        createTestProject(tempDir);
        Path manifestFile = tempDir.resolve(".analysis-manifest.json");

        assertFalse(Files.exists(manifestFile));

        ProjectAnalyzer.ProjectAnalysisResult result =
                analyzer.analyzeIncremental(tempDir, manifestFile);

        assertNotNull(result);
        assertTrue(Files.exists(manifestFile), "Manifest file should be saved");

        String content = Files.readString(manifestFile);
        assertFalse(content.isEmpty());
        assertTrue(content.startsWith("["));

        ProjectAnalyzer.ProjectAnalysisResult result2 =
                analyzer.analyzeIncremental(tempDir, manifestFile);

        assertNotNull(result2);
        assertEquals(2, result2.getFileResults().size());
    }

    @Test
    void testLanguageFamilyCountsInStats() throws IOException {
        createTestProject(tempDir);

        ProjectAnalyzer.ProjectAnalysisResult result =
                analyzer.analyzeIncremental(tempDir, (ProjectAnalyzer.ProjectAnalysisResult) null);

        assertNotNull(result);
        Map<LanguageFamily, Integer> familyCounts = result.getStats().getLanguageFamilyCounts();
        assertNotNull(familyCounts);
        assertFalse(familyCounts.isEmpty());
        assertEquals(2, familyCounts.get(LanguageFamily.JVM));
    }
}

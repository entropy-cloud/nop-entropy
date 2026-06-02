package io.nop.code.core.analyzer;

import io.nop.code.core.adapter.LanguageAdapterRegistry;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestProjectAnalyzerConcurrency {

    @TempDir
    Path tempDir;

    private ProjectAnalyzer analyzer;

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private String vfsPath() {
        return "file:" + tempDir.toAbsolutePath();
    }

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(tempDir.resolve("Foo.java"), "public class Foo {}");
        Files.writeString(tempDir.resolve("Bar.java"), "public class Bar {}");
        Files.writeString(tempDir.resolve("Baz.java"), "public class Baz {}");

        ICodeFileAnalyzer mockAnalyzer = new ICodeFileAnalyzer() {
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

        ILanguageAdapter mockAdapter = new ILanguageAdapter() {
            @Override
            public CodeLanguage getLanguage() {
                return CodeLanguage.JAVA;
            }

            @Override
            public ICodeFileAnalyzer getFileAnalyzer() {
                return mockAnalyzer;
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

        LanguageAdapterRegistry registry = new LanguageAdapterRegistry();
        registry.registerAdapter(mockAdapter);
        analyzer = new ProjectAnalyzer(registry);
    }

    @Test
    void testConcurrentAnalysisNoExceptions() throws Exception {
        int threadCount = 4;
        int iterations = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount * iterations);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount * iterations; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ProjectAnalysisResult result = analyzer.analyzeProject(vfsPath());
                    if (result == null || result.getFileResults().isEmpty()) {
                        errorCount.incrementAndGet();
                    } else {
                        successCount.incrementAndGet();
                    }
                } catch (Throwable t) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All tasks should complete within timeout");
        assertEquals(0, errorCount.get(), "No errors during concurrent analysis");
        assertEquals(threadCount * iterations, successCount.get());
    }

    @Test
    void testConcurrentAnalysisProducesConsistentResults() throws Exception {
        int count = 5;
        List<ProjectAnalysisResult> results = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch latch = new CountDownLatch(count);

        for (int i = 0; i < count; i++) {
            executor.submit(() -> {
                try {
                    results.add(analyzer.analyzeProject(vfsPath()));
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(count, results.size());
        int expectedFileCount = results.get(0).getFileResults().size();
        for (ProjectAnalysisResult result : results) {
            assertEquals(expectedFileCount, result.getFileResults().size(),
                    "All concurrent analyses should produce same file count");
        }
    }
}

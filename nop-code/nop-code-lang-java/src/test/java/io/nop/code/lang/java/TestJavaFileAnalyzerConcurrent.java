package io.nop.code.lang.java;

import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.lang.java.analyzer.JavaFileAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class TestJavaFileAnalyzerConcurrent {

    private static final String JAVA_SOURCE =
            "package com.example;\n" +
            "public class TestClass {\n" +
            "    public void method1() {}\n" +
            "    public void method2() {}\n" +
            "}\n";

    @Test
    void testConcurrentAnalysisNoErrors() throws Exception {
        JavaFileAnalyzer analyzer = new JavaFileAnalyzer();
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    CodeFileAnalysisResult result = analyzer.analyze(
                            "TestClass" + index + ".java", JAVA_SOURCE);
                    assertNotNull(result, "Analysis " + index + " should succeed");
                    assertFalse(result.getSymbols().isEmpty());
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertTrue(errors.isEmpty(), "Concurrent analysis should produce no errors, but got: " + errors);
    }
}

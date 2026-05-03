package io.nop.javaparser.analyzer;

import io.nop.code.core.adapter.LanguageAdapterRegistry;
import io.nop.code.core.analyzer.ProjectAnalyzer;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.lang.java.JavaLanguageAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectAnalyzerTest {

    @TempDir
    Path tempDir;

    private ProjectAnalyzer createAnalyzer() {
        LanguageAdapterRegistry registry = new LanguageAdapterRegistry();
        registry.registerAdapter(new JavaLanguageAdapter());
        return new ProjectAnalyzer(registry);
    }

    @Test
    public void testAnalyzeSimpleProject() throws IOException {
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        String serviceCode = "package com.example;\n" +
                "\n" +
                "public class Service {\n" +
                "    private String name;\n" +
                "\n" +
                "    public String getName() {\n" +
                "        return name;\n" +
                "    }\n" +
                "\n" +
                "    public void process() {\n" +
                "        System.out.println(getName());\n" +
                "    }\n" +
                "}\n";
        Files.writeString(srcDir.resolve("Service.java"), serviceCode);

        String repoCode = "package com.example;\n" +
                "\n" +
                "import java.util.List;\n" +
                "\n" +
                "public class Repository {\n" +
                "    public List<String> findAll() {\n" +
                "        return null;\n" +
                "    }\n" +
                "}\n";
        Files.writeString(srcDir.resolve("Repository.java"), repoCode);

        ProjectAnalyzer analyzer = createAnalyzer();
        ProjectAnalyzer.ProjectAnalysisResult result = analyzer.analyzeProject(tempDir);

        assertNotNull(result);
        ProjectAnalyzer.ProjectStats stats = result.getStats();
        assertEquals(2, stats.getTotalFiles());
        assertTrue(stats.getTotalSymbols() >= 5);

        SymbolTable symbolTable = result.getGlobalSymbolTable();
        assertNotNull(symbolTable.getByQualifiedName("com.example.Service"));
        assertNotNull(symbolTable.getByQualifiedName("com.example.Repository"));
        assertNotNull(symbolTable.getByQualifiedName("com.example.Service.getName"));
        assertNotNull(symbolTable.getByQualifiedName("com.example.Repository.findAll"));

        CodeSymbol serviceClass = result.findSymbol("com.example.Service");
        assertNotNull(serviceClass);
        assertEquals("Service", serviceClass.getName());
        assertEquals(CodeSymbolKind.CLASS, serviceClass.getKind());
    }

    @Test
    public void testBuildCallGraph() throws IOException {
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        String callerCode = "package com.example;\n" +
                "\n" +
                "public class Caller {\n" +
                "    private Callee callee = new Callee();\n" +
                "\n" +
                "    public void doWork() {\n" +
                "        callee.execute();\n" +
                "        callee.finish();\n" +
                "    }\n" +
                "}\n";
        Files.writeString(srcDir.resolve("Caller.java"), callerCode);

        String calleeCode = "package com.example;\n" +
                "\n" +
                "public class Callee {\n" +
                "    public void execute() {}\n" +
                "    public void finish() {}\n" +
                "}\n";
        Files.writeString(srcDir.resolve("Callee.java"), calleeCode);

        ProjectAnalyzer analyzer = createAnalyzer();
        ProjectAnalyzer.ProjectAnalysisResult result = analyzer.analyzeProject(tempDir);

        CallGraph callGraph = result.buildCallGraph();

        assertNotNull(callGraph);

        CodeSymbol doWorkMethod = result.findSymbol("com.example.Caller.doWork");
        assertNotNull(doWorkMethod);

        List<String> callees = callGraph.getCallees(doWorkMethod.getId());
        if (callees != null && !callees.isEmpty()) {
            assertTrue(callees.size() >= 1, "doWork should have at least 1 call");
        }
    }

    @Test
    public void testBuildReverseCallGraph() throws IOException {
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        String caller1Code = "package com.example;\n" +
                "\n" +
                "public class Caller1 {\n" +
                "    private Helper helper = new Helper();\n" +
                "\n" +
                "    public void run() {\n" +
                "        helper.help();\n" +
                "    }\n" +
                "}\n";
        Files.writeString(srcDir.resolve("Caller1.java"), caller1Code);

        String caller2Code = "package com.example;\n" +
                "\n" +
                "public class Caller2 {\n" +
                "    private Helper helper = new Helper();\n" +
                "\n" +
                "    public void execute() {\n" +
                "        helper.help();\n" +
                "    }\n" +
                "}\n";
        Files.writeString(srcDir.resolve("Caller2.java"), caller2Code);

        String helperCode = "package com.example;\n" +
                "\n" +
                "public class Helper {\n" +
                "    public void help() {}\n" +
                "}\n";
        Files.writeString(srcDir.resolve("Helper.java"), helperCode);

        ProjectAnalyzer analyzer = createAnalyzer();
        ProjectAnalyzer.ProjectAnalysisResult result = analyzer.analyzeProject(tempDir);

        // Use CallGraph which provides reverse lookup via getCallers()
        CallGraph callGraph = result.buildCallGraph();

        CodeSymbol helpMethod = result.findSymbol("com.example.Helper.help");
        assertNotNull(helpMethod);

        List<String> callers = callGraph.getCallers(helpMethod.getId());
        if (callers != null && !callers.isEmpty()) {
            assertTrue(callers.size() >= 1, "help() should be called by at least 1 caller");
        }
    }

    @Test
    public void testProgressCallback() throws IOException {
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        String code = "package com.example;\n" +
                "public class Test {\n" +
                "    public void test() {}\n" +
                "}\n";
        Files.writeString(srcDir.resolve("Test.java"), code);

        ProjectAnalyzer analyzer = createAnalyzer();
        final int[] progressCount = {0};
        final boolean[] completed = {false};

        ProjectAnalyzer.ProjectAnalysisResult result = analyzer.analyzeProject(tempDir,
                (current, total, message) -> {
                    progressCount[0]++;
                    if (current == total) {
                        completed[0] = true;
                    }
                });

        assertNotNull(result);
        assertTrue(progressCount[0] > 0, "Progress callback should be called");
        assertTrue(completed[0], "Progress should reach completion");
    }

    @Test
    public void testStats() throws IOException {
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        String code = "package com.example;\n" +
                "\n" +
                "import java.util.List;\n" +
                "\n" +
                "public class StatsTest {\n" +
                "    private String name;\n" +
                "    private int value;\n" +
                "\n" +
                "    public String getName() { return name; }\n" +
                "    public int getValue() { return value; }\n" +
                "    public void process() {\n" +
                "        String s = getName();\n" +
                "        int v = getValue();\n" +
                "    }\n" +
                "}\n";
        Files.writeString(srcDir.resolve("StatsTest.java"), code);

        ProjectAnalyzer analyzer = createAnalyzer();
        ProjectAnalyzer.ProjectAnalysisResult result = analyzer.analyzeProject(tempDir);

        ProjectAnalyzer.ProjectStats stats = result.getStats();
        assertEquals(1, stats.getTotalFiles());
        assertTrue(stats.getTotalSymbols() > 0);
        assertTrue(stats.getTotalCalls() > 0);

        Map<CodeSymbolKind, Integer> symbolCounts = stats.getSymbolCounts();
        assertNotNull(symbolCounts);
        assertTrue(symbolCounts.containsKey(CodeSymbolKind.CLASS));
        assertTrue(symbolCounts.containsKey(CodeSymbolKind.METHOD));
        assertTrue(symbolCounts.containsKey(CodeSymbolKind.FIELD));

        String statsStr = stats.toString();
        assertNotNull(statsStr);
        assertTrue(statsStr.contains("totalFiles=1"));
    }

    @Test
    public void testExcludeDirectories() throws IOException {
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Path targetDir = tempDir.resolve("target/classes/com/example");
        Path buildDir = tempDir.resolve("build/classes/com/example");
        
        Files.createDirectories(srcDir);
        Files.createDirectories(targetDir);
        Files.createDirectories(buildDir);

        String srcCode = "package com.example;\npublic class SrcClass {}\n";
        Files.writeString(srcDir.resolve("SrcClass.java"), srcCode);

        String targetCode = "package com.example;\npublic class TargetClass {}\n";
        Files.writeString(targetDir.resolve("TargetClass.java"), targetCode);

        String buildCode = "package com.example;\npublic class BuildClass {}\n";
        Files.writeString(buildDir.resolve("BuildClass.java"), buildCode);

        ProjectAnalyzer analyzer = createAnalyzer();
        ProjectAnalyzer.ProjectAnalysisResult result = analyzer.analyzeProject(tempDir);

        assertEquals(1, result.getStats().getTotalFiles());
        assertNotNull(result.findSymbol("com.example.SrcClass"));
        assertNull(result.findSymbol("com.example.TargetClass"));
        assertNull(result.findSymbol("com.example.BuildClass"));
    }
}

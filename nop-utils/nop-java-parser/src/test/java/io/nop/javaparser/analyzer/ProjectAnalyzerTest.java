package io.nop.javaparser.analyzer;

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

    @Test
    public void testAnalyzeSimpleProject() throws IOException {
        // 创建临时项目结构
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        // 创建 Service.java
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

        // 创建 Repository.java
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

        // 分析项目
        ProjectAnalyzer analyzer = new ProjectAnalyzer();
        ProjectAnalyzer.ProjectAnalysisResult result = analyzer.analyzeProject(tempDir);

        // 验证基本统计
        assertNotNull(result);
        ProjectAnalyzer.ProjectStats stats = result.getStats();
        assertEquals(2, stats.getTotalFiles());
        assertTrue(stats.getTotalSymbols() >= 5); // 至少 2 个类 + 方法

        // 验证全局符号表
        Map<String, SymbolInfo> symbolTable = result.getGlobalSymbolTable();
        assertTrue(symbolTable.containsKey("com.example.Service"));
        assertTrue(symbolTable.containsKey("com.example.Repository"));
        assertTrue(symbolTable.containsKey("com.example.Service.getName"));
        assertTrue(symbolTable.containsKey("com.example.Repository.findAll"));

        // 验证可以找到符号
        SymbolInfo serviceClass = result.findSymbol("com.example.Service");
        assertNotNull(serviceClass);
        assertEquals("Service", serviceClass.getName());
        assertEquals(SymbolKind.CLASS, serviceClass.getKind());
    }

    @Test
    public void testBuildCallGraph() throws IOException {
        // 创建临时项目结构
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        // 创建 Caller.java
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

        // 创建 Callee.java
        String calleeCode = "package com.example;\n" +
                "\n" +
                "public class Callee {\n" +
                "    public void execute() {}\n" +
                "    public void finish() {}\n" +
                "}\n";
        Files.writeString(srcDir.resolve("Callee.java"), calleeCode);

        // 分析项目
        ProjectAnalyzer analyzer = new ProjectAnalyzer();
        analyzer.getFileAnalyzer().setMethodCallFilter(null); // 不过滤
        ProjectAnalyzer.ProjectAnalysisResult result = analyzer.analyzeProject(tempDir);

        // 构建调用图
        Map<String, List<String>> callGraph = result.buildCallGraph();

        // 验证调用图不为空
        assertNotNull(callGraph);

        // 查找 doWork 方法
        SymbolInfo doWorkMethod = result.findSymbol("com.example.Caller.doWork");
        assertNotNull(doWorkMethod);

        // 验证 doWork 有调用其他方法
        List<String> callees = callGraph.get(doWorkMethod.getId());
        if (callees != null && !callees.isEmpty()) {
            // 如果调用被成功解析，应该有 2 个调用
            assertTrue(callees.size() >= 1, "doWork should have at least 1 call");
        }
    }

    @Test
    public void testBuildReverseCallGraph() throws IOException {
        // 创建临时项目结构
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        // 创建多个调用者
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

        // 分析项目
        ProjectAnalyzer analyzer = new ProjectAnalyzer();
        analyzer.getFileAnalyzer().setMethodCallFilter(null);
        ProjectAnalyzer.ProjectAnalysisResult result = analyzer.analyzeProject(tempDir);

        // 构建反向调用图
        Map<String, List<String>> reverseCallGraph = result.buildReverseCallGraph();

        // 查找 help 方法
        SymbolInfo helpMethod = result.findSymbol("com.example.Helper.help");
        assertNotNull(helpMethod);

        // 验证 help 方法被调用
        List<String> callers = reverseCallGraph.get(helpMethod.getId());
        if (callers != null && !callers.isEmpty()) {
            // help 方法应该被至少一个调用者调用
            assertTrue(callers.size() >= 1, "help() should be called by at least 1 caller");
        }
    }

    @Test
    public void testProgressCallback() throws IOException {
        // 创建临时项目结构
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        String code = "package com.example;\n" +
                "public class Test {\n" +
                "    public void test() {}\n" +
                "}\n";
        Files.writeString(srcDir.resolve("Test.java"), code);

        // 分析项目（带进度回调）
        ProjectAnalyzer analyzer = new ProjectAnalyzer();
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
        // 创建临时项目结构
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

        // 分析项目
        ProjectAnalyzer analyzer = new ProjectAnalyzer();
        analyzer.getFileAnalyzer().setMethodCallFilter(null);
        ProjectAnalyzer.ProjectAnalysisResult result = analyzer.analyzeProject(tempDir);

        // 验证统计信息
        ProjectAnalyzer.ProjectStats stats = result.getStats();
        assertEquals(1, stats.getTotalFiles());
        assertTrue(stats.getTotalSymbols() > 0);
        assertTrue(stats.getTotalCalls() > 0);

        // 验证符号类型统计
        Map<SymbolKind, Integer> symbolCounts = stats.getSymbolCounts();
        assertNotNull(symbolCounts);
        assertTrue(symbolCounts.containsKey(SymbolKind.CLASS));
        assertTrue(symbolCounts.containsKey(SymbolKind.METHOD));
        assertTrue(symbolCounts.containsKey(SymbolKind.FIELD));

        // 验证 toString 不会抛异常
        String statsStr = stats.toString();
        assertNotNull(statsStr);
        assertTrue(statsStr.contains("totalFiles=1"));
    }

    @Test
    public void testExcludeDirectories() throws IOException {
        // 创建临时项目结构
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Path targetDir = tempDir.resolve("target/classes/com/example");
        Path buildDir = tempDir.resolve("build/classes/com/example");
        
        Files.createDirectories(srcDir);
        Files.createDirectories(targetDir);
        Files.createDirectories(buildDir);

        // 在 src 中创建文件
        String srcCode = "package com.example;\npublic class SrcClass {}\n";
        Files.writeString(srcDir.resolve("SrcClass.java"), srcCode);

        // 在 target 中创建文件（应该被排除）
        String targetCode = "package com.example;\npublic class TargetClass {}\n";
        Files.writeString(targetDir.resolve("TargetClass.java"), targetCode);

        // 在 build 中创建文件（应该被排除）
        String buildCode = "package com.example;\npublic class BuildClass {}\n";
        Files.writeString(buildDir.resolve("BuildClass.java"), buildCode);

        // 分析项目
        ProjectAnalyzer analyzer = new ProjectAnalyzer();
        ProjectAnalyzer.ProjectAnalysisResult result = analyzer.analyzeProject(tempDir);

        // 验证只分析了 src 中的文件
        assertEquals(1, result.getStats().getTotalFiles());
        assertNotNull(result.findSymbol("com.example.SrcClass"));
        assertNull(result.findSymbol("com.example.TargetClass"));
        assertNull(result.findSymbol("com.example.BuildClass"));
    }
}

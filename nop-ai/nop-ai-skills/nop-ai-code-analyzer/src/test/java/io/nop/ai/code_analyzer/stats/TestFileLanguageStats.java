package io.nop.ai.code_analyzer.stats;

import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FileLanguageStats 直接调用测试（P2-MA1-005 拆分裁定后新增）。
 * 因无生产调用者，端到端验证以直接实例化为入口：样本目录 统计 → 语言分类 → 聚合 全路径断言结果值。
 */
public class TestFileLanguageStats extends BaseTestCase {

    private static final String JAVA_SAMPLE = "package demo;\n" +
            "\n" +
            "// comment line\n" +
            "\n" +
            "public class Demo {\n" +
            "    // inline comment\n" +
            "    public int add(int a, int b) {\n" +
            "        /* block\n" +
            "        comment */\n" +
            "        return a + b;\n" +
            "    }\n" +
            "}\n";

    private static final String PY_SAMPLE = "# comment\n" +
            "import os\n" +
            "\n" +
            "def f():\n" +
            "    return 1\n";

    private static final String POM_SAMPLE = "<project>\n" +
            "<build/>\n" +
            "</project>\n";

    private Path createSampleDir() throws IOException {
        Path root = Files.createTempDirectory("code-analyzer-stats-test");
        Files.writeString(root.resolve("Demo.java"), JAVA_SAMPLE, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("util.py"), PY_SAMPLE, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("build.pom"), POM_SAMPLE, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("note.log"), "ignore me\n");
        Files.writeString(root.resolve(".secret.java"), "class Secret {}\n");
        Files.writeString(Files.createDirectories(root.resolve("target")).resolve("Ignored.java"), "class Ignored {}\n");
        Files.writeString(Files.createDirectories(root.resolve(".hidden")).resolve("Hidden.java"), "class Hidden {}\n");
        return root;
    }

    @Test
    public void testComprehensiveStatsSampleDirectory() throws IOException {
        Path root = createSampleDir();
        try {
            FileLanguageStats stats = new FileLanguageStats();
            Map<String, LanguageStatsAggregator.LanguageStats> comprehensive = stats.getComprehensiveStats(root);

            assertEquals(3, comprehensive.size(), "only Java/Python/Maven files should be aggregated");

            LanguageStatsAggregator.LanguageStats javaStats = comprehensive.get("Java");
            assertNotNull(javaStats);
            assertEquals(1, javaStats.getFiles());
            assertEquals(12, javaStats.getLines());
            assertEquals(2, javaStats.getBlankLines());
            assertEquals(4, javaStats.getCommentLines());
            assertEquals(6, javaStats.getCodeLines());
            assertEquals(JAVA_SAMPLE.getBytes(StandardCharsets.UTF_8).length, javaStats.getBytes());

            LanguageStatsAggregator.LanguageStats pyStats = comprehensive.get("Python");
            assertNotNull(pyStats);
            assertEquals(1, pyStats.getFiles());
            assertEquals(5, pyStats.getLines());
            assertEquals(1, pyStats.getBlankLines());
            assertEquals(1, pyStats.getCommentLines());
            assertEquals(3, pyStats.getCodeLines());
            assertEquals(PY_SAMPLE.getBytes(StandardCharsets.UTF_8).length, pyStats.getBytes());

            LanguageStatsAggregator.LanguageStats mavenStats = comprehensive.get("Maven");
            assertNotNull(mavenStats, "build.pom should map to Maven via local extension mapping");
            assertEquals(1, mavenStats.getFiles());
            assertEquals(3, mavenStats.getLines());
            assertEquals(POM_SAMPLE.getBytes(StandardCharsets.UTF_8).length, mavenStats.getBytes());
        } finally {
            deleteRecursively(root.toFile());
        }
    }

    @Test
    public void testStatsByTypeAndGitHubApiString() throws IOException {
        Path root = createSampleDir();
        try {
            FileLanguageStats stats = new FileLanguageStats();

            Map<String, Long> byteStats = stats.getByteStats(root.toString());
            assertEquals(3, byteStats.size());
            long javaBytes = JAVA_SAMPLE.getBytes(StandardCharsets.UTF_8).length;
            long pyBytes = PY_SAMPLE.getBytes(StandardCharsets.UTF_8).length;
            long pomBytes = POM_SAMPLE.getBytes(StandardCharsets.UTF_8).length;
            assertEquals(javaBytes, byteStats.get("Java"));
            assertEquals(pyBytes, byteStats.get("Python"));
            assertEquals(pomBytes, byteStats.get("Maven"));

            Map<String, Long> lineStats = stats.getLineStats(root.toString());
            assertEquals(12L, lineStats.get("Java"));
            assertEquals(5L, lineStats.get("Python"));

            Map<String, Long> fileStats = stats.getFileStats(root.toString());
            assertEquals(1L, fileStats.get("Java"));
            assertEquals(1L, fileStats.get("Python"));

            Map<String, Long> codeLineStats = stats.getCodeLineStats(root.toString());
            assertEquals(6L, codeLineStats.get("Java"));
            assertEquals(3L, codeLineStats.get("Python"));

            // GitHub API 兼容字符串：按字节数降序
            String apiString = stats.getLanguagesApiString(root.toString());
            long max = Math.max(javaBytes, Math.max(pyBytes, pomBytes));
            String firstLang = max == javaBytes ? "Java" : (max == pyBytes ? "Python" : "Maven");
            assertTrue(apiString.startsWith("{\"" + firstLang + "\":" + max), "descending bytes order: " + apiString);
            assertTrue(apiString.endsWith("}"));
            assertTrue(apiString.contains("\"Java\":" + javaBytes));
            assertTrue(apiString.contains("\"Python\":" + pyBytes));
            assertEquals("{}", stats.toGitHubLanguagesApiString(Map.of()));
        } finally {
            deleteRecursively(root.toFile());
        }
    }

    @Test
    public void testLocalMappingPriority() throws IOException {
        Path root = Files.createTempDirectory("code-analyzer-stats-test");
        try {
            Files.writeString(root.resolve("sample.xyz"), "content\n", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("sample.txt"), "content\n", StandardCharsets.UTF_8);

            FileLanguageStats stats = new FileLanguageStats();
            stats.addLocalMapping("xyz", "CustomLang");

            Map<String, Long> byteStats = stats.getByteStats(root.toString());
            assertTrue(byteStats.containsKey("CustomLang"), "custom local mapping should take priority");
            assertTrue(byteStats.containsKey("Text"), "global mapping still works for txt");
            assertFalse(byteStats.containsKey("unknown"), "no bogus language");
        } finally {
            deleteRecursively(root.toFile());
        }
    }

    @Test
    public void testWalkIgnoresHiddenAndIgnoredItems() throws IOException {
        Path root = createSampleDir();
        try {
            FileLanguageStats stats = new FileLanguageStats();
            Map<String, LanguageStatsAggregator.LanguageStats> comprehensive = stats.getComprehensiveStats(root);

            // target/ 目录、.hidden/ 目录、隐藏文件、.log 文件全部被排除
            for (LanguageStatsAggregator.LanguageStats languageStats : comprehensive.values()) {
                assertEquals(1, languageStats.getFiles());
            }
            assertEquals(3, comprehensive.size());
            assertTrue(stats.toGitHubLanguagesApiString(stats.getByteStats(root.toString())).contains("\"Java\""));
        } finally {
            deleteRecursively(root.toFile());
        }
    }

    @Test
    public void testCommentDetectionBoundaries() throws IOException {
        CodeLineAnalyzer analyzer = new CodeLineAnalyzer();

        Path javaFile = Files.createTempFile("comment-test", ".java");
        Files.writeString(javaFile, "// line\n# not java comment\n", StandardCharsets.UTF_8);
        try {
            CodeLineAnalyzer.LineStats stats = analyzer.analyzeFileLines(javaFile, "java");
            assertEquals(2, stats.totalLines);
            assertEquals(1, stats.commentLines, "java // counts as comment");
            assertEquals(1, stats.codeLines, "java # does not count as comment");
        } finally {
            Files.deleteIfExists(javaFile);
        }

        Path pyFile = Files.createTempFile("comment-test", ".py");
        Files.writeString(pyFile, "# py comment\nprint(1)\n", StandardCharsets.UTF_8);
        try {
            CodeLineAnalyzer.LineStats stats = analyzer.analyzeFileLines(pyFile, "py");
            assertEquals(2, stats.totalLines);
            assertEquals(1, stats.commentLines);
            assertEquals(1, stats.codeLines);
        } finally {
            Files.deleteIfExists(pyFile);
        }

        // 多行块注释：/* ... */ 期间的行均计入注释
        Path blockFile = Files.createTempFile("comment-test", ".java");
        Files.writeString(blockFile, "/* open\nstill comment\n*/\ncode\n", StandardCharsets.UTF_8);
        try {
            CodeLineAnalyzer.LineStats stats = analyzer.analyzeFileLines(blockFile, "java");
            assertEquals(4, stats.totalLines);
            assertEquals(3, stats.commentLines, "multi-line block comment lines count as comments");
            assertEquals(1, stats.codeLines);
        } finally {
            Files.deleteIfExists(blockFile);
        }
    }

    private void deleteRecursively(java.io.File dir) {
        if (dir == null || !dir.exists())
            return;
        java.io.File[] files = dir.listFiles();
        if (files != null) {
            for (java.io.File file : files) {
                if (file.isDirectory()) {
                    deleteRecursively(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }
}

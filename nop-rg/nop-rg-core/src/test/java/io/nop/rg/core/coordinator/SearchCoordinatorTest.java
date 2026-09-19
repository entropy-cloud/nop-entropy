package io.nop.rg.core.coordinator;

import io.nop.core.initialize.CoreInitialization;
import io.nop.rg.core.NopRgException;
import java.lang.foreign.MemorySegment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SearchCoordinator 集成测试：walker + glob + 策略选择 + 行提取端到端。
 */
public class SearchCoordinatorTest {
    @TempDir
    Path tempDir;

    @BeforeAll
    public static void initAll() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private Path buildTree() throws IOException {
        Files.writeString(tempDir.resolve(".gitignore"), "gen/\n");
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/Main.java"), "int needle = 0;\n// Needle here\nno match\n");
        Files.writeString(tempDir.resolve("src/Util.java"), "nothing");
        Files.writeString(tempDir.resolve("README.md"), "needle in readme\nsecond needle line\n");
        Files.createDirectories(tempDir.resolve("gen"));
        Files.writeString(tempDir.resolve("gen/out.txt"), "needle in generated\n");
        // 二进制文件：头 8 字节含 NUL
        Files.write(tempDir.resolve("bin.dat"), new byte[]{'n', 'e', 0, 'e', 'd', 'l', 'e', 0});
        return tempDir;
    }

    @Test
    public void testLiteralSearchWithGitignoreAndGlob() throws IOException {
        Path root = buildTree();
        SearchCoordinator coordinator = new SearchCoordinator(4, true, false);
        Map<String, SearchCoordinator.FileMatches> results = coordinator.search(
                new SearchCommand(root, "needle", SearchCoordinator.Strategy.LITERAL, false,
                        List.of("**/*.java", "**/*.md", "**/out.txt"), 0));

        // gen/ 被 gitignore 剪掉；bin.dat 二进制跳过；src/Util.java 无命中不出现
        assertEquals(2, results.size());
        assertTrue(results.containsKey("src/Main.java"));
        assertTrue(results.containsKey("README.md"));

        SearchCoordinator.FileMatches main = results.get("src/Main.java");
        // 区分大小写：第二行 "Needle" 不命中
        assertEquals(1, main.lineCount());
        SearchCoordinator.LineMatch line1 = main.getLines().get(0);
        assertEquals(1, line1.getLineNumber());
        assertEquals(4, line1.getSubmatches().get(0).byteStart());
        assertEquals("int needle = 0;", line1.getText());

        SearchCoordinator.FileMatches readme = results.get("README.md");
        assertEquals(2, readme.lineCount());
        assertEquals("needle in readme", readme.getLines().get(0).getText());
    }

    @Test
    public void testIgnoreCaseFoldingStrategy() throws IOException {
        Path root = buildTree();
        SearchCoordinator coordinator = new SearchCoordinator(2, true, false);
        Map<String, SearchCoordinator.FileMatches> results = coordinator.search(
                new SearchCommand(root, "needle", SearchCoordinator.Strategy.LITERAL, true,
                        List.of("src/*.java"), 0));
        SearchCoordinator.FileMatches main = results.get("src/Main.java");
        // -i：第二行 "Needle" 也命中
        assertEquals(2, main.lineCount());
        assertEquals("// Needle here", main.getLines().get(1).getText());
    }

    @Test
    public void testRegexStrategy() throws IOException {
        Path root = buildTree();
        SearchCoordinator coordinator = new SearchCoordinator(2, true, false);
        Map<String, SearchCoordinator.FileMatches> results = coordinator.search(
                new SearchCommand(root, "n[e]edle = \\d", SearchCoordinator.Strategy.REGEX, false,
                        List.of("**/*.java"), 0));
        SearchCoordinator.FileMatches main = results.get("src/Main.java");
        assertEquals(1, main.lineCount());
        assertEquals("int needle = 0;", main.getLines().get(0).getText());
        // 正则命中区间 = "needle = 0" 的字节域
        assertEquals(4, main.getLines().get(0).getSubmatches().get(0).byteStart());
        assertEquals(14, main.getLines().get(0).getSubmatches().get(0).byteEnd());
        assertEquals("needle = 0", main.getLines().get(0).getSubmatches().get(0).text());
    }

    @Test
    public void testBinaryFileSkipped() throws IOException {
        Path root = buildTree();
        SearchCoordinator coordinator = new SearchCoordinator(2, true, false);
        Map<String, SearchCoordinator.FileMatches> results = coordinator.search(
                new SearchCommand(root, "needle", SearchCoordinator.Strategy.LITERAL, false, List.of(), 0));
        assertFalse(results.containsKey("bin.dat"));
        assertTrue(results.containsKey("README.md"));
    }

    @Test
    public void testMaxMatchesTruncationFlag() throws IOException {
        Path root = buildTree();
        SearchCoordinator coordinator = new SearchCoordinator(2, true, false);
        Map<String, SearchCoordinator.FileMatches> results = coordinator.search(
                new SearchCommand(root, "needle", SearchCoordinator.Strategy.LITERAL, false, List.of("README.md"), 1));
        SearchCoordinator.FileMatches readme = results.get("README.md");
        assertEquals(1, readme.lineCount());
        assertTrue(readme.isTruncated());
    }

    @Test
    public void testCrlfLineExtraction() throws IOException {
        Files.write(tempDir.resolve("crlf.txt"),
                "first line\r\nneedle crlf\r\nlast\r\n".getBytes(StandardCharsets.UTF_8));
        SearchCoordinator coordinator = new SearchCoordinator(1, true, false);
        Map<String, SearchCoordinator.FileMatches> results = coordinator.search(
                new SearchCommand(tempDir, "needle", SearchCoordinator.Strategy.LITERAL, false, List.of(), 0));
        SearchCoordinator.FileMatches matches = results.get("crlf.txt");
        assertEquals(1, matches.lineCount());
        SearchCoordinator.LineMatch line = matches.getLines().get(0);
        assertEquals(2, line.getLineNumber());
        // 行文本不含 CR/LF；行终点含 CRLF
        assertEquals("needle crlf", line.getText());
        assertEquals(line.getContentEnd() + 2, line.getLineEnd());
    }

    @Test
    public void testRegexSearcherReusableAcrossSegments() throws IOException {
        // 同一 RegexSearcher 实例在多个不同 segment 上连续使用（coordinator per-command
        // 编译一次的复用契约，audit m7 专项断言）
        Path f1 = tempDir.resolve("r1.txt");
        Path f2 = tempDir.resolve("r2.txt");
        Files.writeString(f1, "needle regex-one\nplain\n");
        Files.writeString(f2, "plain\nneedle regex-two plain\n");
        RegexSearcher searcher = new RegexSearcher("needle regex-\\w+", false);
        try (io.nop.rg.core.io.MappedFileReader r1 = new io.nop.rg.core.io.MappedFileReader(f1);
             io.nop.rg.core.io.MappedFileReader r2 = new io.nop.rg.core.io.MappedFileReader(f2)) {
            MemorySegment seg1 = r1.getSegment();
            MemorySegment seg2 = r2.getSegment();
            List<RegexSearcher.ByteSpan> s1 = searcher.findAll(seg1, Files.size(f1));
            List<RegexSearcher.ByteSpan> s2 = searcher.findAll(seg2, Files.size(f2));
            assertEquals(1, s1.size());
            assertEquals(0, s1.get(0).byteStart());
            assertEquals(1, s2.size());
            assertEquals(6, s2.get(0).byteStart());
        }
    }

    @Test
    public void testRegexOnFileAboveChunkedThresholdFailsExplicitly() throws IOException {
        // 契约裁定（plan 2265 Phase 3）：REGEX + >阈值文件显式抛异常，不静默回退
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            big.append("some text line ").append(i).append('\n');
        }
        Path file = tempDir.resolve("big-regex.txt");
        Files.writeString(file, big.toString()); // > 64 字节阈值
        SearchCoordinator coordinator = new SearchCoordinator(1, false, false, 64);
        assertThrows(NopRgException.class, () -> coordinator.search(
                new SearchCommand(tempDir, "n[e]edle|text", SearchCoordinator.Strategy.REGEX,
                        false, List.of(), 0)));
    }

    @Test
    public void testVectorStrategyExplicitlyFails() throws IOException {
        Path root = buildTree();
        SearchCoordinator coordinator = new SearchCoordinator(1, true, false);
        assertThrows(NopRgException.class, () -> coordinator.search(
                new SearchCommand(root, "needle", SearchCoordinator.Strategy.VECTOR, false, List.of(), 0)));
    }

    @Test
    public void testEmptyPatternRejected() throws IOException {
        Path root = buildTree();
        assertThrows(IllegalArgumentException.class, () -> new SearchCommand(
                root, "", SearchCoordinator.Strategy.LITERAL, false, List.of(), 0));
    }
}

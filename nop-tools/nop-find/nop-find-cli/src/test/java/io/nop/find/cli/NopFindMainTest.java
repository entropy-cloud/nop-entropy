package io.nop.find.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NopFindMain} CLI 集成测试（plan 2379 Phase 3）：
 * 进程内驱动 picocli，断言输出形态、退出码契约与错误路径。
 *
 * <p>fixture 的匹配分数为确定性断言（三级打分：name 前缀档 = len(name) - len(query)），
 * query 统一使用独立 token "nopfind"，避免临时目录名意外命中。
 */
public class NopFindMainTest {

    @TempDir
    Path tempDir;

    private Path root;

    @BeforeEach
    public void buildFixture() throws IOException {
        Files.writeString(tempDir.resolve(".gitignore"), "ignored-dir/\n");
        write("ignored-dir/NopFindIgnored.java");
        write("src/main/java/NopFinder.java");
        write("src/test/java/NopFinderTest.java");
        write("docs/nopfind-guide.md");
        write("plain-nopfind.txt");
        root = tempDir;
    }

    private void write(String rel) throws IOException {
        Path file = tempDir.resolve(rel);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "// " + rel + "\n");
    }

    private record RunResult(int exitCode, String stdout, String stderr) {
    }

    private RunResult run(String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outBytes, true));
        System.setErr(new PrintStream(errBytes, true));
        try {
            CommandLine cmd = new CommandLine(new NopFindMain());
            cmd.setOut(new PrintWriter(outBytes, true));
            cmd.setErr(new PrintWriter(errBytes, true));
            int code = cmd.execute(args);
            System.out.flush();
            System.err.flush();
            return new RunResult(code, outBytes.toString(), errBytes.toString());
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private static final List<String> EXPECTED_ALL = List.of(
            "7\tsrc/main/java/NopFinder.java",
            "9\tdocs/nopfind-guide.md",
            "11\tsrc/test/java/NopFinderTest.java",
            "106\tplain-nopfind.txt");

    // ---------- 文本输出 ----------

    @Test
    public void testTextOutputRanksAndExitsZero() {
        RunResult hit = run("nopfind", root.toString());
        assertEquals(0, hit.exitCode(), hit.stderr());
        assertEquals(EXPECTED_ALL, hit.stdout().lines().toList());
    }

    @Test
    public void testLimitOptionCapsResults() {
        RunResult hit = run("nopfind", root.toString(), "-n", "2");
        assertEquals(0, hit.exitCode(), hit.stderr());
        assertEquals(EXPECTED_ALL.subList(0, 2), hit.stdout().lines().toList());
    }

    @Test
    public void testGlobOptionFiltersResults() {
        RunResult hit = run("nopfind", root.toString(), "-g", "*.java");
        assertEquals(0, hit.exitCode(), hit.stderr());
        assertEquals(List.of("7\tsrc/main/java/NopFinder.java", "11\tsrc/test/java/NopFinderTest.java"),
                hit.stdout().lines().toList());
    }

    @Test
    public void testGlobExclusionSupported() {
        RunResult hit = run("nopfind", root.toString(), "-g", "*.java", "-g", "!src/test/**");
        assertEquals(0, hit.exitCode(), hit.stderr());
        assertEquals(List.of("7\tsrc/main/java/NopFinder.java"), hit.stdout().lines().toList());
    }

    @Test
    public void testNoIgnoreIncludesGitignoredFiles() {
        RunResult hit = run("nopfindignored", root.toString(), "--no-ignore");
        assertEquals(0, hit.exitCode(), hit.stderr());
        assertEquals("5\tignored-dir/NopFindIgnored.java", hit.stdout().strip());

        RunResult pruned = run("nopfindignored", root.toString());
        assertEquals(1, pruned.exitCode());
    }

    @Test
    public void testCaseSensitiveOption() {
        RunResult insensitive = run("NOPFIND", root.toString());
        assertEquals(0, insensitive.exitCode(), insensitive.stderr());
        assertEquals(EXPECTED_ALL, insensitive.stdout().lines().toList());

        RunResult sensitive = run("NOPFIND", root.toString(), "-s");
        assertEquals(1, sensitive.exitCode());
        assertFalse(sensitive.stdout().contains("NopFinder.java"));
    }

    @Test
    public void testThreadsOptionAccepted() {
        RunResult hit = run("nopfind", root.toString(), "--threads", "2");
        assertEquals(0, hit.exitCode(), hit.stderr());
        assertEquals(EXPECTED_ALL, hit.stdout().lines().toList());
    }

    // ---------- JSON 输出 ----------

    @Test
    public void testJsonOutputShape() {
        RunResult hit = run("nopfind", root.toString(), "--json");
        assertEquals(0, hit.exitCode(), hit.stderr());
        String stdout = hit.stdout().strip();
        assertTrue(stdout.startsWith("{\"matches\":["), stdout);
        assertTrue(stdout.endsWith("\"count\":4}"), stdout);
        assertTrue(stdout.contains("\"relativePath\":\"src/main/java/NopFinder.java\",\"score\":7"), stdout);
        assertTrue(stdout.contains("\"path\":\""), stdout);
        assertEquals(stdout.chars().filter(c -> c == '{').count(),
                stdout.chars().filter(c -> c == '}').count(), stdout);
    }

    @Test
    public void testJsonEmptyResultStillPrintsCountZero() {
        RunResult miss = run("zzzznomatchzzzz", root.toString(), "--json");
        assertEquals(1, miss.exitCode());
        assertEquals("{\"matches\":[],\"count\":0}", miss.stdout().strip());
    }

    @Test
    public void testJsonEscapesSpecialCharsInPaths() throws IOException {
        write("odd\"quote-nopfind.txt");
        // --no-ignore：带引号的文件名无法作为 VFS 相对资源路径，绕开 gitignore 规则加载路径
        RunResult hit = run("nopfind", root.toString(), "--no-ignore", "--json");
        assertEquals(0, hit.exitCode(), hit.stderr());
        assertTrue(hit.stdout().contains("odd\\\"quote-nopfind.txt"), hit.stdout());
        assertEquals(hit.stdout().chars().filter(c -> c == '{').count(),
                hit.stdout().chars().filter(c -> c == '}').count(), hit.stdout());
    }

    @Test
    public void testNoLogNoiseOnStdout() {
        // stdout 是数据通道：每行必须是 SCORE<TAB>PATH，nop 初始化日志与 logback 状态行都不得混入
        RunResult hit = run("nopfind", root.toString());
        assertEquals(0, hit.exitCode(), hit.stderr());
        for (String line : hit.stdout().lines().toList()) {
            assertTrue(line.matches("^\\d+\\t.+$"), "unexpected stdout noise: " + line);
        }
    }

    // ---------- 退出码与错误路径（无静默跳过） ----------

    @Test
    public void testMissExitsOne() {
        RunResult miss = run("zzzznomatchzzzz", root.toString());
        assertEquals(1, miss.exitCode());
        assertTrue(miss.stdout().isBlank(), miss.stdout());
    }

    @Test
    public void testEmptyQueryExitsTwo() {
        RunResult result = run("", root.toString());
        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("query must not be empty"), result.stderr());
    }

    @Test
    public void testBlankQueryExitsTwo() {
        RunResult result = run("   ", root.toString());
        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("query must not be empty"), result.stderr());
    }

    @Test
    public void testMissingQueryArgumentExitsTwo() {
        RunResult result = run();
        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("Missing required parameter"), result.stderr());
    }

    @Test
    public void testInvalidPathExitsTwo() {
        RunResult result = run("nopfind", root.resolve("no-such-dir").toString());
        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("not a directory"), result.stderr());
    }

    @Test
    public void testFileAsPathExitsTwo() {
        RunResult result = run("nopfind", root.resolve("docs/nopfind-guide.md").toString());
        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("not a directory"), result.stderr());
    }

    @Test
    public void testNonPositiveLimitExitsTwo() {
        RunResult result = run("nopfind", root.toString(), "-n", "0");
        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("limit must be positive"), result.stderr());
    }

    @Test
    public void testNegativeThreadsExitsTwo() {
        RunResult result = run("nopfind", root.toString(), "--threads", "-1");
        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("threads must not be negative"), result.stderr());
    }

    @Test
    public void testHelpRendersOptions() {
        RunResult result = run("--help");
        assertEquals(0, result.exitCode());
        String help = result.stdout();
        assertTrue(help.contains("--case-sensitive"), help);
        assertTrue(help.contains("--no-ignore"), help);
        assertTrue(help.contains("--json"), help);
        assertTrue(help.contains("QUERY"), help);
        assertTrue(help.contains("PATH"), help);
    }
}
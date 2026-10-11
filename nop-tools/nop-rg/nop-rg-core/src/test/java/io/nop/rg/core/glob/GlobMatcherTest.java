package io.nop.rg.core.glob;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GlobMatcherTest {

    private static boolean match(String glob, String path) {
        return CompiledGlob.compile(glob).matches(path);
    }

    @Test
    public void testDoubleStarSemantics() {
        // a/**/b：零目录与多目录都命中
        assertTrue(match("a/**/b", "a/b"));
        assertTrue(match("a/**/b", "a/x/b"));
        assertTrue(match("a/**/b", "a/x/y/b"));
        assertFalse(match("a/**/b", "a"));
        assertFalse(match("a/**/b", "b"));

        // **/b：任意深度含根
        assertTrue(match("**/b", "b"));
        assertTrue(match("**/b", "x/b"));
        assertTrue(match("**/b", "x/y/b"));

        // 尾部 a/**：a 下至少一段；独立 **：命中一切
        assertTrue(match("a/**", "a/b"));
        assertTrue(match("a/**", "a/b/c"));
        assertFalse(match("a/**", "a"));
        assertTrue(match("**", "anything"));
        assertTrue(match("**", "a/b/c"));

        // 段内 ** 不是独立段：按普通通配处理（不跨段，等价 * 行为由两段 * 承担）
        assertFalse(match("a**b", "a/x/b"));
        assertTrue(match("a**b", "axxb"));
    }

    @Test
    public void testNoSlashGlobMatchesBasename() {
        assertTrue(match("*.java", "A.java"));
        assertTrue(match("*.java", "src/main/java/A.java"));
        assertFalse(match("*.java", "src/A.kt"));
        assertTrue(match("Makefile", "a/b/Makefile"));
        assertFalse(match("Makefile", "a/b/Makefile2"));
    }

    @Test
    public void testStarDoesNotCrossSlashInPath() {
        // 含 / 的模式按完整路径匹配，* 不跨段
        assertTrue(match("src/*.java", "src/A.java"));
        assertFalse(match("src/*.java", "src/main/A.java"));
        assertFalse(match("src/*A.java", "src/main/A.java"));
    }

    @Test
    public void testQuestionMark() {
        assertTrue(match("file?.txt", "file1.txt"));
        assertFalse(match("file?.txt", "file10.txt"));
        assertFalse(match("file?.txt", "file.txt"));
        // ? 不跨段
        assertFalse(match("a?c", "a/c"));
    }

    @Test
    public void testCharacterClass() {
        assertTrue(match("[abc].txt", "a.txt"));
        assertTrue(match("[abc].txt", "c.txt"));
        assertFalse(match("[abc].txt", "d.txt"));
        assertTrue(match("[a-z0-9].log", "5.log"));
        assertFalse(match("[a-z0-9].log", "A.log"));
        // 反转：! 与 ^ 等价
        assertTrue(match("[!abc].txt", "d.txt"));
        assertFalse(match("[!abc].txt", "a.txt"));
        assertTrue(match("[^abc].txt", "d.txt"));
        // ']' 紧随 '[' 为字面成员
        assertTrue(match("[]].txt", "].txt"));
        // 未闭合 '[' 按字面
        assertTrue(match("[abc.txt", "[abc.txt"));
        assertFalse(match("[abc.txt", "xabc.txt"));
        // 转义
        assertTrue(match("\\*.txt", "*.txt"));
        assertFalse(match("\\*.txt", "a.txt"));
    }

    @Test
    public void testEmptyAndLiteralPatterns() {
        // 空模式不匹配任何路径
        assertFalse(match("", "x"));
        assertFalse(match("", ""));
        assertTrue(match("literal", "literal"));
        assertFalse(match("literal", "literalx"));
        // 长路径与深嵌套
        assertTrue(match("a/**/z", "a/b/c/d/e/f/z"));
        assertTrue(match("deep/*", "deep/" + "x".repeat(200)));
    }

    @Test
    public void testCompiledGlobCacheReusesInstance() {
        assertSame(CompiledGlob.compile("*.java"), CompiledGlob.compile("*.java"));
        assertNotSame(CompiledGlob.compile("*.java"), CompiledGlob.compile("*.kt"));
    }

    @Test
    public void testSetMatchingIncludeAndExclude() {
        // 有正则：命中任一正则且无负则命中
        GlobMatcher m = GlobMatcher.of(Arrays.asList("*.java", "!*Test.java"));
        assertTrue(m.accept("src/A.java"));
        assertTrue(m.accept("src/util/B.java"));
        assertFalse(m.accept("src/AUtilTest.java"));
        assertFalse(m.accept("src/A.kt")); // 正则未命中

        // 仅负则：全集减去负则命中
        GlobMatcher onlyExclude = GlobMatcher.of(Arrays.asList("!*.md"));
        assertTrue(onlyExclude.accept("src/A.java"));
        assertFalse(onlyExclude.accept("README.md"));
        assertFalse(onlyExclude.accept("docs/guide.md"));

        // 空集合：保留全部
        assertTrue(GlobMatcher.acceptAll().accept("anything/here.txt"));
        assertTrue(GlobMatcher.of(new ArrayList<>()).accept("anything/here.txt"));
    }

    /**
     * 与系统 rg 抽样对照验收（本机 rg 15.1.0；无 rg 环境自动跳过）。
     * 运行：-Dtest.rg.compare=true 触发。
     */
    @Test
    @EnabledIfSystemProperty(named = "test.rg.compare", matches = "true")
    public void testCompareAgainstSystemRg() throws IOException, InterruptedException {
        Path dir = Files.createTempDirectory("rg-glob-compare");
        List<String> files = Arrays.asList(
                "A.java", "src/B.java", "src/deep/C.java", "readme.md", "docs/g.md",
                "Makefile", "a/x/Makefile", "test.txt", "a/test.txt", "src/SampleTest.java");
        for (String f : files) {
            Path p = dir.resolve(f);
            Files.createDirectories(p.getParent());
            Files.writeString(p, "x");
        }
        String[] globs = {"*.java", "!*Test*", "src/**", "**/Makefile", "[tr]*.txt"};
        for (String glob : globs) {
            // rg 的 glob 匹配基于相对 CWD 的路径，须以临时目录为工作目录（绝对路径作根时 glob 不生效）
            Process p = new ProcessBuilder("rg", "--files", "--glob", glob)
                    .directory(dir.toFile())
                    .start();
            int code = p.waitFor();
            assertEquals(0, code, "rg failed for glob " + glob);
            String out = new String(p.getInputStream().readAllBytes());
            List<String> rgFiles = new ArrayList<>();
            for (String line : out.split("\n")) {
                if (!line.isBlank()) {
                    rgFiles.add(line.trim());
                }
            }
            java.util.Collections.sort(rgFiles);

            List<String> ours = new ArrayList<>();
            GlobMatcher matcher = GlobMatcher.of(Arrays.asList(glob));
            for (String f : files) {
                if (matcher.accept(f)) {
                    ours.add(f);
                }
            }
            java.util.Collections.sort(ours);
            assertEquals(rgFiles, ours, "glob=" + glob);
        }
    }

    @Test
    public void testPathStreamMatchesGlobSemantics() throws IOException {
        // 与 Files/Stream 协作的形状校验：相对路径统一使用 '/' 分隔
        Path dir = Files.createTempDirectory("glob-stream");
        Files.createDirectories(dir.resolve("src/deep"));
        Files.writeString(dir.resolve("src/deep/C.java"), "x");
        try (Stream<Path> stream = Files.walk(dir)) {
            List<String> found = new ArrayList<>();
            stream.forEach(p -> {
                String rel = dir.relativize(p).toString().replace('\\', '/');
                if (!rel.isEmpty() && GlobMatcher.of(Arrays.asList("**/*.java")).accept(rel)) {
                    found.add(rel);
                }
            });
            assertEquals(1, found.size());
            assertEquals("src/deep/C.java", found.get(0));
        }
    }
}

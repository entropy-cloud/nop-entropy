package io.nop.find.core.coordinator;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.find.core.NopFindException;
import io.nop.find.core.model.FindCommand;
import io.nop.find.core.model.FindMatch;
import io.nop.rg.core.walk.ParallelFileWalker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FindCoordinator} 端到端 / 接线 / 并行一致性测试（plan 2379 Phase 2）。
 *
 * <p>fixture 使用独立 token "nopfind"，避免临时目录名意外命中 query。
 */
public class FindCoordinatorTest {

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

    private Path root;

    @BeforeEach
    public void buildFixture() throws IOException {
        Files.writeString(tempDir.resolve(".gitignore"), "ignored-dir/\n*.tmp\n");
        write("ignored-dir/NopIgnored.java");
        write("scratch.tmp");
        write("src/main/java/NopFinder.java");
        write("src/main/java/NopFinderHelper.java");
        write("src/test/java/NopFinderTest.java");
        write("docs/readme.md");
        write("top-level-nopfind.md");
        write("plain.txt");
        root = tempDir;
    }

    private void write(String rel) throws IOException {
        Path file = tempDir.resolve(rel);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "// " + rel + "\n");
    }

    private List<String> rels(List<FindMatch> matches) {
        List<String> result = new ArrayList<>();
        for (FindMatch m : matches) {
            result.add(m.getRelativePath());
        }
        return result;
    }

    private List<String> find(String query, int threads, boolean respectGitignore) {
        return rels(new FindCoordinator(threads, respectGitignore, false)
                .find(FindCommand.of(root, query)));
    }

    // ---------- 端到端 ----------

    @Test
    public void testEndToEndRanksFuzzyMatches() {
        // 前缀命中按 "文件名长度 - query 长度" 排序；子串命中的 docs 文件排在其后
        assertEquals(List.of(
                        "src/main/java/NopFinder.java",
                        "src/test/java/NopFinderTest.java",
                        "src/main/java/NopFinderHelper.java",
                        "top-level-nopfind.md"),
                find("nopfind", 4, true));
    }

    @Test
    public void testScoresAscendAndMatchOrder() {
        List<FindMatch> matches = new FindCoordinator(4, true, false)
                .find(FindCommand.of(root, "nopfind"));
        for (int i = 1; i < matches.size(); i++) {
            assertTrue(matches.get(i - 1).getScore() <= matches.get(i).getScore(),
                    "score not ascending at " + i);
        }
        assertTrue(matches.get(0).getAbsolutePath().startsWith(root.toString()));
    }

    @Test
    public void testNoMatchReturnsEmptyList() {
        assertEquals(List.of(), find("zzzznomatchzzzz", 4, true));
    }

    @Test
    public void testLimitRespected() {
        List<FindMatch> matches = new FindCoordinator(4, true, false)
                .find(FindCommand.builder(root, "nopfind").limit(2).build());
        assertEquals(2, matches.size());
        assertEquals("src/main/java/NopFinder.java", matches.get(0).getRelativePath());
    }

    @Test
    public void testCaseSensitivityPropagates() {
        List<String> insensitive = rels(new FindCoordinator(4, true, false)
                .find(FindCommand.builder(root, "NOPFIND").caseSensitive(false).build()));
        assertTrue(insensitive.contains("src/main/java/NopFinder.java"));

        List<String> sensitive = rels(new FindCoordinator(4, true, false)
                .find(FindCommand.builder(root, "NOPFIND").caseSensitive(true).build()));
        assertFalse(sensitive.contains("src/main/java/NopFinder.java"));
    }

    // ---------- gitignore 接线 ----------

    @Test
    public void testGitignorePrunedByDefault() {
        List<String> found = find("nopfind", 4, true);
        assertFalse(found.contains("ignored-dir/NopIgnored.java"));
        assertFalse(found.contains("scratch.tmp"));
    }

    @Test
    public void testNoIgnoreIncludesIgnoredFiles() {
        // ignored-dir/ 被 .gitignore 剪掉；关闭 respectGitignore 后重新可见
        List<String> found = find("nopignored", 4, false);
        assertEquals(List.of("ignored-dir/NopIgnored.java"), found);
        assertEquals(List.of(), find("nopignored", 4, true));
    }

    @Test
    public void testHiddenFilesExcludedByDefault() throws IOException {
        Files.writeString(root.resolve(".nopfind-hidden.txt"), "x");
        assertFalse(find("nopfind", 4, false).contains(".nopfind-hidden.txt"));
        assertTrue(rels(new FindCoordinator(4, false, true)
                .find(FindCommand.of(root, "nopfind"))).contains(".nopfind-hidden.txt"));
    }

    // ---------- glob 接线 ----------

    @Test
    public void testGlobIncludeFilter() {
        List<String> found = rels(new FindCoordinator(4, true, false)
                .find(FindCommand.builder(root, "nopfind").glob("*.java").build()));
        assertEquals(List.of(
                "src/main/java/NopFinder.java",
                "src/test/java/NopFinderTest.java",
                "src/main/java/NopFinderHelper.java"), found);
    }

    @Test
    public void testGlobExcludeFilter() {
        List<String> found = rels(new FindCoordinator(4, true, false)
                .find(FindCommand.builder(root, "nopfind").glob("*.java").glob("!src/test/**").build()));
        assertFalse(found.contains("src/test/java/NopFinderTest.java"));
        assertTrue(found.contains("src/main/java/NopFinder.java"));
    }

    // ---------- 并行一致性 ----------

    @Test
    public void testParallelResultIdenticalToSingleThread() {
        List<String> single = find("nopfind", 1, true);
        for (int threads : new int[]{2, 4, 8, 16}) {
            assertEquals(single, find("nopfind", threads, true), "threads=" + threads);
        }
    }

    @Test
    public void testParallelScoresIdenticalToSingleThread() {
        FindCommand command = FindCommand.builder(root, "nopfind").limit(3).build();
        String single = describe(new FindCoordinator(1, true, false).find(command));
        for (int threads : new int[]{2, 8}) {
            assertEquals(single, describe(new FindCoordinator(threads, true, false).find(command)),
                    "threads=" + threads);
        }
    }

    @Test
    public void testParallelIdenticalOnLargeFixture() throws IOException {
        for (int i = 0; i < 300; i++) {
            write("bulk/dir" + (i % 7) + "/NopFindBulk" + i + ".java");
        }
        FindCommand command = FindCommand.builder(root, "nopfind").limit(25).build();
        String single = describe(new FindCoordinator(1, true, false).find(command));
        for (int threads : new int[]{2, 4, 8}) {
            assertEquals(single, describe(new FindCoordinator(threads, true, false).find(command)),
                    "threads=" + threads);
        }
        assertTrue(single.split("\n").length >= 25);
    }

    @Test
    public void testParallelTieBreakIndependentOfThreadCount() throws IOException {
        // 同分候选：tie-break 只能来自全局 index（= 排序后候选下标）。
        // 若 index 退化为分片内局部计数，threads>1 时归并顺序会随分片方式改变 —— 本测试即该不变式的钉。
        for (String dir : new String[]{"a", "b", "c", "d", "e"}) {
            write(dir + "/NopfindTie.java");
        }
        FindCommand command = FindCommand.builder(root, "nopfindtie").limit(5).build();
        List<String> expected = List.of("a/NopfindTie.java", "b/NopfindTie.java", "c/NopfindTie.java",
                "d/NopfindTie.java", "e/NopfindTie.java");
        List<FindMatch> single = new FindCoordinator(1, true, false).find(command);
        assertEquals(expected, rels(single));
        // 前缀档：len("nopfindtie.java") - len("nopfindtie") = 15 - 10 = 5
        assertEquals(5, single.get(0).getScore());
        assertEquals(5, single.get(4).getScore());

        String singleDesc = describe(single);
        for (int threads : new int[]{2, 3, 5, 8}) {
            assertEquals(singleDesc, describe(new FindCoordinator(threads, true, false).find(command)),
                    "threads=" + threads);
        }
    }

    private static String describe(List<FindMatch> matches) {
        StringBuilder sb = new StringBuilder();
        for (FindMatch m : matches) {
            sb.append(m.getScore()).append(':').append(m.getRelativePath()).append('\n');
        }
        return sb.toString();
    }

    // ---------- 接线验证（walker 确实被调用） ----------

    @Test
    public void testWalkerActuallyInvokedWithConfiguredFlags() throws IOException {
        SpyCoordinator spy = new SpyCoordinator(3, true, false);
        spy.find(FindCommand.of(root, "nopfind"));
        assertEquals(1, spy.walkersCreated.get(), "FindCoordinator must delegate traversal to ParallelFileWalker");
        assertEquals(1, spy.walksInvoked.get(), "ParallelFileWalker.walk() must actually be invoked");
        assertEquals(root, spy.lastRoot);
        assertEquals(3, spy.lastThreads);
        assertTrue(spy.lastRespectGitignore);
        assertFalse(spy.lastIncludeHidden);
    }

    @Test
    public void testWalkerIsInvokedWithNoIgnoreFlag() {
        SpyCoordinator spy = new SpyCoordinator(2, false, false);
        spy.find(FindCommand.of(root, "nopfind"));
        assertEquals(1, spy.walksInvoked.get());
        assertFalse(spy.lastRespectGitignore);
    }

    // ---------- 错误路径（无静默跳过） ----------

    @Test
    public void testEmptyQueryRejected() {
        assertThrows(NopFindException.class, () -> FindCommand.of(root, ""));
    }

    @Test
    public void testNonPositiveLimitRejected() {
        assertThrows(NopFindException.class, () -> FindCommand.builder(root, "x").limit(0).build());
        assertThrows(NopFindException.class, () -> FindCommand.builder(root, "x").limit(-1).build());
    }

    @Test
    public void testNullCommandRejected() {
        assertThrows(NopFindException.class, () -> new FindCoordinator(2, true, false).find(null));
    }

    @Test
    public void testNonPositiveThreadsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FindCoordinator(0, true, false));
    }

    @Test
    public void testMissingRootFailsFast() {
        // 遍历错误快速失败契约由 ParallelFileWalker 承担（NopRgException ⊂ NopException）
        Path missing = root.resolve("no-such-dir");
        assertThrows(NopException.class, () -> new FindCoordinator(2, true, false)
                .find(FindCommand.of(missing, "nopfind")));
    }

    private static class SpyCoordinator extends FindCoordinator {
        final AtomicInteger walkersCreated = new AtomicInteger();
        final AtomicInteger walksInvoked = new AtomicInteger();
        Path lastRoot;
        int lastThreads;
        boolean lastRespectGitignore;
        boolean lastIncludeHidden;

        SpyCoordinator(int threads, boolean respectGitignore, boolean includeHidden) {
            super(threads, respectGitignore, includeHidden);
        }

        @Override
        ParallelFileWalker newWalker(Path root) {
            walkersCreated.incrementAndGet();
            lastRoot = root;
            lastThreads = getThreads();
            lastRespectGitignore = isRespectGitignore();
            lastIncludeHidden = isIncludeHidden();
            return new ParallelFileWalker(root, lastThreads, lastRespectGitignore, lastIncludeHidden) {
                @Override
                public List<Path> walk() {
                    walksInvoked.incrementAndGet();
                    return super.walk();
                }
            };
        }
    }
}
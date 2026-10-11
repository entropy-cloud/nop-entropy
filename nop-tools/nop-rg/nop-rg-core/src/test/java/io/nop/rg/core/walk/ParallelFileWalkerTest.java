package io.nop.rg.core.walk;

import io.nop.core.initialize.CoreInitialization;
import io.nop.rg.core.NopRgException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ParallelFileWalker 契约测试。前置：GitIgnoreFile 依赖已初始化 VFS（调用方职责），
 * 这里以 CoreInitialization.initialize()/destroy() 承担（同 nop-core TestResourceHelper 模式）。
 */
public class ParallelFileWalkerTest {
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
        // .gitignore: ignored-dir/ 与 *.tmp 被忽略；!keep.tmp 反转
        Files.writeString(tempDir.resolve(".gitignore"), "ignored-dir/\n*.tmp\n!keep.tmp\n");
        Files.createDirectories(tempDir.resolve("ignored-dir"));
        Files.writeString(tempDir.resolve("ignored-dir/x.txt"), "x");
        Files.writeString(tempDir.resolve("a.tmp"), "a");
        Files.writeString(tempDir.resolve("keep.tmp"), "k");
        Files.createDirectories(tempDir.resolve(".hidden"));
        Files.writeString(tempDir.resolve(".hidden/file.txt"), "h");
        Files.createDirectories(tempDir.resolve("sub"));
        Files.writeString(tempDir.resolve("sub/keep2.txt"), "s2");
        Files.writeString(tempDir.resolve("keep.txt"), "k1");
        return tempDir;
    }

    private static List<String> relative(List<Path> files, Path root) {
        return files.stream().map(p -> root.relativize(p).toString().replace('\\', '/')).toList();
    }

    @Test
    public void testDefaultFiltersGitignoreAndHidden() throws IOException {
        Path root = buildTree();
        List<Path> files = ParallelFileWalker.of(root).walk();
        // gitignore 生效：ignored-dir/、a.tmp 被剪；!keep.tmp 反转生效；隐藏文件/目录不入结果
        assertEquals(List.of("keep.tmp", "keep.txt", "sub/keep2.txt"), relative(files, root));
    }

    @Test
    public void testNoIgnoreIncludesIgnoredFiles() throws IOException {
        Path root = buildTree();
        List<Path> files = new ParallelFileWalker(root, 4, false, false).walk();
        assertEquals(List.of("a.tmp", "ignored-dir/x.txt", "keep.tmp", "keep.txt", "sub/keep2.txt"),
                relative(files, root));
    }

    @Test
    public void testIncludeHidden() throws IOException {
        Path root = buildTree();
        List<Path> files = new ParallelFileWalker(root, 4, true, true).walk();
        // includeHidden 后 .gitignore 与 .hidden/file.txt 入结果（gitignore 规则仍生效）
        assertEquals(List.of(".gitignore", ".hidden/file.txt", "keep.tmp", "keep.txt", "sub/keep2.txt"),
                relative(files, root));
    }

    @Test
    public void testThreadCountDoesNotChangeResult() throws IOException {
        Path root = buildTree();
        List<Path> single = new ParallelFileWalker(root, 1, true, false).walk();
        List<Path> multi = new ParallelFileWalker(root, 8, true, false).walk();
        assertEquals(relative(single, root), relative(multi, root));
        List<Path> singleNoIgnore = new ParallelFileWalker(root, 1, false, true).walk();
        List<Path> multiNoIgnore = new ParallelFileWalker(root, 8, false, true).walk();
        assertEquals(relative(singleNoIgnore, root), relative(multiNoIgnore, root));
    }

    @Test
    public void testWalkFailsFastOnUnreadableDirectory() throws Exception {
        // OPT 修复（Wave 2 audit Minor m2）：目录不可读时 walk() 抛 NopRgException，而非静默返回不完整结果
        Path root = buildTree();
        Path locked = root.resolve("locked-dir");
        Files.createDirectories(locked);
        Files.writeString(locked.resolve("x.txt"), "x");
        var perms = java.nio.file.attribute.PosixFilePermissions.fromString("---------");
        java.nio.file.Files.setPosixFilePermissions(locked, perms);
        try {
            assertThrows(NopRgException.class, () -> ParallelFileWalker.of(root).walk());
        } finally {
            java.nio.file.Files.setPosixFilePermissions(locked,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    public void testInvalidArgumentsAndNonDirectory() throws IOException {
        assertThrows(IllegalArgumentException.class,
                () -> new ParallelFileWalker(tempDir, 0, true, false));
        Path file = Files.writeString(tempDir.resolve("f.txt"), "x");
        assertThrows(NopRgException.class, () -> ParallelFileWalker.of(file).walk());
        // 正常路径仍可用（VFS 已由 BeforeAll 初始化）
        assertTrue(ParallelFileWalker.of(tempDir).walk().size() >= 1);
    }

    @Test
    @Timeout(value = 60)
    public void testSymlinkDirCycleNotFollowed() throws IOException {
        // plan 2273 A1：目录环（a/b/loop -> root）未修复时无限递归；默认路径
        // （respectGitignore=true）下 GitIgnoreFile 加载与 walker 遍历双修复后须有限时间终止
        Path root = tempDir.resolve("cycle-root");
        Files.createDirectories(root.resolve("a/b"));
        Files.writeString(root.resolve("a/keep1.txt"), "1");
        Files.writeString(root.resolve("a/b/keep2.txt"), "2");
        try {
            Files.createSymbolicLink(root.resolve("a/b/loop"), root);
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "symbolic links not supported on this platform");
        }
        List<String> rel = relative(ParallelFileWalker.of(root).walk(), root);
        // 链接目录与其目标目录的文件不重复出现
        assertEquals(List.of("a/b/keep2.txt", "a/keep1.txt"), rel);
    }

    @Test
    public void testSymlinkFileFollowedAndDanglingSkipped() throws IOException {
        // plan 2273 A1 行为防漂移：文件符号链接按普通文件读取（与 rg 的差异，cli README 已记录）；
        // 悬空链接 isDirectory/isRegularFile 均为 false，静默跳过（现状钉死）
        Path root = tempDir.resolve("link-root");
        Files.createDirectories(root);
        Files.writeString(root.resolve("real.txt"), "real");
        try {
            Files.createSymbolicLink(root.resolve("link.txt"), root.resolve("real.txt"));
            Files.createSymbolicLink(root.resolve("dangling.txt"), root.resolve("missing.txt"));
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "symbolic links not supported on this platform");
        }
        List<String> rel = relative(new ParallelFileWalker(root, 2, false, false).walk(), root);
        assertEquals(List.of("link.txt", "real.txt"), rel);
    }
}

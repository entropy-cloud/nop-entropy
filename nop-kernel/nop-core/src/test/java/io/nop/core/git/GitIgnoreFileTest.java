package io.nop.core.git;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 单元测试：验证 GitIgnoreFile 的 gitignore 语义（nop-rg roadmap Stage 1 / GIT-04）。
 * 前置：GitIgnoreFile 的规则加载依赖已初始化的 VirtualFileSystem，见 TestPathTreeNode 先例。
 */
public class GitIgnoreFileTest extends BaseTestCase {
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

    private File root;
    private File sub;

    @BeforeEach
    public void setUpDirs() throws IOException {
        root = tempDir.resolve("root").toFile();
        sub = new File(root, "sub");
        assertTrue(sub.mkdirs());
    }

    private GitIgnoreFile ignoreAtRoot(String content) throws IOException {
        Files.writeString(root.toPath().resolve(".gitignore"), content);
        return GitIgnoreFile.create(new FileResource(root));
    }

    private IResource file(String relativePath) {
        return new FileResource(new File(root, relativePath));
    }

    private IResource dir(String relativePath) {
        return new FileResource(new File(root, relativePath));
    }

    @Test
    public void testNoGitIgnoreFileIsEmpty() {
        GitIgnoreFile f = GitIgnoreFile.create(new FileResource(root));
        assertTrue(f.isEmpty());
        // 无规则时任何文件都不忽略
        assertFalse(f.isIgnored(file("anything.txt")));
    }

    @Test
    public void testLiteralNameMatchesInAllSubDirs() throws IOException {
        GitIgnoreFile f = ignoreAtRoot("log.txt\n");
        assertTrue(f.isIgnored(file("log.txt")));
        assertTrue(f.isIgnored(file("sub/log.txt")));
        assertFalse(f.isIgnored(file("other.txt")));
    }

    @Test
    public void testStarMatchesAnyDepthButNotAcrossSlash() throws IOException {
        GitIgnoreFile f = ignoreAtRoot("*.class\n");
        assertTrue(f.isIgnored(file("A.class")));
        assertTrue(f.isIgnored(file("sub/B.class")));
        // 非锚定模式经 **/ 展开后匹配任意深度（等价 git 行为）
        assertTrue(f.isIgnored(file("sub/dir1/C.class")));
        assertFalse(f.isIgnored(file("A.txt")));
    }

    @Test
    public void testAnchoredStarDoesNotCrossSlash() throws IOException {
        GitIgnoreFile f = ignoreAtRoot("/a*b.txt\n");
        assertTrue(f.isIgnored(file("axyz_b.txt")));
        // 锚定模式下 * 不跨段
        assertFalse(f.isIgnored(file("a/x/b.txt")));
        assertFalse(f.isIgnored(file("sub/axyz_b.txt")));
    }

    @Test
    public void testDoubleStarCrossesSegments() throws IOException {
        GitIgnoreFile f = ignoreAtRoot("build/**/out.txt\n");
        assertTrue(f.isIgnored(file("build/out.txt")));
        assertTrue(f.isIgnored(file("build/x/out.txt")));
        assertTrue(f.isIgnored(file("build/x/y/out.txt")));
        assertFalse(f.isIgnored(file("out/out.txt")));
    }

    @Test
    public void testQuestionMarkMatchesSingleChar() throws IOException {
        GitIgnoreFile f = ignoreAtRoot("file?.txt\n");
        assertTrue(f.isIgnored(file("file1.txt")));
        assertFalse(f.isIgnored(file("file10.txt")));
        assertFalse(f.isIgnored(file("file.txt")));
    }

    @Test
    public void testCharacterClass() throws IOException {
        GitIgnoreFile f = ignoreAtRoot("[abc].txt\n");
        assertTrue(f.isIgnored(file("a.txt")));
        assertTrue(f.isIgnored(file("c.txt")));
        assertFalse(f.isIgnored(file("d.txt")));
    }

    @Test
    public void testNegationLastMatchWins() throws IOException {
        GitIgnoreFile f = ignoreAtRoot("*.log\n!keep.log\n");
        assertTrue(f.isIgnored(file("run.log")));
        assertTrue(f.isIgnored(file("sub/run.log")));
        assertFalse(f.isIgnored(file("keep.log")));
    }

    @Test
    public void testNegationOrderMatters() throws IOException {
        // 反转规则在前、忽略规则在后时，忽略生效
        GitIgnoreFile f = ignoreAtRoot("!keep.log\n*.log\n");
        assertTrue(f.isIgnored(file("keep.log")));
    }

    @Test
    public void testAnchoredPatternOnlyMatchesAtRoot() throws IOException {
        GitIgnoreFile f = ignoreAtRoot("/top.txt\n");
        assertTrue(f.isIgnored(file("top.txt")));
        assertFalse(f.isIgnored(file("sub/top.txt")));
    }

    @Test
    public void testUnanchoredPatternMatchesAnyDepth() throws IOException {
        GitIgnoreFile f = ignoreAtRoot("sub/top.txt\n");
        assertTrue(f.isIgnored(file("sub/top.txt")));
        assertFalse(f.isIgnored(file("top.txt")));
    }

    @Test
    public void testCommentAndBlankLinesIgnored() throws IOException {
        GitIgnoreFile f = ignoreAtRoot("# comment line\n\n   \n*.tmp\n");
        assertTrue(f.isIgnored(file("a.tmp")));
        assertFalse(f.isIgnored(file("comment")));
    }

    @Test
    public void testDirectoryOnlyRule() throws IOException {
        GitIgnoreFile f = ignoreAtRoot("build/\n");
        assertTrue(new File(root, "build").mkdirs());
        // 目录本身被忽略
        assertTrue(f.isIgnored(dir("build")));
        // 目录不存在（isDirectory=false）时，同名路径按目录子项正则判定，不匹配
        assertFalse(f.isIgnored(file("buildX")));
        // 子项被忽略
        assertTrue(f.isIgnored(file("build/out.txt")));
        assertTrue(f.isIgnored(file("build/x/out.txt")));
        // 同名前缀不误伤
        assertFalse(f.isIgnored(file("build2/a.txt")));
    }

    @Test
    public void testNestedGitIgnoreScope() throws IOException {
        Files.writeString(root.toPath().resolve(".gitignore"), "*.tmp\n");
        Files.writeString(sub.toPath().resolve(".gitignore"), "!keep.tmp\n");
        GitIgnoreFile f = GitIgnoreFile.create(new FileResource(root));
        assertTrue(f.isIgnored(file("a.tmp")));
        assertTrue(f.isIgnored(file("sub/other.tmp")));
        // 子目录的反转规则在祖先规则之后应用，最后匹配生效
        assertFalse(f.isIgnored(file("sub/keep.tmp")));
    }

    @Test
    public void testRootItselfNotIgnored() throws IOException {
        GitIgnoreFile f = ignoreAtRoot("*\n");
        assertFalse(f.isIgnored(new FileResource(root)));
    }

    @Test
    public void testResourceOutsideRootIsIgnored() throws IOException {
        GitIgnoreFile f = ignoreAtRoot("*.txt\n");
        assertTrue(f.isIgnored(new FileResource(tempDir.resolve("outside.txt").toFile())));
    }

    @Test
    public void testReloadPicksUpNewRules() throws IOException {
        GitIgnoreFile f = ignoreAtRoot("*.tmp\n");
        assertFalse(f.isIgnored(file("added.log")));
        Files.writeString(root.toPath().resolve(".gitignore"), "*.tmp\n*.log\n");
        f.reload();
        assertTrue(f.isIgnored(file("added.log")));
        assertEquals(1, f.getAllRules().size());
    }

    @Test
    @Timeout(value = 60)
    public void testSymlinkDirCycleNotFollowed() throws IOException {
        // 平台不支持符号链接（如无特权 Windows）时跳过
        Path subDir = root.toPath().resolve("sub");
        Files.createDirectories(subDir.resolve("inner"));
        Path link = subDir.resolve("loop");
        try {
            Files.createSymbolicLink(link, root.toPath());
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "symbolic links not supported on this platform");
        }
        // sub/loop -> root：未修复的子目录规则加载会沿符号链接无限递归
        GitIgnoreFile f = ignoreAtRoot("*.log\n");
        // 规则加载正常：环目录不递归，既有匹配语义不受影响
        assertTrue(f.isIgnored(file("a.log")));
        assertTrue(f.isIgnored(file("sub/inner/b.log")));
        assertFalse(f.isIgnored(file("a.txt")));
    }
}

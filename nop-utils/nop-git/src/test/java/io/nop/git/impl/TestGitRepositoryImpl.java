package io.nop.git.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import io.nop.core.unittest.BaseTestCase;
import io.nop.git.api.IGitRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static io.nop.git.GitErrors.ERR_GIT_INVALID_COMMIT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestGitRepositoryImpl extends BaseTestCase {
    File repoDir;
    IGitRepository repository;

    @BeforeEach
    public void setUp() throws Exception {
        repoDir = getTargetFile("git-impl-test/repo");
        FileHelper.deleteAll(repoDir);
        assertTrue(repoDir.mkdirs());

        repository = new GitRepositoryImpl(null, repoDir, null);
        repository.init();

        // JGit commit 需要 committer 身份，写入仓库级配置避免依赖机器全局 git 配置
        File config = new File(repoDir, ".git/config");
        Files.writeString(config.toPath(),
                "[user]\n\tname = tester\n\temail = tester@example.com\n",
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    @AfterEach
    public void tearDown() {
        FileHelper.deleteAll(repoDir);
    }

    private void commitInitial() throws Exception {
        Files.writeString(new File(repoDir, "a.txt").toPath(), "line1\nline2\n", StandardCharsets.UTF_8);
        repository.add(".");
        repository.commit("init", "tester");
    }

    @Test
    public void testGetWorkingTreeDiff() throws Exception {
        commitInitial();

        // 工作区修改已提交文件 + 新增未跟踪文件
        Files.writeString(new File(repoDir, "a.txt").toPath(), "line1\nline2-modified\n", StandardCharsets.UTF_8);
        Files.writeString(new File(repoDir, "b.txt").toPath(), "new-file\n", StandardCharsets.UTF_8);

        List<IGitRepository.GitDiff> diffs = repository.getWorkingTreeDiff();
        assertNotNull(diffs);
        assertEquals(2, diffs.size());

        IGitRepository.GitDiff modifyDiff = diffs.stream().filter(d -> "a.txt".equals(d.getNewPath())).findFirst().orElse(null);
        assertNotNull(modifyDiff);
        assertEquals("MODIFY", modifyDiff.getChangeType());
        assertTrue(modifyDiff.getDiffContent().contains("-line2"));
        assertTrue(modifyDiff.getDiffContent().contains("+line2-modified"));

        IGitRepository.GitDiff addDiff = diffs.stream().filter(d -> "b.txt".equals(d.getNewPath())).findFirst().orElse(null);
        assertNotNull(addDiff);
        assertEquals("ADD", addDiff.getChangeType());
    }

    @Test
    public void testGetWorkingTreeDiff_cleanTree() throws Exception {
        commitInitial();
        List<IGitRepository.GitDiff> diffs = repository.getWorkingTreeDiff();
        assertNotNull(diffs);
        assertTrue(diffs.isEmpty());
    }

    @Test
    public void testGetFileContentInvalidRevision() throws Exception {
        commitInitial();
        Files.writeString(new File(repoDir, "a.txt").toPath(), "changed\n", StandardCharsets.UTF_8);
        repository.add(".");
        repository.commit("change", "tester");

        // 非法 revision 必须报 ERR_GIT_INVALID_COMMIT_ID，而不是把 NPE 包成语义模糊的 NopException
        NopException e = assertThrows(NopException.class,
                () -> repository.getFileContent("a.txt", "no-such-revision"));
        assertEquals(ERR_GIT_INVALID_COMMIT_ID.getErrorCode(), e.getErrorCode());

        // 合法 revision 正常返回历史内容
        String content = repository.getFileContent("a.txt", "HEAD~1");
        assertEquals("line1\nline2\n", content);
    }

    @Test
    public void testGetChangedFilesBetweenCommitsInvalidRevision() {
        NopException e = assertThrows(NopException.class,
                () -> repository.getChangedFilesBetweenCommits("bad-old", "bad-new"));
        assertEquals(ERR_GIT_INVALID_COMMIT_ID.getErrorCode(), e.getErrorCode());
    }
}

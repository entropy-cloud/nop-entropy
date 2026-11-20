package io.nop.git.core;

import io.nop.git.api.ChangeType;
import io.nop.git.api.DiffOptions;
import io.nop.git.api.IGitRepositoryService;
import io.nop.git.api.LogOptions;
import io.nop.git.api.NopGitCommit;
import io.nop.git.api.NopGitDiffEntry;
import io.nop.git.api.NopGitException;
import io.nop.git.api.NopGitStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end facade tests on a real temporary repository: open/init, add,
 * commit, log, branch, revision resolution, status and diff.
 */
class JGitRepositoryServiceLifecycleTest {

    @TempDir
    Path dir;

    private Path file(String name, String content) throws IOException {
        Path path = dir.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    @Test
    void fullLifecycle() throws IOException {
        try (IGitRepositoryService svc = IGitRepositoryService.init(dir.toFile(), "trunk")) {
            // init with explicit default branch; unborn HEAD has no refs yet
            assertTrue(svc.branchList().isEmpty());

            // empty repo semantics
            assertTrue(svc.log(LogOptions.defaults()).isEmpty());
            assertNull(svc.currentBranch());
            assertNull(svc.resolveRevision("HEAD"));

            file("a.txt", "line1\nline2\n");
            svc.add(".");
            NopGitCommit commit = svc.commit("initial commit", "tester", "tester@example.com");

            assertEquals("initial commit", commit.shortMessage());
            assertEquals("tester", commit.authorName());
            assertNotNull(commit.id());
            assertTrue(commit.parentIds().isEmpty());

            // log now has one entry
            List<NopGitCommit> log = svc.log(LogOptions.defaults());
            assertEquals(1, log.size());
            assertEquals(commit.id(), log.get(0).id());
            assertEquals(7, log.get(0).abbrevId().length());

            // maxCount bounds the history
            assertTrue(svc.log(LogOptions.maxCount(1)).size() <= 1);

            // currentBranch and revision resolution after first commit
            assertEquals("trunk", svc.currentBranch());
            assertEquals(commit.id(), svc.resolveRevision("HEAD"));
            assertEquals(commit.id(), svc.resolveRevision("trunk"));
            assertNull(svc.resolveRevision("no-such-branch"));

            // branch list
            assertEquals(1, svc.branchList().size());
            assertTrue(svc.branchList().get(0).isBranch());
            assertEquals(commit.id(), svc.branchList().get(0).objectId());
            assertTrue(svc.branchList().get(0).name().startsWith("refs/heads/"));

            // status is clean after commit
            NopGitStatus status = svc.status();
            assertTrue(status.isClean());
            assertEquals("trunk", status.branch());

            // staged diff after a change
            Files.writeString(dir.resolve("a.txt"), "line1\nline2\nline3\n");
            file("b.txt", "new file\n");
            List<NopGitDiffEntry> worktreeDiff = svc.diff(DiffOptions.worktree());
            // b.txt is untracked: git diff does not report it (only a.txt)
            assertEquals(1, worktreeDiff.size());
            NopGitDiffEntry modified = worktreeDiff.get(0);
            assertEquals("a.txt", modified.newPath());
            assertEquals(ChangeType.MODIFIED, modified.changeType());
            assertEquals(1, modified.insertions());
            assertEquals(0, modified.deletions());

            // untracked files are invisible to git diff (native parity)
            // staging changes moves the diff to --cached
            svc.add(".");
            assertTrue(svc.diff(DiffOptions.worktree()).isEmpty());
            List<NopGitDiffEntry> cachedDiff = svc.diff(DiffOptions.forCached());
            assertEquals(2, cachedDiff.size());

            // second commit gains a parent
            NopGitCommit second = svc.commit("second commit", "tester", "tester@example.com");
            assertEquals(List.of(commit.id()), second.parentIds());

            // rev-vs-worktree diff and clean state
            assertTrue(svc.diff(DiffOptions.against("HEAD")).isEmpty());
            List<NopGitCommit> two = svc.log(LogOptions.maxCount(2));
            assertEquals(2, two.size());
            assertEquals(second.id(), two.get(0).id());
        }
    }

    @Test
    void defaultBranchIsMasterWhenNotConfigured() throws IOException {
        try (IGitRepositoryService svc = IGitRepositoryService.init(dir.toFile())) {
            file("a.txt", "content\n");
            svc.add(".");
            svc.commit("first", "tester", "tester@example.com");
            assertEquals("master", svc.currentBranch());
        }
    }

    @Test
    void openFailsOutsideRepository(@TempDir Path emptyDir) {
        NopGitException e = assertThrows(NopGitException.class,
                () -> IGitRepositoryService.open(emptyDir.toFile()));
        assertTrue(e.getMessage().contains("not a git repository"));
    }

    @Test
    void openFailsBelowJdkBaseline() {
        assertThrows(NopGitException.class, () -> JdkBaseline.requireFeature(99));
        // the real baseline passes on any JVM that can run this test
        JdkBaseline.requireBaseline();
    }

    @Test
    void useAfterCloseFails() {
        IGitRepositoryService svc = IGitRepositoryService.init(dir.toFile());
        svc.close();
        assertThrows(IllegalStateException.class, svc::status);
        svc.close(); // idempotent
    }

    @Test
    void commitFailsWithoutIdentity() throws IOException {
        try (IGitRepositoryService svc = IGitRepositoryService.init(dir.toFile())) {
            org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(dir.toFile());
            git.getRepository().getConfig().setString("user", null, "name", "");
            git.getRepository().getConfig().setString("user", null, "email", "");
            git.getRepository().getConfig().save();
            git.close();

            file("a.txt", "content\n");
            svc.add(".");
            NopGitException e = assertThrows(NopGitException.class,
                    () -> svc.commit("msg", null, null));
            assertTrue(e.getMessage().contains("identity is not configured"));
            assertTrue(svc.log(LogOptions.defaults()).isEmpty());
        }
    }
}

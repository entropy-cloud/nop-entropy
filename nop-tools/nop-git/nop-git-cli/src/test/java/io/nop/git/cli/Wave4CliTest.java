package io.nop.git.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 4 behavior tests: P2 commands, worktree self-build bidirectional
 * compatibility with real git, P3 local/network/history, policy E2E.
 * Network tests use local file remotes only.
 */
class Wave4CliTest {

    @TempDir
    Path base;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(Path repo, String... args) {
        out.reset();
        err.reset();
        String[] full = new String[args.length + 2];
        full[0] = "-C";
        full[1] = repo.toAbsolutePath().toString();
        System.arraycopy(args, 0, full, 2, args.length);
        return GitCompatCli.run(full,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private String out() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return err.toString(StandardCharsets.UTF_8);
    }

    private Path repo(String name) throws Exception {
        Path dir = base.resolve(name);
        Files.createDirectories(dir);
        IGitRepositorySmokeHelper.initRepo(dir);
        Files.writeString(dir.resolve("a.txt"), "one\ntwo\nthree\n");
        assertEquals(0, run(dir, "add", "."));
        assertEquals(0, run(dir, "commit", "-m", "base"));
        return dir;
    }

    // ------------------------------------------------------------------
    // P2
    // ------------------------------------------------------------------

    @Test
    void checkoutCreateAndSwitch() throws Exception {
        Path dir = repo("ck");
        assertEquals(0, run(dir, "checkout", "-b", "feat"));
        assertTrue(err().contains("Switched to a new branch 'feat'"));
        assertEquals(0, run(dir, "checkout", "master"));
        assertTrue(err().contains("Switched to branch 'master'"));
        // already exists
        assertEquals(128, run(dir, "checkout", "-b", "feat"));
    }

    @Test
    void resetModes() throws Exception {
        Path dir = repo("reset");
        Files.writeString(dir.resolve("a.txt"), "changed\n");
        assertEquals(0, run(dir, "reset", "--hard", "HEAD"));
        assertEquals("one\ntwo\nthree\n", Files.readString(dir.resolve("a.txt")));
        // mixed: stage a change then unstage it (worktree change remains)
        Files.writeString(dir.resolve("a.txt"), "one\ntwo\nthree\nfour\n");
        assertEquals(0, run(dir, "add", "."));
        assertEquals(0, run(dir, "reset", "HEAD"));
        assertEquals(0, run(dir, "status", "--porcelain"));
        assertTrue(out().contains(" M a.txt"));
    }

    @Test
    void cleanDryRunAndForce() throws Exception {
        Path dir = repo("clean");
        Files.writeString(dir.resolve("junk.txt"), "junk\n");
        assertEquals(0, run(dir, "clean", "-n"));
        assertTrue(out().contains("Would remove junk.txt"));
        assertTrue(Files.exists(dir.resolve("junk.txt")));
        assertEquals(0, run(dir, "clean", "-f"));
        assertTrue(out().contains("Removing junk.txt"));
        assertFalse(Files.exists(dir.resolve("junk.txt")));
    }

    @Test
    void restoreDiscardsWorktreeChanges() throws Exception {
        Path dir = repo("restore");
        Files.writeString(dir.resolve("a.txt"), "changed\n");
        assertEquals(0, run(dir, "restore", "a.txt"));
        assertEquals("one\ntwo\nthree\n", Files.readString(dir.resolve("a.txt")));
    }

    @Test
    void rmAndMv() throws Exception {
        Path dir = repo("rm");
        Files.writeString(dir.resolve("b.txt"), "b\n");
        assertEquals(0, run(dir, "add", "."));
        assertEquals(0, run(dir, "commit", "-m", "add b"));
        assertEquals(0, run(dir, "rm", "b.txt"));
        assertTrue(out().contains("rm 'b.txt'"));
        assertFalse(Files.exists(dir.resolve("b.txt")));
        assertEquals(0, run(dir, "mv", "a.txt", "renamed.txt"));
        assertTrue(Files.exists(dir.resolve("renamed.txt")));
    }

    @Test
    void stashPushListPop() throws Exception {
        Path dir = repo("stash");
        Files.writeString(dir.resolve("a.txt"), "one\ntwo\nthree\nmodified\n");
        assertEquals(0, run(dir, "stash", "push"));
        assertTrue(err().contains("Saved working directory"));
        assertEquals("one\ntwo\nthree\n", Files.readString(dir.resolve("a.txt")));
        assertEquals(0, run(dir, "stash", "list"));
        assertTrue(out().contains("stash@{0}: WIP on master"));
        assertEquals(0, run(dir, "stash", "pop"));
        assertEquals("one\ntwo\nthree\nmodified\n", Files.readString(dir.resolve("a.txt")));
    }

    // ------------------------------------------------------------------
    // worktree: bidirectional compatibility with real git
    // ------------------------------------------------------------------

    @Test
    void worktreeBidirectionalRealGitCreatesNopReadsAndRemoves() throws Exception {
        Path dir = repo("wt-rn");
        // real git creates the linked worktree
        ProcessBuilder pb = new ProcessBuilder("git", "worktree", "add", "-b", "feat",
                base.resolve("wt-real").toAbsolutePath().toString());
        pb.directory(dir.toFile());
        pb.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
        pb.redirectErrorStream(true);
        String nativeOut;
        java.lang.Process process = pb.start();
        try (var is = process.getInputStream()) {
            nativeOut = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        int nativeExit = process.waitFor();
        assertEquals(0, nativeExit, "native worktree add failed: " + nativeOut);

        // nop-git reads it
        assertEquals(0, run(dir, "worktree", "list"));
        assertTrue(out().contains("feat"));
        // porcelain shows registry format like native
        assertEquals(0, run(dir, "worktree", "list", "--porcelain"));
        assertTrue(out().contains("worktree "));
        assertTrue(out().contains("branch refs/heads/feat"));

        // nop-git removes the real-git-created worktree
        assertEquals(0, run(dir, "worktree", "remove", "--force",
                base.resolve("wt-real").toAbsolutePath().toString()));
        assertFalse(Files.exists(base.resolve("wt-real")));
        // real git no longer lists it
        pb = new ProcessBuilder("git", "worktree", "list");
        pb.directory(dir.toFile());
        String nativeList = new String(pb.start().getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertFalse(nativeList.contains("wt-real"));
    }

    @Test
    void worktreeBidirectionalNopCreatesRealGitConsumes() throws Exception {
        Path dir = repo("wt-nr");
        assertEquals(0, run(dir, "worktree", "add", "-b", "topic",
                base.resolve("wt-nop").toAbsolutePath().toString()));
        // files materialized
        assertTrue(Files.exists(base.resolve("wt-nop").resolve("a.txt")));
        // real git recognizes it
        ProcessBuilder pb = new ProcessBuilder("git", "worktree", "list", "--porcelain");
        pb.directory(dir.toFile());
        String porcelain = new String(pb.start().getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertTrue(porcelain.contains("branch refs/heads/topic"));
        // real git commits inside the nop-git worktree; main repo sees it
        Files.writeString(base.resolve("wt-nop").resolve("b.txt"), "x\n");
        pb = new ProcessBuilder("git", "add", ".");
        pb.directory(base.resolve("wt-nop").toFile());
        assertEquals(0, pb.start().waitFor());
        pb = new ProcessBuilder("git", "-c", "user.name=t", "-c", "user.email=t@t",
                "commit", "-m", "in worktree");
        pb.directory(base.resolve("wt-nop").toFile());
        assertEquals(0, pb.start().waitFor());
        pb = new ProcessBuilder("git", "rev-parse", "topic");
        pb.directory(dir.toFile());
        String topicSha = new String(pb.start().getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).trim();
        pb = new ProcessBuilder("git", "rev-parse", "HEAD");
        pb.directory(base.resolve("wt-nop").toFile());
        String wtSha = new String(pb.start().getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).trim();
        assertEquals(topicSha, wtSha);

        // metadata byte format: commondir exactly "../.."
        String commondir = Files.readString(dir.resolve(".git/worktrees/wt-nop/commondir"),
                StandardCharsets.UTF_8);
        assertEquals("../..\n", commondir);
        String gitdir = Files.readString(dir.resolve(".git/worktrees/wt-nop/gitdir"),
                StandardCharsets.UTF_8);
        assertTrue(gitdir.equals(base.resolve("wt-nop").toRealPath() + "/.git\n"));

        // nop-git removes it
        assertEquals(0, run(dir, "worktree", "remove", "--force",
                base.resolve("wt-nop").toAbsolutePath().toString()),
                () -> "err: " + err());
    }

    @Test
    void worktreeRemoveRejectsMainAndLocked() throws Exception {
        Path dir = repo("wt-lock");
        assertEquals(0, run(dir, "worktree", "add", "-b", "w",
                base.resolve("wt-l").toAbsolutePath().toString()));
        // locked file blocks removal without --force
        Path topic = dir.resolve(".git/worktrees/wt-l");
        if (!Files.isDirectory(topic))
            topic = dir.resolve(".git/worktrees/wt-l-1");
        Files.writeString(topic.resolve("locked"), "");
        assertNotEquals(0, run(dir, "worktree", "remove",
                base.resolve("wt-l").toAbsolutePath().toString()));
        assertTrue(err().contains("locked"));
        assertEquals(0, run(dir, "worktree", "remove", "--force",
                base.resolve("wt-l").toAbsolutePath().toString()));
        // main worktree is not removable
        assertNotEquals(0, run(dir, "worktree", "remove", dir.toAbsolutePath().toString()));
    }

    // ------------------------------------------------------------------
    // P3 local
    // ------------------------------------------------------------------

    @Test
    void remoteManagement() throws Exception {
        Path dir = repo("remote");
        assertEquals(0, run(dir, "remote", "add", "origin",
                base.resolve("remote-target").toAbsolutePath().toString()));
        assertEquals(0, run(dir, "remote", "-v"));
        assertTrue(out().contains("origin"));
        assertTrue(out().contains("(fetch)"));
        assertEquals(0, run(dir, "remote", "set-url", "origin", "https://example.com/x.git"));
        assertEquals(0, run(dir, "remote", "-v"));
        assertTrue(out().contains("https://example.com/x.git"));
        assertEquals(0, run(dir, "remote", "rm", "origin"));
        assertEquals(0, run(dir, "remote"));
        assertFalse(out().contains("origin"));
    }

    // ------------------------------------------------------------------
    // P3 network (local file remotes)
    // ------------------------------------------------------------------

    @Test
    void cloneCommitPushFetchPullRoundtrip() throws Exception {
        Path originDir = repo("origin");
        Path cloneDir = base.resolve("clone");
        assertEquals(0, run(base, "clone", originDir.toAbsolutePath().toString(),
                cloneDir.toAbsolutePath().toString()));
        assertTrue(Files.exists(cloneDir.resolve("a.txt")));

        // commit in the clone and push to origin
        Files.writeString(cloneDir.resolve("c.txt"), "c\n");
        assertEquals(0, run(cloneDir, "add", "."));
        assertEquals(0, run(cloneDir, "commit", "-m", "clone work"));
        // origin has no branch tracking: push to master
        assertEquals(0, run(cloneDir, "push", "origin", "master"));

        // another clone fetches/pulls the change
        Path clone2 = base.resolve("clone2");
        assertEquals(0, run(base, "clone", originDir.toAbsolutePath().toString(),
                clone2.toAbsolutePath().toString()));
        assertEquals(0, run(clone2, "pull", "origin", "master"));
        assertTrue(Files.exists(clone2.resolve("c.txt")));
    }

    // ------------------------------------------------------------------
    // P3 history
    // ------------------------------------------------------------------

    @Test
    void mergeFastForwardAndCherryPick() throws Exception {
        Path dir = repo("merge");
        assertEquals(0, run(dir, "checkout", "-b", "feat"));
        Files.writeString(dir.resolve("feat.txt"), "feat\n");
        assertEquals(0, run(dir, "add", "."));
        assertEquals(0, run(dir, "commit", "-m", "feat work"));
        assertEquals(0, run(dir, "checkout", "master"));
        assertEquals(0, run(dir, "merge", "feat"));
        assertTrue(out().contains("Fast-forward"));
        assertTrue(Files.exists(dir.resolve("feat.txt")));

        // cherry-pick: create an orphan change on feat, pick it to master
        assertEquals(0, run(dir, "checkout", "feat"));
        Files.writeString(dir.resolve("picked.txt"), "picked\n");
        assertEquals(0, run(dir, "add", "."));
        assertEquals(0, run(dir, "commit", "-m", "to pick"));
        assertEquals(0, run(dir, "checkout", "master"));
        assertEquals(0, run(dir, "cherry-pick", "feat"));
        assertTrue(out().contains("] to pick") || out().contains("to pick"));
    }

    @Test
    void rebaseSimpleForm() throws Exception {
        Path dir = repo("rebase");
        assertEquals(0, run(dir, "checkout", "-b", "topic"));
        Files.writeString(dir.resolve("t.txt"), "t\n");
        assertEquals(0, run(dir, "add", "."));
        assertEquals(0, run(dir, "commit", "-m", "topic work"));
        assertEquals(0, run(dir, "checkout", "master"));
        Files.writeString(dir.resolve("m.txt"), "m\n");
        assertEquals(0, run(dir, "add", "."));
        assertEquals(0, run(dir, "commit", "-m", "master work"));
        assertEquals(0, run(dir, "checkout", "topic"));
        assertEquals(0, run(dir, "rebase", "master"));
        assertTrue(err().contains("Successfully rebased"));
        assertTrue(Files.exists(dir.resolve("m.txt")));
        assertTrue(Files.exists(dir.resolve("t.txt")));
    }

    @Test
    void checkoutDashDashRestoresFile() throws Exception {
        Path dir = repo("ckdd");
        Files.writeString(dir.resolve("a.txt"), "one\ntwo\nthree\nchanged\n");
        assertEquals(0, run(dir, "checkout", "--", "a.txt"));
        assertEquals("one\ntwo\nthree\n", Files.readString(dir.resolve("a.txt")));
    }

    @Test
    void initBareLayoutAndCloneRoundtrip() throws Exception {
        Path originDir = base.resolve("bare-origin.git");
        assertEquals(0, run(base, "init", "--bare", "bare-origin.git"));
        // bare layout: HEAD/refs live at the top level, no nested .git dir
        assertTrue(Files.exists(originDir.resolve("HEAD")));
        assertTrue(Files.isDirectory(originDir.resolve("refs")));
        assertFalse(Files.exists(originDir.resolve(".git")));

        // seed from a work clone: clone bare (empty ok on first push after branch)
        Path work = base.resolve("bare-work");
        assertEquals(0, run(base, "init", "bare-work"));
        Files.writeString(work.resolve("a.txt"), "x\n");
        assertEquals(0, run(work, "add", "."));
        assertEquals(0, run(work, "commit", "-m", "base"));
        assertEquals(0, run(work, "remote", "add", "origin",
                originDir.toAbsolutePath().toString()));
        assertEquals(0, run(work, "push", "origin", "master"));

        Path clone = base.resolve("bare-clone");
        
        assertEquals(0, run(base, "clone", originDir.toAbsolutePath().toString(),
                clone.toAbsolutePath().toString()));
        assertTrue(Files.exists(clone.resolve("a.txt")));
    }

    @Test
    void stashListLineHasNoDuplicatedPrefix() throws Exception {
        Path dir = repo("stash2");
        Files.writeString(dir.resolve("a.txt"), "one\ntwo\nthree\nmod\n");
        assertEquals(0, run(dir, "stash", "push"));
        assertEquals(0, run(dir, "stash", "list"));
        // native line: stash@{0}: WIP on master: <sha> <subject> — the WIP
        // prefix must appear exactly once
        long count = out().lines().filter(l -> l.contains("stash@{0}: WIP on master:"))
                .count();
        assertEquals(1, count);
        assertTrue(out().lines().noneMatch(l ->
                l.contains("WIP on master:") && l.indexOf("WIP on master:") !=
                        l.lastIndexOf("WIP on master:")));
    }

    @Test
    void initHonorsExplicitBranch() throws Exception {
        Path dir = base.resolve("init-b");
        assertEquals(0, run(base, "init", "-b", "main", "init-b"));
        // verify branch name through a commit
        Files.writeString(dir.resolve("x.txt"), "x\n");
        assertEquals(0, run(dir, "add", "."));
        assertEquals(0, run(dir, "commit", "-m", "first"));
        assertEquals(0, run(dir, "branch", "--show-current"));
        assertEquals("main\n", out());
    }

    // ------------------------------------------------------------------
    // policy E2E (entry-level interception for Wave 4 operations)
    // ------------------------------------------------------------------

    @Test
    void policyBlocksResetHardViaCli() throws Exception {
        Path dir = repo("pol-reset");
        Files.createDirectories(dir.resolve(".nop-git"));
        Files.writeString(dir.resolve(".nop-git/policy.yaml"), """
                mode: enforce
                default_effect: allow
                option_rules:
                  - operation: reset
                    deny_flags: ["--hard"]
                audit_log: ".nop-git/audit.log"
                """);
        assertEquals(1, run(dir, "reset", "--hard", "HEAD"));
        assertTrue(err().contains("fatal: policy denied:"));
    }

    @Test
    void worktreeRemoveDefaultsToReview() throws Exception {
        Path dir = repo("pol-wt");
        Files.createDirectories(dir.resolve(".nop-git"));
        Files.writeString(dir.resolve(".nop-git/policy.yaml"), """
                mode: enforce
                default_effect: allow
                audit_log: ".nop-git/audit.log"
                review_timeout_seconds: 0
                """);
        assertEquals(0, run(dir, "worktree", "add", "-b", "w",
                base.resolve("wt-pol").toAbsolutePath().toString()));
        // worktree-remove is in the builtin review set; timeout 0 → denied
        assertEquals(1, run(dir, "worktree", "remove",
                base.resolve("wt-pol").toAbsolutePath().toString()));
        assertTrue(err().contains("fatal: policy denied:"));
    }

    @Test
    void protectedBranchDirectPushDenied() throws Exception {
        Path originDir = base.resolve("pb-origin");
        Files.createDirectories(originDir);
        IGitRepositorySmokeHelper.initRepo(originDir);
        Path dir = repo("pb");
        assertEquals(0, run(dir, "remote", "add", "origin",
                originDir.toAbsolutePath().toString()));
        Files.createDirectories(dir.resolve(".nop-git"));
        // policy lives in the pushing repository
        Files.writeString(dir.resolve(".nop-git/policy.yaml"), """
                mode: enforce
                default_effect: allow
                protected_branches:
                  patterns: ["master"]
                  rules:
                    allow_direct_push: false
                audit_log: ".nop-git/audit.log"
                """);
        assertEquals(1, run(dir, "push", "origin", "master"));
        assertTrue(err().contains("fatal: policy denied:"));
    }
}

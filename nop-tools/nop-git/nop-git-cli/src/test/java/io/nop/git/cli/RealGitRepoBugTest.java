package io.nop.git.cli;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.nop.git.cli.GitCompatCli.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Regression tests for the 2026-11-21 live audit: every case builds its
 * repository with the REAL system git (not JGit fixtures), because the defects
 * only manifest on repositories whose commits/messages/branches were written
 * by native git — the JGit-based golden fixtures silently diverge (e.g. JGit
 * stores commit messages without the trailing newline native git writes, which
 * hid the extra blank line in {@code git log}).
 */
class RealGitRepoBugTest {

    @TempDir
    Path base;

    private static boolean hasGit() {
        try {
            Process p = new ProcessBuilder("git", "--version").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Path realRepo(String name, String... setupScript) throws Exception {
        assumeTrue(hasGit(), "system git required");
        Path dir = base.resolve(name);
        Files.createDirectories(dir);
        StringBuilder script = new StringBuilder("git init -q -b main\n");
        for (String line : setupScript) {
            script.append(line).append('\n');
        }
        Path scriptFile = Files.createTempFile("nopgit-setup", ".sh");
        Files.writeString(scriptFile, script);
        Process p = new ProcessBuilder("bash", scriptFile.toString())
                .directory(dir.toFile())
                .redirectErrorStream(false)
                .start();
        int exit = p.waitFor();
        if (exit != 0) {
            String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException("setup failed: " + err);
        }
        return dir;
    }

    private record RunResult(int exit, String stdout, String stderr) {
    }

    private RunResult cli(Path repo, String... args) {
        List<String> full = new java.util.ArrayList<>(List.of("-C", repo.toString()));
        full.addAll(List.of(args));
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        int exit = run(full.toArray(new String[0]),
                new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                new PrintStream(errBuf, true, StandardCharsets.UTF_8));
        return new RunResult(exit, outBuf.toString(), errBuf.toString());
    }

    private static final String IDENT =
            "git config user.name Fixture User && git config user.email fixture@example.com";

    // bug #1: log over a native-git repo must not emit an extra blank line
    // (native commits store a trailing newline in the message)
    @Test
    void logNoTrailingBlankLineOnNativeRepo() throws Exception {
        Path repo = realRepo("log", IDENT,
                "echo a > a.txt && git add -A && git commit -qm first",
                "echo b > b.txt && git add -A && git commit -qm second");
        RunResult r = cli(repo, "log");
        assertEquals(0, r.exit());
        assertFalse(r.stdout().endsWith("    second\n\n"), "trailing blank line after last commit");
        assertTrue(r.stdout().endsWith("    first\n"));  // oldest commit renders last
        assertFalse(r.stdout().contains("\n\n\n"), "double blank line between commits");
    }

    // bug #2: merge commits must render the native "Merge: <a> <b>" line
    @Test
    void logRendersMergeParentsLine() throws Exception {
        Path repo = realRepo("merge", IDENT,
                "echo a > a.txt && git add -A && git commit -qm base",
                "git checkout -q -b feature && echo f > f.txt && git add -A && git commit -qm feat",
                "git checkout -q main && echo m > m.txt && git add -A && git commit -qm mainwork",
                "git merge -q --no-ff -m 'merge feature' feature");
        RunResult r = cli(repo, "log");
        assertEquals(0, r.exit());
        assertTrue(r.stdout().contains("Merge: "), "missing Merge: line in merge commit log");
        // exactly two 7-char abbreviations
        assertTrue(r.stdout().matches("(?s).*Merge: [0-9a-f]{7} [0-9a-f]{7}\\n.*"));
    }

    // bug #3: add with a pathspec that matches nothing fails natively
    @Test
    void addNonexistentPathspecFails() throws Exception {
        Path repo = realRepo("addmiss", IDENT,
                "echo a > a.txt && git add -A && git commit -qm base");
        RunResult r = cli(repo, "add", "nonexistent.txt");
        assertEquals(128, r.exit());
        assertEquals("fatal: pathspec 'nonexistent.txt' did not match any files",
                r.stderr().trim());
        RunResult ok = cli(repo, "add", "a.txt");
        assertEquals(0, ok.exit());
    }

    // bug #13: status pathspec args must be accepted and filter the report
    @Test
    void statusAcceptsPathspec() throws Exception {
        Path repo = realRepo("statuspath", IDENT,
                "echo a > a.txt && mkdir -p sub && echo s > sub/s.txt && git add -A && git commit -qm base",
                "echo dirty >> a.txt && echo dirty >> sub/s.txt && echo new > untracked.txt");
        RunResult r = cli(repo, "status", ".");
        assertEquals(0, r.exit(), r.stderr());
        assertTrue(r.stdout().contains("On branch main"));
        RunResult sub = cli(repo, "status", "sub");
        assertEquals(0, sub.exit());
        assertTrue(sub.stdout().contains("sub/s.txt"), sub.stdout());
        assertFalse(sub.stdout().contains("a.txt"), "pathspec did not filter: " + sub.stdout());
    }

    // bug #14: porcelain v1 quotes paths containing spaces
    @Test
    void porcelainQuotesPathsNeedingQuotes() throws Exception {
        Path repo = realRepo("quote", IDENT,
                "echo a > a.txt && git add -A && git commit -qm base",
                "echo x > 'keep me.txt' && git add -A");
        RunResult r = cli(repo, "status", "--porcelain");
        assertEquals(0, r.exit());
        assertTrue(r.stdout().contains("A  \"keep me.txt\""), r.stdout());
    }

    // bug #7: unknown ref checkout/switch use distinct native messages+codes
    @Test
    void checkoutUnknownRefNativeErrors() throws Exception {
        Path repo = realRepo("checkoutmiss", IDENT,
                "echo a > a.txt && git add -A && git commit -qm base");
        RunResult co = cli(repo, "checkout", "nonexistent");
        assertEquals(1, co.exit(), co.stderr());
        assertEquals("error: pathspec 'nonexistent' did not match any file(s) known to git",
                co.stderr().trim());
        RunResult sw = cli(repo, "switch", "nonexistent");
        assertEquals(128, sw.exit(), sw.stderr());
        assertEquals("fatal: invalid reference: nonexistent", sw.stderr().trim());
    }

    // bug #5: stash pop/apply on an empty stash exit 1 without fatal prefix
    @Test
    void stashPopEmptyNativeMessage() throws Exception {
        Path repo = realRepo("stashempty", IDENT,
                "echo a > a.txt && git add -A && git commit -qm base");
        RunResult r = cli(repo, "stash", "pop");
        assertEquals(1, r.exit());
        assertEquals("No stash entries found.", r.stderr().trim());
        assertFalse(r.stderr().contains("fatal:"));
    }

    // bug #6: merge with an unresolvable ref prints the bare native message
    @Test
    void mergeUnknownRefBareMessage() throws Exception {
        Path repo = realRepo("mergemiss", IDENT,
                "echo a > a.txt && git add -A && git commit -qm base");
        RunResult r = cli(repo, "merge", "nope");
        assertEquals(1, r.exit());
        assertEquals("merge: nope - not something we can merge", r.stderr().trim());
        assertFalse(r.stderr().contains("fatal:"));
    }

    // bug #4: reset --hard prints the native HEAD line
    @Test
    void resetHardPrintsHeadLine() throws Exception {
        Path repo = realRepo("resethard", IDENT,
                "echo a > a.txt && git add -A && git commit -qm base",
                "echo dirty >> a.txt");
        RunResult r = cli(repo, "reset", "--hard");
        assertEquals(0, r.exit(), r.stderr());
        assertTrue(r.stdout().startsWith("HEAD is now at "), r.stdout());
        assertTrue(r.stdout().contains("base"), r.stdout());
    }

    // bug #8: commit with nothing staged renders status and exits 1 even
    // without -m
    @Test
    void commitNothingToCommitNativeOutput() throws Exception {
        Path repo = realRepo("commitclean", IDENT,
                "echo a > a.txt && git add -A && git commit -qm base");
        RunResult r = cli(repo, "commit");
        assertEquals(1, r.exit());
        assertTrue(r.stdout().contains("nothing to commit, working tree clean"), r.stdout());
        assertTrue(r.stdout().contains("On branch main"), r.stdout());
    }

    // bugs #9/#10: up-to-date push/pull report native messages
    @Test
    void pushPullUpToDateMessages() throws Exception {
        assumeTrue(hasGit(), "system git required");
        Path remote = base.resolve("remote.git");
        Files.createDirectories(remote);
        GitCommandRunner.run(remote, java.util.Map.of(),
                "init", "-q", "--bare", "-b", "main");
        // clone, commit, push with the real git so origin/tracking exists
        GitCommandRunner.run(base, java.util.Map.of(),
                "clone", "-q", remote.toAbsolutePath().toString(), "work");
        Path work = base.resolve("work");
        GitCommandRunner.run(work, java.util.Map.of(), "config", "user.name", Fixture.NAME);
        GitCommandRunner.run(work, java.util.Map.of(), "config", "user.email", Fixture.EMAIL);
        Files.writeString(work.resolve("a.txt"), "a");
        GitCommandRunner.run(work, java.util.Map.of(), "add", "-A");
        GitCommandRunner.run(work, java.util.Map.of(), "commit", "-qm", "base");
        GitCommandRunner.run(work, java.util.Map.of(), "push", "-u", "origin", "main");
        RunResult push = cli(work, "push");
        assertEquals(0, push.exit());
        assertEquals("Everything up-to-date", push.stderr().trim());
        RunResult pull = cli(work, "pull");
        assertEquals(0, pull.exit(), pull.stderr());
        assertTrue(pull.stdout().contains("Already up to date."), pull.stdout());
    }

    // bug #11: init on an existing repository reports reinit and keeps state
    @Test
    void initExistingRepoReportsReinit() throws Exception {
        Path repo = realRepo("reinit", IDENT,
                "echo a > a.txt && git add -A && git commit -qm base");
        RunResult r = cli(repo, "init", "-b", "other");
        assertEquals(0, r.exit());
        assertTrue(r.stdout().contains("Reinitialized existing Git repository in"), r.stdout());
        assertEquals("warning: re-init: ignored --initial-branch=other", r.stderr().trim());
        // branch unchanged
        RunResult branch = cli(repo, "rev-parse", "--abbrev-ref", "HEAD");
        assertEquals("main", branch.stdout().trim());
        assertFalse(r.stdout().contains("/./"), "path not normalized: " + r.stdout());
    }
}

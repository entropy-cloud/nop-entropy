package io.nop.git.cli;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI-tool compatibility smoke checklist (roadmap item 11): the concrete
 * output contracts consumed by Claude Code (read-only detection), Aider
 * (add+commit parsing) and Codex CLI (doctor version parsing).
 */
class AiToolSmokeTest {

    @TempDir
    Path base;

    private ByteArrayOutputStream out;
    private ByteArrayOutputStream err;

    private int run(Path repo, String... args) {
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
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

    @Test
    void claudeCodeReadOnlyProbes() throws Exception {
        Path repo = Fixture.cleanSingleCommit(base, "cc");
        assertEquals(0, run(repo, "status"));
        assertEquals(0, run(repo, "diff"));
        assertEquals(0, run(repo, "log"));
        assertFalse((out() + err()).contains("\u001b["), "no ANSI escapes");
    }

    @Test
    void aiderAddCommitParsing() throws Exception {
        Path repo = Fixture.cleanSingleCommit(base, "aider");
        Files.writeString(repo.resolve("a.txt"), "one\ntwo\nthree\nedited\n");
        assertEquals(0, run(repo, "add", "."));
        assertEquals(0, run(repo, "commit", "-m", "aider edit"));
        // Aider parses "[branch sha] message"
        assertTrue(Pattern.compile("\\[master [0-9a-f]{7,}] aider edit")
                        .matcher(out()).find(),
                "commit line not parseable: " + out());
    }

    @Test
    void codexDoctorVersionParsing() {
        assertEquals(0, run(base, "--version"));
        assertTrue(Pattern.compile("git version \\d+\\.\\d+\\.\\d+")
                        .matcher(out()).find(),
                "version output not parseable: " + out());
    }

    @Test
    void unimplementedSubcommandErrorsLoudly() {
        assertEquals(1, run(base, "push"));
        assertTrue(err().contains("git: 'push' is not a nop-git command."));
        assertEquals(1, run(base, "rebase"));
        assertTrue(err().contains("git: 'rebase' is not a nop-git command."));
    }

    @Test
    void configRoundtripAndMissingKeyExitCode() throws Exception {
        Path repo = Fixture.cleanSingleCommit(base, "cfg");
        assertEquals(0, run(repo, "config", "user.name", "tester"));
        assertEquals(0, run(repo, "config", "--get", "user.name"));
        assertEquals("tester\n", out());
        // missing key: native exits 1 with no output
        assertEquals(1, run(repo, "config", "--get", "user.missing"));
        assertEquals("", out());
    }

    @Test
    void outsideRepositoryIsFatal128() {
        int exit = run(base, "status");
        assertEquals(128, exit);
        assertTrue(err().startsWith("fatal: not a git repository"));
    }

    @Test
    void nopGitInternalNamespaceIsConsumed() throws Exception {
        Path repo = Fixture.cleanSingleCommit(base, "internal");
        List<String> args = List.of("--nop-git-internal:audit=on", "status");
        assertEquals(0, run(repo, args.toArray(new String[0])));
    }
}

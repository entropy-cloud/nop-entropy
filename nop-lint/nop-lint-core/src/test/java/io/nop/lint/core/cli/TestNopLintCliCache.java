package io.nop.lint.core.cli;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.testing.JavaBindingTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code --cache} result-replay face (roadmap item 43, design 11 §4):
 * a cold run populates the artifact, a warm run replays byte-identical
 * diagnostics with cache hits counted, and the invalidation matrix (file
 * change / rule-set change / --rules change / format version) plus the
 * fail-closed faces (corrupt artifact, mutual exclusions) all hold.
 */
public class TestNopLintCliCache {

    private static final String VALID_PREFIX = "/test/lint/cli-rules";

    @TempDir
    Path dir;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private record Run(int exitCode, String stdout, String stderr) {
    }

    private Run run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = NopLintCli.runFull(args, JavaBindingTestSupport.registryWithJava(),
                VALID_PREFIX,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Run(code, out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    private static String diagnosticLines(String stdout) {
        StringBuilder sb = new StringBuilder();
        for (String line : stdout.split("\n")) {
            if (line.matches(".*\\.java:[0-9]+: (error|warning|info|hint): .*")) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private static final String VIOLATING = "class Warn {\n    void x() {\n"
            + "        System.out.println(\"w\");\n    }\n}\n";

    @Test
    public void warmRunReplaysByteIdenticalDiagnosticsWithHits() throws Exception {
        Files.createDirectories(dir.resolve("src"));
        Files.writeString(dir.resolve("src/warn.java"), VIOLATING);
        Path cache = dir.resolve("cache.json");

        Run cold = run("check", "--cache", cache.toString(), dir.resolve("src").toString());
        assertTrue(Files.exists(cache), "the cold run writes the artifact");
        String coldDiagnostics = diagnosticLines(cold.stdout());

        Run warm = run("check", "--cache", cache.toString(), dir.resolve("src").toString());
        String warmDiagnostics = diagnosticLines(warm.stdout());
        assertEquals(coldDiagnostics, warmDiagnostics,
                "the replayed DIAGNOSTIC STREAM must be byte-identical (the summary block's"
                + " engine stats legitimately zero out on a warm run)");
        assertTrue(warm.stdout().contains("cache: 1 hit(s)"), warm.stdout());
    }

    @Test
    public void fileChangeInvalidatesTheEntry() throws Exception {
        Path cache = dir.resolve("cache.json");
        Files.createDirectories(dir.resolve("src"));
        Path file = dir.resolve("src/warn.java");
        Files.writeString(file, VIOLATING);
        run("check", "--cache", cache.toString(), dir.resolve("src").toString());

        Files.writeString(file, VIOLATING + VIOLATING);
        Run run = run("check", "--cache", cache.toString(), dir.resolve("src").toString());
        assertEquals(2, run.stdout().split("warning: demo/no-print").length - 1,
                "the changed file is re-linted, not replayed: " + run.stdout());
        assertTrue(!run.stdout().contains("cache:"),
                "a re-linted (miss) file renders no cache line — the cache row"
                + " appears only on hits (R1 Minor 4): " + run.stdout());
    }

    @Test
    public void rulesFilterChangeInvalidatesTheArtifact() throws Exception {
        Path cache = dir.resolve("cache.json");
        Files.createDirectories(dir.resolve("src"));
        Files.writeString(dir.resolve("src/warn.java"), VIOLATING);

        Run first = run("check", "--cache", cache.toString(), dir.resolve("src").toString());
        assertTrue(first.stdout().contains("demo/no-print"), first.stdout());

        // same artifact, narrowed rule set: the entry must NOT be replayed
        // (a widened/no-filter replay would serve diagnostics the selection
        // no longer produces — the R1 Major 1 face)
        Run narrowed = run("check", "--cache", cache.toString(), "--rules",
                "demo/no-bare-throw", dir.resolve("src").toString());
        assertTrue(!narrowed.stdout().contains("cache:"),
                "the narrowed run must miss (a 0-hit run renders no cache line): "
                        + narrowed.stdout());
        assertTrue(!narrowed.stdout().contains("demo/no-print"),
                "the narrowed selection produces no no-print diagnostics: " + narrowed.stdout());
    }

    @Test
    public void corruptCacheFileFailsClosed() throws Exception {
        Path cache = dir.resolve("cache.json");
        Files.createDirectories(dir.resolve("src"));
        Files.writeString(cache, "{not json at all");
        Files.writeString(dir.resolve("src/warn.java"), VIOLATING);

        Run run = run("check", "--cache", cache.toString(), dir.resolve("src").toString());
        assertEquals(NopLintCli.EXIT_INTERNAL, run.exitCode());
        assertTrue(run.stderr().contains("corrupt"), run.stderr());
    }

    @Test
    public void cacheMutuallyExclusiveWithFixAndBaseline() throws Exception {
        Files.createDirectories(dir.resolve("src"));
        Files.writeString(dir.resolve("src/warn.java"), VIOLATING);
        Path cache = dir.resolve("cache.json");

        Run withFix = run("check", "--cache", cache.toString(), "--fix", dir.toString());
        assertEquals(NopLintCli.EXIT_INTERNAL, withFix.exitCode());
        assertTrue(withFix.stderr().contains("--cache cannot combine with --fix"), withFix.stderr());

        Run withBaseline = run("check", "--cache", cache.toString(), "--baseline",
                "b.json", dir.toString());
        assertEquals(NopLintCli.EXIT_INTERNAL, withBaseline.exitCode());
        assertTrue(withBaseline.stderr().contains("--baseline"), withBaseline.stderr());
    }

    @Test
    public void cacheMissReadsEachFileOnceAndReplaysFromTheSameBytes() throws Exception {
        Files.createDirectories(dir.resolve("src"));
        Files.writeString(dir.resolve("src/warn.java"), VIOLATING);
        Files.writeString(dir.resolve("src/clean.java"), "class Clean {\n}\n");
        Path cache = dir.resolve("cache.json");

        LanguageRegistry registry = JavaBindingTestSupport.registryWithJava();
        TargetScanner.ScanResult scan = TargetScanner.scan(
                List.of(dir.resolve("src").toString()), registry);
        AtomicInteger reads = new AtomicInteger();

        // the counting reader proves one read per cache-miss file: before the
        // single-read fix the flow read the file twice (the run loop and
        // again inside lintFile), which let a hash and the diagnostics it
        // keys describe two different snapshots. With one read, the hashed
        // bytes and the linted bytes are the same array by construction.
        CheckOutcome outcome = new CheckRunner(registry, new RuleSetLoader(), VALID_PREFIX)
                .run(scan, LintProfile.STANDARD, CliOptions.FixMode.NONE,
                        CliOptions.BaselineOp.NONE, null, cache.toString(),
                        path -> {
                            reads.incrementAndGet();
                            try {
                                return Files.readAllBytes(path);
                            } catch (java.io.IOException e) {
                                throw new java.io.UncheckedIOException(e);
                            }
                        });

        assertEquals(2, reads.get(),
                "two cache-miss files, exactly one read each (was 2x per file before"
                        + " the single-read fix)");
        assertEquals(1, outcome.summary().getTotalDiagnostics(),
                "the violating file was linted from the reader's bytes");
        assertTrue(Files.exists(cache), "the miss flow still populates the artifact");

        // the entries built from the reader's bytes replay on a normal run
        Run warm = run("check", "--cache", cache.toString(), dir.resolve("src").toString());
        assertTrue(warm.stdout().contains("cache: 2 hit(s)"),
                "both entries (hashed from the same bytes that were linted) replay: "
                        + warm.stdout());
    }
}

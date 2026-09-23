package io.nop.lint.core.fix;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.engine.LintStats;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.SourceRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The FixApplier multipass matrix (roadmap item 25 Phase 2): single-apply,
 * convergence across passes, the non-convergence guard, the pass cap,
 * syntax-break rollback with content restored, dry-run writing nothing, and
 * the fail-closed write path.
 */
public class TestFixApplier {

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"),
            null);

    @TempDir
    Path dir;

    // ==================== stub lint plumbing ====================

    /**
     * A stub lint entry: the function maps the current content to its
     * diagnostics, the stats are irrelevant to the applier.
     */
    private static FixApplier applier(Function<String, List<Diagnostic>> lint) {
        return applier(lint, FixApplier.DEFAULT_MAX_PASSES);
    }

    private static FixApplier applier(Function<String, List<Diagnostic>> lint, int maxPasses) {
        return new FixApplier(source -> new LintResult(lint.apply(
                new String(source, StandardCharsets.UTF_8)), LintStats.builder().build()),
                JAVA, maxPasses);
    }

    private static Diagnostic fixDiagnostic(String ruleId, String source, String target,
                                            String replacement, int order) {
        int start = source.indexOf(target);
        return new Diagnostic(ruleId, "warning", "fix " + ruleId,
                new SourceRange(start, start + target.length()),
                new Fix(new SourceRange(start, start + target.length()), replacement,
                        ruleId, "desc", order));
    }

    private Path write(String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file);
    }

    // ==================== scenarios ====================

    @Test
    public void singleFixIsAppliedAtomicallyToDisk() throws IOException {
        String source = "class T { void m() { foo(); } }";
        Path file = write("A.java", source);
        FixApplier applier = applier(s -> s.contains("foo")
                ? List.of(fixDiagnostic("r/a", s, "foo", "bar", 0))
                : List.of());

        FixApplier.FixResult result = applier.run(file, source.getBytes(StandardCharsets.UTF_8), false);

        assertEquals("class T { void m() { bar(); } }", read(file));
        assertEquals(1, result.stats().applied());
        assertEquals(1, result.stats().passes());
        assertEquals(0, result.stats().nonConvergent());
        assertEquals(0, result.stats().rollbacks());
        assertEquals("class T { void m() { bar(); } }",
                new String(result.finalSource(), StandardCharsets.UTF_8));
    }

    @Test
    public void dryRunComputesTheLoopButWritesNothing() throws IOException {
        String source = "class T { void m() { foo(); } }";
        Path file = write("B.java", source);
        FixApplier applier = applier(s -> s.contains("foo")
                ? List.of(fixDiagnostic("r/b", s, "foo", "bar", 0))
                : List.of());

        FixApplier.FixResult result = applier.run(file, source.getBytes(StandardCharsets.UTF_8), true);

        assertEquals(source, read(file), "the dry-run never touches the file");
        assertEquals("class T { void m() { bar(); } }",
                new String(result.finalSource(), StandardCharsets.UTF_8),
                "the in-memory result carries the proposal");
    }

    @Test
    public void multipassConvergesWhenCandidateCountDecreases() throws IOException {
        String source = "class T { void m() { p1(); p2(); } }";
        Path file = write("C.java", source);
        // pass 1 rewrites p1/p2 into t1/t2; pass 2 rewrites t1 (one fewer
        // candidate); pass 3 finds nothing — the count strictly decreases
        FixApplier applier = applier(s -> {
            List<Diagnostic> fixes = new java.util.ArrayList<>();
            if (s.contains("p1")) {
                fixes.add(fixDiagnostic("r/c1", s, "p1", "t1", 0));
            }
            if (s.contains("p2")) {
                fixes.add(fixDiagnostic("r/c2", s, "p2", "t2", 1));
            }
            if (!s.contains("p1") && s.contains("t1")) {
                fixes.add(fixDiagnostic("r/c3", s, "t1", "q", 2));
            }
            return fixes;
        });

        FixApplier.FixResult result = applier.run(file, source.getBytes(StandardCharsets.UTF_8), false);

        assertEquals("class T { void m() { q(); t2(); } }", read(file));
        assertEquals(2, result.stats().passes(), "pass 1 fixes p1/p2, pass 2 fixes t1, pass 3 converges");
        assertEquals(3, result.stats().applied());
        assertEquals(0, result.stats().nonConvergent());
    }

    @Test
    public void nonConvergentCandidateCountStopsAndKeepsLastWrite() throws IOException {
        String source = "class T { void m() { int a = 1; } }";
        Path file = write("D.java", source);
        // every pass offers exactly one fix over the current constant — the
        // count never decreases, so the guard must stop after pass 1
        FixApplier applier = applier(s -> {
            if (s.contains("a = 1")) {
                return List.of(fixDiagnostic("r/d", s, "a = 1", "a = 2", 0));
            }
            if (s.contains("a = 2")) {
                return List.of(fixDiagnostic("r/d", s, "a = 2", "a = 3", 0));
            }
            return List.of(fixDiagnostic("r/d", s, "a = 3", "a = 4", 0));
        });

        FixApplier.FixResult result = applier.run(file, source.getBytes(StandardCharsets.UTF_8), false);

        assertEquals("class T { void m() { int a = 2; } }", read(file),
                "the last successful pass's write stays on disk");
        assertEquals(1, result.stats().nonConvergent());
        assertEquals(1, result.stats().passes());
        assertEquals(1, result.stats().applied());
    }

    @Test
    public void passCapStopsWithNonConvergentAccounting() throws IOException {
        String source = "class T { void m() { foo(); } }";
        Path file = write("E.java", source);
        // each pass swaps the marker pair — always exactly one candidate, so
        // the single-pass cap must end the loop before the guard ever fires
        FixApplier bounded = applier(s -> {
            if (s.contains("foo")) {
                return List.of(fixDiagnostic("r/e", s, "foo", "foh", 0));
            }
            return List.of(fixDiagnostic("r/e", s, "foh", "foo", 0));
        }, 1);

        FixApplier.FixResult result = bounded.run(file,
                source.getBytes(StandardCharsets.UTF_8), false);

        assertEquals(1, result.stats().nonConvergent(), "the cap stop is the same honest accounting");
        assertEquals(1, result.stats().passes());
        assertEquals("class T { void m() { foh(); } }", read(file));
    }

    @Test
    public void syntaxBreakRollsBackToThePreviousContent() throws IOException {
        String source = "class T { void m() { foo(); } }";
        Path file = write("F.java", source);
        // the rewrite replaces a statement with a stray identifier — the
        // parse produces an ERROR node the original did not have
        FixApplier applier = applier(s -> List.of(fixDiagnostic("r/f", s, "foo();", "grobl", 0)));

        FixApplier.FixResult result = applier.run(file, source.getBytes(StandardCharsets.UTF_8), false);

        assertEquals(source, read(file), "the file is restored to the pre-pass content");
        assertEquals(source, new String(result.finalSource(), StandardCharsets.UTF_8));
        assertEquals(1, result.stats().rollbacks());
        assertEquals(0, result.stats().nonConvergent(),
                "a rollback is accounted as a rollback, not as non-convergence");
    }

    @Test
    public void alreadyBrokenSourceIsNotRollbackBait() {
        String source = "class T { void m() { foo( } }";
        FixApplier applier = applier(s -> {
            // a fix that keeps the same recovery-node count (still one broken
            // spot) must not be rolled back as a "syntax break"
            if (!s.contains("foo")) {
                return List.of();
            }
            int start = s.indexOf("foo");
            return List.of(new Diagnostic("r/g", "warning", "m",
                    new SourceRange(start, start + 3),
                    new Fix(new SourceRange(start, start + 3), "bar", "r/g", "d", 0)));
        });

        FixApplier.FixResult result = applier.run(dir.resolve("missing/G.java"),
                source.getBytes(StandardCharsets.UTF_8), true);

        assertTrue(new String(result.finalSource(), StandardCharsets.UTF_8).contains("bar"),
                "equal recovery-node count passes the guard");
        assertEquals(0, result.stats().rollbacks());
    }

    @Test
    public void noCandidatesConvergeImmediatelyWithoutWrites() throws IOException {
        String source = "class T { void m() { clean(); } }";
        Path file = write("H.java", source);
        FixApplier applier = applier(s -> List.of());

        FixApplier.FixResult result = applier.run(file, source.getBytes(StandardCharsets.UTF_8), false);

        assertEquals(source, read(file));
        assertEquals(0, result.stats().passes());
        assertEquals(0, result.stats().applied());
        assertArrayEquals(source.getBytes(StandardCharsets.UTF_8), result.finalSource());
    }

    @Test
    public void writeFailureFailsClosedWithThePreviousContentIntact() throws IOException {
        String source = "class T { void m() { foo(); } }";
        Path missingDir = dir.resolve("no-such-dir");
        Path file = missingDir.resolve("I.java");
        FixApplier applier = applier(s -> List.of(fixDiagnostic("r/i", s, "foo", "bar", 0)));

        NopLintException ex = assertThrows(NopLintException.class,
                () -> applier.run(file, source.getBytes(StandardCharsets.UTF_8), false));
        assertTrue(ex.getMessage().contains("I.java"), ex.getMessage());
        assertTrue(ex.getMessage().contains("keeps its previous content"), ex.getMessage());
    }
}

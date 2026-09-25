package io.nop.lint.core.fix;

import io.nop.lint.core.NopLintException;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The diagnostic-agnostic edit-plan entry matrix (nop-refactor WI4): single
 * edit, conflict merge accounting, dry-run, guard rollback with the
 * rolledBack/appliedEdits flags (a returned ending, not an exception), the
 * stale-range fail-closed path, the write-failure fail-closed path, and the
 * already-broken-source guard tolerance. The multipass behavior on top of
 * this core stays covered by {@link TestFixApplier} unchanged.
 */
public class TestEditPlanApplier {

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"),
            null);

    @TempDir
    Path dir;

    private static Fix edit(String ruleId, String source, String target, String replacement) {
        int start = source.indexOf(target);
        return new Fix(new SourceRange(start, start + target.length()), replacement,
                ruleId, "desc", 0);
    }

    private Path write(String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file);
    }

    @Test
    public void singleEditIsAppliedAtomicallyToDisk() throws IOException {
        String source = "class T { void m() { foo(); } }";
        Path file = write("A.java", source);

        EditPlanApplier.EditPlanResult result = EditPlanApplier.apply(file,
                source.getBytes(StandardCharsets.UTF_8),
                List.of(edit("op/a", source, "foo", "bar")), JAVA, false);

        assertEquals("class T { void m() { bar(); } }", read(file));
        assertFalse(result.rolledBack());
        assertEquals(1, result.appliedEdits());
        assertEquals(0, result.skippedConflicts());
        assertEquals("class T { void m() { bar(); } }",
                new String(result.finalSource(), StandardCharsets.UTF_8));
    }

    @Test
    public void dryRunComputesButWritesNothing() throws IOException {
        String source = "class T { void m() { foo(); } }";
        Path file = write("B.java", source);

        EditPlanApplier.EditPlanResult result = EditPlanApplier.apply(file,
                source.getBytes(StandardCharsets.UTF_8),
                List.of(edit("op/b", source, "foo", "bar")), JAVA, true);

        assertEquals(source, read(file), "the dry-run never touches the file");
        assertFalse(result.rolledBack());
        assertEquals(1, result.appliedEdits(), "the proposal still counts its edits");
    }

    @Test
    public void overlappingConflictKeepsFirstAndCountsTheRest() throws IOException {
        String source = "class T { void m() { foo(); } }";
        Path file = write("C.java", source);
        // both edits target the same range: the earlier-priority one wins,
        // the second is a counted conflict
        Fix first = edit("op/c1", source, "foo", "bar");
        Fix second = new Fix(first.range(), "baz", "op/c2", "desc", 1);

        EditPlanApplier.EditPlanResult result = EditPlanApplier.apply(file,
                source.getBytes(StandardCharsets.UTF_8), List.of(first, second), JAVA, false);

        assertEquals("class T { void m() { bar(); } }", read(file),
                "the earlier-priority edit survives");
        assertEquals(1, result.appliedEdits());
        assertEquals(1, result.skippedConflicts());
        assertFalse(result.rolledBack());
    }

    @Test
    public void guardRollbackIsAReturnedEndingWithZeroSurvivingEdits() throws IOException {
        String source = "class T { void m() { foo(); } }";
        Path file = write("D.java", source);
        // the rewrite replaces a statement with a stray identifier — the
        // parse produces an ERROR node the original did not have
        Fix breaking = edit("op/d", source, "foo();", "grobl");

        EditPlanApplier.EditPlanResult result = EditPlanApplier.apply(file,
                source.getBytes(StandardCharsets.UTF_8), List.of(breaking), JAVA, false);

        assertEquals(source, read(file), "the file is restored to the input content");
        assertArrayEquals(source.getBytes(StandardCharsets.UTF_8), result.finalSource());
        assertTrue(result.rolledBack(), "the guard outcome is a flag, not an exception");
        assertEquals(0, result.appliedEdits(), "a rolled-back edit does not count as applied");
    }

    @Test
    public void dryRunRollbackAlsoReportsTheFlagWithoutWrites() throws IOException {
        String source = "class T { void m() { foo(); } }";
        Path file = write("E.java", source);
        Fix breaking = edit("op/e", source, "foo();", "grobl");

        EditPlanApplier.EditPlanResult result = EditPlanApplier.apply(file,
                source.getBytes(StandardCharsets.UTF_8), List.of(breaking), JAVA, true);

        assertEquals(source, read(file));
        assertTrue(result.rolledBack());
        assertEquals(0, result.appliedEdits());
    }

    @Test
    public void alreadyBrokenSourceIsNotRollbackBait() throws IOException {
        String source = "class T { void m() { foo( } }";
        Path file = write("F.java", source);
        // the edit keeps the same recovery-node count (still one broken
        // spot), so the guard must not treat it as a syntax break
        Fix sameBreakage = edit("op/f", source, "foo", "bar");

        EditPlanApplier.EditPlanResult result = EditPlanApplier.apply(file,
                source.getBytes(StandardCharsets.UTF_8), List.of(sameBreakage), JAVA, false);

        assertFalse(result.rolledBack(), "equal recovery-node count passes the guard");
        assertEquals(1, result.appliedEdits());
        assertTrue(read(file).contains("bar"));
    }

    @Test
    public void outOfRangeEditFailsClosed() throws IOException {
        String source = "class T { void m(); }";
        Path file = write("G.java", source);
        Fix stale = new Fix(new SourceRange(source.length() - 2, source.length() + 50), "x",
                "op/g", "desc", 0);

        NopLintException ex = assertThrows(NopLintException.class,
                () -> EditPlanApplier.apply(file, source.getBytes(StandardCharsets.UTF_8),
                        List.of(stale), JAVA, false));
        assertTrue(ex.getMessage().contains("op/g"), ex.getMessage());
        assertTrue(ex.getMessage().contains("stale range"), ex.getMessage());
    }

    @Test
    public void writeFailureFailsClosedWithTheInputIntact() throws IOException {
        String source = "class T { void m() { foo(); } }";
        Path file = dir.resolve("no-such-dir").resolve("H.java");
        Fix fine = edit("op/h", source, "foo", "bar");

        NopLintException ex = assertThrows(NopLintException.class,
                () -> EditPlanApplier.apply(file, source.getBytes(StandardCharsets.UTF_8),
                        List.of(fine), JAVA, false));
        assertTrue(ex.getMessage().contains("H.java"), ex.getMessage());
        assertTrue(ex.getMessage().contains("keeps its previous content"), ex.getMessage());
    }

    @Test
    public void emptyEditListIsIdentityWithoutWrites() throws IOException {
        String source = "class T { void m() { clean(); } }";
        Path file = write("I.java", source);

        EditPlanApplier.EditPlanResult result = EditPlanApplier.apply(file,
                source.getBytes(StandardCharsets.UTF_8), List.of(), JAVA, false);

        assertArrayEquals(source.getBytes(StandardCharsets.UTF_8), result.finalSource());
        assertFalse(result.rolledBack());
        assertEquals(0, result.appliedEdits());
        assertEquals(0, result.skippedConflicts());
        assertEquals(source, read(file));
    }
}

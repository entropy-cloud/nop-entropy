package io.nop.lint.core.suppress;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.node.SourceRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The baseline engine matrix (roadmap item 27 Phase 2, plan
 * 2026-09-24-0050-1): fingerprint content addressing (line drift immune,
 * text change sensitive, multi-byte content correct), the write merge, the
 * multi-set consumption with stale detection, and the fail-closed file
 * loading.
 */
public class TestBaselineEngine {

    @TempDir
    Path dir;

    private static Diagnostic diagnostic(String ruleId, byte[] source, int start, int end) {
        return new Diagnostic(ruleId, "warning", "m", new SourceRange(start, end));
    }

    // ==================== fingerprint ====================

    @Test
    public void sameTextAtDifferentOffsetsHashesTheSame() {
        byte[] a = "int x = foo(1); // one".getBytes(StandardCharsets.UTF_8);
        byte[] b = "    int y = foo(1); // two".getBytes(StandardCharsets.UTF_8);

        String fa = BaselineEngine.fingerprint("r/x", a, new SourceRange(8, 14));
        String fb = BaselineEngine.fingerprint("r/x", b, new SourceRange(12, 18));

        assertEquals(fa, fb, "the same rule + same content at different line positions is one"
                + " baseline family (line drift immune)");
    }

    @Test
    public void differentTextHashesDifferently() {
        byte[] a = "foo(1);".getBytes(StandardCharsets.UTF_8);
        byte[] b = "foo(2);".getBytes(StandardCharsets.UTF_8);

        assertNotEquals(BaselineEngine.fingerprint("r/x", a, new SourceRange(0, 7)),
                BaselineEngine.fingerprint("r/x", b, new SourceRange(0, 7)));
    }

    @Test
    public void ruleIdParticipatesInTheHash() {
        byte[] a = "foo(1);".getBytes(StandardCharsets.UTF_8);
        assertNotEquals(BaselineEngine.fingerprint("r/x", a, new SourceRange(0, 7)),
                BaselineEngine.fingerprint("r/y", a, new SourceRange(0, 7)));
    }

    @Test
    public void multiByteContentHashesTheExactSlice() {
        // 中文注释 before the range: a char-offset slice would cut the wrong
        // bytes; the byte slice must not
        String source = "int x = 1; // 中文注释\nfoo(1);";
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        int prefixBytes = "int x = 1; // ".getBytes(StandardCharsets.UTF_8).length
                + "中文注释".getBytes(StandardCharsets.UTF_8).length + 1;
        String f1 = BaselineEngine.fingerprint("r/x", bytes, new SourceRange(prefixBytes,
                prefixBytes + 7));
        // the same text without the CJK prefix must hash the same
        byte[] plain = "foo(1);".getBytes(StandardCharsets.UTF_8);
        String f2 = BaselineEngine.fingerprint("r/x", plain, new SourceRange(0, 7));
        assertEquals(f2, f1, "the byte slice skips the multi-byte prefix exactly");
    }

    // ==================== write ====================

    @Test
    public void writeEntriesMergeSameRuleAndTextWithCounts() {
        byte[] source = "foo(1); bar(); foo(1);".getBytes(StandardCharsets.UTF_8);
        List<Diagnostic> diagnostics = List.of(
                diagnostic("r/a", source, 0, 7),
                diagnostic("r/b", source, 8, 14),
                diagnostic("r/a", source, 15, 22));

        List<BaselineFile.Entry> entries = BaselineEngine.writeEntries("src/X.java", diagnostics,
                source);

        assertEquals(2, entries.size(), "same rule + same text merges into one entry");
        BaselineFile.Entry first = entries.get(0);
        assertEquals("r/a", first.rule());
        assertEquals("src/X.java", first.file());
        assertEquals(2, first.count(), "two foo(1) occurrences");
        assertEquals(1, entries.get(1).count());
    }

    // ==================== compute / consumption / stale ====================

    @Test
    public void consumptionIsCappedAtEntryCountAndStaleDetected() {
        byte[] source = "foo(1); foo(1); foo(1);".getBytes(StandardCharsets.UTF_8);
        List<Diagnostic> diagnostics = List.of(
                diagnostic("r/a", source, 0, 7),
                diagnostic("r/a", source, 8, 15),
                diagnostic("r/a", source, 16, 23));
        List<BaselineFile.Entry> entries = List.of(
                new BaselineFile.Entry("r/a", "src/X.java", BaselineEngine.fingerprint("r/a",
                        source, new SourceRange(0, 7)), 2));

        BaselineEngine.FileBaseline baseline = BaselineEngine.compute("src/X.java", diagnostics,
                entries, source);

        assertEquals(2, baseline.consumed(), "the entry forgives two occurrences");
        assertTrue(baseline.staleEntries().isEmpty(),
                "count=2 was fully used by the first two occurrences — no stale residue");
        assertTrue(baseline.suppresses("r/a", entries.get(0).fingerprint()),
                "the decision set contains the forgiven key (stateless across fix passes)");
        assertTrue(baseline.suppresses("r/a", BaselineEngine.fingerprint("r/a", source,
                        new SourceRange(16, 23))),
                "the pass/report predicate is KEY-based: a forgiven key suppresses its whole "
                        + "family in fix modes; the occurrence cap shows in consumed()=2 and in "
                        + "the NONE-mode multi-set accounting (see the CLI e2e)");
    }

    @Test
    public void fullyUnusedEntryIsWhollyStale() {
        byte[] source = "bar();".getBytes(StandardCharsets.UTF_8);
        List<BaselineFile.Entry> entries = List.of(
                new BaselineFile.Entry("r/a", "src/X.java", "deadbeef", 3));

        BaselineEngine.FileBaseline baseline = BaselineEngine.compute("src/X.java", List.of(),
                entries, source);

        assertEquals(0, baseline.consumed());
        assertEquals(1, baseline.staleEntries().size());
        assertEquals(3, baseline.staleEntries().get(0).count());
    }

    // ==================== file round-trip and fail-closed loading ====================

    @Test
    public void fileRoundTripsThroughThePlatformYamlToolkit() throws IOException {
        Path file = dir.resolve("baseline.yml");
        BaselineFile original = new BaselineFile(BaselineFile.VERSION_1, List.of(
                new BaselineFile.Entry("r/a", "src/X.java", "fingerprint-1", 2),
                new BaselineFile.Entry("r/b", "src/Y.java", "fingerprint-2", 1)));
        original.writeTo(file);

        BaselineFile loaded = BaselineFile.load(file);
        assertEquals(BaselineFile.VERSION_1, loaded.version());
        assertEquals(original.entries(), loaded.entries());
    }

    @Test
    public void corruptedYamlFailsClosed() throws IOException {
        Path file = dir.resolve("broken.yml");
        Files.writeString(file, "{{{ not yaml ]]");
        assertThrows(NopLintException.class, () -> BaselineFile.load(file));
    }

    @Test
    public void unknownVersionFailsClosed() throws IOException {
        Path file = dir.resolve("v99.yml");
        Files.writeString(file, "version: 99\nentries: []\n");
        NopLintException ex = assertThrows(NopLintException.class, () -> BaselineFile.load(file));
        assertTrue(ex.getMessage().contains("version"), ex.getMessage());
    }

    @Test
    public void incompleteEntryFailsClosed() throws IOException {
        Path file = dir.resolve("incomplete.yml");
        Files.writeString(file, "version: 1\nentries:\n  - rule: r/a\n    count: 1\n");
        NopLintException ex = assertThrows(NopLintException.class, () -> BaselineFile.load(file));
        assertTrue(ex.getMessage().contains("incomplete"), ex.getMessage());
    }
}

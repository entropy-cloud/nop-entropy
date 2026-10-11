package io.nop.lint.core.engine;

import io.nop.lint.core.node.SourceRange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Value model proofs for {@link Diagnostic} and {@link LintStats}: field
 * completeness, immutability (no mutation path after build), and the
 * builder-side accounting contract (defaults zero, distinct counters, id
 * recording on profile skip).
 */
public class TestEngineValueModels {

    @Test
    public void diagnosticCarriesAllFields() {
        SourceRange range = new SourceRange(10, 42);
        Diagnostic diagnostic = new Diagnostic("demo/rule", "warning", "some message", range);

        assertEquals("demo/rule", diagnostic.ruleId());
        assertEquals("warning", diagnostic.severity());
        assertEquals("some message", diagnostic.message());
        assertEquals(range, diagnostic.range());
        assertEquals(new SourceRange(10, 42), diagnostic.range(), "range must be the node's byte range");
    }

    @Test
    public void diagnosticIsImmutableValue() {
        Diagnostic first = new Diagnostic("demo/rule", "error", "m", new SourceRange(0, 1));
        Diagnostic same = new Diagnostic("demo/rule", "error", "m", new SourceRange(0, 1));
        Diagnostic other = new Diagnostic("demo/other", "error", "m", new SourceRange(0, 1));

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, other);
    }

    @Test
    public void diagnosticRejectsNullFields() {
        assertThrows(NullPointerException.class, () -> new Diagnostic(null, "error", "m", new SourceRange(0, 1)));
        assertThrows(NullPointerException.class, () -> new Diagnostic("r", null, "m", new SourceRange(0, 1)));
        assertThrows(NullPointerException.class, () -> new Diagnostic("r", "error", null, new SourceRange(0, 1)));
        assertThrows(NullPointerException.class, () -> new Diagnostic("r", "error", "m", null));
    }

    @Test
    public void statsBuilderStartsAtZero() {
        LintStats stats = LintStats.builder().build();

        assertEquals(0, stats.getRulesLoaded());
        assertEquals(0, stats.getRulesExecuted());
        assertEquals(0, stats.getRulesSkippedByProfile());
        assertEquals(0, stats.getRulesKindFiltered());
        assertEquals(0, stats.getDiagnostics());
        assertTrue(stats.getSkippedRuleIds().isEmpty(), "no skipped rules must mean an empty id list");
    }

    @Test
    public void statsBuilderAccumulatesDistinctCounters() {
        LintStats stats = LintStats.builder()
                .rulesLoaded(4)
                .incRulesExecuted()
                .incRulesExecuted()
                .incRulesKindFiltered()
                .incRulesSkippedByProfile("demo/a")
                .incRulesSkippedByProfile("demo/b")
                .incDiagnostics(3)
                .build();

        assertEquals(4, stats.getRulesLoaded());
        assertEquals(2, stats.getRulesExecuted());
        assertEquals(1, stats.getRulesKindFiltered());
        assertEquals(2, stats.getRulesSkippedByProfile());
        assertEquals(List.of("demo/a", "demo/b"), stats.getSkippedRuleIds(),
                "skipped rule ids must be recorded in skip order");
        assertEquals(3, stats.getDiagnostics());
    }

    @Test
    public void statsBuiltResultIsImmutable() {
        LintStats.Builder builder = LintStats.builder().rulesLoaded(1).incRulesSkippedByProfile("demo/x");
        LintStats stats = builder.build();

        builder.incRulesExecuted().incRulesSkippedByProfile("demo/y");

        assertEquals(1, stats.getRulesLoaded(), "build must snapshot the counters");
        assertEquals(0, stats.getRulesExecuted());
        assertEquals(List.of("demo/x"), stats.getSkippedRuleIds());
        assertThrows(UnsupportedOperationException.class, () -> stats.getSkippedRuleIds().add("demo/z"),
                "the skipped id list must not expose a mutation path");
    }

    @Test
    public void statsSkippedRuleIdIsRequired() {
        assertThrows(NullPointerException.class, () -> LintStats.builder().incRulesSkippedByProfile(null));
    }
}

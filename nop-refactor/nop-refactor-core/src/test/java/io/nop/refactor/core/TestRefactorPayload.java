package io.nop.refactor.core;

import io.nop.lint.core.node.SourceRange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The payload contract tests (nop-refactor WI5 Phase 1): the record
 * component counts pin the baseline §四 field set byte-for-byte — "the
 * contract must not shrink" made executable — and the fail-closed
 * constructions prove no reason-free / context-free NonApply and no blank
 * FileEdit can exist.
 */
public class TestRefactorPayload {

    @Test
    public void refactorResultCarriesTheFullBaselineContract() {
        assertEquals(6, RefactorResult.class.getRecordComponents().length,
                "baseline §四 RefactorResult = applied/edits/diff/verification/stats/nonApplied");
        assertEquals(4, Verification.class.getRecordComponents().length,
                "baseline §四 Verification = parseOk/errorNodeCount/residualDiagnostics/symbolIntact");
        assertEquals(3, FileEdit.class.getRecordComponents().length,
                "baseline §四 FileEdit = path/range/summary");
        assertEquals(5, RefactorStats.class.getRecordComponents().length,
                "baseline §四 RefactorStats = files/edits/skipped buckets/cost tier "
                        + "(+ the R1-sanctioned residualRuleCount presentation form)");
        assertEquals(3, NonApply.Reason.values().length,
                "baseline §四 NonApply reasons = conflict/out-of-scope/unresolved-target");
    }

    @Test
    public void symbolIntactIsNullForTheCodemodFace() {
        Verification verification = new Verification(true, 0, 0, null);
        assertNull(verification.symbolIntact(),
                "the codemod face never produces symbolIntact (rename-class only, WI9-WI12)");
    }

    @Test
    public void nonApplyRejectsMissingReasonPathOrDetail() {
        assertThrows(NullPointerException.class,
                () -> new NonApply(null, "a/B.java", "context"),
                "a drop without a reason is a silent skip");
        assertThrows(NopRefactorException.class,
                () -> new NonApply(NonApply.Reason.CONFLICT, " ", "context"),
                "a drop without a locatable path is a silent skip");
        NopRefactorException ex = assertThrows(NopRefactorException.class,
                () -> new NonApply(NonApply.Reason.OUT_OF_SCOPE, "a/B.java", " "));
        assertTrue(ex.getMessage().contains("OUT_OF_SCOPE"), ex.getMessage());
    }

    @Test
    public void fileEditRejectsBlankPathOrSummaryAndNullRange() {
        assertThrows(NopRefactorException.class,
                () -> new FileEdit(" ", new SourceRange(0, 1), "s"));
        assertThrows(NopRefactorException.class,
                () -> new FileEdit("a.java", null, "s"));
        assertThrows(NopRefactorException.class,
                () -> new FileEdit("a.java", new SourceRange(0, 1), " "));
    }

    @Test
    public void skippedBucketsMirrorTheNonAppliedPopulation() {
        var buckets = RefactorStats.SkippedBuckets.of(List.of(
                new NonApply(NonApply.Reason.CONFLICT, "a.java", "overlaps demo/x"),
                new NonApply(NonApply.Reason.CONFLICT, "b.java", "overlaps demo/y"),
                new NonApply(NonApply.Reason.OUT_OF_SCOPE, "c.java", "exempted demo/z")));

        assertEquals(2, buckets.conflict());
        assertEquals(1, buckets.outOfScope());
        assertEquals(0, buckets.unresolvedTarget());
        assertEquals(3, buckets.total());
    }

    @Test
    public void statsRejectNegativeResidualRuleCount() {
        assertThrows(NopRefactorException.class,
                () -> new RefactorStats(0, 0, RefactorStats.SkippedBuckets.of(List.of()),
                        RefactorStats.CostTier.IN_PROCESS, -1));
    }
}

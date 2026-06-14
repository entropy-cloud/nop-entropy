package io.nop.ai.agent.skill;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SkillCurationResult} model construction, round-trip,
 * {@link SkillCurationResult#empty()} factory, and the success/fail marker
 * distinction.
 */
public class TestSkillCurationResult {

    @Test
    void emptyFactoryProducesSuccessResult() {
        SkillCurationResult result = SkillCurationResult.empty();

        assertNotNull(result);
        assertTrue(result.isSuccess(), "empty() must produce success marker");
        assertTrue(result.getAssessments().isEmpty());
        assertTrue(result.getCoverageGaps().isEmpty());
        assertTrue(result.getRedundancies().isEmpty());
        assertEquals("no-op", result.getMetadata().getCuratorType());
        assertTrue(result.getMetadata().isSuccess());
        assertEquals(0, result.getMetadata().getTotalTokens());
        assertNull(result.getMetadata().getFailureDetail());
    }

    @Test
    void failedFactoryProducesFailResult() {
        SkillCurationResult result = SkillCurationResult.failed("llm", "LLM service unavailable");

        assertNotNull(result);
        assertFalse(result.isSuccess(), "failed() must produce fail marker");
        assertTrue(result.getAssessments().isEmpty());
        assertEquals("llm", result.getMetadata().getCuratorType());
        assertFalse(result.getMetadata().isSuccess());
        assertEquals("LLM service unavailable", result.getMetadata().getFailureDetail());
    }

    @Test
    void successAndFailMarkersAreDistinguishable() {
        SkillCurationResult successEmpty = SkillCurationResult.empty();
        SkillCurationResult failed = SkillCurationResult.failed("llm", "error");

        // Both have empty assessments but different markers — this is the
        // core distinction the plan requires.
        assertTrue(successEmpty.getAssessments().isEmpty());
        assertTrue(failed.getAssessments().isEmpty());
        assertTrue(successEmpty.isSuccess());
        assertFalse(failed.isSuccess());
        assertTrue(successEmpty.getMetadata().isSuccess());
        assertFalse(failed.getMetadata().isSuccess());
    }

    @Test
    void fullConstructionRoundTripsAllFields() {
        List<SkillCurationResult.SkillAssessment> assessments = Arrays.asList(
                new SkillCurationResult.SkillAssessment(
                        "web-search", SkillQualityRating.WELL_DEFINED,
                        "Good coverage", "Clear goal and scope"),
                new SkillCurationResult.SkillAssessment(
                        "log-analysis", SkillQualityRating.NEEDS_IMPROVEMENT,
                        "Add intent signatures", "Goal is vague")
        );
        List<String> gaps = Collections.singletonList("No skill for code formatting");
        List<String> redundancies = Collections.singletonList("code-review overlaps with audit");
        SkillCurationResult.SkillCurationMetadata metadata = new SkillCurationResult.SkillCurationMetadata(
                "llm", "gpt-4o-mini", 500, 200, 700, true, null);

        SkillCurationResult result = new SkillCurationResult(assessments, gaps, redundancies, metadata);

        assertEquals(2, result.getAssessments().size());
        assertEquals("web-search", result.getAssessments().get(0).getSkillName());
        assertEquals(SkillQualityRating.WELL_DEFINED, result.getAssessments().get(0).getRating());
        assertEquals("Good coverage", result.getAssessments().get(0).getRecommendation());
        assertEquals("Clear goal and scope", result.getAssessments().get(0).getRationale());
        assertEquals("log-analysis", result.getAssessments().get(1).getSkillName());
        assertEquals(SkillQualityRating.NEEDS_IMPROVEMENT, result.getAssessments().get(1).getRating());

        assertEquals(1, result.getCoverageGaps().size());
        assertEquals("No skill for code formatting", result.getCoverageGaps().get(0));
        assertEquals(1, result.getRedundancies().size());
        assertEquals("code-review overlaps with audit", result.getRedundancies().get(0));

        assertEquals("llm", result.getMetadata().getCuratorType());
        assertEquals("gpt-4o-mini", result.getMetadata().getModelName());
        assertEquals(500, result.getMetadata().getPromptTokens());
        assertEquals(200, result.getMetadata().getCompletionTokens());
        assertEquals(700, result.getMetadata().getTotalTokens());
        assertTrue(result.getMetadata().isSuccess());
        assertTrue(result.isSuccess());
        assertTrue(result.hasAssessments());
    }

    @Test
    void nullMetadataDefaultsToNoOp() {
        SkillCurationResult result = new SkillCurationResult(null, null, null, null);

        assertNotNull(result.getMetadata());
        assertEquals("no-op", result.getMetadata().getCuratorType());
        assertTrue(result.isSuccess());
    }

    @Test
    void nullCollectionsYieldEmptyLists() {
        SkillCurationResult result = new SkillCurationResult(null, null, null, null);

        assertTrue(result.getAssessments().isEmpty());
        assertTrue(result.getCoverageGaps().isEmpty());
        assertTrue(result.getRedundancies().isEmpty());
    }

    @Test
    void assessmentsListIsImmutable() {
        List<SkillCurationResult.SkillAssessment> assessments = new java.util.ArrayList<>();
        assessments.add(new SkillCurationResult.SkillAssessment(
                "x", SkillQualityRating.WELL_DEFINED, "", ""));

        SkillCurationResult result = new SkillCurationResult(assessments, null, null, null);

        java.util.List<SkillCurationResult.SkillAssessment> list = result.getAssessments();
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> list.add(new SkillCurationResult.SkillAssessment(
                        "y", SkillQualityRating.REDUNDANT, "", "")));
    }

    @Test
    void skillAssessmentEqualsAndHashCode() {
        SkillCurationResult.SkillAssessment a1 = new SkillCurationResult.SkillAssessment(
                "web-search", SkillQualityRating.WELL_DEFINED, "rec", "why");
        SkillCurationResult.SkillAssessment a2 = new SkillCurationResult.SkillAssessment(
                "web-search", SkillQualityRating.WELL_DEFINED, "rec", "why");
        SkillCurationResult.SkillAssessment b = new SkillCurationResult.SkillAssessment(
                "web-search", SkillQualityRating.NEEDS_IMPROVEMENT, "rec", "why");

        assertEquals(a1, a2);
        assertEquals(a1.hashCode(), a2.hashCode());
        assertFalse(a1.equals(b));
    }

    @Test
    void metadataFailedFactory() {
        SkillCurationResult.SkillCurationMetadata meta =
                SkillCurationResult.SkillCurationMetadata.failed("llm", "timeout");

        assertFalse(meta.isSuccess());
        assertEquals("llm", meta.getCuratorType());
        assertEquals("timeout", meta.getFailureDetail());
        assertEquals(0, meta.getTotalTokens());
    }

    @Test
    void metadataNoOpFactory() {
        SkillCurationResult.SkillCurationMetadata meta =
                SkillCurationResult.SkillCurationMetadata.noOp();

        assertTrue(meta.isSuccess());
        assertEquals("no-op", meta.getCuratorType());
        assertNull(meta.getFailureDetail());
        assertEquals(0, meta.getTotalTokens());
    }

    @Test
    void hasAssessmentsReflectsAssessmentList() {
        SkillCurationResult empty = SkillCurationResult.empty();
        assertFalse(empty.hasAssessments());

        SkillCurationResult withAssessments = new SkillCurationResult(
                Collections.singletonList(new SkillCurationResult.SkillAssessment(
                        "x", SkillQualityRating.WELL_DEFINED, "", "")),
                null, null, null);
        assertTrue(withAssessments.hasAssessments());
    }
}

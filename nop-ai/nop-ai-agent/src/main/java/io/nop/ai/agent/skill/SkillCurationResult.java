package io.nop.ai.agent.skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of skill curation (design {@code skill-system-design.md} §5.5).
 * Carries advisory curation recommendations produced by an
 * {@link ISkillCurator}:
 * <ul>
 *   <li>{@link #getAssessments()} — per-skill advisory assessments (quality
 *       rating, recommendation, rationale).</li>
 *   <li>{@link #getCoverageGaps()} — registry-level gaps (capabilities that no
 *       registered skill covers). May be empty.</li>
 *   <li>{@link #getRedundancies()} — registry-level redundancies (skills that
 *       overlap with each other). May be empty.</li>
 *   <li>{@link #getMetadata()} — curation metadata (curator type, model name,
 *       token usage, success/fail marker).</li>
 * </ul>
 *
 * <p>The result is <b>advisory and non-mutating</b> — it recommends, never
 * modifies skill definitions.
 *
 * <p>The {@link SkillCurationMetadata#isSuccess() success/fail marker}
 * distinguishes "curation succeeded with zero skills" (success=true, empty
 * assessments) from "curation failed" (success=false). Both are explicit,
 * never produced by swallowing an exception.
 */
public final class SkillCurationResult {

    private final List<SkillAssessment> assessments;
    private final List<String> coverageGaps;
    private final List<String> redundancies;
    private final SkillCurationMetadata metadata;

    public SkillCurationResult(List<SkillAssessment> assessments,
                               List<String> coverageGaps,
                               List<String> redundancies,
                               SkillCurationMetadata metadata) {
        this.assessments = assessments != null
                ? Collections.unmodifiableList(new ArrayList<>(assessments))
                : Collections.emptyList();
        this.coverageGaps = coverageGaps != null
                ? Collections.unmodifiableList(new ArrayList<>(coverageGaps))
                : Collections.emptyList();
        this.redundancies = redundancies != null
                ? Collections.unmodifiableList(new ArrayList<>(redundancies))
                : Collections.emptyList();
        this.metadata = metadata != null ? metadata : SkillCurationMetadata.noOp();
    }

    /**
     * An empty success result: zero assessments, zero gaps, zero redundancies,
     * with a "no-op" success marker. This is the canonical "nothing to curate"
     * result — it signals explicit success, not an error.
     */
    public static SkillCurationResult empty() {
        return new SkillCurationResult(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                SkillCurationMetadata.noOp());
    }

    /**
     * A failed curation result: zero assessments, with an explicit fail marker
     * and failure detail. Used by {@link LLMCurator} and the engine when
     * curation could not be performed (LLM error, unparseable response, etc.).
     *
     * @param curatorType   the type label of the curator that failed
     * @param failureDetail a human-readable description of the failure
     */
    public static SkillCurationResult failed(String curatorType, String failureDetail) {
        return new SkillCurationResult(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                SkillCurationMetadata.failed(curatorType, failureDetail));
    }

    /**
     * Per-skill advisory assessments. Never null; may be empty (zero skills
     * curated, or all batches failed).
     */
    public List<SkillAssessment> getAssessments() {
        return assessments;
    }

    /**
     * Registry-level coverage gaps identified during curation. Never null;
     * may be empty.
     */
    public List<String> getCoverageGaps() {
        return coverageGaps;
    }

    /**
     * Registry-level redundancies identified during curation. Never null;
     * may be empty.
     */
    public List<String> getRedundancies() {
        return redundancies;
    }

    /**
     * Curation metadata including the success/fail marker. Never null.
     */
    public SkillCurationMetadata getMetadata() {
        return metadata;
    }

    /**
     * Convenience: whether curation succeeded.
     */
    public boolean isSuccess() {
        return metadata.isSuccess();
    }

    /**
     * Convenience: whether any per-skill assessment was produced.
     */
    public boolean hasAssessments() {
        return !assessments.isEmpty();
    }

    @Override
    public String toString() {
        return "SkillCurationResult{success=" + metadata.isSuccess()
                + ", curatorType=" + metadata.getCuratorType()
                + ", assessments=" + assessments.size()
                + ", coverageGaps=" + coverageGaps.size()
                + ", redundancies=" + redundancies.size()
                + (metadata.getTotalTokens() > 0 ? ", tokens=" + metadata.getTotalTokens() : "")
                + "}";
    }

    // ===== nested data classes =====

    /**
     * Advisory assessment of a single skill definition. Carries the quality
     * rating, a recommendation for improvement, and the rationale behind the
     * rating.
     */
    public static final class SkillAssessment {
        private final String skillName;
        private final SkillQualityRating rating;
        private final String recommendation;
        private final String rationale;

        public SkillAssessment(String skillName, SkillQualityRating rating,
                               String recommendation, String rationale) {
            this.skillName = skillName;
            this.rating = rating;
            this.recommendation = recommendation;
            this.rationale = rationale;
        }

        /**
         * Name of the assessed skill (matches {@link SkillModel#getName()}).
         */
        public String getSkillName() {
            return skillName;
        }

        /**
         * Quality rating assigned by the curator.
         */
        public SkillQualityRating getRating() {
            return rating;
        }

        /**
         * Improvement recommendation. For {@link SkillQualityRating#WELL_DEFINED}
         * skills this may be an empty string or a minor refinement note.
         */
        public String getRecommendation() {
            return recommendation;
        }

        /**
         * Rationale explaining why the rating was assigned.
         */
        public String getRationale() {
            return rationale;
        }

        @Override
        public String toString() {
            return "SkillAssessment{" + skillName + ", rating=" + rating + "}";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SkillAssessment that = (SkillAssessment) o;
            return Objects.equals(skillName, that.skillName)
                    && rating == that.rating
                    && Objects.equals(recommendation, that.recommendation)
                    && Objects.equals(rationale, that.rationale);
        }

        @Override
        public int hashCode() {
            return Objects.hash(skillName, rating, recommendation, rationale);
        }
    }

    /**
     * Metadata about a curation pass: the curator type label, the model used
     * (if LLM-backed), accumulated token usage, and a success/fail marker with
     * optional failure detail.
     */
    public static final class SkillCurationMetadata {
        private final String curatorType;
        private final String modelName;
        private final int promptTokens;
        private final int completionTokens;
        private final int totalTokens;
        private final boolean success;
        private final String failureDetail;

        public SkillCurationMetadata(String curatorType, String modelName,
                                     int promptTokens, int completionTokens, int totalTokens,
                                     boolean success, String failureDetail) {
            this.curatorType = curatorType != null ? curatorType : "unknown";
            this.modelName = modelName;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
            this.success = success;
            this.failureDetail = failureDetail;
        }

        /**
         * Metadata for a no-op pass-through curation: success with zero tokens.
         */
        public static SkillCurationMetadata noOp() {
            return new SkillCurationMetadata("no-op", null, 0, 0, 0, true, null);
        }

        /**
         * Metadata for a failed curation: fail marker with detail.
         */
        public static SkillCurationMetadata failed(String curatorType, String failureDetail) {
            return new SkillCurationMetadata(curatorType != null ? curatorType : "unknown",
                    null, 0, 0, 0, false, failureDetail);
        }

        /**
         * Type label of the curator (e.g. {@code "no-op"}, {@code "llm"}).
         */
        public String getCuratorType() {
            return curatorType;
        }

        /**
         * Model name used for LLM-backed curation, or null for non-LLM curators.
         */
        public String getModelName() {
            return modelName;
        }

        public int getPromptTokens() {
            return promptTokens;
        }

        public int getCompletionTokens() {
            return completionTokens;
        }

        public int getTotalTokens() {
            return totalTokens;
        }

        /**
         * Success marker. {@code true} means curation completed (possibly with
         * zero skills assessed); {@code false} means curation failed.
         */
        public boolean isSuccess() {
            return success;
        }

        /**
         * Human-readable failure detail, or null when {@link #isSuccess()} is
         * true.
         */
        public String getFailureDetail() {
            return failureDetail;
        }

        @Override
        public String toString() {
            return "SkillCurationMetadata{curatorType=" + curatorType
                    + ", success=" + success
                    + (modelName != null ? ", model=" + modelName : "")
                    + (totalTokens > 0 ? ", tokens=" + totalTokens : "")
                    + (failureDetail != null ? ", failureDetail=" + failureDetail : "")
                    + "}";
        }
    }
}

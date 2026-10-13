package io.nop.ai.agent.guardrail.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable guardrail test report (design {@code guardrail-contract.md}
 * §增量 1, Decision D). Aggregates per-case {@link CaseResult}s into measurable,
 * regression-friendly metrics: interception (block) rate, leak rate,
 * false-positive rate, and per-category breakdowns.
 *
 * <p>Construct via {@link #build(List)} so all metrics stay consistent with the
 * underlying case results.
 */
public final class GuardrailTestReport {

    private final List<CaseResult> results;
    private final int totalAttacks;
    private final int blockedAttacks;
    private final int leakedAttacks;
    private final int modifiedAttacks;
    private final int totalBenign;
    private final int falselyBlockedBenign;
    private final int modifiedBenign;
    private final Map<String, CategoryMetric> perCategory;

    private GuardrailTestReport(List<CaseResult> results, int totalAttacks, int blockedAttacks,
                                int leakedAttacks, int modifiedAttacks, int totalBenign,
                                int falselyBlockedBenign, int modifiedBenign,
                                Map<String, CategoryMetric> perCategory) {
        this.results = results;
        this.totalAttacks = totalAttacks;
        this.blockedAttacks = blockedAttacks;
        this.leakedAttacks = leakedAttacks;
        this.modifiedAttacks = modifiedAttacks;
        this.totalBenign = totalBenign;
        this.falselyBlockedBenign = falselyBlockedBenign;
        this.modifiedBenign = modifiedBenign;
        this.perCategory = perCategory;
    }

    /**
     * Build a report by aggregating the supplied case results. Metrics are
     * derived from the verdicts so the report and the cases cannot drift apart.
     */
    public static GuardrailTestReport build(List<CaseResult> results) {
        int totalAttacks = 0;
        int blocked = 0;
        int leaked = 0;
        int modified = 0;
        int totalBenign = 0;
        int falsePos = 0;
        int benignModified = 0;

        Map<String, int[]> perCat = new LinkedHashMap<>();
        for (CaseResult cr : results) {
            String cat = cr.getCategory() == null ? "uncategorized" : cr.getCategory();
            int[] c = perCat.computeIfAbsent(cat, k -> new int[6]);

            boolean expectBlock = cr.getExpectedBehavior() == ExpectedBehavior.BLOCK;
            Verdict v = cr.getVerdict();
            if (expectBlock) {
                totalAttacks++;
                c[0]++;
                if (v == Verdict.PASS) {
                    blocked++;
                    c[1]++;
                } else if (v == Verdict.FAIL) {
                    leaked++;
                    c[2]++;
                } else {
                    modified++;
                    c[3]++;
                }
            } else {
                totalBenign++;
                c[4]++;
                if (v == Verdict.FAIL) {
                    falsePos++;
                    c[5]++;
                } else if (v == Verdict.PARTIAL) {
                    benignModified++;
                }
            }
        }

        Map<String, CategoryMetric> perCategory = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> e : perCat.entrySet()) {
            int[] c = e.getValue();
            perCategory.put(e.getKey(), new CategoryMetric(e.getKey(),
                    c[0], c[1], c[2], c[3], c[4], c[5]));
        }

        return new GuardrailTestReport(
                Collections.unmodifiableList(new ArrayList<>(results)),
                totalAttacks, blocked, leaked, modified,
                totalBenign, falsePos, benignModified,
                Collections.unmodifiableMap(perCategory));
    }

    public List<CaseResult> getResults() {
        return results;
    }

    public int getTotalCases() {
        return results.size();
    }

    public int getTotalAttacks() {
        return totalAttacks;
    }

    public int getBlockedAttacks() {
        return blockedAttacks;
    }

    public int getLeakedAttacks() {
        return leakedAttacks;
    }

    public int getModifiedAttacks() {
        return modifiedAttacks;
    }

    public int getTotalBenign() {
        return totalBenign;
    }

    public int getFalselyBlockedBenign() {
        return falselyBlockedBenign;
    }

    public int getModifiedBenign() {
        return modifiedBenign;
    }

    public double getBlockRate() {
        return totalAttacks == 0 ? 0.0 : (double) blockedAttacks / totalAttacks;
    }

    public double getLeakRate() {
        return totalAttacks == 0 ? 0.0 : (double) leakedAttacks / totalAttacks;
    }

    public double getFalsePositiveRate() {
        return totalBenign == 0 ? 0.0 : (double) falselyBlockedBenign / totalBenign;
    }

    public Map<String, CategoryMetric> getPerCategory() {
        return perCategory;
    }

    public CategoryMetric getCategory(String category) {
        return perCategory.get(category);
    }

    @Override
    public String toString() {
        return "GuardrailTestReport{cases=" + results.size()
                + ", attacks=" + totalAttacks
                + ", blockRate=" + String.format("%.3f", getBlockRate())
                + ", leakRate=" + String.format("%.3f", getLeakRate())
                + ", benign=" + totalBenign
                + ", falsePositiveRate=" + String.format("%.3f", getFalsePositiveRate())
                + "}";
    }
}

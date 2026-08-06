package io.nop.ai.agent.guardrail.test;

import io.nop.ai.agent.security.SecurityCheckpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable aggregated report over a batch of {@link CheckpointTestResult}s
 * (design {@code guardrail-contract.md} §增量 4, 裁定 E). Provides measurable,
 * regression-friendly metrics: pass rate, deny rate, and per-category /
 * per-matchedRule breakdowns so consumers can locate which checkpoint layer is
 * over- or under-firing.
 *
 * <p>Construct via {@link #build(List)} so all metrics stay consistent with the
 * underlying case results.
 *
 * <p><b>No hard fail-fast threshold</b> (裁定 E, consistent with 增量 1 裁定 D):
 * the report exposes concrete numbers that callers may assert on, but does not
 * itself abort a run. Gating policy is the consumer's decision.
 */
public final class CheckpointTestReport {

    private final List<CheckpointTestResult> results;
    private final int totalCases;
    private final int passed;
    private final int failed;
    private final int denied;
    private final int allowed;
    private final Map<String, int[]> perCategoryRaw;
    private final Map<String, int[]> perMatchedRuleRaw;

    private CheckpointTestReport(List<CheckpointTestResult> results, int totalCases, int passed,
                                 int failed, int denied, int allowed,
                                 Map<String, int[]> perCategoryRaw,
                                 Map<String, int[]> perMatchedRuleRaw) {
        this.results = results;
        this.totalCases = totalCases;
        this.passed = passed;
        this.failed = failed;
        this.denied = denied;
        this.allowed = allowed;
        this.perCategoryRaw = perCategoryRaw;
        this.perMatchedRuleRaw = perMatchedRuleRaw;
    }

    /**
     * Build a report by aggregating the supplied results. Metrics are derived
     * from the verdicts so the report and the cases cannot drift apart.
     */
    public static CheckpointTestReport build(List<CheckpointTestResult> results) {
        int total = results.size();
        int pass = 0;
        int denied = 0;
        // perCategory: [total, passed, denied]
        Map<String, int[]> perCat = new LinkedHashMap<>();
        // perMatchedRule: [count] keyed by actualMatchedRule ("(allow)" bucket for ALLOW outcomes)
        Map<String, int[]> perRule = new LinkedHashMap<>();

        for (CheckpointTestResult r : results) {
            if (r.isPassed()) {
                pass++;
            }
            boolean isDenied = r.getActualDecision() != SecurityCheckpoint.Decision.ALLOW;
            if (isDenied) {
                denied++;
            }
            String cat = r.getCategory() == null ? "uncategorized" : r.getCategory();
            int[] c = perCat.computeIfAbsent(cat, k -> new int[3]);
            c[0]++;
            if (r.isPassed()) {
                c[1]++;
            }
            if (isDenied) {
                c[2]++;
            }
            String ruleKey = isDenied && r.getActualMatchedRule() != null
                    ? r.getActualMatchedRule()
                    : (isDenied ? "(deny:no-rule)" : "(allow)");
            perRule.computeIfAbsent(ruleKey, k -> new int[1])[0]++;
        }

        return new CheckpointTestReport(
                Collections.unmodifiableList(new ArrayList<>(results)),
                total, pass, total - pass, denied, total - denied,
                perCat, perRule);
    }

    public List<CheckpointTestResult> getResults() {
        return results;
    }

    public int getTotalCases() {
        return totalCases;
    }

    public int getPassed() {
        return passed;
    }

    public int getFailed() {
        return failed;
    }

    public int getDenied() {
        return denied;
    }

    public int getAllowed() {
        return allowed;
    }

    public double getPassRate() {
        return totalCases == 0 ? 0.0 : (double) passed / totalCases;
    }

    public double getDenyRate() {
        return totalCases == 0 ? 0.0 : (double) denied / totalCases;
    }

    /**
     * @return per-category metric snapshot: each value is an unmodifiable map
     *         from category name to {@link CategorySlice} (total / passed /
     *         denied counts)
     */
    public Map<String, CategorySlice> getPerCategory() {
        Map<String, CategorySlice> out = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> e : perCategoryRaw.entrySet()) {
            int[] c = e.getValue();
            out.put(e.getKey(), new CategorySlice(e.getKey(), c[0], c[1], c[2]));
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * @return per-matchedRule counts (how many cases resolved at each checkpoint
     *         layer). {@code "(allow)"} buckets ALLOW outcomes and
     *         {@code "(deny:no-rule)"} buckets denies with no captured rule.
     */
    public Map<String, Integer> getPerMatchedRule() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> e : perMatchedRuleRaw.entrySet()) {
            out.put(e.getKey(), e.getValue()[0]);
        }
        return Collections.unmodifiableMap(out);
    }

    @Override
    public String toString() {
        return "CheckpointTestReport{cases=" + totalCases
                + ", passed=" + passed
                + ", passRate=" + String.format("%.3f", getPassRate())
                + ", denyRate=" + String.format("%.3f", getDenyRate())
                + ", matchedRules=" + getPerMatchedRule().keySet()
                + "}";
    }

    /**
     * Per-category metric slice (total / passed / denied counts).
     */
    public static final class CategorySlice {
        private final String category;
        private final int total;
        private final int passed;
        private final int denied;

        public CategorySlice(String category, int total, int passed, int denied) {
            this.category = category;
            this.total = total;
            this.passed = passed;
            this.denied = denied;
        }

        public String getCategory() {
            return category;
        }

        public int getTotal() {
            return total;
        }

        public int getPassed() {
            return passed;
        }

        public int getDenied() {
            return denied;
        }

        public double getPassRate() {
            return total == 0 ? 0.0 : (double) passed / total;
        }
    }
}

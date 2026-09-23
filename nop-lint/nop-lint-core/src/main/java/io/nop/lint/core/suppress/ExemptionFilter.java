package io.nop.lint.core.suppress;

import io.nop.lint.core.rule.RuleSetModel;

import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;

/**
 * The run-level exemption surface of the ruleset (roadmap item 27, design 09
 * §4): a diagnostic whose rule id is exempted for the diagnostic's file is
 * removed at the CheckRunner layer — never inside {@code LintEngine} or
 * {@code SuppressionFilter}, so RuleTester fixtures are untouched by ruleset
 * exemptions (design 09 §6 red line).
 *
 * <p>The probed file is normalized before matching so the globs see one
 * canonical form regardless of the invocation root. The filter is a stateless
 * predicate — under {@code --fix} it applies identically to every multipass
 * candidate sweep and to the report lint (plan 2026-09-24-0050-1,
 * fix-interaction adjudication).</p>
 */
public final class ExemptionFilter {

    private static final ExemptionFilter EMPTY = new ExemptionFilter(List.of());

    private final List<RuleSetModel.Exemption> exemptions;

    private ExemptionFilter(List<RuleSetModel.Exemption> exemptions) {
        this.exemptions = exemptions;
    }

    public static ExemptionFilter of(List<RuleSetModel.Exemption> exemptions) {
        if (exemptions == null || exemptions.isEmpty()) {
            return EMPTY;
        }
        return new ExemptionFilter(List.copyOf(exemptions));
    }

    /**
     * True when at least one exemption names {@code ruleId} and one of its
     * compiled globs matches the normalized file path.
     */
    public boolean suppresses(String ruleId, Path file) {
        Path normalized = file.normalize();
        for (RuleSetModel.Exemption exemption : exemptions) {
            if (!exemption.ruleId().equals(ruleId)) {
                continue;
            }
            for (PathMatcher matcher : exemption.matchers()) {
                if (matcher.matches(normalized)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The number of exemptions in this filter (zero = the empty filter).
     */
    public int size() {
        return exemptions.size();
    }

    /**
     * The canonical path form shared with the baseline layer: normalized,
     * separated by forward slashes.
     */
    public static String normalize(Path file) {
        return file.normalize().toString().replace('\\', '/');
    }
}

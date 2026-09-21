package io.nop.lint.core.engine;

import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The v1 pipeline tail shared by every profile: kind-bit filtering first,
 * matching second, diagnostics third (design 11 §1 fixed order, v1 subset
 * of design 03 §1.1). A rule whose target kinds are disjoint from the
 * file's kind occurrences is counted via {@code rulesKindFiltered} and its
 * matcher is never invoked — the mutual exclusivity of rulesKindFiltered
 * and rulesExecuted is the observable proof that filtering short-circuits
 * matching rather than filtering matches afterwards.
 */
final class RuleSetRunner {

    private RuleSetRunner() {
    }

    /**
     * Runs compiled rules against one tree, accumulating stats. The caller
     * owns the rulesLoaded / rulesSkippedByProfile accounting (those happen
     * before compilation); this loop owns the kind-filtered / executed /
     * diagnostics counters.
     */
    static List<Diagnostic> run(List<CompiledRule> rules, LintTree tree, LintStats.Builder stats) {
        Set<Integer> occurringKinds = KindIndex.collect(tree.root());
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (CompiledRule rule : rules) {
            if (!rule.canMatchKinds(occurringKinds)) {
                stats.incRulesKindFiltered();
                continue;
            }
            stats.incRulesExecuted();
            List<LintNode> matches = rule.match(tree);
            for (LintNode node : matches) {
                diagnostics.add(new Diagnostic(rule.ruleId(), rule.severity(), rule.message(), node.range()));
            }
            stats.incDiagnostics(matches.size());
        }
        return diagnostics;
    }
}

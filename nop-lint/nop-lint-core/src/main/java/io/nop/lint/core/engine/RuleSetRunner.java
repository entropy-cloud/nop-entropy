package io.nop.lint.core.engine;

import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.pattern.Match;
import io.nop.lint.core.xscript.SourceMap;
import io.nop.lint.core.xscript.XScriptEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *
 * <p>Xscript rules execute their compiled script per match (design 07 §3
 * resource semantics, enforced here so the counters stay engine-owned):
 * a script failure skips exactly that match with a warning count; a rule
 * whose scripts fail {@value #XSCRIPT_CONSECUTIVE_FAILURE_LIMIT} times in a
 * row is disabled for the remainder of the run and its id reported; a match
 * aborted at the diagnostic cap keeps the diagnostics reported before the
 * cap and is counted.</p>
 */
final class RuleSetRunner {

    private static final Logger LOG = LoggerFactory.getLogger(RuleSetRunner.class);

    /**
     * Consecutive script failures after which a rule is disabled for the
     * rest of the run (design 07 §3).
     */
    static final int XSCRIPT_CONSECUTIVE_FAILURE_LIMIT = 50;

    private RuleSetRunner() {
    }

    /**
     * Runs compiled rules against one tree, accumulating stats. The caller
     * owns the rulesLoaded / rulesSkippedByProfile accounting (those happen
     * before compilation); this loop owns the kind-filtered / executed /
     * diagnostics counters and the xscript resource semantics.
     */
    static List<Diagnostic> run(List<CompiledRule> rules, LintTree tree, LintStats.Builder stats) {
        Set<Integer> occurringKinds = KindIndex.collect(tree.root());
        List<Diagnostic> diagnostics = new ArrayList<>();
        SourceMap sourceMap = null;
        for (CompiledRule rule : rules) {
            if (!rule.canMatchKinds(occurringKinds)) {
                stats.incRulesKindFiltered();
                continue;
            }
            stats.incRulesExecuted();
            List<Match> matches = rule.matchWithCaptures(tree);
            if (rule.xscriptEngine() != null) {
                if (sourceMap == null) {
                    sourceMap = new SourceMap(tree.source());
                }
                runXscriptRule(rule.xscriptEngine(), matches, sourceMap, diagnostics, stats);
            } else {
                for (Match match : matches) {
                    diagnostics.add(new Diagnostic(rule.ruleId(), rule.severity(), rule.message(),
                            match.node().range()));
                }
                stats.incDiagnostics(matches.size());
            }
        }
        return diagnostics;
    }

    private static void runXscriptRule(XScriptEngine engine, List<Match> matches, SourceMap sourceMap,
                                       List<Diagnostic> diagnostics, LintStats.Builder stats) {
        int consecutiveFailures = 0;
        for (Match match : matches) {
            stats.incXscriptMatchesExecuted();
            XScriptEngine.MatchOutcome outcome;
            try {
                outcome = engine.executeMatch(match.node(), match.env(), sourceMap);
            } catch (Exception | StackOverflowError e) {                consecutiveFailures++;
                stats.incXscriptFailedMatches();
                LOG.warn("nop.lint.xscript.match-failed:ruleId={},failure={}",
                        engine.ruleId(), consecutiveFailures, e);
                if (consecutiveFailures >= XSCRIPT_CONSECUTIVE_FAILURE_LIMIT) {
                    stats.addDisabledRuleId(engine.ruleId());
                    LOG.warn("nop.lint.xscript.rule-disabled:ruleId={},consecutiveFailures={}",
                            engine.ruleId(), consecutiveFailures);
                    return;
                }
                continue;
            }
            consecutiveFailures = 0;
            if (outcome.capped()) {
                stats.incXscriptCappedMatches();
                LOG.warn("nop.lint.xscript.diagnostics-capped:ruleId={},limit={}",
                        engine.ruleId(), engine.maxDiagnosticsPerMatch());
            }
            diagnostics.addAll(outcome.diagnostics());
            stats.incDiagnostics(outcome.diagnostics().size());
        }
    }
}

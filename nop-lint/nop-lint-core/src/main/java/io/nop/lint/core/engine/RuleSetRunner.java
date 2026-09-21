package io.nop.lint.core.engine;

import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.pattern.Match;
import io.nop.lint.core.xscript.SourceMap;
import io.nop.lint.core.xscript.XScriptDeadline;
import io.nop.lint.core.xscript.XScriptEngine;
import io.nop.lint.core.xscript.XScriptTimeoutException;
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
 * cap and is counted. Each match runs under a profile-scaled deadline
 * (route A wrapper, design 07 §4): a timeout is a bounded non-match,
 * counted separately and never feeding the consecutive-failure disable
 * path.</p>
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
     * diagnostics counters and the xscript resource semantics. The profile
     * scales each rule's per-match xscript budget
     * ({@link LintProfile#xscriptBudgetMs(int)}).
     */
    static List<Diagnostic> run(List<CompiledRule> rules, LintTree tree, LintStats.Builder stats,
                                LintProfile profile) {
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
                runXscriptRule(rule, matches, sourceMap, diagnostics, stats, profile);
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

    private static void runXscriptRule(CompiledRule rule, List<Match> matches, SourceMap sourceMap,
                                       List<Diagnostic> diagnostics, LintStats.Builder stats,
                                       LintProfile profile) {
        XScriptEngine engine = rule.xscriptEngine();
        int consecutiveFailures = 0;
        int timedOut = 0;
        int processed = 0;
        for (Match match : matches) {
            processed++;
            stats.incXscriptMatchesExecuted();
            // Route A (design 07 §4): every match runs under a profile-scaled
            // deadline; the globally installed LintDeadlineExecutor enforces
            // it at each executor entry.
            XScriptDeadline deadline = XScriptDeadline.startNow(engine.ruleId(),
                    profile.xscriptBudgetMs(rule.xscriptTimeoutMs()));
            XScriptEngine.MatchOutcome outcome;
            try {
                outcome = engine.executeMatch(match.node(), match.env(), sourceMap, deadline);
            } catch (XScriptTimeoutException e) {
                // design 07 §3 timeout semantics: the match is treated as
                // non-matching, counted separately, and never feeds the
                // consecutive-failure disable path.
                timedOut++;
                stats.incXscriptTimedOutMatches();
                LOG.warn("nop.lint.xscript.match-timeout:ruleId={},budgetMs={},timedOut={}",
                        engine.ruleId(), deadline.budgetMs(), timedOut, e);
                continue;
            } catch (Exception | StackOverflowError e) {
                consecutiveFailures++;
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
        if (shouldWarnTimeoutRate(processed, timedOut)) {
            LOG.warn("nop.lint.xscript.timeout-rate-high:ruleId={},matches={},timedOut={} (the rule's "
                    + "per-match timeout rate exceeds 1% - consider optimizing the xscript or its budget)",
                    engine.ruleId(), processed, timedOut);
        }
    }

    /**
     * The design 07 §3 timeout-rate warning rule: more than 1% of a rule's
     * matches in one run timed out. Package-visible for the boundary tests.
     */
    static boolean shouldWarnTimeoutRate(int matchesProcessed, int timedOut) {
        return matchesProcessed > 0 && timedOut * 100L > matchesProcessed;
    }
}

package io.nop.lint.core.engine;

import io.nop.lint.core.constraint.Constraint;
import io.nop.lint.core.constraint.ConstraintContext;
import io.nop.lint.core.fix.Fix;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.pattern.Match;
import io.nop.lint.core.semantic.TypeResolutionException;
import io.nop.lint.core.xscript.SourceMap;
import io.nop.lint.core.xscript.XScriptDeadline;
import io.nop.lint.core.xscript.XScriptEngine;
import io.nop.lint.core.xscript.XScriptTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The v1 pipeline tail shared by every profile: kind-bit filtering first,
 * matching second, constraint filtering third, diagnostics last (design 11
 * §1 fixed order, v1 subset of design 03 §1.1 — the constraint position sits
 * between {@code Match[]} and xscript per the §1.1 pipeline). A rule whose
 * target kinds are disjoint from the file's kind occurrences is counted via
 * {@code rulesKindFiltered} and its matcher is never invoked — the mutual
 * exclusivity of rulesKindFiltered and rulesExecuted is the observable proof
 * that filtering short-circuits matching rather than filtering matches
 * afterwards. A match that fails any constraint of its rule produces no
 * diagnostic and lands in the {@code constraintFilteredMatches} counter —
 * observable removal, never a silent drop (design 01 §3.2 Decision).
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
 *
 * <p>Budget and degrade ladder (design 11 §5, roadmap item 31): the
 * per-lint-call {@link FileBudget} carries two clocks over one budget value.
 * When the total clock is exhausted the ladder engages once — deep
 * analyzers close first (only those a live probe served), then L2 (only
 * when a live resolver was serving it), then the xscript deadlines tighten
 * to {@value #LADDER_TIGHTENED_XSCRIPT_MS}ms, then fix generation closes —
 * every closure recorded in order via {@code degradedAnalyzers}, and rules
 * re-judged at their boundary: a rule whose {@code requires} intersects the
 * closed set degrades (the same counted, diagnostic-free exit as the gate).
 * Matching itself can never degrade, so the pattern-stage clock is the last
 * line of defense: once it alone exceeds the budget the breaker aborts the
 * file's remaining rules (ids recorded, file marked degraded). When both
 * clocks are over budget at a boundary the breaker wins. Fix generation is
 * profile-gated on top: the fast profile never opens it (design 11 §5 "fast
 * 本就不开", live drift corrected by item 31), standard/deep generate until
 * the ladder closes them, each skipped generation counted. The fast
 * profile's 10ms xscript slice is its ladder counterpart: once exhausted,
 * remaining matches skip the script and still report their pattern-layer
 * result — counted per match, rule ids recorded (never silent).</p>
 */
final class RuleSetRunner {

    private static final Logger LOG = LoggerFactory.getLogger(RuleSetRunner.class);

    /**
     * Consecutive script failures after which a rule is disabled for the
     * rest of the run (design 07 §3).
     */
    static final int XSCRIPT_CONSECUTIVE_FAILURE_LIMIT = 50;

    /**
     * The per-match xscript budget the engaged ladder tightens to, in
     * milliseconds (design 11 §5 ladder step: 100ms → 20ms).
     */
    static final int LADDER_TIGHTENED_XSCRIPT_MS = LintProfile.LADDER_TIGHTENED_XSCRIPT_BUDGET_MS;

    /**
     * The stats id under which the ladder records the xscript tightening.
     */
    static final String XSCRIPT_ANALYZER_ID = "xscript";

    /**
     * The stats id under which the ladder records the fix closure.
     */
    static final String FIX_ANALYZER_ID = "fix";

    /**
     * The stats id under which the ladder records the L2 resolver cutoff.
     */
    static final String L2_ANALYZER_ID = LintCapability.L2.name();

    private RuleSetRunner() {
    }

    /**
     * Runs compiled rules against one tree, accumulating stats. The caller
     * owns the rulesLoaded / rulesSkippedByProfile accounting (those happen
     * before compilation); this loop owns the kind-filtered / executed /
     * diagnostics counters, the xscript resource semantics, and the budget
     * machinery (ladder engagement, boundary re-judgment, breaker, fast
     * slice, fix gating).
     *
     * @param analyzers the run's deep-analyzer availability probes (null =
     *                  none served — the ladder then has no deep levels to
     *                  close)
     * @param l2Open    true when the run serves L2 type queries (live,
     *                  ready resolver; the ladder records its closure)
     */
    static List<Diagnostic> run(List<CompiledRule> rules, LintTree tree, LintStats.Builder stats,
                                LintProfile profile, FileBudget budget,
                                DeepResolvers deep, boolean l2Open, String filePath) {
        int[] occurringKinds = KindIndex.collect(tree.root());
        List<Diagnostic> diagnostics = new ArrayList<>();
        SourceMap sourceMap = null;
        // the fix generation ordinal: ruleset declaration order, then match
        // order — the conflict priority the Fixer merge relies on (item 25)
        int fixOrder = 0;
        boolean fixOpen = profile != LintProfile.FAST;
        boolean breakerFired = false;
        for (CompiledRule rule : rules) {
            if (breakerFired) {
                stats.addBreakerAbortedRuleId(rule.ruleId());
                continue;
            }
            if (budget.patternStageExceeded()) {
                // the circuit breaker wins over the ladder at a shared
                // boundary (plan R2 Minor A): matching cannot degrade, so
                // the remaining rules abort with their ids recorded
                breakerFired = true;
                stats.addBreakerAbortedRuleId(rule.ruleId());
                LOG.warn("nop.lint.budget.breaker:ruleId={},patternNanos={} (the pattern stage "
                                + "alone consumed the file budget; the file's remaining rules"
                                + " are aborted and the file is marked degraded)",
                        rule.ruleId(), budget.patternNanos());
                continue;
            }
            if (!budget.ladderEngaged() && budget.totalExpired()) {
                engageLadder(budget, stats, profile, deep, l2Open, fixOpen);
            }
            if (budget.ladderEngaged() && !Collections.disjoint(rule.requires(),
                    budget.closedCapabilities())) {
                // boundary re-judgment (plan Decision 5): the gate passed
                // this rule when its analyzers were live; the closure
                // revokes exactly that answer, through the same counted,
                // diagnostic-free exit
                stats.incRulesDegraded(rule.ruleId());
                LOG.warn("nop.lint.budget.rule-degraded:ruleId={},closed={}",
                        rule.ruleId(), budget.closedCapabilities());
                continue;
            }
            if (!rule.canMatchKinds(occurringKinds)) {
                stats.incRulesKindFiltered();
                continue;
            }
            stats.incRulesExecuted();
            long matchStart = budget.now();
            List<Match> matches = rule.matchWithCaptures(tree);
            budget.addPatternNanos(budget.now() - matchStart);
            if (!rule.constraints().isEmpty()) {
                try {
                    matches = applyConstraints(rule, matches, stats);
                } catch (TypeResolutionException e) {
                    // design 11 §5 degrade: a type query the run cannot
                    // answer degrades exactly this rule — counted, logged,
                    // no diagnostics from it, and never an L1-faked answer
                    // (roadmap hard constraint).
                    stats.incRulesDegraded(rule.ruleId());
                    LOG.warn("nop.lint.l2.rule-degraded:ruleId={}", rule.ruleId(), e);
                    continue;
                }
                if (matches.isEmpty()) {
                    continue;
                }
            }
            if (rule.xscriptEngine() != null) {
                if (sourceMap == null) {
                    sourceMap = new SourceMap(tree.source());
                }
                runXscriptRule(rule, matches, sourceMap, diagnostics, stats, profile, budget,
                        deep, filePath);
            } else {
                byte[] source = tree.source();
                boolean suggestionOnly = rule.fixSuggestOnly();
                // fix generation surface (design 11 §5, item 31): fast never
                // opens it; standard/deep generate until the ladder closes
                // it — each skipped generation counted
                boolean fixActive = fixOpen && !budget.fixClosed();
                for (Match match : matches) {
                    Fix fix = null;
                    if (rule.templateFix() != null && !suggestionOnly) {
                        if (fixActive) {
                            fix = new Fix(match.node().range(),
                                    rule.templateFix().apply(match.env(), source),
                                    rule.ruleId(), rule.fixDescription(), fixOrder++);
                        } else if (fixOpen) {
                            // only the ladder closure counts as a degraded
                            // generation — fast's closed-by-profile surface
                            // is not a degradation event
                            stats.incFixesDegraded(1);
                        }
                    }
                    diagnostics.add(new Diagnostic(rule.ruleId(), rule.severity(), rule.message(),
                            match.node().range(), fix));
                }
                stats.incDiagnostics(matches.size());
            }
        }
        return diagnostics;
    }

    /**
     * Engages the degrade ladder once and records every closure in ladder
     * order (design 11 §5): the deep analyzers a live probe actually
     * served, the L2 resolver when it was live, then the xscript tightening
     * and the fix closure. The budget takes the whole closure in one call;
     * the ordered stats record mirrors it.
     */
    private static void engageLadder(FileBudget budget, LintStats.Builder stats, LintProfile profile,
                                     DeepResolvers deep, boolean l2Open, boolean fixOpen) {
        Set<LintCapability> closed = EnumSet.noneOf(LintCapability.class);
        List<String> record = new ArrayList<>(4);
        if (DeepResolvers.live(deep.dataflow())) {
            closed.add(LintCapability.L3);
            record.add(LintCapability.L3.name());
        }
        if (DeepResolvers.live(deep.semantic())) {
            closed.add(LintCapability.L4);
            record.add(LintCapability.L4.name());
        }
        if (DeepResolvers.live(deep.scope())) {
            closed.add(LintCapability.SCOPE);
            record.add(LintCapability.SCOPE.name());
        }
        if (DeepResolvers.live(deep.metrics())) {
            closed.add(LintCapability.METRICS);
            record.add(LintCapability.METRICS.name());
        }
        if (l2Open) {
            closed.add(LintCapability.L2);
            record.add(L2_ANALYZER_ID);
        }
        record.add(XSCRIPT_ANALYZER_ID);
        if (fixOpen) {
            record.add(FIX_ANALYZER_ID);
        }
        budget.engageLadder(closed, true, fixOpen);
        for (String id : record) {
            stats.addDegradedAnalyzer(id);
        }
        LOG.warn("nop.lint.budget.ladder-engaged:profile={},closed={} (the file budget is "
                + "exhausted; cheaper analysis continues)", profile, record);
    }

    /**
     * The per-match constraint filter (design 03 §1.1 position: after
     * matching, before xscript). A match survives only when every constraint
     * of its rule holds; each removed match is counted. Evaluation
     * exceptions propagate — a constraint failure is a filter outcome, an
     * evaluation error is a defect and must surface (no swallowing).
     */
    private static List<Match> applyConstraints(CompiledRule rule, List<Match> matches,
                                                 LintStats.Builder stats) {
        List<Constraint> constraints = rule.constraints();
        List<Match> kept = new ArrayList<>(matches.size());
        for (Match match : matches) {
            boolean allHold = true;
            ConstraintContext ctx = new ConstraintContext(match.node(), match.env());
            for (Constraint constraint : constraints) {
                if (!constraint.holds(ctx)) {
                    allHold = false;
                    break;
                }
            }
            if (allHold) {
                kept.add(match);
            } else {
                stats.incConstraintFilteredMatches();
            }
        }
        return kept;
    }

    private static void runXscriptRule(CompiledRule rule, List<Match> matches, SourceMap sourceMap,
                                       List<Diagnostic> diagnostics, LintStats.Builder stats,
                                       LintProfile profile, FileBudget budget,
                                       DeepResolvers deep, String filePath) {
        XScriptEngine engine = rule.xscriptEngine();
        int consecutiveFailures = 0;
        int timedOut = 0;
        int processed = 0;
        for (Match match : matches) {
            if (profile == LintProfile.FAST && budget.xscriptSliceExpired()) {
                // design 11 §5 fast 预算闭合: the file's shared xscript
                // slice is gone — remaining matches skip the script and
                // still report their pattern-layer result (counted, rule
                // ids recorded; never a silent drop of the check)
                stats.incXscriptBudgetExceeded(rule.ruleId());
                diagnostics.add(new Diagnostic(rule.ruleId(), rule.severity(), rule.message(),
                        match.node().range(), null));
                stats.incDiagnostics(1);
                continue;
            }
            processed++;
            stats.incXscriptMatchesExecuted();
            // Route A (design 07 §4): every match runs under a profile- and
            // budget-scaled deadline; the globally installed
            // LintDeadlineExecutor enforces it at each executor entry.
            XScriptDeadline deadline = XScriptDeadline.startNow(engine.ruleId(),
                    xscriptBudgetMs(rule, profile, budget));
            XScriptEngine.MatchOutcome outcome;
            try {
                outcome = engine.executeMatch(match.node(), match.env(), sourceMap, deadline,
                        deep, filePath);
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
     * The effective per-match xscript budget (design 07 §3 profile scaling
     * + design 11 §5 ladder tightening, roadmap item 31): fast takes
     * min(20ms, rule value, remaining slice); standard/deep take the rule
     * value until the ladder tightens it to min(20ms, rule value). Always
     * >= 1ms (the deadline's floor; the sub-1ms slice case is diverted to
     * the skip path before this computation).
     */
    static int xscriptBudgetMs(CompiledRule rule, LintProfile profile, FileBudget budget) {
        int value = profile.xscriptBudgetMs(rule.xscriptTimeoutMs());
        if (profile == LintProfile.FAST) {
            value = Math.min(value, (int) (budget.remainingSliceNanos() / 1_000_000L));
        } else if (budget.xscriptTightened()) {
            value = Math.min(value, LADDER_TIGHTENED_XSCRIPT_MS);
        }
        return Math.max(1, value);
    }

    /**
     * The design 07 §3 timeout-rate warning rule: more than 1% of a rule's
     * matches in one run timed out. Package-visible for the boundary tests.
     */
    static boolean shouldWarnTimeoutRate(int matchesProcessed, int timedOut) {
        return matchesProcessed > 0 && timedOut * 100L > matchesProcessed;
    }
}

package io.nop.lint.core.engine;

import io.nop.core.model.object.DynamicObject;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import io.nop.lint.core.semantic.TypeResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-file budget, degrade ladder v2, circuit breaker, and fast slice
 * proofs (roadmap item 31 Phase 2, design 11 §5; plan 2026-09-24-0900-1
 * Decisions 2/3/4/5/7/8). A programmed fake clock drives every budget read,
 * so exhaustion, ladder order, breaker priority, and slice skips are all
 * deterministic; the tightened-deadline test alone rides the real clock
 * (the in-script deadline enforcement is deliberately outside the fake —
 * plan R2 Minor C) and bounds the wall time instead.
 */
public class TestBudgetDegradeLadder {

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"),
            null);

    private static final String SOURCE = "class Demo {\n"
            + "  void m() {\n"
            + "    foo.bar();\n"
            + "    System.out.println(\"a\");\n"
            + "    System.out.println(\"b\");\n"
            + "  }\n"
            + "}\n";

    private final RuleDslParser parser = new RuleDslParser();
    private final LanguageRegistry registry = LanguageRegistry.empty();
    private final FakeResolver resolver = new FakeResolver();

    public TestBudgetDegradeLadder() {
        registry.register(JAVA);
    }

    // ==================== ladder: ordered closure + boundary re-judgment ====================

    @Test
    public void ladderClosesInOrderAndReJudgesRulesAtBoundary() {
        // reads per executed rule: boundary, matchStart, matchEnd; the
        // floor rule runs healthy, then the clock jumps past the DEEP
        // budget (5s, design 11 §2)
        long t0 = 1_000_000_000_000L;
        long past = t0 + TimeUnit.MILLISECONDS.toNanos(6000);
        ProgrammedClock clock = new ProgrammedClock(
                t0,                                    // budget start (deadline t0+500ms)
                t0, t0, t0,                            // floor rule: boundary + match timing
                past,                                  // deep rule boundary: total expired
                past,                                  // l2 rule boundary
                past, past, past,                      // xscript rule: boundary + match
                past, past, past                       // fix rule: boundary + match
        );

        LintResult result = new LintEngine(registry, LintProfile.DEEP, resolver,
                cap -> cap == LintCapability.L3, null, null, clock)
                .lint(List.of(
                        patternRule("demo/floor", "$A.bar()", null),
                        patternRule("demo/deep", "$A.bar()", "L3"),
                        patternRule("demo/l2rule", "$A.bar()", "L2"),
                        xscriptRule("demo/xscript-rule"),
                        fixRule("demo/fix-rule")),
                        JAVA, "demo/S.java", SOURCE);

        LintStats stats = result.stats();
        // the ladder closure record, in ladder order (plan Decision 2: the
        // only analyzers that were actually open)
        assertEquals(List.of("L3", "L2", "xscript", "fix"), stats.getDegradedAnalyzers());
        // boundary re-judgment: capability-bearing rules degrade after closure
        assertEquals(List.of("demo/deep", "demo/l2rule"), stats.getDegradedRuleIds());
        // the floor stages keep running (design 11 §5: pattern/kind/约束永不关闭)
        assertEquals(3, stats.getRulesExecuted(), "floor + xscript + fix rules execute");
        assertEquals(2, stats.getRulesDegraded(), "deep + L2 rules degrade at their boundaries");
        assertTrue(stats.getBreakerAbortedRuleIds().isEmpty(), "pattern stage never exceeded");

        // the xscript rule still produces (tightened deadlines, not closed)
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.ruleId().equals("demo/xscript-rule")));
        // the fix rule's diagnostics survive without their fix carrier, and
        // every skipped generation is counted (plan Decision 4)
        Diagnostic fixDiagnostic = result.diagnostics().stream()
                .filter(d -> d.ruleId().equals("demo/fix-rule")).findFirst().orElseThrow();
        assertNull(fixDiagnostic.fix(), "the ladder's fix closure removes the carrier");
        assertEquals(1, stats.getFixesDegraded());
    }

    @Test
    public void ladderRecordsOnlyAnalyzersThatWereOpen() {
        long t0 = 1_000_000_000_000L;
        long past = t0 + TimeUnit.MILLISECONDS.toNanos(6000);
        // no probes wired and no resolver: nothing deep/L2 was ever open,
        // so the ladder can only close the xscript and fix levels
        ProgrammedClock clock = new ProgrammedClock(t0, past, past, past, past);

        LintResult result = new LintEngine(registry, LintProfile.DEEP, null, null, null, null, clock)
                .lint(List.of(
                        patternRule("demo/deep", "$A.bar()", "L3"),
                        patternRule("demo/floor", "$A.bar()", null)),
                        JAVA, "demo/S.java", SOURCE);

        assertEquals(List.of("xscript", "fix"), result.stats().getDegradedAnalyzers());
        // the L3 rule degraded at the gate (no probe), not at the ladder
        assertEquals(List.of("demo/deep"), result.stats().getDegradedRuleIds());
        assertEquals(1, result.stats().getRulesExecuted());
    }

    // ==================== circuit breaker: the pattern stage's last line ====================

    @Test
    public void breakerAbortsRemainingRulesWhenPatternStageAloneExceedsBudget() {
        long t0 = 1_000_000_000_000L;
        long bigMatch = t0 + TimeUnit.MILLISECONDS.toNanos(900);
        // the floor rule's matching phase alone pushes the pattern clock
        // past the budget while the total clock never expires: pure breaker
        ProgrammedClock clock = new ProgrammedClock(
                t0,                                    // budget start
                t0, t0, bigMatch,                      // floor rule: pattern delta 900ms
                t0,                                    // breaker-boundary read for rule 2
                t0                                     // breaker-boundary read for rule 3
        );

        LintResult result = new LintEngine(registry, LintProfile.STANDARD, null, null, null, null, clock)
                .lint(List.of(
                        patternRule("demo/floor", "$A.bar()", null),
                        patternRule("demo/next", "$A.bar()", null),
                        patternRule("demo/last", "$A.bar()", null)),
                        JAVA, "demo/S.java", SOURCE);

        LintStats stats = result.stats();
        assertEquals(List.of("demo/next", "demo/last"), stats.getBreakerAbortedRuleIds(),
                "the remaining rules abort with their ids recorded");
        assertTrue(stats.getDegradedAnalyzers().isEmpty(),
                "the total clock never expired: the ladder never engaged");
        assertEquals(1, stats.getRulesExecuted(), "the rule before the breaker ran normally");
        assertEquals(3, stats.getRulesKindFiltered() + stats.getRulesExecuted()
                        + stats.getRulesSkippedByProfile() + stats.getRulesDegraded()
                        + stats.getBreakerAbortedRuleIds().size(),
                "every loaded rule leaves through exactly one observable exit");
    }

    @Test
    public void breakerWinsOverLadderAtASharedBoundary() {
        long t0 = 1_000_000_000_000L;
        long past = t0 + TimeUnit.MILLISECONDS.toNanos(6000);
        long farPast = t0 + TimeUnit.MILLISECONDS.toNanos(12000);
        // rule 1's boundary expires the total clock (ladder engages), and
        // its match phase pushes the pattern clock past the budget too, so
        // rule 2's boundary holds both conditions — the breaker must win
        // (plan R2 Minor A: abort takes precedence over degrade)
        ProgrammedClock clock = new ProgrammedClock(
                t0,                                    // budget start
                past,                                  // rule 1 boundary: total expired
                t0, farPast,                           // rule 1 match: pattern delta 1200ms
                past,                                  // rule 2 boundary: both conditions hold
                past                                   // rule 3 boundary
        );

        LintResult result = new LintEngine(registry, LintProfile.DEEP, resolver,
                cap -> cap == LintCapability.L3, null, null, clock)
                .lint(List.of(
                        patternRule("demo/first", "$A.bar()", null),
                        patternRule("demo/second", "$A.bar()", "L3"),
                        patternRule("demo/third", "$A.bar()", null)),
                        JAVA, "demo/S.java", SOURCE);

        LintStats stats = result.stats();
        assertEquals(List.of("demo/second", "demo/third"), stats.getBreakerAbortedRuleIds(),
                "the breaker aborts even a capability-bearing rule at the shared boundary");
        assertFalse(stats.getDegradedRuleIds().contains("demo/second"),
                "the breaker exit takes precedence over the ladder degrade");
        assertEquals(List.of("L3", "L2", "xscript", "fix"), stats.getDegradedAnalyzers(),
                "the ladder engaged at rule 1 and its record stands");
        assertEquals(1, stats.getRulesExecuted());
    }

    // ==================== fast slice: skip + pattern-layer result ====================

    @Test
    public void fastSliceExhaustionSkipsScriptsAndReportsPatternLayer() {
        long t0 = 1_000_000_000_000L;
        long ms = TimeUnit.MILLISECONDS.toNanos(1);
        // FAST budget reads: start consumes 2 (deadline + slice deadline);
        // per executed rule: boundary + matchStart + matchEnd; per match:
        // sliceExpired + remaining (skip path reads sliceExpired only)
        ProgrammedClock clock = new ProgrammedClock(
                t0, t0,                                // budget start
                t0, t0, t0,                            // rule boundary + match timing
                t0 + 9 * ms, t0 + 9 * ms,              // match 1: 9ms of slice left
                t0 + 9 * ms + ms / 2,                  // match 2 sliceExpired: 0.5ms < 1ms
                t0 + 9 * ms + ms / 2                   // (no further reads on the skip path)
        );

        LintEngine engine = new LintEngine(registry, LintProfile.FAST, null, null, null, null, clock);
        LintResult result = engine.lint(List.of(xscriptRule("demo/slice-rule")), JAVA, "demo/S.java", SOURCE);

        LintStats stats = result.stats();
        assertEquals(1, stats.getXscriptMatchesExecuted(), "match 1 still ran its script");
        assertEquals(1, stats.getXscriptBudgetExceededMatches(), "match 2 skipped at the slice");
        assertEquals(List.of("demo/slice-rule"), stats.getXscriptBudgetExceededRuleIds());

        List<Diagnostic> diagnostics = result.diagnostics();
        assertEquals(2, diagnostics.size(),
                "the skipped match still reports its pattern-layer result (design 11 §5)");
        Diagnostic scripted = diagnostics.get(0);
        Diagnostic patternLayer = diagnostics.get(1);
        assertTrue(patternLayer.range().startByte() > scripted.range().startByte(),
                "the pattern-layer diagnostic sits on the second (skipped) match node");
        assertEquals("msg demo/slice-rule", patternLayer.message(),
                "the pattern-layer result carries the rule's static message");
    }

    @Test
    public void fastProfileNeverGeneratesFixesAndNeverCountsItAsDegradation() {
        LintEngine engine = new LintEngine(registry, LintProfile.FAST, null, null, null, null,
                new ProgrammedClock(repeating(1_000_000_000_000L)));
        LintResult result = engine.lint(List.of(fixRule("demo/fix-rule")), JAVA, "demo/S.java", SOURCE);

        assertEquals(1, result.diagnostics().size());
        assertNull(result.diagnostics().get(0).fix(),
                "design 11 §5: fast 本就不开 fix generation (live drift corrected, item 31)");
        assertEquals(0, result.stats().getFixesDegraded(),
                "a closed-by-profile surface is not a degradation event");
    }

    // ==================== tightened deadline: real wiring proof ====================

    @Test
    public void tightenedDeadlineIsReallyConsumedByXscriptExecution() {
        long t0 = 1_000_000_000_000L;
        long past = t0 + TimeUnit.MILLISECONDS.toNanos(600);
        // the deadloop rule (declared 1000ms) hits an already-exhausted
        // budget: the ladder tightens its budget to 20ms, and the in-script
        // enforcement (real clock) must abort it at ~20ms, not ~1s
        DynamicObject raw = new DynamicObject("lint-rule");
        raw.addProp("id", "demo/deadloop");
        raw.addProp("severity", "warning");
        raw.addProp("message", "never reported");
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", "System.out.println($M)");
        raw.addProp("rule", rule);
        raw.addProp("xscriptTimeoutMs", 1000);
        raw.addProp("xscript", "let i = 0; while (i >= 0) { i = i + 1; }");
        RuleDslModel deadloop = parser.parseRuleModel(raw);

        ProgrammedClock clock = new ProgrammedClock(t0, past, past, past, past);
        LintEngine engine = new LintEngine(registry, LintProfile.STANDARD, null, null, null, null, clock);

        long start = System.nanoTime();
        LintResult result = engine.lint(List.of(deadloop), JAVA, "demo/S.java", SOURCE);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertEquals(2, result.stats().getXscriptTimedOutMatches(),
                "both deadloop matches abort at the tightened deadline");
        assertTrue(elapsedMs < 500,
                "the tightened 20ms budget must be consumed (a non-tightened 1000ms "
                        + "budget aborts after ~1s); elapsed=" + elapsedMs + "ms");
        assertTrue(result.diagnostics().isEmpty(), "a timed-out match reports nothing");
    }

    // ==================== budget scope: one lint call (plan Decision 7) ====================

    @Test
    public void everyLintCallStartsWithAFreshBudget() {
        long t0 = 1_000_000_000_000L;
        long past = t0 + TimeUnit.MILLISECONDS.toNanos(6000);
        List<RuleDslModel> rules = List.of(patternRule("demo/deep", "$A.bar()", "L3"),
                patternRule("demo/floor", "$A.bar()", null));

        LintEngine engine = new LintEngine(registry, LintProfile.DEEP, resolver,
                cap -> cap == LintCapability.L3, null, null,
                new ProgrammedClock(t0, past, past, past, past));
        LintResult exhausted = engine.lint(rules, JAVA, "demo/S.java", SOURCE);
        assertEquals(4, exhausted.stats().getDegradedAnalyzers().size(),
                "the first call's budget exhausts and engages the ladder");

        LintResult fresh = new LintEngine(registry, LintProfile.DEEP, resolver,
                cap -> cap == LintCapability.L3, null, null, new ProgrammedClock(repeating(t0)))
                .lint(rules, JAVA, "demo/S.java", SOURCE);
        assertTrue(fresh.stats().getDegradedAnalyzers().isEmpty(),
                "the next lint call starts from a fresh budget (a --fix multipass "
                        + "pays a fresh budget per pass)");
        assertEquals(2, fresh.stats().getRulesExecuted(), "both rules run healthy again");
    }

    // ==================== healthy budget: nothing engages ====================

    @Test
    public void healthyBudgetLeavesNoBudgetTraces() {
        LintEngine engine = new LintEngine(registry, LintProfile.STANDARD, resolver,
                cap -> cap == LintCapability.L3, null, null,
                new ProgrammedClock(repeating(1_000_000_000_000L)));
        LintResult result = engine.lint(List.of(
                patternRule("demo/floor", "$A.bar()", null),
                xscriptRule("demo/xscript-rule")), JAVA, "demo/S.java", SOURCE);

        assertTrue(result.stats().getDegradedAnalyzers().isEmpty());
        assertTrue(result.stats().getBreakerAbortedRuleIds().isEmpty());
        assertEquals(0, result.stats().getXscriptBudgetExceededMatches());
        assertEquals(0, result.stats().getFixesDegraded());
        assertEquals(2, result.stats().getRulesExecuted());
    }

    // ==================== helpers ====================

    private static long[] repeating(long value) {
        return new long[]{value};
    }

    /**
     * A clock that returns the programmed values in order and sticks at the
     * last one — budget reads are countable per the runner's documented
     * read points, so each program pins the exact boundary behavior.
     */
    private static final class ProgrammedClock implements LongSupplier {
        private final long[] values;
        private int index;

        ProgrammedClock(long... values) {
            this.values = values;
        }

        @Override
        public long getAsLong() {
            long value = values[Math.min(index, values.length - 1)];
            index++;
            return value;
        }
    }

    private RuleDslModel patternRule(String id, String pattern, String requires) {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", id);
        model.addProp("severity", "warning");
        model.addProp("message", "msg " + id);
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", pattern);
        model.addProp("rule", rule);
        if (requires != null) {
            model.addProp("requires", requires);
        }
        return parser.parseRuleModel(model);
    }

    private RuleDslModel xscriptRule(String id) {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", id);
        model.addProp("severity", "warning");
        model.addProp("message", "msg " + id);
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", "System.out.println($M)");
        model.addProp("rule", rule);
        model.addProp("xscript", "report({ message: 'hit', severity: 'warning' });");
        return parser.parseRuleModel(model);
    }

    private RuleDslModel fixRule(String id) {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", id);
        model.addProp("severity", "warning");
        model.addProp("message", "msg " + id);
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", "$A.bar()");
        model.addProp("rule", rule);
        DynamicObject fix = new DynamicObject("fix");
        fix.addProp("description", "rewrite");
        fix.addProp("template", "$A");
        fix.addProp("suggest", false);
        model.addProp("fix", fix);
        return parser.parseRuleModel(model);
    }

    /**
     * Minimal live resolver: never queried by these rules — its presence
     * alone decides the gate's and the ladder's L2 open state.
     */
    private static final class FakeResolver implements TypeResolver {

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void initProject(java.nio.file.Path tsConfigPath) {
            throw new UnsupportedOperationException("not queried by these tests");
        }

        @Override
        public boolean isAssignableTo(String filePath, int line, int column, String expectedType) {
            throw new UnsupportedOperationException("not queried by these tests");
        }

        @Override
        public String typeNameAt(String filePath, int line, int col) {
            throw new UnsupportedOperationException("not queried by these tests");
        }
    }
}

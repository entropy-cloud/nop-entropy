package io.nop.lint.core.bench;

import io.nop.core.model.object.DynamicObject;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.pattern.Match;
import io.nop.lint.core.pattern.SourcePattern;
import io.nop.lint.core.pattern.SourcePatternCompiler;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Baseline benchmarks for the pattern kernel (roadmap item 13) and the rule
 * engine (roadmap item 31): rule-set compilation (cold path), full-tree
 * matching (hot path), the end-to-end parse+match flow, and the full engine
 * pipeline (gate → match → constraints → xscript → suppression tail). All
 * numbers land in nop-lint/docs/perf-baseline.md via the runner in this
 * package.
 */
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class LintBenchmarks {

    private static final String[] RULE_PATTERNS = {
            "throw new RuntimeException($$$ARGS)",
            "$OBJ.dao().$METHOD($$$ARGS)",
            "class $C extends CrudBizModel { $$$ }",
    };

    private SourcePattern[] patterns;
    private LintTree tree;
    private LintEngine engine;
    private List<RuleDslModel> engineRules;

    @Setup
    public void setup() {
        patterns = new SourcePattern[RULE_PATTERNS.length];
        for (int i = 0; i < RULE_PATTERNS.length; i++) {
            patterns[i] = SourcePatternCompiler.compile(RULE_PATTERNS[i], BenchLanguage.get());
        }
        tree = BenchLanguage.get().parse(BenchCorpus.JAVA_SOURCE);

        RuleDslParser parser = new RuleDslParser();
        engineRules = List.of(
                patternRule(parser, "bench/throw-raw", RULE_PATTERNS[0]),
                patternRule(parser, "bench/dao-access", RULE_PATTERNS[1]),
                patternRule(parser, "bench/crud-extends", RULE_PATTERNS[2]));
        LanguageRegistry registry = LanguageRegistry.empty();
        registry.register(BenchLanguage.get());
        engine = new LintEngine(registry, LintProfile.STANDARD);
    }

    private static RuleDslModel patternRule(RuleDslParser parser, String id, String pattern) {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", id);
        model.addProp("severity", "warning");
        model.addProp("message", "msg " + id);
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", pattern);
        model.addProp("rule", rule);
        return parser.parseRuleModel(model);
    }

    /**
     * Cold-path reference: compiling the full baseline rule set once.
     */
    @Benchmark
    public SourcePattern[] compileRuleSet() {
        SourcePattern[] compiled = new SourcePattern[RULE_PATTERNS.length];
        for (int i = 0; i < RULE_PATTERNS.length; i++) {
            compiled[i] = SourcePatternCompiler.compile(RULE_PATTERNS[i], BenchLanguage.get());
        }
        return compiled;
    }

    /**
     * Hot path: all baseline patterns over one parsed compilation unit.
     */
    @Benchmark
    public int matchAllPatterns() {
        int hits = 0;
        for (SourcePattern pattern : patterns) {
            hits += pattern.matchIn(tree.root()).size();
        }
        return hits;
    }

    /**
     * End-to-end lint of one file: parse + match. The per-op time is the
     * ms/file figure compared against the design 11 §6 budget.
     */
    @Benchmark
    public int parseAndMatch() {
        LintTree fresh = BenchLanguage.get().parse(BenchCorpus.JAVA_SOURCE);
        LintNode root = fresh.root();
        int hits = 0;
        for (SourcePattern pattern : patterns) {
            List<Match> matches = pattern.matchIn(root);
            hits += matches.size();
        }
        return hits;
    }

    /**
     * The full engine pipeline over one file (roadmap item 31's performance
     * gate): gate → kind filter → matching → budget boundary checks →
     * suppression tail → stats. The per-op time is the engine-level ms/file
     * figure the budget machinery must not move.
     */
    @Benchmark
    public int engineLint() {
        LintResult result = engine.lint(engineRules, "java", "bench/OrderService.java",
                BenchCorpus.JAVA_SOURCE);
        return result.diagnostics().size();
    }
}

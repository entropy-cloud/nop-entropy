package io.nop.lint.core.bench;

import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.pattern.Match;
import io.nop.lint.core.pattern.SourcePattern;
import io.nop.lint.core.pattern.SourcePatternCompiler;
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
 * Baseline benchmarks for the pattern kernel (roadmap item 13): rule-set
 * compilation (cold path), full-tree matching (hot path), and the end-to-end
 * parse+lint flow. All numbers land in nop-lint/docs/perf-baseline.md via the
 * runner in this package.
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

    @Setup
    public void setup() {
        patterns = new SourcePattern[RULE_PATTERNS.length];
        for (int i = 0; i < RULE_PATTERNS.length; i++) {
            patterns[i] = SourcePatternCompiler.compile(RULE_PATTERNS[i], BenchLanguage.get());
        }
        tree = BenchLanguage.get().parse(BenchCorpus.JAVA_SOURCE);
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
}

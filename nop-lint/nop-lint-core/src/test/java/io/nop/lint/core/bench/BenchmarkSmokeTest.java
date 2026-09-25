package io.nop.lint.core.bench;

import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.pattern.Match;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Anti-rot guard for the benchmark suite (mirrors the nop-treesitter smoke
 * discipline): run every benchmark in-process with tiny iterations and assert
 * the runner saw them all — plus a real-match assertion so the corpus can
 * never silently stop matching.
 */
class BenchmarkSmokeTest {

    @Test
    void allBenchmarksRunAndCorpusMatches() throws RunnerException {
        assertCorpusMatches();

        Options options = new OptionsBuilder()
                .include(LintBenchmarks.class.getSimpleName())
                .warmupIterations(1)
                .warmupTime(TimeValue.milliseconds(100))
                .measurementIterations(1)
                .measurementTime(TimeValue.milliseconds(100))
                .forks(0)
                .shouldFailOnError(true)
                .build();
        Collection<org.openjdk.jmh.results.RunResult> results = new Runner(options).run();
        assertTrue(results.size() >= 8, "all benchmarks (4 baseline + 2 large-corpus + 2 site micros)"
                + " must run, got " + results.size());
    }

    private void assertCorpusMatches() {
        var pattern = io.nop.lint.core.pattern.SourcePatternCompiler.compile(
                "throw new RuntimeException($$$ARGS)", BenchLanguage.get());
        LintNode root = BenchLanguage.get().parse(BenchCorpus.JAVA_SOURCE).root();
        int hits = 0;
        for (Match m : pattern.matchIn(root)) {
            hits++;
        }
        assertTrue(hits >= 2, "corpus must contain matchable throws, got " + hits);
    }
}

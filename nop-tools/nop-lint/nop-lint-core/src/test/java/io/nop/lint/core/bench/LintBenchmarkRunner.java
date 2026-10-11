package io.nop.lint.core.bench;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

/**
 * Full baseline run — launch from the repo root so the result file lands in
 * the repo-root {@code _tmp/}: {@code nop-lint/bench/run-benchmarks.sh}.
 */
public class LintBenchmarkRunner {

    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
                .include(LintBenchmarks.class.getSimpleName())
                .warmupIterations(3)
                .warmupTime(TimeValue.seconds(1))
                .measurementIterations(5)
                .measurementTime(TimeValue.seconds(1))
                .forks(1)
                .addProfiler("gc")
                .result("_tmp/lint-bench-result.txt")
                .resultFormat(ResultFormatType.TEXT)
                .shouldFailOnError(true)
                .build();
        new Runner(options).run();
    }
}

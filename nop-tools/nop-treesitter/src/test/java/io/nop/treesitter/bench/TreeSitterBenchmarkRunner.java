package io.nop.treesitter.bench;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

/**
 * Full benchmark run — launch from the repo root so the result file lands in
 * the repo-root {@code _tmp/}: {@code java -cp <test classpath>
 * io.nop.treesitter.bench.TreeSitterBenchmarkRunner} (see
 * bench/run-c-reference.sh for the classpath recipe).
 */
public class TreeSitterBenchmarkRunner {

    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
                .include(JsonParseBenchmark.class.getSimpleName())
                .include(JavaParseBenchmark.class.getSimpleName())
                .warmupIterations(3)
                .warmupTime(TimeValue.seconds(1))
                .measurementIterations(5)
                .measurementTime(TimeValue.seconds(1))
                .forks(1)
                .addProfiler("gc")
                .result("_tmp/bench-result.txt")
                .resultFormat(ResultFormatType.TEXT)
                .shouldFailOnError(true)
                .build();
        new Runner(options).run();
    }
}

package io.nop.treesitter.bench;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

/**
 * Launch from the repo root with the test classpath (see
 * bench/run-c-reference.sh's classpath recipe for the module + deps layout).
 */
public class TreeSitterJniComparisonRunner {

    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
                .include(JniVsPureBenchmark.class.getSimpleName())
                .warmupIterations(3)
                .warmupTime(TimeValue.seconds(1))
                .measurementIterations(5)
                .measurementTime(TimeValue.seconds(1))
                .forks(1)
                .addProfiler("gc")
                .result("_tmp/jni-vs-pure-result.txt")
                .resultFormat(ResultFormatType.TEXT)
                .shouldFailOnError(true)
                .build();
        new Runner(options).run();
    }
}

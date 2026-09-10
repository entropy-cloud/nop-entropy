package io.nop.treesitter.bench;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves every @Benchmark method executes (JMH swallows benchmark exceptions
 * unless failOnError is set — the 5-result assertion is the anti-hollow check).
 * Not a numbers test: suite-parallel timing is meaningless.
 */
class TreeSitterBenchmarkSmokeTest {

    @Test
    void everyBenchmarkMethodExecutes() throws Exception {
        Options options = new OptionsBuilder()
                .include(JsonParseBenchmark.class.getSimpleName())
                .include(JavaParseBenchmark.class.getSimpleName())
                .warmupIterations(1)
                .warmupTime(TimeValue.milliseconds(100))
                .measurementIterations(1)
                .measurementTime(TimeValue.milliseconds(100))
                .forks(0)
                .shouldFailOnError(true)
                .build();
        assertEquals(5, new Runner(options).run().size(),
                "5 benchmark methods across JsonParseBenchmark and JavaParseBenchmark");
    }
}

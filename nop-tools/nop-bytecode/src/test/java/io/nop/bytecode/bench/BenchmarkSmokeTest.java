package io.nop.bytecode.bench;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bench smoke test: the *Benchmark classes are not picked up by surefire, so without this
 * run a broken benchmark or corpus would never be noticed (nop-lint BenchmarkSmokeTest
 * precedent). Runs one benchmark with minimal iterations and asserts results came back.
 */
class BenchmarkSmokeTest {

    @Test
    void benchSmokeRunsAndProducesResults() throws Exception {
        Options opt = new OptionsBuilder()
                .include(NullflowKernelBenchmark.class.getSimpleName() + ".runNullflow")
                .warmupIterations(0)
                .measurementIterations(1)
                .measurementTime(TimeValue.milliseconds(200))
                .forks(0) // in-JVM for speed; the real baseline (fork 1) is run via the documented command
                .build();
        Collection<RunResult> results = new Runner(opt).run();
        assertFalse(results.isEmpty(), "smoke run produced results");
        results.forEach(r -> assertTrue(r.getPrimaryResult().getScore() > 0));
        // also assert the Main entry point (used by the documented repro command) is loadable
        assertTrue(Main.class.getClassLoader() != null);
    }
}

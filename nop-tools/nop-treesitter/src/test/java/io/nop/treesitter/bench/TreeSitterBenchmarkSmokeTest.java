package io.nop.treesitter.bench;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.language.Language;
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

    /**
     * The fixtures must parse without recovery nodes — both runtimes consume
     * the same bytes, and a malformed fixture would poison the numbers.
     */
    @Test
    void benchSourcesAreWellFormed() {
        Language json = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");
        Language java = Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin");
        assertWellFormed(json, BenchSources.jsonSource(10 * 1024));
        assertWellFormed(json, BenchSources.jsonSource(100 * 1024));
        assertWellFormed(json, BenchSources.jsonSource(1024 * 1024));
        assertWellFormed(java, BenchSources.javaSingleFile());
        for (byte[] file : BenchSources.javaProjectFiles()) {
            assertWellFormed(java, file);
        }
    }

    private void assertWellFormed(Language language, byte[] source) {
        String sexp = TSParser.parse(language, source).toSexpString(false);
        org.junit.jupiter.api.Assertions.assertFalse(sexp.contains("(ERROR")
                && sexp.contains("(MISSING"), "fixture must parse without recovery nodes");
    }
}

package io.nop.lint.graphql;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * The GraphQL fast-profile latency benchmark (roadmap item 43, design 11
 * §2/§6): {@code Lint__checkSource} against the design's <100 ms budget.
 * Scope = the biz method called directly with the real rule library and the
 * real engine (the GraphQLEngine query-parsing face is a thin wrapper and
 * is excluded from the measured window — recorded in perf-baseline.md).
 * The fork JVM MUST initialize the platform in setup: unlike the core bench
 * (which uses a self-contained bench language), the real service needs the
 * VFS and the ServiceLoader.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class GraphQlLintBenchmark {

    NopLintBizModel service;
    String source;

    @Setup(Level.Trial)
    public void setup() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        service = new NopLintBizModel();
        StringBuilder sb = new StringBuilder("package demo;\n\nclass OrderService {\n");
        for (int i = 0; i < 30; i++) {
            sb.append("    void method").append(i)
                    .append("(String a, String b) {\n        System.out.println(a + b);\n    }\n\n");
        }
        sb.append("}\n");
        source = sb.toString();
        // warm the lazy singleton (engine + rule set load once)
        service.checkSource(source, "java", null, null);
    }

    @Benchmark
    public LintCheckResult graphqlCheckSource() {
        return service.checkSource(source, "java", null, null);
    }
}

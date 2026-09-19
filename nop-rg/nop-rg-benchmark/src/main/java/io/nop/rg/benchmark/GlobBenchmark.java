package io.nop.rg.benchmark;

import io.nop.rg.core.glob.CompiledGlob;
import io.nop.rg.core.glob.GlobMatcher;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Glob 匹配吞吐（plan 2265 JMH-03）。
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
public class GlobBenchmark {

    private String[] paths;
    private CompiledGlob single;
    private GlobMatcher set;

    @Setup
    public void setup() {
        paths = new String[2000];
        String[] dirs = {"src", "src/main", "src/test", "docs", "lib/core"};
        String[] exts = {".java", ".md", ".txt", ".xml"};
        for (int i = 0; i < paths.length; i++) {
            paths[i] = dirs[i % dirs.length] + "/File" + i + exts[i % exts.length];
        }
        single = CompiledGlob.compile("*.java");
        set = GlobMatcher.of(List.of("**/*.java", "**/*.md", "!**/File7*"));
    }

    @Benchmark
    public void singleGlob(Blackhole bh) {
        int count = 0;
        for (String path : paths) {
            if (single.matches(path)) {
                count++;
            }
        }
        bh.consume(count);
    }

    @Benchmark
    public void globSet(Blackhole bh) {
        int count = 0;
        for (String path : paths) {
            if (set.accept(path)) {
                count++;
            }
        }
        bh.consume(count);
    }
}

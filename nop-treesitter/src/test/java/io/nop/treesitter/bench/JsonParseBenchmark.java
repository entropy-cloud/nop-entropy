package io.nop.treesitter.bench;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
public class JsonParseBenchmark {

    Language json;
    byte[] source10K;
    byte[] source100K;
    byte[] source1M;

    @Setup
    public void setup() {
        json = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");
        source10K = BenchSources.jsonSource(10 * 1024);
        source100K = BenchSources.jsonSource(100 * 1024);
        source1M = BenchSources.jsonSource(1024 * 1024);
    }

    @Benchmark
    public void parse10K(Blackhole blackhole) {
        TSTree tree = TSParser.parse(json, source10K);
        blackhole.consume(tree.toSexpString(false).length());
    }

    @Benchmark
    public void parse100K(Blackhole blackhole) {
        TSTree tree = TSParser.parse(json, source100K);
        blackhole.consume(tree.toSexpString(false).length());
    }

    @Benchmark
    public void parse1M(Blackhole blackhole) {
        TSTree tree = TSParser.parse(json, source1M);
        blackhole.consume(tree.toSexpString(false).length());
    }
}

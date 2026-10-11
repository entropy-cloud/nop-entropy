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
public class JavaParseBenchmark {

    Language java;
    byte[] singleFile;
    byte[][] projectFiles;

    @Setup
    public void setup() {
        java = Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin");
        singleFile = BenchSources.javaSingleFile();
        projectFiles = BenchSources.javaProjectFiles();
    }

    @Benchmark
    public void parseSingleFile(Blackhole blackhole) {
        TSTree tree = TSParser.parse(java, singleFile);
        blackhole.consume(tree.toSexpString(false).length());
    }

    @Benchmark
    public void parseProject25Files(Blackhole blackhole) {
        long chars = 0;
        for (byte[] file : projectFiles) {
            TSTree tree = TSParser.parse(java, file);
            chars += tree.toSexpString(false).length();
        }
        blackhole.consume(chars);
    }
}

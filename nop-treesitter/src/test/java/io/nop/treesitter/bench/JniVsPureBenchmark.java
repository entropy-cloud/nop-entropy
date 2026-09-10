package io.nop.treesitter.bench;

import io.nop.treesitter.compat.TSNode;
import io.nop.treesitter.compat.TSParser;
import io.nop.treesitter.compat.TSTree;
import io.nop.treesitter.compat.TreeSitterTypescript;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Answers "why is the JNI embedded runtime faster, and by how much" with JMH
 * rigor: identical parse + full-tree-walk work through the legacy JNI API and
 * this module's compat API on the same source. Run via
 * {@code TreeSitterJniComparisonRunner} (system-property gated out of the
 * normal suite).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
public class JniVsPureBenchmark {

    private String source;

    @Setup
    public void setup() {
        StringBuilder sb = new StringBuilder(16 * 1024);
        sb.append("export class GeneratedService {\n")
                .append("    private readonly cache = new Map<string, number>();\n");
        for (int i = 0; i < 60; i++) {
            sb.append("    async load").append(i)
                    .append("<T extends object>(key: string): Promise<T | undefined> {\n")
                    .append("        return this.cache.get(key) as T;\n")
                    .append("    }\n");
        }
        sb.append("}\n");
        source = sb.toString();
    }

    @Benchmark
    public void jniParseAndWalk(Blackhole blackhole) {
        org.treesitter.TSParser parser = new org.treesitter.TSParser();
        parser.setLanguage(new org.treesitter.TreeSitterTypescript());
        org.treesitter.TSTree tree = parser.parseString(null, source);
        blackhole.consume(walkJni(tree.getRootNode()));
    }

    @Benchmark
    public void pureParseAndWalk(Blackhole blackhole) {
        TSParser parser = new TSParser();
        parser.setLanguage(new TreeSitterTypescript());
        TSTree tree = parser.parseString(null, source);
        TSNode root = tree.getRootNode();
        blackhole.consume(walkCompat(root));
    }

    private long walkJni(org.treesitter.TSNode node) {
        long chars = node.getType().length();
        for (int i = 0; i < node.getChildCount(); i++) {
            chars += walkJni(node.getChild(i));
        }
        return chars;
    }

    private long walkCompat(TSNode node) {
        long chars = node.getType().length();
        for (int i = 0; i < node.getChildCount(); i++) {
            chars += walkCompat(node.getChild(i));
        }
        return chars;
    }
}

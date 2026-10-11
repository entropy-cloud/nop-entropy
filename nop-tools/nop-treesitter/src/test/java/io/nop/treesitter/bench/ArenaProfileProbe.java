package io.nop.treesitter.bench;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;

/**
 * PERF-04 arena profile: nodes allocated per parse for each fixture size.
 * Run via the same classpath recipe as TreeSitterBenchmarkRunner.
 */
public class ArenaProfileProbe {

    public static void main(String[] args) {
        Language json = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");
        Language java = Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin");
        profile("json-10k", json, BenchSources.jsonSource(10 * 1024));
        profile("json-100k", json, BenchSources.jsonSource(100 * 1024));
        profile("json-1m", json, BenchSources.jsonSource(1024 * 1024));
        profile("java-single", java, BenchSources.javaSingleFile());
    }

    private static void profile(String name, Language language, byte[] source) {
        long before = usedMemory();
        TSTree tree = TSParser.parse(language, source);
        long after = usedMemory();
        int nodes = tree.arena().size();
        System.out.println(name + ": source=" + source.length + "B arenaNodes=" + nodes
                + " bytesPerNode≈" + (source.length / (double) nodes)
                + " heapDelta≈" + ((after - before) / 1024 / 1024) + "MB");
    }

    private static long usedMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }
}

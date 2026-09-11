package io.nop.treesitter.corpus;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class ScratchStatsTest {

    @Test
    void stats() throws Exception {
        System.setProperty("ts.stats", "true");
        Language l = Language.fromClasspath("/grammars/typescript/tree-sitter-typescript-blob.bin");
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
        byte[] src = sb.toString().getBytes(StandardCharsets.UTF_8);
        System.out.println("SRC bytes=" + src.length);
        TSTree tree = TSParser.parse(l, src);
        var arena = tree.arena();
        int cap = arena.capacity();
        int live = 0;
        for (int i = 0; i < cap; i++) {
            if (arena.isLive(i)) {
                live++;
            }
        }
        long colBytes = cap * (15L * 4 + 2) + cap * 8L;
        long recordBytes = cap * 80L;
        System.out.println("ARENA size=" + arena.size() + " cap=" + cap + " live=" + live
                + " dead=" + (arena.size() - live)
                + " colBytesFinal~" + colBytes + " recordCache~" + recordBytes
                + " totalFinal~" + (colBytes + recordBytes) + " withDoubling~" + 2 * (colBytes + recordBytes));
    }
}

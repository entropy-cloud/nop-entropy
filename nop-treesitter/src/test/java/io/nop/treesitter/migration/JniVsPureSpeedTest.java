package io.nop.treesitter.migration;

import io.nop.treesitter.compat.TSNode;
import io.nop.treesitter.compat.TSParser;
import io.nop.treesitter.compat.TSTree;
import io.nop.treesitter.compat.TreeSitterTypescript;
import org.junit.jupiter.api.EnabledIfSystemProperty;
import org.junit.jupiter.api.Test;

/**
 * Speed comparison (user-requested): legacy JNI embedded tree-sitter vs the
 * pure-Java runtime through the compat layer, identical work per op
 * (parse + full tree walk). No speed assertions — prints measured numbers.
 */
class JniVsPureSpeedTest {

    private static final String SOURCE = buildSource();

    private static String buildSource() {
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
        return sb.toString();
    }

    @Test
    @EnabledIfSystemProperty(named = "ts.speed", matches = "true")
    void measureLegacyJni() {
        long parsed = 0;
        org.treesitter.TSParser parser = new org.treesitter.TSParser();
        parser.setLanguage(new org.treesitter.TreeSitterTypescript());
        long start = System.nanoTime();
        int warmup = 200;
        int measured = 1000;
        for (int i = 0; i < warmup + measured; i++) {
            org.treesitter.TSTree tree = parser.parseString(null, SOURCE);
            parsed += walkJni(tree.getRootNode());

            if (i == warmup - 1) {
                start = System.nanoTime();
                parsed = 0;
            }
        }
        long elapsed = System.nanoTime() - start;
        report("JNI", parsed, elapsed);
    }

    @Test
    @EnabledIfSystemProperty(named = "ts.speed", matches = "true")
    void measurePureJavaCompat() {
        long parsed = 0;
        TSParser parser = new TSParser();
        parser.setLanguage(new TreeSitterTypescript());
        long start = System.nanoTime();
        int warmup = 200;
        int measured = 1000;
        for (int i = 0; i < warmup + measured; i++) {
            TSTree tree = parser.parseString(null, SOURCE);
            parsed += walkCompat(tree.getRootNode());
            if (i == warmup - 1) {
                start = System.nanoTime();
                parsed = 0;
            }
        }
        long elapsed = System.nanoTime() - start;
        report("pure", parsed, elapsed);
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

    private void report(String side, long parsed, long elapsedNanos) {
        double seconds = elapsedNanos / 1e9;
        double opsPerSec = parsed / seconds;
        double kbPerSec = parsed * SOURCE.getBytes().length / 1024.0 / seconds;
        System.out.printf("[speed] %s: %d parses in %.2fs -> %.0f ops/s, %.0f KB/s (source %d bytes)%n",
                side, parsed, seconds, opsPerSec, kbPerSec, SOURCE.getBytes().length);
    }
}

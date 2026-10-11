package io.nop.treesitter.bench;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.compat.TSNode;
import io.nop.treesitter.compat.TreeSitterTypescript;
import io.nop.treesitter.language.Language;

import java.lang.management.ManagementFactory;

/**
 * ThreadMXBean allocation probe for the perf-closure plan gates. Prints
 * bytes/op for: json-100k parse-only, json-100k parse+render, TS-8KB walk over
 * a pre-parsed tree. Optional arg selects one workload: {@code parse},
 * {@code walk} (default runs both). Run via the test classpath (see
 * run-c-reference.sh).
 */
public class AllocProbe {

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "all";
        com.sun.management.ThreadMXBean bean =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().getId();

        if (mode.equals("parse") || mode.equals("all")) {
            jsonProbe(bean, tid);
        }
        if (mode.equals("walk") || mode.equals("all")) {
            walkProbe(bean, tid);
        }
    }

    private static void jsonProbe(com.sun.management.ThreadMXBean bean, long tid) {
        Language json = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");
        byte[] json100k = BenchSources.jsonSource(100 * 1024);

        for (int i = 0; i < 40; i++) {
            TSTree t = TSParser.parse(json, json100k);
            t.toSexpString(false);
        }

        int n = 40;
        long b = bean.getThreadAllocatedBytes(tid);
        for (int i = 0; i < n; i++) {
            TSTree t = TSParser.parse(json, json100k);
            if (t.toSexpString(false).isEmpty()) {
                throw new AssertionError();
            }
        }
        long parseRender = (bean.getThreadAllocatedBytes(tid) - b) / n;

        b = bean.getThreadAllocatedBytes(tid);
        for (int i = 0; i < n; i++) {
            TSParser.parse(json, json100k);
        }
        long parseOnly = (bean.getThreadAllocatedBytes(tid) - b) / n;

        System.out.println("json-100k parse-only   : " + parseOnly / 1024 + " KB/op ("
                + parseOnly + " B)");
        System.out.println("json-100k parse+render : " + parseRender / 1024 + " KB/op ("
                + parseRender + " B); render share "
                + (parseRender - parseOnly) / 1024 + " KB/op");
    }

    private static void walkProbe(com.sun.management.ThreadMXBean bean, long tid) {
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
        String tsSource = sb.toString();

        io.nop.treesitter.compat.TSParser parser = new io.nop.treesitter.compat.TSParser();
        parser.setLanguage(new TreeSitterTypescript());
        io.nop.treesitter.compat.TSTree tsTree = parser.parseString(null, tsSource);
        TSNode root = tsTree.getRootNode();

        for (int i = 0; i < 200; i++) {
            walk(root);
        }
        int wn = 200;
        long b = bean.getThreadAllocatedBytes(tid);
        for (int i = 0; i < wn; i++) {
            walk(root);
        }
        long walkBytes = (bean.getThreadAllocatedBytes(tid) - b) / wn;

        System.out.println("ts-8kb walk            : " + walkBytes / 1024 + " KB/op ("
                + walkBytes + " B)");
    }

    private static long walk(TSNode node) {
        long chars = node.getType().length();
        for (int i = 0; i < node.getChildCount(); i++) {
            chars += walk(node.getChild(i));
        }
        return chars;
    }
}

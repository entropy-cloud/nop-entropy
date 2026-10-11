package io.nop.treesitter.bench;

import java.util.Random;

/**
 * Deterministic benchmark fixtures: the same bytes feed the JMH benchmarks
 * and, via {@link BenchSourcesDump}, the C reference harness.
 */
public final class BenchSources {

    private static final String[] WORDS = {"alpha", "beta", "gamma", "delta", "epsilon"};

    private BenchSources() {
    }

    /**
     * Nested JSON (objects / arrays / strings with escapes / numbers), grown
     * past {@code targetBytes} and truncated at the last complete top-level
     * value boundary.
     */
    public static byte[] jsonSource(int targetBytes) {
        Random random = new Random(0x5EED);
        StringBuilder sb = new StringBuilder(targetBytes + 1024);
        while (sb.length() < targetBytes) {
            appendValue(sb, random, 0);
            sb.append('\n');
        }
        String text = sb.toString();
        int cut = text.lastIndexOf('\n', Math.min(text.length(), targetBytes + 2048));
        String truncated = cut > 0 ? text.substring(0, cut) : text;
        return truncated.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void appendValue(StringBuilder sb, Random random, int depth) {
        int kind = random.nextInt(depth < 3 ? 8 : 10);
        switch (kind) {
            case 0, 1 -> appendObject(sb, random, depth);
            case 2 -> appendArray(sb, random, depth);
            case 3, 4, 5 -> appendString(sb, random);
            default -> sb.append(random.nextInt(2) == 0 ? random.nextInt(100000) : random.nextDouble());
        }
    }

    private static void appendObject(StringBuilder sb, Random random, int depth) {
        sb.append('{');
        int fields = 1 + random.nextInt(5);
        for (int i = 0; i < fields; i++) {
            if (i > 0) {
                sb.append(',');
            }
            appendString(sb, random);
            sb.append(':');
            appendValue(sb, random, depth + 1);
        }
        sb.append('}');
    }

    private static void appendArray(StringBuilder sb, Random random, int depth) {
        sb.append('[');
        int items = 1 + random.nextInt(6);
        for (int i = 0; i < items; i++) {
            if (i > 0) {
                sb.append(',');
            }
            appendValue(sb, random, depth + 1);
        }
        sb.append(']');
    }

    private static void appendString(StringBuilder sb, Random random) {
        sb.append('"');
        int words = 1 + random.nextInt(4);
        for (int i = 0; i < words; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(WORDS[random.nextInt(WORDS.length)]);
            if (random.nextInt(8) == 0) {
                sb.append("\\n");
            }
        }
        sb.append('"');
    }

    public static byte[] javaSingleFile() {
        StringBuilder sb = new StringBuilder(12 * 1024);
        sb.append("package com.example.bench;\n\npublic class GeneratedService {\n");
        for (int i = 0; i < 40; i++) {
            sb.append("    private int field").append(i).append(" = ").append(i).append(";\n");
        }
        for (int i = 0; i < 40; i++) {
            sb.append("    public int method").append(i).append("(int arg) {\n")
                    .append("        int local = arg + field").append(i % 40).append(";\n")
                    .append("        for (int j = 0; j < 10; j++) {\n")
                    .append("            local += j * ").append(i).append(";\n")
                    .append("        }\n")
                    .append("        return local;\n")
                    .append("    }\n");
        }
        sb.append("}\n");
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public static byte[][] javaProjectFiles() {
        byte[][] files = new byte[25][];
        for (int f = 0; f < 25; f++) {
            StringBuilder sb = new StringBuilder(4 * 1024);
            sb.append("package com.example.bench.p").append(f).append(";\n\n")
                    .append("public class ProjectClass").append(f).append(" {\n");
            for (int i = 0; i < 15; i++) {
                sb.append("    public String render").append(i).append("(String input) {\n")
                        .append("        return input + \"-").append(f).append('-').append(i).append("\";\n")
                        .append("    }\n");
            }
            sb.append("}\n");
            files[f] = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return files;
    }
}

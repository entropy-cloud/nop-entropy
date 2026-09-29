package io.nop.bytecode.cli;

import java.io.PrintStream;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic finding renderer: console (one line per finding) and JSON (hand-escaped, no
 * external dependency). Both forms use the same ordering — className → methodName → insnIndex
 * → ruleId (tie-break) — so consumers see identical order across formats.
 */
public final class FindingRenderer {

    private static final Comparator<Finding> ORDER = Comparator.comparing(Finding::className)
            .thenComparing(Finding::methodName)
            .thenComparingInt(Finding::insnIndex)
            .thenComparing(Finding::ruleId)
            .thenComparing(Finding::ref);

    private FindingRenderer() { }

    public static List<Finding> sorted(List<Finding> findings) {
        return findings.stream().sorted(ORDER).toList();
    }

    public static void renderConsole(List<Finding> findings, PrintStream out) {
        for (Finding f : sorted(findings)) {
            out.println("[" + f.severity() + "] " + f.ruleId() + " " + f.location()
                    + " (" + f.ref() + ") " + f.message());
        }
    }

    public static void renderJson(List<Finding> findings, PrintStream out) {
        out.println("[");
        List<Finding> sorted = sorted(findings);
        for (int i = 0; i < sorted.size(); i++) {
            Finding f = sorted.get(i);
            out.println("  {\"ruleId\": \"" + escape(f.ruleId()) + "\", \"severity\": \"" + escape(f.severity())
                    + "\", \"message\": \"" + escape(f.message()) + "\", \"className\": \"" + escape(f.className())
                    + "\", \"methodName\": \"" + escape(f.methodName()) + "\", \"insnIndex\": " + f.insnIndex()
                    + ", \"ref\": \"" + escape(f.ref()) + "\"}" + (i + 1 < sorted.size() ? "," : ""));
        }
        out.println("]");
    }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}

package io.nop.lint.core.cli;

import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.node.LineIndex;

import java.io.IOException;
import java.io.Writer;

/**
 * The four machine-readable report formats (roadmap item 39, design 03
 * §2.4; plan Decision 1/4): each renders the diagnostic stream only — no
 * dry-run diffs, no suggestion annotations — with deterministic ordering
 * (files in scan order, diagnostics in engine order). v1 SARIF is the
 * minimal 2.1.0 compliant face (runs/results, per-rule severity mapping
 * error→error / warning→warning / info+hint→note), not the full schema.
 */
public final class MachineReporters {

    private MachineReporters() {
    }

    /**
     * SARIF 2.1.0, minimal compliant surface.
     */
    public static final class Sarif implements Reporter {
        @Override
        public void render(CheckOutcome outcome, Writer out) throws IOException {
            out.write("{\n  \"version\": \"2.1.0\",\n");
            out.write("  \"$schema\": \"https://json.schemastore.org/sarif-2.1.0.json\",\n");
            out.write("  \"runs\": [{\n    \"tool\": {\"driver\": {\"name\": \"nop-lint\",\n");
            out.write("      \"informationUri\": \"https://github.com/entropy-cloud/nop-entropy\"}},\n");
            out.write("    \"results\": [");
            boolean first = true;
            for (FileFindings finding : outcome.findings()) {
                for (Diagnostic d : finding.diagnostics()) {
                    if (!first) {
                        out.write(',');
                    }
                    first = false;
                    LineIndex lines = finding.lines();
                    out.write("\n      {\"ruleId\": " + quote(d.ruleId()));
                    out.write(", \"level\": " + quote(sarifLevel(d.severity())));
                    out.write(", \"message\": {\"text\": " + quote(d.message()) + "}");
                    out.write(", \"locations\": [{\"physicalLocation\": {\"artifactLocation\": {\"uri\": "
                            + quote(finding.displayPath()) + "}");
                    out.write(", \"region\": {\"startLine\": " + lines.startLine(d.range())
                            + ", \"endLine\": " + lines.endLine(d.range()) + "}}}]");
                    out.write('}');
                }
            }
            out.write(first ? "]}\n  }]}\n" : "\n    }]}\n");
        }
    }

    /**
     * The checkstyle XML dialect (the CI consumers' convention): one
     * {@code <file>} per scanned file with findings, severity mapped
     * error→error / warning→warning / info+hint→info.
     */
    public static final class CheckstyleXml implements Reporter {
        @Override
        public void render(CheckOutcome outcome, Writer out) throws IOException {
            out.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            out.write("<checkstyle version=\"1.0.0\">\n");
            for (FileFindings finding : outcome.findings()) {
                if (finding.diagnostics().isEmpty()) {
                    continue;
                }
                out.write("  <file name=" + quote(Xml.escape(finding.displayPath())) + ">\n");
                for (Diagnostic d : finding.diagnostics()) {
                    out.write("    <error line=\"" + finding.lines().startLine(d.range())
                            + "\" severity=\"" + Xml.checkstyleSeverity(d.severity())
                            + "\" message=" + quote(Xml.escape(d.message()))
                            + " source=" + quote(Xml.escape(d.ruleId())) + "/>\n");
                }
                out.write("  </file>\n");
            }
            out.write("</checkstyle>\n");
        }
    }

    /**
     * The plain JSON face: the diagnostics array plus the severity totals —
     * the stable machine contract for ad-hoc tooling.
     */
    public static final class Json implements Reporter {
        @Override
        public void render(CheckOutcome outcome, Writer out) throws IOException {
            out.write("{\n  \"diagnostics\": [");
            boolean first = true;
            for (FileFindings finding : outcome.findings()) {
                for (Diagnostic d : finding.diagnostics()) {
                    if (!first) {
                        out.write(',');
                    }
                    first = false;
                    LineIndex lines = finding.lines();
                    out.write("\n    {\"path\": " + quote(finding.displayPath()));
                    out.write(", \"line\": " + lines.startLine(d.range())
                            + ", \"endLine\": " + lines.endLine(d.range()));
                    out.write(", \"severity\": " + quote(d.severity()));
                    out.write(", \"ruleId\": " + quote(d.ruleId()));
                    out.write(", \"message\": " + quote(d.message()) + "}");
                }
            }
            RunSummary s = outcome.summary();
            out.write(first ? "],\n" : "\n  ],\n");
            out.write("  \"summary\": {\"filesScanned\": " + s.getFilesScanned());
            out.write(", \"error\": " + s.getErrorCount() + ", \"warning\": " + s.getWarningCount());
            out.write(", \"info\": " + s.getInfoCount() + ", \"hint\": " + s.getHintCount());
            out.write(", \"other\": " + s.getOtherCount() + ", \"total\": "
                    + s.getTotalDiagnostics() + "}\n}\n");
        }
    }

    /**
     * The JUnit XML face: one {@code <testsuite>} per file, one
     * {@code <testcase>} per diagnostic with {@code name = ruleId} (plan
     * Decision 1) — CI systems read it as test failures.
     */
    public static final class JunitXml implements Reporter {
        @Override
        public void render(CheckOutcome outcome, Writer out) throws IOException {
            out.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            out.write("<testsuites>\n");
            for (FileFindings finding : outcome.findings()) {
                if (finding.diagnostics().isEmpty()) {
                    continue;
                }
                int errors = 0;
                for (Diagnostic d : finding.diagnostics()) {
                    if ("error".equals(d.severity())) {
                        errors++;
                    }
                }
                out.write("  <testsuite name=" + quote(Xml.escape(finding.displayPath()))
                        + " tests=\"" + finding.diagnostics().size()
                        + "\" failures=\"" + errors + "\">\n");
                for (Diagnostic d : finding.diagnostics()) {
                    out.write("    <testcase name=" + quote(Xml.escape(d.ruleId()))
                            + " classname=" + quote(Xml.escape(finding.displayPath())) + ">\n");
                    out.write("      <failure message=" + quote(Xml.escape(d.message()))
                            + " type=\"" + d.severity() + "\">"
                            + Xml.escape(d.message()) + "</failure>\n");
                    out.write("    </testcase>\n");
                }
                out.write("  </testsuite>\n");
            }
            out.write("</testsuites>\n");
        }
    }

    private static String sarifLevel(String severity) {
        if ("error".equals(severity) || "warning".equals(severity)) {
            return severity;
        }
        return "note";
    }

    private static String quote(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * The XML escaping + severity mapping shared by the two XML formats.
     */
    private static final class Xml {
        private Xml() {
        }

        static String escape(String value) {
            StringBuilder sb = new StringBuilder(value.length());
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '<' -> sb.append("&lt;");
                    case '>' -> sb.append("&gt;");
                    case '"' -> sb.append("&quot;");
                    case '&' -> sb.append("&amp;");
                    default -> sb.append(c);
                }
            }
            return sb.toString();
        }

        static String checkstyleSeverity(String severity) {
            if ("error".equals(severity) || "warning".equals(severity)) {
                return severity;
            }
            return "info";
        }
    }
}

package io.nop.refactor.core.cli;

import io.nop.refactor.core.NonApply;
import io.nop.refactor.core.RefactorResult;

import java.io.IOException;

/**
 * The refactor CLI's render faces (nop-refactor WI7): one payload, two
 * renderings — a human console face (mode header, the unified diff, the
 * nonApplied enumeration, the stats line) and a machine JSON face over the
 * same {@link RefactorResult} (the MachineReporters json precedent: a
 * hand-written, escaping-aware serializer — no second payload, no field
 * drift, and the WI5 record surface is the single contract). Both faces
 * render over {@link Appendable}, so System.out and test Writers drive the
 * identical code path.
 */
public final class RefactorRenderers {

    private RefactorRenderers() {
    }

    public static void renderConsole(RefactorOptions.Mode mode, RefactorResult result,
                                     Appendable out) throws IOException {
        out.append(mode == RefactorOptions.Mode.PREVIEW ? "preview" : "apply");
        out.append(result.applied() ? " (written)\n" : " (dry-run, nothing written)\n");
        if (!result.diff().isEmpty()) {
            out.append(result.diff());
            if (!result.diff().endsWith("\n")) {
                out.append('\n');
            }
        }
        if (!result.nonApplied().isEmpty()) {
            out.append("non-applied (").append(String.valueOf(result.nonApplied().size()))
                    .append("):\n");
            for (NonApply nonApply : result.nonApplied()) {
                out.append("  [").append(nonApply.reason().name()).append("] ")
                        .append(nonApply.path()).append(": ").append(nonApply.detail())
                        .append('\n');
            }
        }
        out.append("files=").append(String.valueOf(result.stats().filesAffected()));
        out.append(", edits=").append(String.valueOf(result.stats().editsApplied()));
        out.append(", conflicts=").append(String.valueOf(result.stats().skipped().conflict()));
        out.append(", outOfScope=").append(String.valueOf(result.stats().skipped().outOfScope()));
        out.append(", unresolved=")
                .append(String.valueOf(result.stats().skipped().unresolvedTarget()));
        out.append(", parseOk=").append(String.valueOf(result.verification().parseOk()));
        out.append(", errorNodes=").append(String.valueOf(result.verification().errorNodeCount()));
        out.append(", residual=")
                .append(String.valueOf(result.verification().residualDiagnostics()))
                .append('\n');
    }

    public static void renderJson(RefactorResult result, Appendable out) throws IOException {
        out.append("{\n  \"applied\": ").append(String.valueOf(result.applied()));
        out.append(",\n  \"edits\": [");
        boolean first = true;
        for (var edit : result.edits()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append("\n    {\"path\": ").append(quote(edit.path()));
            out.append(", \"startByte\": ").append(String.valueOf(edit.range().startByte()));
            out.append(", \"endByte\": ").append(String.valueOf(edit.range().endByte()));
            out.append(", \"summary\": ").append(quote(edit.summary())).append('}');
        }
        out.append(first ? "],\n" : "\n  ],\n");
        out.append("  \"diff\": ").append(quote(result.diff()));
        out.append(",\n  \"verification\": {\"parseOk\": ")
                .append(String.valueOf(result.verification().parseOk()));
        out.append(", \"errorNodeCount\": ")
                .append(String.valueOf(result.verification().errorNodeCount()));
        out.append(", \"residualDiagnostics\": ")
                .append(String.valueOf(result.verification().residualDiagnostics()));
        out.append(", \"symbolIntact\": ")
                .append(result.verification().symbolIntact() == null
                        ? "null"
                        : String.valueOf(result.verification().symbolIntact()))
                .append('}');
        out.append(",\n  \"stats\": {\"filesAffected\": ")
                .append(String.valueOf(result.stats().filesAffected()));
        out.append(", \"editsApplied\": ")
                .append(String.valueOf(result.stats().editsApplied()));
        out.append(", \"skipped\": {\"conflict\": ")
                .append(String.valueOf(result.stats().skipped().conflict()));
        out.append(", \"outOfScope\": ")
                .append(String.valueOf(result.stats().skipped().outOfScope()));
        out.append(", \"unresolvedTarget\": ")
                .append(String.valueOf(result.stats().skipped().unresolvedTarget()))
                .append('}');
        out.append(", \"costTier\": ").append(quote(result.stats().costTier().name()));
        out.append(", \"residualRuleCount\": ")
                .append(String.valueOf(result.stats().residualRuleCount()))
                .append('}');
        out.append(",\n  \"nonApplied\": [");
        first = true;
        for (NonApply nonApply : result.nonApplied()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append("\n    {\"reason\": ").append(quote(nonApply.reason().name()));
            out.append(", \"path\": ").append(quote(nonApply.path()));
            out.append(", \"detail\": ").append(quote(nonApply.detail())).append('}');
        }
        out.append(first ? "]\n" : "\n  ]\n");
        out.append("}\n");
    }

    private static String quote(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 8);
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
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
}

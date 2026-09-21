package io.nop.lint.core.cli;

import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.node.LineIndex;

import java.util.List;
import java.util.Objects;

/**
 * One linted file's findings, carried together with its {@link LineIndex}
 * so the console renderer applies the same 1-based line/endLine conversion
 * as the RuleTester (design 03 §4.2 v1 行号语义: start byte and last byte
 * each fall on their own line). Diagnostics stay in engine (rule/match)
 * order — the run is deterministic, so the render order is stable.
 */
public record FileFindings(String displayPath, LineIndex lines, List<Diagnostic> diagnostics) {

    public FileFindings {
        if (displayPath == null || displayPath.isBlank())
            throw new IllegalArgumentException("displayPath must not be blank");
        Objects.requireNonNull(lines, "lines must not be null");
        Objects.requireNonNull(diagnostics, "diagnostics must not be null");
        diagnostics = List.copyOf(diagnostics);
    }
}

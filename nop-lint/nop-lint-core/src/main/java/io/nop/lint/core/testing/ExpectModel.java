package io.nop.lint.core.testing;

import java.util.List;
import java.util.Objects;

/**
 * The parsed content of one {@code *.expect} fixture file: where it came
 * from and the expected diagnostics, in declaration order. Immutable; the
 * assertion layer consumes this model (order-insensitive matching happens
 * against real {@code LintResult} diagnostics).
 */
public record ExpectModel(String resourcePath, List<ExpectDiagnostic> diagnostics) {

    public ExpectModel {
        Objects.requireNonNull(resourcePath, "resourcePath must not be null");
        Objects.requireNonNull(diagnostics, "diagnostics must not be null");
        diagnostics = List.copyOf(diagnostics);
    }

    @Override
    public String toString() {
        return "ExpectModel[" + resourcePath + ", " + diagnostics.size() + " diagnostics]";
    }
}

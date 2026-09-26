package io.nop.refactor.core.symbol;

import io.nop.lint.core.node.SourceRange;

import java.util.Objects;

/**
 * One reference to a resolved target symbol (plan 09 adjudication 3): the
 * identifier occurrence's name, byte range, and containing file. Only
 * occurrences that pass the binding filter (same package / import /
 * qualified name) surface here — the reference-count face the rename
 * ladder's verification builds on.
 */
public record SymbolReference(String name, SourceRange range, String path) {

    public SymbolReference {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(range, "range must not be null");
        Objects.requireNonNull(path, "path must not be null");
    }
}

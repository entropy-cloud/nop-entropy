package io.nop.refactor.core.symbol;

import io.nop.lint.core.node.SourceRange;

import java.util.Objects;

/**
 * One declaration the index knows about (plan 09 adjudication 3): the
 * simple name, its byte range (the identifier itself), the kind, the
 * declaring file's path, and — for types — the fully qualified name the
 * binding filter matches imports against. {@code fqn} is blank for
 * non-type declarations.
 */
public record SymbolDeclaration(String name, SourceRange range, SymbolKind kind,
                                String path, String fqn) {

    public SymbolDeclaration {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(range, "range must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(path, "path must not be null");
        fqn = fqn == null ? "" : fqn;
    }

    public boolean hasFqn() {
        return !fqn.isBlank();
    }
}

package io.nop.refactor.core.operation;

import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.refactor.core.NopRefactorException;
import io.nop.refactor.core.symbol.SymbolResolverAdapter;
import io.nop.refactor.core.symbol.SymbolResolverAdapter.SourceFile;
import io.nop.refactor.core.symbol.SymbolResolverAdapter.SymbolTarget;

import java.util.List;
import java.util.Objects;

/**
 * The rename operation's input (plan 10 adjudication 1 — the baseline §三
 * RenameInput triple plus the WI10 module face): the target locator (FQN or
 * file + byte offset — {@link SymbolTarget} enforces exactly-one-form), the
 * new name, the symbol-domain scope (v1 is always module-scoped — WI2
 * adjudication 1), the single module's source files the rename works over,
 * and the language adapter that owns the resolution semantics (core depends
 * only on the SPI interface — runtime injection, zero core→java dependency;
 * this shape is the WI12 GraphQL RenameInput's predecessor). The language
 * and engine feed the framework's single apply/verify points exactly as the
 * codemod request does — the rename never grows a second landing path.
 */
public record RenameRequest(SymbolTarget target, String newName, RenameScope scope,
                            List<SourceFile> files, SymbolResolverAdapter resolver,
                            LintLanguage language, LintEngine engine) {

    public RenameRequest {
        if (target == null) {
            throw new NopRefactorException("a rename requires a non-null target locator "
                    + "(FQN or file + byte offset; fail-closed)");
        }
        if (scope == null) {
            throw new NopRefactorException("a rename requires a non-null symbol-domain "
                    + "scope (v1 is module-scoped only; fail-closed)");
        }
        if (newName == null || !newName.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            throw new NopRefactorException("a rename requires a valid identifier as "
                    + "'newName' (got " + (newName == null ? "null" : "'" + newName + "'")
                    + "; fail-closed)");
        }
        Objects.requireNonNull(files, "files must not be null (the rename works over the "
                + "module's files; fail-closed)");
        files = List.copyOf(files);
        Objects.requireNonNull(resolver, "resolver must not be null (the resolution "
                + "semantics are injected at runtime; fail-closed)");
        Objects.requireNonNull(language, "language must not be null (the framework's "
                + "single apply point re-parses through it; fail-closed)");
        Objects.requireNonNull(engine, "engine must not be null (the framework's single "
                + "verify point requires it; fail-closed)");
    }
}

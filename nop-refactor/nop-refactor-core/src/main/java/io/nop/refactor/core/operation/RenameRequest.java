package io.nop.refactor.core.operation;

import io.nop.refactor.core.NopRefactorException;
import io.nop.refactor.core.symbol.SymbolResolverAdapter.SymbolTarget;

/**
 * The rename operation's input (plan 09 adjudication 5 — the baseline §三
 * RenameInput triple, transcribed): the target locator (FQN or file + byte
 * offset — {@link SymbolTarget} enforces exactly-one-form), the new name, and
 * the symbol domain scope — v1 is always module-scoped (WI2 adjudication 1;
 * the project/classpath scope was rejected out of budget).
 */
public record RenameRequest(SymbolTarget target, String newName, RenameScope scope) {

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
        if (scope != RenameScope.MODULE) {
            throw new NopRefactorException("v1 rename is module-scoped only (WI2 "
                    + "adjudication: the project/classpath domain is out of budget); got "
                    + scope);
        }
    }
}

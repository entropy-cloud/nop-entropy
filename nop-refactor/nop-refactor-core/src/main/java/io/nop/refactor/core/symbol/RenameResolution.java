package io.nop.refactor.core.symbol;

import io.nop.lint.core.node.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * The four-state rename resolution (plan 10 adjudication 2): the explicit
 * outcome of asking an adapter for a first-rung rename plan — RESOLVED with
 * the target declaration plus the byte ranges to rewrite (the declaration's
 * own identifier and every bound reference) and the pre-computed
 * symbol-intact assertion, or one of the three structured refusals (CONFLICT
 * / OUT_OF_SCOPE / UNRESOLVED), each with non-blank context. A refusal never
 * produces ranges; a RESOLVED state never carries a blank detail (the
 * constructor is fail-closed, same face as {@link SymbolResolverAdapter.Resolution}).
 */
public record RenameResolution(State state, String detail, SymbolDeclaration declaration,
                               List<SourceRange> occurrences, Boolean symbolIntact) {

    public enum State {
        /** the rename is plannable: declaration + rewrite ranges + assertion ride along. */
        RESOLVED,
        /** the new name already exists in the rename's conflict domain (plan 10 adjudication 3). */
        CONFLICT,
        /** the target kind is outside the first rung (locals/parameters only; WI11 owns the rest). */
        OUT_OF_SCOPE,
        /** the locator does not resolve inside the indexed module. */
        UNRESOLVED
    }

    public RenameResolution {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(detail, "detail must not be null");
        occurrences = List.copyOf(occurrences == null ? List.of() : occurrences);
        if (state == State.RESOLVED) {
            Objects.requireNonNull(declaration, "a RESOLVED rename must carry its declaration");
            Objects.requireNonNull(symbolIntact,
                    "a RESOLVED rename must carry its pre-computed symbolIntact assertion "
                            + "(null is the rewrite-face face, never a rename face; fail-closed)");
        } else {
            if (detail.isBlank()) {
                throw new io.nop.refactor.core.NopRefactorException("a " + state
                        + " rename resolution must carry non-blank context for the caller's "
                        + "retry decision (fail-closed)");
            }
            if (!occurrences.isEmpty() || declaration != null || symbolIntact != null) {
                throw new io.nop.refactor.core.NopRefactorException("a " + state
                        + " rename resolution must not carry declaration/ranges/assertion "
                        + "(a refusal that looks plannable is a silent-contract break; fail-closed)");
            }
        }
    }

    public static RenameResolution resolved(SymbolDeclaration declaration,
                                            List<SourceRange> occurrences, boolean symbolIntact) {
        return new RenameResolution(State.RESOLVED, "", declaration, occurrences, symbolIntact);
    }

    public static RenameResolution conflict(String detail) {
        return new RenameResolution(State.CONFLICT, detail, null, null, null);
    }

    public static RenameResolution outOfScope(String detail) {
        return new RenameResolution(State.OUT_OF_SCOPE, detail, null, null, null);
    }

    public static RenameResolution unresolved(String detail) {
        return new RenameResolution(State.UNRESOLVED, detail, null, null, null);
    }
}

package io.nop.refactor.core.symbol;

import io.nop.lint.core.node.SourceRange;

import java.util.ArrayList;
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
                               List<SourceRange> occurrences, Boolean symbolIntact,
                               List<FileRewrite> fileRewrites) {

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
        fileRewrites = List.copyOf(fileRewrites == null ? List.of() : fileRewrites);
        if (state == State.RESOLVED) {
            Objects.requireNonNull(declaration, "a RESOLVED rename must carry its declaration");
            Objects.requireNonNull(symbolIntact,
                    "a RESOLVED rename must carry its pre-computed symbolIntact assertion "
                            + "(null is the rewrite-face face, never a rename face; fail-closed)");
            if (fileRewrites.isEmpty()) {
                throw new io.nop.refactor.core.NopRefactorException("a RESOLVED rename must "
                        + "carry at least one file rewrite (a rename without rewrites is a "
                        + "silent no-op; fail-closed)");
            }
        } else {
            if (detail.isBlank()) {
                throw new io.nop.refactor.core.NopRefactorException("a " + state
                        + " rename resolution must carry non-blank context for the caller's "
                        + "retry decision (fail-closed)");
            }
            if (!occurrences.isEmpty() || declaration != null || symbolIntact != null
                    || !fileRewrites.isEmpty()) {
                throw new io.nop.refactor.core.NopRefactorException("a " + state
                        + " rename resolution must not carry declaration/ranges/assertion "
                        + "(a refusal that looks plannable is a silent-contract break; fail-closed)");
            }
        }
    }

    /**
     * The single-file factory (the WI10 first rung): the rewrite set wraps
     * into one {@link FileRewrite} on the declaration's own file.
     */
    public static RenameResolution resolved(SymbolDeclaration declaration,
                                            List<RenameSpan> spans, boolean symbolIntact) {
        List<FileRewrite> rewrites = List.of(new FileRewrite(declaration.path(), spans));
        return new RenameResolution(State.RESOLVED, "", declaration, spansOf(rewrites),
                symbolIntact, rewrites);
    }

    private static List<SourceRange> spansOf(List<FileRewrite> rewrites) {
        List<SourceRange> ranges = new ArrayList<>();
        for (FileRewrite rewrite : rewrites) {
            for (RenameSpan span : rewrite.spans()) {
                ranges.add(span.range());
            }
        }
        return ranges;
    }

    /**
     * The cross-file factory (the WI11 second rung): {@code occurrences}
     * mirrors the declaration file's entry for the single-file compatibility
     * face; a non-blank {@code detail} carries the rewrite summary (plan 11
     * adjudication 9's observable-exclusion counter rides here).
     */
    public static RenameResolution resolvedMultiFile(SymbolDeclaration declaration,
                                                     List<FileRewrite> fileRewrites,
                                                     boolean symbolIntact, String detail) {
        List<SourceRange> targetRanges = spansOf(fileRewrites.stream()
                .filter(rewrite -> rewrite.path().equals(declaration.path()))
                .toList());
        return new RenameResolution(State.RESOLVED, detail, declaration, targetRanges,
                symbolIntact, fileRewrites);
    }

    public static RenameResolution conflict(String detail) {
        return new RenameResolution(State.CONFLICT, detail, null, null, null, null);
    }

    public static RenameResolution outOfScope(String detail) {
        return new RenameResolution(State.OUT_OF_SCOPE, detail, null, null, null, null);
    }

    public static RenameResolution unresolved(String detail) {
        return new RenameResolution(State.UNRESOLVED, detail, null, null, null, null);
    }
}

package io.nop.refactor.core.symbol;

import io.nop.lint.core.node.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * The language-agnostic symbol resolution SPI (roadmap WI9, plan 09
 * adjudication 3 — the WI2 adjudications as a contract): an adapter owns
 * parsing and symbol semantics for one language; core stays language-free
 * (it never sees a language AST). The v1 semantic operation set is the
 * declaration index (simple name → declarations) plus the reference
 * resolution over it, with the binding filter (same package / import /
 * qualified name) and an explicit unresolvable result — never a silent
 * null. The symbol domain is v1 module-scoped (WI2 adjudication 1); the
 * reference search rides the operation's embedded lightweight index, not
 * nop-code (WI2 adjudication 2 — the CORE→CODE edge stays unwired).
 *
 * <p>Value types are language-neutral: a name (String), a byte range
 * ({@link SourceRange}), a kind ({@link SymbolKind}). The Java adapter is
 * the first implementation module (nop-refactor-java).</p>
 */
public interface SymbolResolverAdapter {

    /**
     * Builds the declaration index over one module's source files (the
     * single-module symbol domain of the WI2 adjudication).
     */
    DeclarationIndex buildIndex(List<SourceFile> files);

    /**
     * Resolves the target and returns its references under the binding
     * filter. An unresolvable target is an explicit {@link Resolution}
     * with {@code resolved=false} and a non-blank detail — never a silent
     * empty list posing as "zero references".
     */
    Resolution resolveReference(DeclarationIndex index, SymbolTarget target);

    /**
     * The four-state rename resolution (plan 10 adjudication 2, the WI10
     * first rung): locates the target declaration through the index (the
     * index's kind table decides OUT_OF_SCOPE for non-variable kinds —
     * definitionOf cannot locate method/type name positions), rejects
     * conflicts on the method-boundary + field-face domain, collects the
     * rewrite ranges (the declaration's identifier plus every bound
     * NameExpr occurrence, bound through the scope semantics — never a
     * bare simple-name text scan), and pre-computes the symbol-intact
     * assertion on the planned post-rename content.
     */
    RenameResolution renameResolution(DeclarationIndex index, SymbolTarget target, String newName);

    /**
     * One indexed source file: its display path and full content.
     */
    record SourceFile(String path, String content) {

        public SourceFile {
            Objects.requireNonNull(path, "path must not be null");
            Objects.requireNonNull(content, "content must not be null");
        }
    }

    /**
     * The opaque index handle an adapter builds and consumes. Core sees no
     * structure — the adapter is free to index however its language needs.
     */
    interface DeclarationIndex {

        /**
         * The number of files the index covers (a cheap sanity face for
         * consumers and tests).
         */
        int fileCount();
    }

    /**
     * The rename target locator (baseline §三 RenameInput, plan 09
     * adjudication 5): exactly one of the two machine-friendly forms — an
     * FQN, or a file plus a byte offset. No cursor, no selection.
     */
    record SymbolTarget(String fqn, String path, Long byteOffset) {

        public SymbolTarget {
            boolean hasFqn = fqn != null && !fqn.isBlank();
            boolean hasOffset = path != null && !path.isBlank() && byteOffset != null;
            if (hasFqn == hasOffset) {
                throw new io.nop.refactor.core.NopRefactorException(
                        "a rename target carries exactly one of the two locator forms "
                                + "(FQN, or file + byte offset); got "
                                + (hasFqn ? "both" : "neither") + " (fail-closed)");
            }
        }

        public static SymbolTarget ofFqn(String fqn) {
            return new SymbolTarget(fqn, null, null);
        }

        public static SymbolTarget ofOffset(String path, long byteOffset) {
            return new SymbolTarget(null, path, byteOffset);
        }

        public boolean isFqnForm() {
            return fqn != null && !fqn.isBlank();
        }
    }

    /**
     * The resolution outcome: resolved with the reference list, or
     * explicitly unresolved with context for the caller's retry decision
     * (vision principle 6 — structured, never a silent skip).
     */
    record Resolution(boolean resolved, String detail, List<SymbolReference> references) {

        public Resolution {
            Objects.requireNonNull(detail, "detail must not be null");
            references = List.copyOf(references == null ? List.of() : references);
            if (resolved && detail.isBlank() && references.isEmpty()) {
                throw new io.nop.refactor.core.NopRefactorException(
                        "a resolved resolution must carry references or context "
                                + "(an empty resolution is indistinguishable from an "
                                + "unresolved one; fail-closed)");
            }
        }

        public static Resolution of(List<SymbolReference> references) {
            // a resolved-but-unreferenced target (an unused local, an
            // unreferenced private field) is a legal outcome, not a
            // resolution failure — the non-blank detail keeps it
            // distinguishable from an unresolved target (fail-closed
            // constructor contract)
            return new Resolution(true, references.isEmpty()
                    ? "resolved: zero bound references in the indexed module" : "",
                    references);
        }

        public static Resolution unresolved(String detail) {
            return new Resolution(false, detail, List.of());
        }
    }
}

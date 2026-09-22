package io.nop.lint.core.semantic;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.SourcePositions;

import java.util.Objects;

/**
 * The per-run query support a type-consuming constraint evaluates through
 * (roadmap item 20 Phase 2): the run's {@link TypeResolver}, the file being
 * linted, and the source bytes that turn the facade's byte offsets into
 * wire positions. Immutable; captured at rule-compile time, so the per-match
 * constraint context (design 01 §5 adjudicated two-field form) stays
 * untouched.
 *
 * <p>Null-support runs are the documented "no L2 in this run" state: the
 * engine's profile gate keeps type-consuming rules out of them, and a
 * constraint that still reaches a query fails loudly (the guarded-fail
 * invariant of the typeOf constraint).</p>
 */
public final class TypeQuerySupport {

    private final TypeResolver resolver;
    private final String filePath;
    private final byte[] source;

    /**
     * @param resolver the run's resolver; null only in runs that can never
     *                 evaluate a type query (the gate must keep those rules
     *                 out)
     * @param filePath the path the file is linted under (resolver project
     *                 relative or absolute, per the resolver's contract)
     * @param source   the UTF-8 source bytes the node ranges slice
     */
    public TypeQuerySupport(TypeResolver resolver, String filePath, byte[] source) {
        this.resolver = resolver;
        this.filePath = filePath == null || filePath.isBlank()
                ? null
                : filePath;
        this.source = source == null ? new byte[0] : source;
    }

    public TypeResolver resolver() {
        return resolver;
    }

    public boolean hasFileContext() {
        return filePath != null;
    }

    /**
     * The assignability answer for the capture node's position (the {@code
     * typeOf} constraint's evaluation, design 01 §3.2 Decision: {@code is}
     * holds when the capture's type is assignable to the declared type in
     * the project's checker).
     *
     * @throws TypeResolutionException when the backend cannot answer
     * @throws NopLintException        when this support cannot form a
     *                                 well-formed query (no resolver / no
     *                                 file context) — the gate invariant
     *                                 broke and the run must surface it
     */
    public boolean isAssignableTo(LintNode capture, String expectedType) {
        Objects.requireNonNull(capture, "capture must not be null");
        if (resolver == null)
            throw new NopLintException("a type query ran without a TypeResolver (the profile gate "
                    + "must keep type-consuming rules out of L2-less runs; invariant broken)");
        if (!hasFileContext())
            throw new NopLintException("a type query ran without a file path (the run must lint a "
                    + "named file for L2 rules; invariant broken)");
        SourcePositions.LineCol pos = SourcePositions.lineColUtf16(source, capture.range().startByte());
        return resolver.isAssignableTo(filePath, pos.line(), pos.colUtf16(), expectedType);
    }
}

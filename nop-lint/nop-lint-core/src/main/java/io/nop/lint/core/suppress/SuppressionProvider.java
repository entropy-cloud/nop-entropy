package io.nop.lint.core.suppress;

import io.nop.lint.core.node.LintTree;

import java.util.List;

/**
 * Extracts annotation-carried suppression spans from one parsed tree
 * (design 09 §3). Implemented per language (the Java binding supplies the
 * {@code @SuppressWarnings} extraction), because locating annotation nodes
 * and their declaration scope requires the language's grammar — the core
 * engine stays grammar-agnostic.
 *
 * <p>Extraction must fail closed: a malformed nop-lint annotation value is a
 * contract violation and is reported as an exception, never fabricated into
 * a suppression (Minimum Rules #24). Values that belong to other tools (for
 * example PMD/ErrorProne aliases, roadmap item 29) produce no span.</p>
 */
public interface SuppressionProvider {

    /**
     * The suppression spans declared by annotations in {@code tree}, in
     * source order.
     */
    List<SuppressionSpan> extractSpans(LintTree tree);
}

package io.nop.jq;

import io.nop.jq.ast.JqAstNode;

import java.util.List;

/**
 * Compiled jq query interface.
 */
public interface IJsonQuery {
    /**
     * Execute the query against a root object and return all outputs.
     */
    List<Object> apply(Object root);

    /**
     * Execute with host-provided resource controls (tool sandbox seam):
     * {@code envVars} replaces the process environment for {@code $ENV}/
     * {@code env} (null = default {@code System.getenv()}); {@code deadlineAtMillis}
     * bounds wall-clock time (0 = no deadline). Implementations may ignore the
     * controls; the default falls back to {@link #apply(Object)}.
     */
    default List<Object> apply(Object root, java.util.Map<String, String> envVars, long deadlineAtMillis) {
        return apply(root);
    }

    /**
     * Execute the query and return only the first output.
     */
    Object applyOne(Object root);

    /**
     * Get the original jq expression string.
     */
    String getExpression();

    /**
     * Get the AST root node (for debugging/inspection).
     */
    JqAstNode getAst();

    /**
     * Check if this query supports direct execution (AST-based).
     */
    boolean isDirectExecution();

    /**
     * Get the XLang expression (legacy, for backward compatibility).
     * Returns null for AST-based queries.
     */
    default String getXLangExpression() { return null; }
}

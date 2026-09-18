package io.nop.jq.jq;

import io.nop.jq.jq.ast.JqAstNode;

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

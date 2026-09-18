package io.nop.jq.jq;

import java.util.List;

/**
 * Compiled jq query that can be applied to different root objects.
 */
public interface IJsonQuery {
    /**
     * Apply the query to the root object and return all results.
     */
    List<Object> apply(Object root);

    /**
     * Apply the query and return only the first result.
     */
    Object applyOne(Object root);

    /**
     * Get the original jq expression.
     */
    String getExpression();

    /**
     * Get the translated XLang expression.
     */
    String getXLangExpression();
}

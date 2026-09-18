package io.nop.jq.jsonpath;

import io.nop.jq.JsonAccessor;

/**
 * A predicate applied to array elements in a filter segment.
 */
public interface Filter {
    /**
     * @param accessor property accessor
     * @param root     the original root object
     * @param item     the current item being tested
     * @return true if the item passes the filter
     */
    boolean apply(JsonAccessor accessor, Object root, Object item);
}

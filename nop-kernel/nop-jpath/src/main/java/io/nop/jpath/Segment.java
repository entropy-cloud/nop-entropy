package io.nop.jpath;

import io.nop.jpath.JsonAccessor;

/**
 * A single step in a JsonPath pipeline. Each Segment takes the current result
 * and produces the next result.
 */
public interface Segment {
    /**
     * Evaluate this segment against the current value.
     *
     * @param accessor property accessor
     * @param root     the original root object (for filter expressions like @.root)
     * @param current  the current value in the pipeline
     * @return the result of this segment's evaluation
     */
    Object eval(JsonAccessor accessor, Object root, Object current);
}

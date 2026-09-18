package io.nop.jq.jsonpath;

import io.nop.jq.JsonAccessor;

/**
 * Segment that returns the root object. This is always the first segment.
 */
public class RootSegment implements Segment {
    public static final RootSegment INSTANCE = new RootSegment();

    RootSegment() {
    }

    @Override
    public Object eval(JsonAccessor accessor, Object root, Object current) {
        return root;
    }

    @Override
    public String toString() {
        return "$";
    }
}

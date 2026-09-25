package io.nop.jq.jsonpath;

import io.nop.jq.JsonAccessor;

/**
 * Segment that sets a value on the parent object. Used for write operations.
 */
public class SetPropertySegment implements Segment {
    private final String propertyName;
    private final Object value;

    public SetPropertySegment(String propertyName, Object value) {
        this.propertyName = propertyName;
        this.value = value;
    }

    @Override
    public Object eval(JsonAccessor accessor, Object root, Object current) {
        if (current == null)
            return null;
        accessor.setProperty(current, propertyName, value);
        return current;
    }
}

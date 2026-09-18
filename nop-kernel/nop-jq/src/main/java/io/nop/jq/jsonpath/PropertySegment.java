package io.nop.jq.jsonpath;

import io.nop.jq.JsonAccessor;

/**
 * Segment that accesses a named property on the current object.
 */
public class PropertySegment implements Segment {
    private final String propertyName;

    public PropertySegment(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public Object eval(JsonAccessor accessor, Object root, Object current) {
        if (current == null)
            return null;
        return accessor.getProperty(current, propertyName);
    }

    @Override
    public String toString() {
        return "." + propertyName;
    }
}
